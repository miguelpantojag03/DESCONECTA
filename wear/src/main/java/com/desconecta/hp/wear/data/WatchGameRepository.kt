package com.desconecta.hp.wear.data

import android.content.Context
import android.net.Uri
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.DataLayerPaths
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.model.GameState
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
) {
    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val _snapshot = MutableStateFlow(WatchSnapshot())
    val snapshot: StateFlow<WatchSnapshot> = _snapshot.asStateFlow()

    private val _events = MutableSharedFlow<WireEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<WireEvent> = _events

    private val pending = ConcurrentHashMap<String, CompletableDeferred<CommandResult>>()

    /** Carga el último estado guardado y comprueba la conexión. Se llama al abrir la app. */
    suspend fun refresh() {
        runCatching {
            val uri = Uri.Builder().scheme(PutDataRequest.WEAR_URI_SCHEME).path(DataLayerPaths.GAME_STATE).build()
            val items = dataClient.getDataItems(uri).await()
            try {
                items.firstOrNull()?.let { item ->
                    val map = DataMapItem.fromDataItem(item).dataMap
                    map.getByteArray(DataLayerPaths.KEY_STATE)?.let { applyState(it, map.getLong(DataLayerPaths.KEY_UPDATED_AT)) }
                }
            } finally {
                items.release()
            }
        }
        _snapshot.update { it.copy(loading = false) }
        checkConnection()
        if (_snapshot.value.connection == Connection.CONNECTED) {
            send(WireCommand.RequestSync(UUID.randomUUID().toString()), awaitResult = false)
        }
    }

    suspend fun checkConnection() {
        val connected = runCatching {
            capabilityClient
                .getCapability(DataLayerPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await().nodes.isNotEmpty()
        }.getOrDefault(false)
        setConnected(connected)
    }

    // --- Entradas desde WatchListenerService -------------------------------------------

    fun applyState(bytes: ByteArray, syncedAt: Long) {
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
        _snapshot.update { it.copy(connection = if (connected) Connection.CONNECTED else Connection.DISCONNECTED) }
    }

    // --- Comandos -----------------------------------------------------------------------

    suspend fun send(command: WireCommand, awaitResult: Boolean = true): CommandOutcome {
        val node = runCatching {
            capabilityClient
                .getCapability(DataLayerPaths.CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                .await().nodes.minByOrNull { if (it.isNearby) 0 else 1 }
        }.getOrNull()
        if (node == null) {
            setConnected(false)
            return CommandOutcome.Offline
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

    companion object {
        private const val COMMAND_TIMEOUT_MS = 6_000L
        fun newRequestId(): String = UUID.randomUUID().toString()
    }
}
