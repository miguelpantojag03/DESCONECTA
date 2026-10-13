package com.desconecta.hp.wear.data

import android.content.ComponentName
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.desconecta.hp.shared.datalayer.DataLayerPaths
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.wear.complication.HpComplicationService
import com.desconecta.hp.wear.complication.StreakComplicationService
import com.desconecta.hp.wear.tile.HpTileService
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Punto de entrada de todo lo que llega del teléfono, con la app abierta o cerrada:
 * estado, eventos, resultados de comandos y cambios de conexión.
 *
 * Las vibraciones se disparan AQUÍ (y no en la pantalla) para que el jugador sienta las
 * advertencias aunque no esté mirando el reloj.
 */
@AndroidEntryPoint
class WatchListenerService : WearableListenerService() {

    @Inject lateinit var repository: WatchGameRepository
    @Inject lateinit var feedback: WatchFeedback

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == DataLayerPaths.GAME_STATE) {
                val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                map.getByteArray(DataLayerPaths.KEY_STATE)?.let {
                    repository.applyState(it, map.getLong(DataLayerPaths.KEY_UPDATED_AT))
                }
            }
        }
        repository.setConnected(true)
        refreshSurfaces()
    }

    override fun onMessageReceived(message: MessageEvent) {
        when (message.path) {
            DataLayerPaths.EVENT -> runCatching { WireCodec.decodeEvent(message.data) }.getOrNull()?.let(::handleEvent)
            DataLayerPaths.COMMAND_RESULT -> runCatching { WireCodec.decodeResult(message.data) }.getOrNull()
                ?.let(repository::onCommandResult)
        }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        if (info.name == DataLayerPaths.CAPABILITY_PHONE) {
            repository.setConnected(info.nodes.isNotEmpty())
        }
    }

    private fun handleEvent(event: WireEvent) {
        feedback.play(event, repository.snapshot.value.game?.settings?.haptics ?: true)
        repository.onEvent(event)
    }

    private fun refreshSurfaces() {
        TileService.getUpdater(this).requestUpdate(HpTileService::class.java)
        listOf(HpComplicationService::class.java, StreakComplicationService::class.java).forEach {
            ComplicationDataSourceUpdateRequester.create(this, ComponentName(this, it)).requestUpdateAll()
        }
    }
}
