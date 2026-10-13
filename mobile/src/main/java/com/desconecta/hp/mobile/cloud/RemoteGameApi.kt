package com.desconecta.hp.mobile.cloud

import com.desconecta.hp.mobile.data.db.HourlyUsage
import com.desconecta.hp.mobile.di.SupabaseGateway
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.MonsterArt
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

// =====================================================================================
// DTOs: espejo exacto de las columnas de 002_schema.sql
// =====================================================================================

@Serializable
data class RemoteProfile(
    @SerialName("nombre_jugador") val playerName: String,
    @SerialName("avatar_id") val avatarId: String,
    val nivel: Int,
    val xp: Int,
    val titulo: String,
    @SerialName("vida_maxima") val maxHp: Int,
    @SerialName("racha_actual") val streak: Int,
    @SerialName("racha_maxima") val bestStreak: Int,
    @SerialName("modo_familiar") val familyMode: Boolean,
    @SerialName("revivir_disponible_en") val reviveAvailableAt: String,
)

@Serializable
data class RemoteInventory(@SerialName("tipo_objeto") val type: String, val cantidad: Int)

@Serializable
data class RemoteAchievement(
    @SerialName("achievement_id") val id: String,
    @SerialName("desbloqueado_en") val unlockedAt: String,
)

@Serializable
private data class RemoteMonsterRow(
    val id: String,
    @SerialName("package_name") val packageName: String,
    @SerialName("limite_minutos") val limitMinutes: Int,
)

@Serializable
private data class MonsterInsert(
    @SerialName("package_name") val packageName: String,
    @SerialName("nombre_app") val appLabel: String,
    @SerialName("nombre_monstruo") val name: String,
    @SerialName("ilustracion_id") val art: String,
    @SerialName("limite_minutos") val limitMinutes: Int,
    @SerialName("es_aliado") val ally: Boolean,
)

@Serializable
private data class MonsterUpdate(
    @SerialName("nombre_app") val appLabel: String,
    @SerialName("nombre_monstruo") val name: String,
    @SerialName("ilustracion_id") val art: String,
    @SerialName("es_aliado") val ally: Boolean,
    val activo: Boolean = true,
)

@Serializable
private data class SessionInsert(
    val fecha: String,
    @SerialName("hp_inicial") val hpStart: Int,
    @SerialName("hp_final") val hpEnd: Int,
)

@Serializable
data class SessionUpdate(
    @SerialName("hp_final") val hpEnd: Int,
    @SerialName("hp_regenerado") val regen: Int,
    @SerialName("pociones_usadas") val potions: Int,
    @SerialName("game_over") val gameOver: Boolean,
    @SerialName("hora_game_over") val gameOverAt: String?,
)

@Serializable
private data class UsageUpsert(
    @SerialName("package_name") val packageName: String,
    val fecha: String,
    val hora: Int,
    val minutos: Int,
    @SerialName("es_aliado") val ally: Boolean,
)

@Serializable
private data class DeviceInsert(val tipo: String, val modelo: String)

/** Lo que el servidor decide (fuente de verdad anti-trampas). */
data class ServerSnapshot(
    val profile: RemoteProfile?,
    val inventory: List<RemoteInventory>,
    val achievements: List<RemoteAchievement>,
)

/** Resultado de las funciones del servidor que devuelven {"ok", "motivo", …}. */
data class RpcResult(val ok: Boolean, val reason: String?, val raw: JsonObject)

/** Resultado de close_day. */
data class DayCloseResult(
    val xpEarned: Int,
    val hpEnd: Int,
    val level: Int,
    val leveledUp: Boolean,
    val streak: Int,
    val achievements: List<String>,
    val items: List<String>,
)

/** Id de ilustración del catálogo (monster_catalog.id) ↔ MonsterArt. */
fun MonsterArt.catalogId(): String = name.lowercase()
fun monsterArtFromCatalog(id: String): MonsterArt =
    MonsterArt.entries.firstOrNull { it.catalogId() == id } ?: MonsterArt.FANTASMA

/**
 * Llamadas a Supabase. No contiene lógica de reintentos: eso es del SyncWorker. Las
 * acciones sensibles usan las RPC de 003_functions.sql, nunca UPDATE directos.
 */
@Singleton
class RemoteGameApi @Inject constructor(private val gateway: SupabaseGateway) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun client(): SupabaseClient = gateway.client ?: error("La nube no está configurada")

    private fun uid(): String = client().auth.currentUserOrNull()?.id ?: error("Sin sesión")

    // --- Lecturas (lo que decide el servidor) ------------------------------------------

    suspend fun fetchSnapshot(): ServerSnapshot {
        val c = client()
        val me = uid()
        val profile = c.from("profiles")
            .select(Columns.list("nombre_jugador", "avatar_id", "nivel", "xp", "titulo", "vida_maxima",
                "racha_actual", "racha_maxima", "modo_familiar", "revivir_disponible_en")) {
                filter { eq("id", me) }
            }.decodeSingleOrNull<RemoteProfile>()
        val inventory = c.from("inventory").select(Columns.list("tipo_objeto", "cantidad")).decodeList<RemoteInventory>()
        val achievements = c.from("user_achievements").select(Columns.list("achievement_id", "desbloqueado_en"))
            .decodeList<RemoteAchievement>()
        return ServerSnapshot(profile, inventory, achievements)
    }

    // --- Escrituras directas permitidas por RLS + permisos por columna --------------------

    /** Crea el día si no existe y actualiza las columnas permitidas (nunca XP ni "cerrado"). */
    suspend fun upsertSession(dayKey: String, hpStart: Int, update: SessionUpdate) {
        val c = client()
        c.from("daily_sessions").upsert(SessionInsert(dayKey, hpStart, update.hpEnd)) {
            onConflict = "user_id,fecha"
            ignoreDuplicates = true
        }
        c.from("daily_sessions").update(update) { filter { eq("fecha", dayKey) } }
    }

    /** Sube uso agregado por hora (upsert: la última cifra de cada hora gana). */
    suspend fun upsertUsage(rows: List<HourlyUsage>) {
        if (rows.isEmpty()) return
        client().from("app_usage").upsert(
            rows.map { UsageUpsert(it.packageName, it.dayKey, it.hour, it.minutes.coerceIn(0, 60), it.isAlly) },
        ) { onConflict = "user_id,fecha,hora,package_name" }
    }

    /**
     * Sincroniza la lista de monstruos y aliadas. Las filas nuevas se insertan con su
     * límite; las existentes solo cambian columnas permitidas y el límite va por RPC.
     */
    suspend fun syncMonsters(monsters: List<Monster>, allies: List<Pair<String, String>>) {
        val c = client()
        val remote = c.from("monsters").select(Columns.list("id", "package_name", "limite_minutos"))
            .decodeList<RemoteMonsterRow>().associateBy { it.packageName }

        val wanted = monsters.map { it.packageName to it } + allies.map { (pkg, label) -> pkg to null as Monster? }
        val wantedPkgs = wanted.map { it.first }.toSet()

        for ((pkg, monster) in wanted) {
            val label = monster?.appLabel ?: allies.first { it.first == pkg }.second
            val existing = remote[pkg]
            if (existing == null) {
                c.from("monsters").insert(
                    MonsterInsert(
                        packageName = pkg,
                        appLabel = label.take(60),
                        name = (monster?.name ?: label).take(28),
                        art = (monster?.art ?: MonsterArt.FANTASMA).catalogId(),
                        limitMinutes = monster?.limitMinutes ?: 30,
                        ally = monster == null,
                    ),
                )
            } else {
                c.from("monsters").update(
                    MonsterUpdate(
                        appLabel = label.take(60),
                        name = (monster?.name ?: label).take(28),
                        art = (monster?.art ?: MonsterArt.FANTASMA).catalogId(),
                        ally = monster == null,
                    ),
                ) { filter { eq("id", existing.id) } }
                if (monster != null && monster.limitMinutes != existing.limitMinutes) {
                    setMonsterLimit(existing.id, monster.limitMinutes)
                }
            }
        }
        remote.values.filter { it.packageName !in wantedPkgs }.forEach { gone ->
            c.from("monsters").delete { filter { eq("id", gone.id) } }
        }
    }

    suspend fun monsterId(packageName: String): String? =
        client().from("monsters").select(Columns.list("id", "package_name", "limite_minutos")) {
            filter { eq("package_name", packageName) }
        }.decodeSingleOrNull<RemoteMonsterRow>()?.id

    suspend fun updateProfile(fields: JsonObject) {
        client().from("profiles").update(fields) { filter { eq("id", uid()) } }
    }

    suspend fun registerDevice(type: String, model: String) {
        val c = client()
        c.from("devices").upsert(DeviceInsert(type, model.take(80))) {
            onConflict = "user_id,tipo,modelo"
            ignoreDuplicates = true
        }
        c.from("devices").update(buildJsonObject { put("ultima_conexion", java.time.Instant.now().toString()) }) {
            filter {
                eq("tipo", type)
                eq("modelo", model.take(80))
            }
        }
    }

    // --- RPC (acciones sensibles) ------------------------------------------------------

    private suspend fun rpc(name: String, params: JsonObject): JsonObject {
        val data = client().postgrest.rpc(name, params).data
        return if (data.isBlank() || data == "null") JsonObject(emptyMap()) else json.parseToJsonElement(data).jsonObject
    }

    private fun JsonObject.toResult() = RpcResult(
        ok = this["ok"]?.jsonPrimitive?.boolean ?: false,
        reason = this["motivo"]?.jsonPrimitive?.content,
        raw = this,
    )

    suspend fun useItem(type: String, origin: String, dayKey: String, clientId: String, monsterId: String?): RpcResult =
        rpc("use_item", buildJsonObject {
            put("p_tipo", type)
            put("p_origen", origin)
            put("p_fecha", dayKey)
            put("p_client_id", clientId)
            put("p_monster_id", monsterId?.let(::JsonPrimitive) ?: kotlinx.serialization.json.JsonNull)
        }).toResult()

    suspend fun setMonsterLimit(monsterId: String, minutes: Int): RpcResult =
        rpc("set_monster_limit", buildJsonObject {
            put("p_monster_id", monsterId)
            put("p_minutos", minutes)
        }).toResult()

    suspend fun revive(pin: String, dayKey: String, clientId: String): RpcResult =
        rpc("revive", buildJsonObject {
            put("p_pin", pin)
            put("p_fecha", dayKey)
            put("p_client_id", clientId)
        }).toResult()

    suspend fun setPin(pin: String) {
        client().postgrest.rpc("set_pin", buildJsonObject { put("p_pin", pin) })
    }

    suspend fun closeDay(dayKey: String): DayCloseResult? {
        val r = rpc("close_day", buildJsonObject { put("p_fecha", dayKey) })
        if (r["ok"]?.jsonPrimitive?.boolean != true) return null
        return DayCloseResult(
            xpEarned = r["xp_ganada"]?.jsonPrimitive?.intOrNull ?: 0,
            hpEnd = r["hp_final"]?.jsonPrimitive?.intOrNull ?: 0,
            level = r["nivel"]?.jsonPrimitive?.intOrNull ?: 1,
            leveledUp = r["subio_nivel"]?.jsonPrimitive?.boolean ?: false,
            streak = r["racha"]?.jsonPrimitive?.int ?: 0,
            achievements = r["logros"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
            items = r["objetos"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
        )
    }

    suspend fun startWeek(dayKey: String) {
        client().postgrest.rpc("start_week", buildJsonObject { put("p_semana", dayKey) })
    }

    suspend fun exportMyData(): String = client().postgrest.rpc("export_my_data").data

    suspend fun deleteMyAccount() {
        client().postgrest.rpc("delete_my_account")
    }
}
