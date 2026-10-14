package com.desconecta.hp.mobile.ai.data

import com.desconecta.hp.mobile.cloud.AuthRepository
import com.desconecta.hp.mobile.cloud.AuthState
import com.desconecta.hp.mobile.cloud.RemoteGameApi
import com.desconecta.hp.mobile.cloud.toRemoteFields
import com.desconecta.hp.mobile.data.db.AiSettingsEntity
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.mobile.sync.SyncOp
import com.desconecta.hp.mobile.sync.SyncQueue
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.datalayer.WireCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Progreso del periodo de aprendizaje (7 días con datos antes de predecir o sugerir límites). */
data class LearningState(val day: Int, val of: Int, val done: Boolean)

/**
 * Ajustes de IA: Room es la caché local y Supabase (ai_settings) la fuente de verdad cuando
 * hay cuenta. Gana el cambio más reciente; el modo familiar lo decide el perfil del servidor
 * y apaga la nube.
 */
@Singleton
class AiSettingsRepository @Inject constructor(
    private val db: HpDatabase,
    private val queue: SyncQueue,
    private val auth: AuthRepository,
    private val remote: RemoteGameApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val dao get() = db.aiSettings()

    val settings: StateFlow<AiSettings> = dao.observe()
        .map { it?.let(::decode) ?: AiSettings() }
        .stateIn(scope, SharingStarted.Eagerly, AiSettings())

    @OptIn(ExperimentalCoroutinesApi::class)
    val learning: Flow<LearningState> = settings.flatMapLatest { s ->
        dao.daysSince(dayKey(s.learningStartedAt)).map { days ->
            LearningState(AiSettings.learningDay(days), AiSettings.LEARNING_DAYS, !AiSettings.isLearning(days))
        }
    }

    init {
        // Primera vez: el aprendizaje empieza hoy.
        scope.launch {
            mutex.withLock {
                if (dao.get() == null) save(AiSettings(learningStartedAt = System.currentTimeMillis()), push = false)
            }
        }
    }

    private val signedIn get() = auth.state.value is AuthState.SignedIn

    /** Aplica un cambio del jugador, lo guarda y, con cuenta, lo encola para Supabase. */
    suspend fun update(transform: (AiSettings, Long) -> AiSettings) = mutex.withLock {
        val current = dao.get()?.let(::decode) ?: AiSettings()
        val next = transform(current, System.currentTimeMillis())
        if (next != current) save(next, push = true)
    }

    /**
     * Trae lo del servidor. [adopt] = acaba de entrar otra cuenta: se toman sus ajustes tal
     * cual (igual que el progreso). Si no, gana lo más reciente, salvo que haya cambios
     * locales aún sin subir.
     */
    suspend fun mergeServer(adopt: Boolean = false) {
        if (!signedIn) return
        val (row, family) = remote.fetchAiSettings()
        row ?: return
        mutex.withLock {
            val server = row.toModel(family)
            val local = dao.get()?.let(::decode) ?: AiSettings()
            val next = when {
                adopt -> server
                queue.hasPendingAiSettings() -> local.copy(familyMode = family, cloudAllowed = local.cloudAllowed && !family)
                else -> AiSettings.resolve(local, server, family)
            }
            if (next != local) save(next, push = false)
        }
    }

    /**
     * "Borrar mis datos de IA": en la nube (insights, voz) si hay cuenta, y reinicia el
     * aprendizaje. Los ajustes (qué está activo, tono) se conservan.
     */
    suspend fun deleteAiData(): Result<Unit> = runCatching {
        if (signedIn) remote.deleteMyAiData()
        mutex.withLock {
            val current = dao.get()?.let(::decode) ?: AiSettings()
            val now = System.currentTimeMillis()
            save(current.copy(learningStartedAt = now, updatedAt = now), push = signedIn)
        }
    }

    private suspend fun save(s: AiSettings, push: Boolean) {
        dao.put(AiSettingsEntity(json = WireCodec.json.encodeToString(AiSettings.serializer(), s)))
        if (push && signedIn) queue.enqueue(SyncOp.AiSettingsOp(s.toRemoteFields(), s.familyMode))
    }

    private fun decode(e: AiSettingsEntity): AiSettings? =
        runCatching { WireCodec.json.decodeFromString(AiSettings.serializer(), e.json) }.getOrNull()

    private fun dayKey(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs.takeIf { it > 0 } ?: System.currentTimeMillis())
            .atZone(ZoneId.systemDefault()).toLocalDate().toString()
}
