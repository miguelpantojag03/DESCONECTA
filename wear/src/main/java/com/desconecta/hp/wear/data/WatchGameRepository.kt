package com.desconecta.hp.wear.data

import android.content.Context
import android.net.Uri
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.DataLayerPaths
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.datalayer.toWire
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.domain.Outcome
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.SampleData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

enum class Connection { UNKNOWN, CONNECTED, DISCONNECTED }

/** Lo que el reloj sabe del juego en este momento. */
data class WatchSnapshot(
    /** null = todavía no se ha recibido nunca un estado (primer uso / cargando). */
    val game: GameState? = null,
    val connection: Connection = Connection.UNKNOWN,
    val loading: Boolean = true,
    /** Epoch ms de la última sincronización recibida. */
    val lastSyncAt: Long = 0L,
    /** Modo demostración: datos de ejemplo y reglas aplicadas en el propio reloj. */
    val demo: Boolean = false,
)

/** Resultado de un comando enviado al teléfono, ya interpretado para la interfaz. */
sealed interface CommandOutcome {
    data object Ok : CommandOutcome
    data class Rejected(val result: CommandResult) : CommandOutcome
    /** El teléfono no está conectado: el comando no se pudo entregar. */
    data object Offline : CommandOutcome
    /** Se entregó pero no respondió a tiempo. */
    data object Timeout : CommandOutcome
}

/**
 * Fuente de datos del reloj. El reloj NO ejecuta reglas: refleja el estado que publica
 * el teléfono y le envía comandos ("usar poción"). Si se desconecta, sigue mostrando el
 * último estado (los DataItems quedan guardados localmente) y avisa de que no está al día.
 */
@Singleton
class WatchGameRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cloud: CloudWatchLink,
    private val feedback: WatchFeedback,
) {
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val _snapshot = MutableStateFlow(WatchSnapshot())
    val snapshot: StateFlow<WatchSnapshot> = _snapshot.asStateFlow()

    private val _events = MutableSharedFlow<WireEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WireEvent> = _events

    private val pending = ConcurrentHashMap<String, CompletableDeferred<CommandResult>>()

    // Conexión por dos vías: Bluetooth (Data Layer) y Wi‑Fi (Supabase). Basta una.
    @Volatile private var dataLayerConnected = false
    @Volatile private var cloudConnected = false

    /** true si el reloj está vinculado a la nube por código (aunque no haya red ahora). */
    val cloudLinked: Boolean get() = cloud.isLinked
    val cloudAvailable: Boolean get() = cloud.isAvailable

    /** Carga el último estado guardado y comprueba la conexión. Se llama al abrir la app. */
    suspend fun refresh() {
        if (_snapshot.value.demo) return
        runCatching {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(DataLayerPaths.GAME_STATE).build()
            // Sin teléfono emparejado los servicios de Google pueden tardar mucho: máximo 3 s.
            val items = withTimeoutOrNull(LOAD_TIMEOUT_MS) { dataClient.getDataItems(uri).await() } ?: return@runCatching
            try {
                items.firstOrNull()?.let { item ->
                    val map = DataMapItem.fromDataItem(item).dataMap
                    map.getByteArray(DataLayerPaths.KEY_STATE)?.let { applyState(it, map.getLong(DataLayerPaths.KEY_UPDATED_AT)) }
                }
            } finally {
                items.release()
            }
        }
        if (cloud.isLinked) {
            cloud.fetchState()?.let { applyState(it, System.currentTimeMillis()) }
            startCloud()
        }
        _snapshot.update { it.copy(loading = false) }
        checkConnection()
        if (_snapshot.value.connection == Connection.CONNECTED) {
            send(WireCommand.RequestSync(UUID.randomUUID().toString()), awaitResult = false)
        }
    }

    suspend fun checkConnection() {
        val connected = runCatching {
            withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                capabilityClient
                    .getCapability(DataLayerPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                    .await().nodes.isNotEmpty()
            } ?: false
        }.getOrDefault(false)
        setConnected(connected)
    }

    // --- Entradas desde WatchListenerService -------------------------------------------

    fun applyState(bytes: ByteArray, syncedAt: Long) {
        if (_snapshot.value.demo) return
        val state = runCatching { WireCodec.decodeState(bytes) }.getOrNull() ?: return
        _snapshot.update { it.copy(game = state, loading = false, lastSyncAt = syncedAt) }
    }

    fun onEvent(event: WireEvent) {
        _events.tryEmit(event)
    }

    fun onCommandResult(result: CommandResult) {
        pending.remove(result.requestId)?.complete(result)
    }

    fun setConnected(connected: Boolean) {
        dataLayerConnected = connected
        publishConnection()
    }

    private fun publishConnection() {
        if (_snapshot.value.demo) return
        val any = dataLayerConnected || cloudConnected
        _snapshot.update { it.copy(connection = if (any) Connection.CONNECTED else Connection.DISCONNECTED) }
    }

    // --- Nube (Supabase) ----------------------------------------------------------------

    private suspend fun startCloud() {
        if (cloud.isListening) return
        cloud.start(
            onState = { applyState(it, System.currentTimeMillis()) },
            onEvent = { event ->
                feedback.play(event, _snapshot.value.game?.settings?.haptics ?: true)
                onEvent(event)
            },
            onResult = ::onCommandResult,
            onConnected = { ok ->
                cloudConnected = ok
                publishConnection()
            },
        )
    }

    /** Vincula este reloj con el código de 6 dígitos que muestra el teléfono. */
    suspend fun linkWithCode(code: String): Result<Unit> {
        val result = cloud.redeem(code)
        if (result.isSuccess) {
            _snapshot.update { it.copy(demo = false) }
            refresh()
        }
        return result
    }

    suspend fun unlinkCloud() {
        cloud.unlink()
        cloudConnected = false
        publishConnection()
    }

    // --- Comandos -----------------------------------------------------------------------

    suspend fun send(command: WireCommand, awaitResult: Boolean = true): CommandOutcome {
        if (_snapshot.value.demo) return demoCommand(command)
        val node = runCatching {
            capabilityClient
                .getCapability(DataLayerPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await().nodes.minByOrNull { if (it.isNearby) 0 else 1 }
        }.getOrNull()
        if (node == null) {
            setConnected(false)
            return if (cloud.isListening) sendViaCloud(command, awaitResult) else CommandOutcome.Offline
        }
        val deferred = CompletableDeferred<CommandResult>()
        if (awaitResult) pending[command.requestId] = deferred
        val sent = runCatching {
            messageClient.sendMessage(node.id, DataLayerPaths.COMMAND, WireCodec.encodeCommand(command)).await()
        }.isSuccess
        if (!sent) {
            pending.remove(command.requestId)
            setConnected(false)
            return CommandOutcome.Offline
        }
        if (!awaitResult) return CommandOutcome.Ok
        val result = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
        pending.remove(command.requestId)
        return when {
            result == null -> CommandOutcome.Timeout
            result.ok -> CommandOutcome.Ok
            else -> CommandOutcome.Rejected(result)
        }
    }

    private suspend fun sendViaCloud(command: WireCommand, awaitResult: Boolean): CommandOutcome {
        val deferred = CompletableDeferred<CommandResult>()
        if (awaitResult) pending[command.requestId] = deferred
        if (!cloud.send(command)) {
            pending.remove(command.requestId)
            return CommandOutcome.Offline
        }
        if (!awaitResult) return CommandOutcome.Ok
        val result = withTimeoutOrNull(COMMAND_TIMEOUT_MS) { deferred.await() }
        pending.remove(command.requestId)
        return when {
            result == null -> CommandOutcome.Timeout
            result.ok -> CommandOutcome.Ok
            else -> CommandOutcome.Rejected(result)
        }
    }

    // --- Modo demostración ----------------------------------------------------------------

    private val demoScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val demoEngine = GameEngine()
    private var demoJob: Job? = null

    /**
     * Arranca una partida de ejemplo sin teléfono: un monstruo ataca 1 HP cada
     * [DEMO_TICK_MS] y los objetos se aplican con el mismo GameEngine que usa el teléfono.
     * Sirve para presentar la app sin emparejar dispositivos.
     */
    fun startDemo() {
        val now = System.currentTimeMillis()
        _snapshot.value = WatchSnapshot(
            game = SampleData.gameState(hp = 63).copy(
                monsters = SampleData.monsters.map { it.copy(shieldUntil = if (it.shieldUntil != null) now + 9 * 60_000L else null) },
                updatedAt = now,
            ),
            connection = Connection.CONNECTED,
            loading = false,
            lastSyncAt = now,
            demo = true,
        )
        demoJob?.cancel()
        demoJob = demoScope.launch {
            while (true) {
                delay(DEMO_TICK_MS)
                applyDemo { s ->
                    val target = s.activeAttacker ?: s.monsters.firstOrNull { !it.isDefeated && !it.isShielded(System.currentTimeMillis()) }?.packageName
                    if (target == null) Outcome(s) else demoEngine.onUsage(s, target, 1)
                }
            }
        }
    }

    private fun applyDemo(op: (GameState) -> Outcome): Outcome? {
        val current = _snapshot.value.game ?: return null
        val out = op(current)
        _snapshot.update { it.copy(game = out.state, lastSyncAt = System.currentTimeMillis()) }
        out.events.forEach { e -> e.toWire(out.state)?.let { _events.tryEmit(it) } }
        return out
    }

    private fun demoCommand(command: WireCommand): CommandOutcome {
        val out = when (command) {
            is WireCommand.UsePotion -> applyDemo { demoEngine.usePotion(it, command.wakeMonster) }
            is WireCommand.UseShield -> applyDemo { demoEngine.useShield(it, command.packageName) }
            is WireCommand.SetLimit -> applyDemo { demoEngine.setMonsterLimit(it, command.packageName, command.minutes) }
            is WireCommand.Revive -> if (command.pin != DEMO_PIN) {
                return CommandOutcome.Rejected(CommandResult(command.requestId, ok = false, wrongPin = true))
            } else applyDemo { demoEngine.revive(it) }
            is WireCommand.RequestSync -> return CommandOutcome.Ok
        }
        return if (out?.rejection == null) CommandOutcome.Ok
        else CommandOutcome.Rejected(CommandResult(command.requestId, ok = false, rejection = out.rejection))
    }

    companion object {
        /** PIN del modo demo para probar "Revivir". */
        const val DEMO_PIN = "1234"
        private const val DEMO_TICK_MS = 6_000L
        private const val LOAD_TIMEOUT_MS = 3_000L
        private const val COMMAND_TIMEOUT_MS = 6_000L
        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
