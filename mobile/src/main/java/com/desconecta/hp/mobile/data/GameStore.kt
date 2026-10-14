package com.desconecta.hp.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.model.GameState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "hp_prefs")

/**
 * Dónde viven los datos: solo en el teléfono (sin cuenta, Room/DataStore como única base) o
 * sincronizados con Supabase. Por defecto, solo en el dispositivo hasta que el jugador
 * crea una cuenta y da su consentimiento.
 */
enum class AccountMode { LOCAL_ONLY, CLOUD }

/** Preferencias que no viajan al reloj. */
data class PhonePrefs(
    val onboardingDone: Boolean = false,
    val maxHpWeekday: Int = 120,
    /** HP extra los sábados y domingos (0 = igual que entre semana). */
    val weekendBonus: Int = 0,
    val hasPin: Boolean = false,
    val accountMode: AccountMode = AccountMode.LOCAL_ONLY,
    /** Epoch ms del consentimiento para guardar datos en la nube (0 = no dado). */
    val cloudConsentAt: Long = 0L,
    /** Ya se ofreció crear cuenta al terminar la bienvenida (solo se ofrece una vez). */
    val accountOffered: Boolean = false,
)

/**
 * DataStore: el estado del día (JSON) y las preferencias. El historial y los logros
 * van en Room (consultas por fecha para el calendario y el salón de logros).
 */
@Singleton
class GameStore @Inject constructor(@param:ApplicationContext private val context: Context) {

    private object Keys {
        val STATE = stringPreferencesKey("game_state")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val MAX_HP = intPreferencesKey("max_hp_weekday")
        val WEEKEND_BONUS = intPreferencesKey("weekend_bonus")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val ACCOUNT_MODE = stringPreferencesKey("account_mode")
        val CLOUD_CONSENT = androidx.datastore.preferences.core.longPreferencesKey("cloud_consent_at")
        val BOUND_USER = stringPreferencesKey("bound_user_id")
        val ACCOUNT_OFFERED = booleanPreferencesKey("account_offered")
    }

    val prefs: Flow<PhonePrefs> = context.dataStore.data.map { p ->
        PhonePrefs(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            maxHpWeekday = p[Keys.MAX_HP] ?: 120,
            weekendBonus = p[Keys.WEEKEND_BONUS] ?: 0,
            hasPin = p[Keys.PIN_HASH] != null,
            accountMode = runCatching { AccountMode.valueOf(p[Keys.ACCOUNT_MODE] ?: "") }.getOrDefault(AccountMode.LOCAL_ONLY),
            cloudConsentAt = p[Keys.CLOUD_CONSENT] ?: 0L,
            accountOffered = p[Keys.ACCOUNT_OFFERED] ?: false,
        )
    }

    suspend fun loadState(): GameState? =
        context.dataStore.data.first()[Keys.STATE]?.let { json ->
            runCatching { WireCodec.json.decodeFromString(GameState.serializer(), json) }.getOrNull()
        }

    suspend fun saveState(state: GameState) {
        val json = WireCodec.json.encodeToString(GameState.serializer(), state)
        context.dataStore.edit { it[Keys.STATE] = json }
    }

    suspend fun clearState() = context.dataStore.edit { it.remove(Keys.STATE) }

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[Keys.ONBOARDING] = done }

    suspend fun setMaxHp(weekday: Int, weekendBonus: Int) = context.dataStore.edit {
        it[Keys.MAX_HP] = weekday
        it[Keys.WEEKEND_BONUS] = weekendBonus
    }

    suspend fun currentPrefs(): PhonePrefs = prefs.first()

    suspend fun setAccountMode(mode: AccountMode, consentAt: Long? = null) = context.dataStore.edit {
        it[Keys.ACCOUNT_MODE] = mode.name
        if (consentAt != null) it[Keys.CLOUD_CONSENT] = consentAt
    }

    suspend fun setAccountOffered() = context.dataStore.edit { it[Keys.ACCOUNT_OFFERED] = true }

    /** Consentimiento explícito para guardar datos en la nube (pantalla previa a crear cuenta). */
    suspend fun setCloudConsent(at: Long) = context.dataStore.edit { it[Keys.CLOUD_CONSENT] = at }

    /**
     * Cuenta cuyo progreso hay en la caché local (null = progreso de invitado). Si se entra
     * con otra cuenta, el progreso local se descarta en vez de mezclarse.
     */
    suspend fun boundUserId(): String? = context.dataStore.data.first()[Keys.BOUND_USER]

    suspend fun setBoundUserId(id: String) = context.dataStore.edit { it[Keys.BOUND_USER] = id }

    // --- PIN (solo se guarda un hash con sal; nunca el PIN) ------------------------------

    suspend fun setPin(pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()
        context.dataStore.edit {
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_HASH] = hash(pin, salt)
        }
    }

    suspend fun checkPin(pin: String): Boolean {
        val p: Preferences = context.dataStore.data.first()
        val salt = p[Keys.PIN_SALT] ?: return false
        val expected = p[Keys.PIN_HASH] ?: return false
        return MessageDigest.isEqual(hash(pin, salt).toByteArray(), expected.toByteArray())
    }

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).toHex()

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
