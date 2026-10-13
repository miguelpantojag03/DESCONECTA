package com.desconecta.hp.mobile.cloud

import android.os.Build
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.data.GameStore
import com.desconecta.hp.mobile.di.SupabaseGateway
import com.desconecta.hp.mobile.sync.SyncQueue
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Une el juego local con Supabase:
 *  · Activa la sincronización solo con sesión y modo nube (consentimiento).
 *  · Realtime (postgres_changes) en inventory, profiles, user_achievements,
 *    weekly_challenges y ai_insights: cuando el servidor cambia algo (level up, logro,
 *    objeto ganado), se descarga y se fusiona; GameRepository lo reenvía al reloj.
 *  · Marca un aviso para el reloj si la sesión caducó ("Inicia sesión en tu teléfono").
 *
 * Usa dagger.Lazy para GameRepository porque este también le encola operaciones.
 */
@Singleton
class CloudBridge @Inject constructor(
    private val gateway: SupabaseGateway,
    private val auth: AuthRepository,
    private val api: RemoteGameApi,
    private val queue: SyncQueue,
    private val repository: dagger.Lazy<GameRepository>,
    private val store: GameStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pullMutex = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var channel: RealtimeChannel? = null
    private var listenJobs = emptyList<Job>()

    fun isCloudActive(): Boolean = auth.state.value is AuthState.SignedIn

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            auth.state.collect { state ->
                when (state) {
                    is AuthState.SignedIn -> onSignedIn(state.userId)
                    is AuthState.Expired -> {
                        stopRealtime()
                        repository.get().setSyncIssue("Inicia sesión en tu teléfono")
                    }
                    else -> {
                        stopRealtime()
                        repository.get().setSyncIssue(null)
                    }
                }
            }
        }
        // Varias notificaciones seguidas (p. ej. close_day toca 4 tablas) → una sola descarga.
        scope.launch {
            changes.debounce(800).collect { runCatching { pullAndMerge() } }
        }
    }

    private suspend fun onSignedIn(userId: String) {
        val repo = repository.get()
        repo.setSyncIssue(null)
        // Solo se llega a una sesión pasando por la pantalla de consentimiento (o por el
        // enlace del correo que se pidió desde ella): ya se puede sincronizar.
        if (store.currentPrefs().accountMode != com.desconecta.hp.mobile.data.AccountMode.CLOUD) {
            store.setAccountMode(com.desconecta.hp.mobile.data.AccountMode.CLOUD)
        }
        runCatching { api.registerDevice("telefono", "${Build.MANUFACTURER} ${Build.MODEL}") }
        if (store.boundUserId() != userId) {
            // Invitado → cuenta, o cambio de cuenta: el progreso local NO se sube ni se mezcla.
            // Lo pendiente era de otra identidad: se descarta.
            queue.clear()
            val snapshot = runCatching { api.fetchSnapshot() }.getOrNull()
            repo.adoptAccount(snapshot)
            store.setBoundUserId(userId)
            // Sin descarga no sabemos si la cuenta es nueva: no se pisa su perfil.
            repo.enqueueFullState(includeProfile = snapshot != null && repo.isFreshProfile(snapshot))
        } else {
            repo.enqueueFullState()
        }
        queue.schedulePeriodic()
        queue.scheduleNow()
        startRealtime(userId)
    }

    private suspend fun startRealtime(userId: String) {
        val client = gateway.client ?: return
        if (channel != null) return
        val ch = client.channel("hp-user-$userId")
        val tables = listOf("inventory" to "user_id", "profiles" to "id", "user_achievements" to "user_id",
            "weekly_challenges" to "user_id", "ai_insights" to "user_id")
        listenJobs = tables.map { (table, column) ->
            ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                this.table = table
                filter(column, io.github.jan.supabase.postgrest.query.filter.FilterOperator.EQ, userId)
            }.let { flow -> scope.launch { flow.collect { changes.tryEmit(Unit) } } }
        }
        channel = ch
        runCatching { ch.subscribe() }
    }

    private suspend fun stopRealtime() {
        listenJobs.forEach { it.cancel() }
        listenJobs = emptyList()
        channel?.let { ch -> runCatching { gateway.client?.realtime?.removeChannel(ch) } }
        channel = null
    }

    /** Descarga lo que decide el servidor y lo fusiona con el estado local. */
    suspend fun pullAndMerge() = pullMutex.withLock {
        if (!isCloudActive()) return@withLock
        val snapshot = api.fetchSnapshot()
        repository.get().mergeServer(
            snapshot,
            pendingPotions = queue.pendingItemUses("pocion"),
            pendingShields = queue.pendingItemUses("escudo"),
        )
    }

    /** Sube el día ya (sin esperar a la cola): lo necesita Revivir para validar el Game Over. */
    suspend fun uploadSessionNow(op: com.desconecta.hp.mobile.sync.SyncOp.Session) = api.upsertSession(
        op.dayKey,
        op.hpStart,
        SessionUpdate(
            hpEnd = op.hpEnd.coerceAtMost(op.hpStart),
            regen = op.regen.coerceIn(0, 20),
            potions = op.potions.coerceIn(0, 10),
            gameOver = op.gameOver,
            gameOverAt = op.gameOverAt?.takeIf { op.gameOver }?.let { java.time.Instant.ofEpochMilli(it).toString() },
        ),
    )

    /** Revivir exige conexión y valida el PIN en el servidor. */
    suspend fun reviveOnline(pin: String, dayKey: String, clientId: String): RpcResult =
        api.revive(pin, dayKey, clientId)

    suspend fun setPinOnline(pin: String) = api.setPin(pin)
}
