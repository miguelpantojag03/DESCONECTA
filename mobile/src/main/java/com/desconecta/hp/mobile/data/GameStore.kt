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

/** Preferencias que no viajan al reloj. */
data class PhonePrefs(
    val onboardingDone: Boolean = false,
    val maxHpWeekday: Int = 120,
    /** HP extra los sábados y domingos (0 = igual que entre semana). */
    val weekendBonus: Int = 0,
    val hasPin: Boolean = false,
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
    }

    val prefs: Flow<PhonePrefs> = context.dataStore.data.map { p ->
        PhonePrefs(
            onboardingDone = p[Keys.ONBOARDING] ?: false,
            maxHpWeekday = p[Keys.MAX_HP] ?: 120,
            weekendBonus = p[Keys.WEEKEND_BONUS] ?: 0,
            hasPin = p[Keys.PIN_HASH] != null,
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
