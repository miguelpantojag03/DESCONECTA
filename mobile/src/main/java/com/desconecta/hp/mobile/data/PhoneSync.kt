package com.desconecta.hp.mobile.data

import android.content.Context
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.DataLayerPaths
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.model.GameState
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica el estado y los eventos hacia el reloj.
 *
 * - Estado → DataClient (persistente: si el reloj está desconectado, lo recibe al volver).
 * - Eventos → MessageClient (instantáneos; si el reloj no está, se pierden sin problema
 *   porque el estado completo llega igualmente).
 */
@Singleton
class PhoneSync @Inject constructor(@param:ApplicationContext private val context: Context) {

    private val dataClient = Wearable.getDataClient(context)
    private val messageClient = Wearable.getMessageClient(context)
    private val capabilityClient = Wearable.getCapabilityClient(context)

    private val _watchConnected = MutableStateFlow(false)
    val watchConnected: StateFlow<Boolean> = _watchConnected.asStateFlow()

    suspend fun publishState(state: GameState) {
        runCatching {
            val request = PutDataMapRequest.create(DataLayerPaths.GAME_STATE).apply {
                dataMap.putByteArray(DataLayerPaths.KEY_STATE, WireCodec.encodeState(state))
                // Marca de tiempo: fuerza onDataChanged aunque el estado sea idéntico.
                dataMap.putLong(DataLayerPaths.KEY_UPDATED_AT, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            dataClient.putDataItem(request).await()
        }
    }

    suspend fun sendEvent(event: WireEvent) {
        watchNodes().forEach { node ->
            runCatching { messageClient.sendMessage(node, DataLayerPaths.EVENT, WireCodec.encodeEvent(event)).await() }
        }
    }

    suspend fun sendResult(nodeId: String, result: CommandResult) {
        runCatching { messageClient.sendMessage(nodeId, DataLayerPaths.COMMAND_RESULT, WireCodec.encodeResult(result)).await() }
    }

    /** Actualiza [watchConnected]. */
    suspend fun refreshConnection(): Boolean {
        val connected = watchNodes().isNotEmpty()
        _watchConnected.value = connected
        return connected
    }

    fun setConnected(connected: Boolean) {
        _watchConnected.value = connected
    }

    private suspend fun watchNodes(): List<String> = runCatching {
        capabilityClient.getCapability(DataLayerPaths.CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
            .await().nodes.map { it.id }
    }.getOrDefault(emptyList())
}
