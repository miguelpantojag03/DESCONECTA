package com.desconecta.hp.shared.cloud

import com.desconecta.hp.shared.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Acceso a Supabase compartido por teléfono y reloj.
 *
 * La nube cumple dos papeles:
 *  1. Respaldo: el teléfono guarda estado, historial y logros (tablas con RLS).
 *  2. Canal en tiempo real con el reloj por Wi‑Fi: Realtime Broadcast en el canal
 *     "hp-<watch_token>", con los MISMOS mensajes que la Data Layer (WireCodec).
 *     Así el reloj funciona aunque no esté emparejado por Bluetooth con el teléfono.
 *
 * Si no hay credenciales en local.properties, [client] es null y la app funciona igual
 * (solo Data Layer + almacenamiento local).
 */
object HpCloud {

    val isConfigured: Boolean = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_KEY.isNotBlank()

    val client: SupabaseClient? by lazy {
        if (!isConfigured) null
        else createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
        }
    }

    fun channelName(watchToken: String) = "hp-$watchToken"

    /** Eventos del canal (equivalen a las rutas de la Data Layer). */
    object Events {
        const val STATE = "state"       // teléfono → reloj: GameState
        const val EVENT = "event"       // teléfono → reloj: WireEvent
        const val COMMAND = "command"   // reloj → teléfono: WireCommand
        const val RESULT = "result"     // teléfono → reloj: CommandResult
    }

    /** Envuelve un JSON de WireCodec en el cuerpo de un mensaje Broadcast. */
    fun wrap(bytes: ByteArray): JsonObject = buildJsonObject { put("data", JsonPrimitive(bytes.decodeToString())) }

    fun unwrap(message: JsonObject): ByteArray? = message["data"]?.jsonPrimitive?.content?.encodeToByteArray()
}
