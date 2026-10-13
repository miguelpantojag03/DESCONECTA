package com.desconecta.hp.mobile.data

import com.desconecta.hp.mobile.data.db.DayRecord
import com.desconecta.hp.shared.cloud.HpCloud
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.model.GameState
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Lo que la interfaz muestra sobre la nube (Configuración → Nube y reloj). */
data class CloudStatus(
    val configured: Boolean = HpCloud.isConfigured,
    val signedIn: Boolean = false,
    val lastSyncAt: Long = 0L,
    /** Última vez que un reloj vinculado por Wi‑Fi dio señales de vida. */
    val watchSeenAt: Long = 0L,
    val error: String? = null,
)

@Serializable
private data class PlayerRow(val state: JsonElement)

@Serializable
private data class TokenRow(@SerialName("watch_token") val watchToken: String)

@Serializable
private data class HistoryRow(
    @SerialName("day_key") val dayKey: String,
    @SerialName("max_hp") val maxHp: Int,
    @SerialName("hp_left") val hpLeft: Int,
    @SerialName("game_over") val gameOver: Boolean,
    @SerialName("xp_earned") val xpEarned: Int,
    val usage: JsonObject,
)

@Serializable
private data class AchievementRow(val id: String, @SerialName("unlocked_at") val unlockedAt: String)

/**
 * Teléfono ↔ Supabase. Todo es "mejor esfuerzo": si no hay red, la app sigue con Room y
 * DataStore, y lo pendiente se vuelve a subir en la siguiente sincronización.
 */
@Singleton
class CloudSync @Inject constructor() {

    private val client = HpCloud.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _status = MutableStateFlow(CloudStatus())
    val status: StateFlow<CloudStatus> = _status.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var listenJob: Job? = null
    private var token: String? = null

    /** Lo pone GameRepository: ejecuta un comando del reloj y devuelve el resultado. */
    var commandHandler: (suspend (WireCommand) -> CommandResult)? = null

    private suspend fun ensureSession(): Boolean {
        val c = client ?: return false
        return runCatching {
            c.auth.awaitInitialization()
            if (c.auth.currentSessionOrNull() == null) c.auth.signInAnonymously()
            _status.update { it.copy(signedIn = true, error = null) }
            true
        }.getOrElse { e ->
            _status.update { it.copy(signedIn = false, error = "No se pudo iniciar sesión en la nube: ${e.message}") }
            false
        }
    }

    /** Sube el estado del día y lo difunde al reloj vinculado por Wi‑Fi. */
    fun publishState(state: GameState) = scope.launch {
        val c = client ?: return@launch
        mutex.withLock {
            if (!ensureSession()) return@withLock
            runCatching {
                val json = WireCodec.json.encodeToJsonElement(GameState.serializer(), state)
                c.from("players").upsert(PlayerRow(json)) { onConflict = "user_id" }
                ensureChannel()
                channel?.broadcast(HpCloud.Events.STATE, HpCloud.wrap(WireCodec.encodeState(state)))
                _status.update { it.copy(lastSyncAt = System.currentTimeMillis(), error = null) }
            }.onFailure { e -> _status.update { it.copy(error = "Sin conexión con la nube (${e.message})") } }
        }
    }

    fun sendEvent(event: WireEvent) = scope.launch {
        runCatching { channel?.broadcast(HpCloud.Events.EVENT, HpCloud.wrap(WireCodec.encodeEvent(event))) }
    }

    fun pushHistory(record: DayRecord) = scope.launch {
        val c = client ?: return@launch
        if (!ensureSession()) return@launch
        runCatching {
            val usage = buildJsonObject {
                record.usageByApp.split(";").filter { "=" in it }.forEach { pair ->
                    val (pkg, min) = pair.split("=", limit = 2)
                    put(pkg, JsonPrimitive(min.toIntOrNull() ?: 0))
                }
            }
            c.from("day_history").upsert(
                HistoryRow(record.dayKey, record.maxHp, record.hpLeft, record.gameOver, record.xpEarned, usage),
            ) { onConflict = "user_id,day_key" }
        }
    }

    fun pushAchievement(id: String, unlockedAt: Long) = scope.launch {
        val c = client ?: return@launch
        if (!ensureSession()) return@launch
        runCatching {
            c.from("achievements").upsert(
                AchievementRow(id, java.time.Instant.ofEpochMilli(unlockedAt).toString()),
            ) { onConflict = "user_id,id" }
        }
    }

    /** Código de 6 dígitos para vincular un reloj por Wi‑Fi (válido 10 min). */
    suspend fun createLinkCode(state: GameState): Result<String> = runCatching {
        val c = client ?: error("La nube no está configurada")
        check(ensureSession()) { "No se pudo iniciar sesión en la nube" }
        // La fila del jugador debe existir para que el código apunte a su token.
        publishState(state).join()
        _status.value.error?.let { error(it) }
        c.postgrest.rpc("create_link_code").data.trim('"')
    }

    /** Invalida el token: todos los relojes vinculados por Wi‑Fi dejan de recibir datos. */
    suspend fun unlinkWatches(): Result<Unit> = runCatching {
        val c = client ?: error("La nube no está configurada")
        check(ensureSession())
        c.postgrest.rpc("rotate_watch_token")
        mutex.withLock { closeChannel() }
    }

    /** Abre (una vez) el canal del token actual y escucha los comandos del reloj. */
    private suspend fun ensureChannel() {
        val c = client ?: return
        val current = c.from("players").select(Columns.list("watch_token")).decodeSingle<TokenRow>().watchToken
        if (current == token && channel != null) return
        closeChannel()
        token = current
        val ch = c.channel(HpCloud.channelName(current))
        channel = ch
        listenJob = scope.launch {
            ch.broadcastFlow<JsonObject>(HpCloud.Events.COMMAND).collect { message ->
                val bytes = HpCloud.unwrap(message) ?: return@collect
                val command = runCatching { WireCodec.decodeCommand(bytes) }.getOrNull() ?: return@collect
                _status.update { it.copy(watchSeenAt = System.currentTimeMillis()) }
                val result = commandHandler?.invoke(command) ?: return@collect
                runCatching { ch.broadcast(HpCloud.Events.RESULT, HpCloud.wrap(WireCodec.encodeResult(result))) }
            }
        }
        ch.subscribe(blockUntilSubscribed = true)
    }

    private suspend fun closeChannel() {
        listenJob?.cancel()
        listenJob = null
        channel?.let { runCatching { it.unsubscribe() } }
        channel = null
        token = null
    }
}
