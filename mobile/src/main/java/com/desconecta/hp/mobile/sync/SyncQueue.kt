package com.desconecta.hp.mobile.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.mobile.data.db.SyncOpEntity
import com.desconecta.hp.shared.model.Ally
import com.desconecta.hp.shared.model.Monster
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Operaciones pendientes de subir. Cada una sabe su clave de fusión. */
@Serializable
sealed interface SyncOp {
    val coalesceKey: String?

    /** Estado del día (solo importa la última versión). */
    @Serializable @SerialName("session")
    data class Session(
        val dayKey: String,
        val hpStart: Int,
        val hpEnd: Int,
        val regen: Int,
        val potions: Int,
        val gameOver: Boolean,
        val gameOverAt: Long? = null,
    ) : SyncOp {
        override val coalesceKey get() = "session:$dayKey"
    }

    /** Uso de un objeto: cada uso es único (idempotencia por clientId en el servidor). */
    @Serializable @SerialName("use_item")
    data class UseItem(
        val type: String,
        val origin: String,
        val dayKey: String,
        val clientId: String,
        val packageName: String? = null,
    ) : SyncOp {
        override val coalesceKey: String? get() = null
    }

    /** Lista completa de monstruos y aliadas (incluye límites). */
    @Serializable @SerialName("monsters")
    data class Monsters(val monsters: List<Monster>, val allies: List<Ally>) : SyncOp {
        override val coalesceKey get() = "monsters"
    }

    @Serializable @SerialName("close_day")
    data class CloseDay(val dayKey: String) : SyncOp {
        override val coalesceKey get() = "close:$dayKey"
    }

    @Serializable @SerialName("start_week")
    data class StartWeek(val dayKey: String) : SyncOp {
        override val coalesceKey get() = "week:$dayKey"
    }

    /**
     * Ajustes de IA: solo importa la última versión. Lleva también el modo familiar (columna
     * del perfil), que se aplica ANTES: así el servidor nunca ve "nube permitida" con el
     * modo familiar aún activo, aunque los cambios se fusionen en la cola.
     */
    @Serializable @SerialName("ai_settings")
    data class AiSettingsOp(val fields: JsonObject, val familyMode: Boolean) : SyncOp {
        override val coalesceKey get() = "ai_settings"
    }

    /** Columnas editables del perfil (nombre, avatar, vida máxima…). */
    @Serializable @SerialName("profile")
    data class Profile(val fields: JsonObject) : SyncOp {
        override val coalesceKey get() = "profile"
    }
}

/** Estado de sincronización que muestra la interfaz (discreto en el teléfono). */
data class SyncStatus(
    val pending: Int = 0,
    val lastSyncAt: Long = 0L,
    val error: String? = null,
    val running: Boolean = false,
)

@Singleton
class SyncQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val db: HpDatabase,
) {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "op" }

    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()
    val pendingCount: Flow<Int> = db.sync().pendingCount()

    suspend fun enqueue(op: SyncOp) {
        db.sync().enqueue(
            SyncOpEntity(
                type = op::class.simpleName.orEmpty(),
                payload = json.encodeToString(SyncOp.serializer(), op),
                coalesceKey = op.coalesceKey,
                createdAt = System.currentTimeMillis(),
            ),
        )
        scheduleNow()
    }

    fun decode(entity: SyncOpEntity): SyncOp? = runCatching { json.decodeFromString(SyncOp.serializer(), entity.payload) }.getOrNull()

    /** Usos de objeto aún sin subir (para no "devolver" una poción ya gastada al fusionar). */
    suspend fun pendingItemUses(type: String): Int = db.sync().countMatching("UseItem", "\"type\":\"$type\"")

    /** Hay cambios de ajustes de IA sin subir (entonces no se pisan con lo del servidor). */
    suspend fun hasPendingAiSettings(): Boolean = db.sync().byKey("ai_settings") != null

    fun report(transform: (SyncStatus) -> SyncStatus) {
        _status.value = transform(_status.value)
    }

    suspend fun clear() = db.sync().clear()

    /** Subida en cuanto haya red (se fusiona con una pendiente si ya existía). */
    fun scheduleNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.NAME_NOW,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    /** Respaldo periódico: cada hora sube el uso agregado aunque no haya otras acciones. */
    fun schedulePeriodic() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(networkConstraint)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build(),
        )
    }

    fun cancelAll() {
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.NAME_NOW)
        WorkManager.getInstance(context).cancelUniqueWork(SyncWorker.NAME_PERIODIC)
    }

    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
}
