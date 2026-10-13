package com.desconecta.hp.mobile.cloud

import com.desconecta.hp.mobile.data.AccountMode
import com.desconecta.hp.mobile.data.GameStore
import com.desconecta.hp.mobile.di.SupabaseGateway
import android.content.Intent
import com.desconecta.hp.mobile.di.AUTH_HOST
import com.desconecta.hp.mobile.di.AUTH_RECOVERY_URL
import com.desconecta.hp.mobile.di.AUTH_SCHEME
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.IDToken
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/** Estado de la cuenta tal como lo muestra la interfaz. */
sealed interface AuthState {
    /** La app se compiló sin credenciales de Supabase: solo modo local. */
    data object Unconfigured : AuthState
    data object Loading : AuthState
    /** "Modo solo en este dispositivo": el jugador eligió no tener cuenta. */
    data object LocalOnly : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val userId: String, val email: String?, val emailConfirmed: Boolean) : AuthState
    /** La sesión caducó y no se pudo renovar: hay que volver a iniciar sesión. */
    data class Expired(val reason: String?) : AuthState
}

/**
 * Cuenta del jugador en Supabase Auth: correo + contraseña y Google. La sesión se guarda
 * en el teléfono y el token se renueva solo (auth-kt). El conteo de HP y el bloqueo de
 * apps NO dependen nunca de este estado.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val gateway: SupabaseGateway,
    private val store: GameStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val client get() = gateway.client

    val state: StateFlow<AuthState> = run {
        val c = client ?: return@run MutableStateFlow<AuthState>(AuthState.Unconfigured)
        combine(c.auth.sessionStatus, store.prefs.map { it.accountMode }) { status, mode ->
            when (status) {
                is SessionStatus.Authenticated -> {
                    val user = status.session.user
                    AuthState.SignedIn(
                        userId = user?.id.orEmpty(),
                        email = user?.email,
                        emailConfirmed = user?.emailConfirmedAt != null,
                    )
                }
                is SessionStatus.Initializing -> AuthState.Loading
                is SessionStatus.RefreshFailure ->
                    if (mode == AccountMode.CLOUD) AuthState.Expired("No se pudo renovar la sesión") else AuthState.LocalOnly
                is SessionStatus.NotAuthenticated ->
                    when {
                        mode == AccountMode.CLOUD && !status.isSignOut -> AuthState.Expired(null)
                        mode == AccountMode.LOCAL_ONLY -> AuthState.LocalOnly
                        else -> AuthState.SignedOut
                    }
            }
        }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)
    }

    val configured: Boolean get() = client != null

    fun userId(): String? = client?.auth?.currentUserOrNull()?.id

    suspend fun signUp(email: String, password: String, playerName: String): Result<Boolean> = runCatching {
        val c = client ?: error("La nube no está configurada")
        c.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            // El trigger handle_new_user lee este nombre para crear el perfil.
            data = buildJsonObject { put("nombre_jugador", JsonPrimitive(playerName.take(16))) }
        }
        // true = ya hay sesión; false = falta verificar el correo.
        c.auth.currentSessionOrNull() != null
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val c = client ?: error("La nube no está configurada")
        c.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    /** Google: el ID token se obtiene en la pantalla con Credential Manager. */
    suspend fun signInWithGoogle(idToken: String, rawNonce: String?): Result<Unit> = runCatching {
        val c = client ?: error("La nube no está configurada")
        c.auth.signInWith(IDToken) {
            this.idToken = idToken
            provider = Google
            nonce = rawNonce
        }
    }

    /** El enlace del correo vuelve a la app ([AUTH_RECOVERY_URL]) y abre "Contraseña nueva". */
    suspend fun sendPasswordReset(email: String): Result<Unit> = runCatching {
        val c = client ?: error("La nube no está configurada")
        c.auth.resetPasswordForEmail(email.trim(), redirectUrl = AUTH_RECOVERY_URL)
    }

    suspend fun updatePassword(newPassword: String): Result<Unit> = runCatching {
        val c = client ?: error("La nube no está configurada")
        c.auth.updateUser { password = newPassword }
        Unit
    }

    private val _recovery = MutableStateFlow(false)
    /** true = se abrió el enlace de "olvidé mi contraseña": hay que pedir la nueva. */
    val recovery: StateFlow<Boolean> = _recovery

    fun recoveryHandled() { _recovery.value = false }

    /**
     * Enlaces hpdesconecta://auth/… de los correos. Confirmar la cuenta inicia sesión sola;
     * el de recuperación además abre la pantalla de contraseña nueva.
     */
    fun handleDeeplink(intent: Intent) {
        val c = client ?: return
        val uri = intent.data ?: return
        if (uri.scheme != AUTH_SCHEME || uri.host != AUTH_HOST) return
        val isRecovery = uri.path == "/recovery"
        c.handleDeeplinks(intent) {
            if (isRecovery) _recovery.value = true
        }
    }

    /** Tras borrar la cuenta en el servidor: la sesión ya no vale, se olvida solo en local. */
    suspend fun forgetSession() {
        runCatching { client?.auth?.signOut(SignOutScope.LOCAL) }
        runCatching { client?.auth?.clearSession() }
        store.setAccountMode(AccountMode.LOCAL_ONLY)
    }

    suspend fun resendConfirmation(email: String): Result<Unit> = runCatching {
        val c = client ?: error("La nube no está configurada")
        c.auth.resendEmail(OtpType.Email.SIGNUP, email.trim())
    }

    suspend fun signOut() {
        runCatching { client?.auth?.signOut() }
        store.setAccountMode(AccountMode.LOCAL_ONLY)
    }

    /** El jugador aceptó guardar sus datos en la nube (consentimiento explícito). */
    suspend fun enableCloud() = store.setAccountMode(AccountMode.CLOUD, consentAt = System.currentTimeMillis())

    /** "Modo solo en este dispositivo". */
    suspend fun useLocalOnly() = store.setAccountMode(AccountMode.LOCAL_ONLY)
}
