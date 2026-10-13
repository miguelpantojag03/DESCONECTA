package com.desconecta.hp.wear.data

import android.content.Context
import com.desconecta.hp.shared.cloud.HpCloud
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reloj ↔ Supabase, para cuando el reloj no está emparejado por Bluetooth con el
 * teléfono pero tiene Wi‑Fi. El reloj no inicia sesión: canjea un código de 6 dígitos
 * por el token secreto del jugador y con él lee el estado y usa el canal en tiempo real.
 */
@Singleton
class CloudWatchLink @Inject constructor(@param:ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences("hp_cloud", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var channel: RealtimeChannel? = null
    private val jobs = mutableListOf<Job>()

    val token: String? get() = prefs.getString(KEY_TOKEN, null)
    val isAvailable: Boolean get() = HpCloud.isConfigured
    val isLinked: Boolean get() = isAvailable && token != null
    val isListening: Boolean get() = channel != null

    /** Canjea el código que muestra el teléfono. */
    suspend fun redeem(code: String): Result<Unit> = runCatching {
        val client = HpCloud.client ?: error("Esta versión no tiene la nube configurada")
        val raw = client.postgrest.rpc("redeem_link_code", buildJsonObject { put("p_code", JsonPrimitive(code)) }).data.trim()
        val token = raw.trim('"')
        require(raw != "null" && token.isNotBlank()) { "Código incorrecto o caducado" }
        UUID.fromString(token) // valida el formato
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    /** Estado actual guardado en la nube (o null). */
    suspend fun fetchState(): ByteArray? {
        val client = HpCloud.client ?: return null
        val t = token ?: return null
        return runCatching {
            val raw = client.postgrest.rpc("get_state_by_token", buildJsonObject { put("p_token", JsonPrimitive(t)) }).data.trim()
            if (raw == "null" || raw.isBlank()) null else raw.encodeToByteArray()
        }.getOrNull()
    }

    /**
     * Se suscribe al canal del jugador. Los callbacks se ejecutan en un hilo de fondo.
     * Envía un latido cada minuto para que el teléfono sepa que el reloj está activo.
     */
    suspend fun start(
        onState: (ByteArray) -> Unit,
        onEvent: (WireEvent) -> Unit,
        onResult: (CommandResult) -> Unit,
        onConnected: (Boolean) -> Unit,
    ) {
        val client = HpCloud.client ?: return
        val t = token ?: return
        if (channel != null) return
        val ch = client.channel(HpCloud.channelName(t))
        channel = ch
        jobs += scope.launch {
            ch.broadcastFlow<JsonObject>(HpCloud.Events.STATE).collect { m -> HpCloud.unwrap(m)?.let(onState) }
        }
        jobs += scope.launch {
            ch.broadcastFlow<JsonObject>(HpCloud.Events.EVENT).collect { m ->
                HpCloud.unwrap(m)?.let { runCatching { WireCodec.decodeEvent(it) }.getOrNull() }?.let(onEvent)
            }
        }
        jobs += scope.launch {
            ch.broadcastFlow<JsonObject>(HpCloud.Events.RESULT).collect { m ->
                HpCloud.unwrap(m)?.let { runCatching { WireCodec.decodeResult(it) }.getOrNull() }?.let(onResult)
            }
        }
        val ok = runCatching { ch.subscribe(blockUntilSubscribed = true) }.isSuccess
        onConnected(ok)
        if (ok) {
            jobs += scope.launch {
                while (true) {
                    send(WireCommand.RequestSync(UUID.randomUUID().toString()))
                    delay(HEARTBEAT_MS)
                }
            }
        }
    }

    suspend fun send(command: WireCommand): Boolean {
        val ch = channel ?: return false
        return runCatching { ch.broadcast(HpCloud.Events.COMMAND, HpCloud.wrap(WireCodec.encodeCommand(command))) }.isSuccess
    }

    suspend fun unlink() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        channel?.let { runCatching { it.unsubscribe() } }
        channel = null
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    private companion object {
        const val KEY_TOKEN = "watch_token"
        const val HEARTBEAT_MS = 60_000L
    }
}
