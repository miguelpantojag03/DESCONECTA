package com.desconecta.hp.mobile

import com.desconecta.hp.mobile.cloud.RemoteGameApi
import com.desconecta.hp.mobile.cloud.SessionUpdate
import com.desconecta.hp.mobile.data.db.HourlyUsage
import com.desconecta.hp.mobile.di.SupabaseGateway
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.MonsterArt
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemoryCodeVerifierCache
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

/**
 * Prueba la capa de datos contra el Supabase REAL (permisos por columna, RPC, RLS).
 * Crea un usuario temporal y lo borra al final. Solo se ejecuta si se pide:
 *   ./gradlew :mobile:testDebugUnitTest --tests "*RemoteGameApiIntegrationTest" -Dhp.integration=true
 */
class RemoteGameApiIntegrationTest {

    @Test
    fun fullDayAgainstRealSupabase() = runBlocking {
        assumeTrue("Solo con -Dhp.integration=true", System.getProperty("hp.integration") == "true")
        assumeTrue("Sin credenciales", BuildConfig.SUPABASE_URL.isNotBlank())
        // En la JVM no hay hilo principal de Android.
        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.Dispatchers.Unconfined)

        val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_KEY) {
            install(Auth) {
                // En la JVM no hay SharedPreferences: sesión en memoria.
                sessionManager = MemorySessionManager()
                codeVerifierCache = MemoryCodeVerifierCache()
                enableLifecycleCallbacks = false
            }
            install(Postgrest)
        }
        client.auth.signInAnonymously()
        val api = RemoteGameApi(SupabaseGateway(client))
        val day = LocalDate.now().minusDays(1).toString()

        try {
            // Perfil creado por el trigger de registro.
            val start = api.fetchSnapshot()
            assertEquals(0, start.profile!!.xp)
            assertEquals(1, start.inventory.first { it.type == "pocion" }.cantidad)

            // Monstruos y aliadas (insert + update por columnas + límite por RPC).
            val tiktok = Monster("com.zhiliaoapp.musically", "TikTok", "Scrollzilla", MonsterArt.SCROLLZILLA, limitMinutes = 30)
            api.syncMonsters(listOf(tiktok), listOf("com.google.android.apps.maps" to "Maps"))
            api.syncMonsters(listOf(tiktok.copy(limitMinutes = 45, name = "Scrolli")), listOf("com.google.android.apps.maps" to "Maps"))
            val id = api.monsterId("com.zhiliaoapp.musically")
            assertTrue("el monstruo debe existir", id != null)

            // Día (dos veces: crear y actualizar) + uso por hora.
            api.upsertSession(day, 120, SessionUpdate(hpEnd = 90, regen = 0, potions = 0, gameOver = false, gameOverAt = null))
            api.upsertSession(day, 120, SessionUpdate(hpEnd = 70, regen = 5, potions = 1, gameOver = false, gameOverAt = null))
            api.upsertUsage(listOf(HourlyUsage(day, 21, "com.zhiliaoapp.musically", 40, isAlly = false)))
            api.upsertUsage(listOf(HourlyUsage(day, 21, "com.zhiliaoapp.musically", 45, isAlly = false)))   // la última cifra gana

            // Poción (idempotente).
            val clientId = UUID.randomUUID().toString()
            assertTrue(api.useItem("pocion", "reloj", day, clientId, null).ok)
            assertEquals("ya_registrado", api.useItem("pocion", "reloj", day, clientId, null).reason)

            // Perfil: columnas permitidas sí…
            api.updateProfile(buildJsonObject { put("nombre_jugador", JsonPrimitive("Integración")) })
            // …XP no (anti-trampas): debe fallar.
            val cheat = runCatching { api.updateProfile(buildJsonObject { put("xp", JsonPrimitive(9999)) }) }
            assertTrue("el servidor debe rechazar escribir XP", cheat.isFailure)

            // Cierre del día: 120 − 45 + 5 + 10 (poción) = 90 máx.; declaró 70 → 70 XP.
            val closed = api.closeDay(day)!!
            assertEquals(70, closed.xpEarned)
            assertTrue("first_day" in closed.achievements)

            val end = api.fetchSnapshot()
            assertEquals(70, end.profile!!.xp)
            assertEquals("Integración", end.profile!!.playerName)
            assertEquals(1, end.profile!!.streak)
            assertTrue(end.achievements.any { it.id == "first_potion" })

            // Cerrar dos veces no duplica la XP.
            assertFalse(api.closeDay(day)!!.xpEarned == 140)
            assertEquals(70, api.fetchSnapshot().profile!!.xp)

            api.startWeek(LocalDate.now().toString())
            api.registerDevice("telefono", "Prueba JVM")
            val export = api.exportMyData()
            assertTrue(export.contains("Integración") && !export.contains("pin_hash"))
            println("OK · capa de datos verificada contra Supabase real")
        } finally {
            api.deleteMyAccount()
            client.from("profiles").select() // tras borrar, la sesión ya no tiene perfil
        }
    }
}
