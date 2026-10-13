package com.desconecta.hp.mobile.di

import android.content.Context
import androidx.room.Room
import com.desconecta.hp.mobile.BuildConfig
import com.desconecta.hp.mobile.data.db.HpDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.FlowType
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

/**
 * Acceso a Supabase. [client] es null si no hay credenciales en local.properties: la app
 * funciona entonces solo en "Modo solo en este dispositivo".
 */
class SupabaseGateway(val client: SupabaseClient?) {
    val configured: Boolean get() = client != null
}

const val AUTH_SCHEME = "hpdesconecta"
const val AUTH_HOST = "auth"
/** Enlace del correo de "olvidé mi contraseña": abre la pantalla de contraseña nueva. */
const val AUTH_RECOVERY_URL = "$AUTH_SCHEME://$AUTH_HOST/recovery"

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): HpDatabase =
        Room.databaseBuilder(context, HpDatabase::class.java, "hp.db")
            // v1 → v2 añade la cola de sincronización y el uso por hora. El historial local
            // se puede rehacer desde la nube, así que en desarrollo se recrea la base.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun supabase(): SupabaseGateway {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_KEY
        if (url.isBlank() || key.isBlank()) return SupabaseGateway(null)
        return SupabaseGateway(
            createSupabaseClient(url, key) {
                install(Auth) {
                    // Sesión persistente y renovación automática del token. Los enlaces de los
                    // correos (confirmar cuenta, nueva contraseña) vuelven a la app por
                    // hpdesconecta://auth/… con PKCE: el código solo sirve en este teléfono.
                    scheme = AUTH_SCHEME
                    host = AUTH_HOST
                    flowType = FlowType.PKCE
                }
                install(Postgrest)
                install(Realtime)
                install(Storage)
                install(Functions)
            },
        )
    }
}
