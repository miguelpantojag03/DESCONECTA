package com.desconecta.hp.mobile.ui.account

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desconecta.hp.mobile.cloud.AuthRepository
import com.desconecta.hp.mobile.cloud.AuthValidation
import com.desconecta.hp.mobile.cloud.GoogleSignIn
import com.desconecta.hp.mobile.cloud.RemoteGameApi
import com.desconecta.hp.mobile.cloud.authErrorMessage
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.data.GameStore
import com.desconecta.hp.mobile.sync.SyncQueue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Lo que muestran las pantallas de cuenta. Un solo estado: nunca dos mensajes a la vez. */
data class AccountUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val notice: AccountNotice? = null,
)

sealed interface AccountNotice {
    /** Cuenta creada; falta abrir el enlace del correo. */
    data class CheckEmail(val email: String) : AccountNotice
    data class ResetSent(val email: String) : AccountNotice
    data object ConfirmationResent : AccountNotice
    /** Sesión iniciada / contraseña cambiada / cuenta borrada: la pantalla se cierra. */
    data object Done : AccountNotice
}

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val remote: RemoteGameApi,
    private val store: GameStore,
    private val queue: SyncQueue,
    private val repository: GameRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AccountUiState())
    val ui: StateFlow<AccountUiState> = _ui.asStateFlow()

    val recovery: StateFlow<Boolean> = auth.recovery
    val googleAvailable: Boolean get() = GoogleSignIn.available

    fun clearMessages() = _ui.update { it.copy(error = null, notice = null) }

    // --- Consentimiento ------------------------------------------------------------------

    fun acceptCloud() = viewModelScope.launch { store.setCloudConsent(System.currentTimeMillis()) }

    fun stayLocal() = viewModelScope.launch { auth.useLocalOnly() }

    // --- Entrar / crear cuenta -----------------------------------------------------------

    fun signIn(email: String, password: String) = run {
        auth.signIn(email, password).map { AccountNotice.Done }
    }

    fun signUp(email: String, password: String) = run {
        if (AuthValidation.passwordError(password) != null) return@run Result.failure(IllegalArgumentException())
        val name = repository.state.value?.player?.name ?: "Jugador"
        auth.signUp(email, password, name).map { hasSession ->
            if (hasSession) AccountNotice.Done else AccountNotice.CheckEmail(email.trim())
        }
    }

    fun google(activityContext: Context) = viewModelScope.launch {
        _ui.value = AccountUiState(busy = true)
        when (val r = GoogleSignIn.requestIdToken(activityContext)) {
            is GoogleSignIn.Result.Token -> {
                val result = auth.signInWithGoogle(r.idToken, r.rawNonce)
                _ui.value = result.fold({ AccountUiState(notice = AccountNotice.Done) }, { AccountUiState(error = authErrorMessage(it)) })
            }
            GoogleSignIn.Result.Cancelled -> _ui.value = AccountUiState()
            is GoogleSignIn.Result.Failed -> _ui.value = AccountUiState(error = r.message)
        }
    }

    fun sendReset(email: String) = run {
        auth.sendPasswordReset(email).map { AccountNotice.ResetSent(email.trim()) }
    }

    fun resendConfirmation(email: String) = run {
        auth.resendConfirmation(email).map { AccountNotice.ConfirmationResent }
    }

    fun updatePassword(password: String) = run {
        auth.updatePassword(password).map {
            auth.recoveryHandled()
            AccountNotice.Done
        }
    }

    fun cancelRecovery() = auth.recoveryHandled()

    // --- Borrar cuenta -------------------------------------------------------------------

    /**
     * Borra la cuenta y TODOS sus datos en Supabase (RPC delete_my_account, en cascada).
     * El juego del teléfono sigue en "solo en este dispositivo", salvo que también se pida
     * borrarlo.
     */
    fun deleteAccount(alsoThisPhone: Boolean) = run {
        runCatching {
            remote.deleteMyAccount()
            queue.cancelAll()
            queue.clear()
            auth.forgetSession()
            if (alsoThisPhone) repository.reset()
            AccountNotice.Done
        }
    }

    suspend fun exportData(): Result<String> = runCatching { remote.exportMyData() }

    private fun run(block: suspend () -> Result<AccountNotice>) {
        if (_ui.value.busy) return
        viewModelScope.launch {
            _ui.value = AccountUiState(busy = true)
            val result = block()
            _ui.value = result.fold(
                onSuccess = { AccountUiState(notice = it) },
                onFailure = { AccountUiState(error = authErrorMessage(it)) },
            )
        }
    }
}
