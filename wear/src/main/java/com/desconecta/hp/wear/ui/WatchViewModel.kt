package com.desconecta.hp.wear.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.domain.Rejection
import com.desconecta.hp.wear.data.CommandOutcome
import com.desconecta.hp.wear.data.WatchGameRepository
import com.desconecta.hp.wear.data.WatchSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Estado de una acción enviada al teléfono (usar objeto, cambiar límite, revivir). */
sealed interface ActionState {
    data object Idle : ActionState
    data object Sending : ActionState
    data class Success(val message: String) : ActionState
    data class Failure(val message: String, val retryable: Boolean) : ActionState
}

@HiltViewModel
class WatchViewModel @Inject constructor(
    private val repository: WatchGameRepository,
) : ViewModel() {

    val snapshot: StateFlow<WatchSnapshot> = repository.snapshot

    /** Reloj de pared para cuentas regresivas; solo late mientras alguien lo observa. */
    val now: StateFlow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), System.currentTimeMillis())

    private val queue = ArrayDeque<WireEvent>()
    private val _alert = MutableStateFlow<WireEvent?>(null)
    /** Alerta a pantalla completa visible ahora (una a la vez, en orden de llegada). */
    val alert: StateFlow<WireEvent?> = _alert.asStateFlow()

    private val _action = MutableStateFlow<ActionState>(ActionState.Idle)
    val action: StateFlow<ActionState> = _action.asStateFlow()

    /** Pedido del teléfono de abrir la confirmación de poción (con app a despertar o null). */
    data class PotionRequest(val wakeMonster: String?)

    private val _potionRequest = MutableStateFlow<PotionRequest?>(null)
    val potionRequest: StateFlow<PotionRequest?> = _potionRequest.asStateFlow()

    fun requestPotion(wakeMonster: String?) {
        _potionRequest.value = PotionRequest(wakeMonster)
    }

    fun consumePotionRequest() {
        _potionRequest.value = null
    }

    private var limitJob: Job? = null

    init {
        viewModelScope.launch { repository.refresh() }
        viewModelScope.launch {
            repository.events.collect { event ->
                if (event is WireEvent.OpenPotion) {
                    _potionRequest.value = PotionRequest(event.wakeMonster)
                    return@collect
                }
                // El Game Over ya tiene su propia pantalla (depende del estado, no del evento).
                if (event is WireEvent.GameOver || event is WireEvent.Healed) return@collect
                queue.addLast(event)
                if (_alert.value == null) _alert.value = queue.removeFirst()
            }
        }
    }

    fun dismissAlert() {
        _alert.value = queue.removeFirstOrNull()
    }

    fun retryConnection() = viewModelScope.launch { repository.refresh() }

    fun startDemo() = repository.startDemo()


    fun usePotion(wakeMonster: String? = null) = run(
        WireCommand.UsePotion(WatchGameRepository.newRequestId(), wakeMonster),
        if (wakeMonster == null) "¡Poción usada! +10 HP" else "¡Poción usada! App despierta 10 min",
    )

    fun useShield(packageName: String) =
        run(WireCommand.UseShield(WatchGameRepository.newRequestId(), packageName), "Escudo activo 15 min")

    fun revive(pin: String) = run(WireCommand.Revive(WatchGameRepository.newRequestId(), pin), "¡Has revivido con 15 HP!")

    /**
     * Cambiar el límite con la corona genera muchos pasos seguidos: se espera a que el
     * usuario pare (debounce de 700 ms) y se envía una sola vez el valor final.
     */
    fun setLimit(packageName: String, minutes: Int) {
        limitJob?.cancel()
        limitJob = viewModelScope.launch {
            delay(700)
            repository.send(WireCommand.SetLimit(WatchGameRepository.newRequestId(), packageName, minutes))
        }
    }

    fun resetAction() {
        _action.value = ActionState.Idle
    }

    private fun run(command: WireCommand, successMessage: String) {
        if (_action.value == ActionState.Sending) return
        _action.value = ActionState.Sending
        viewModelScope.launch {
            // Se envía UNA sola vez y luego se asigna el estado. (Antes iba dentro de update {},
            // que repite su bloque si el estado cambia a la vez: reenviaba el comando.)
            val outcome = repository.send(command)
            if (outcome == CommandOutcome.Timeout) {
                // Sin respuesta no se sabe si se aplicó: se resincroniza para que el estado lo aclare.
                repository.send(WireCommand.RequestSync(WatchGameRepository.newRequestId()), awaitResult = false)
            }
            _action.value = when (outcome) {
                CommandOutcome.Ok -> ActionState.Success(successMessage)
                CommandOutcome.Offline -> ActionState.Failure("Tu teléfono no está conectado. Acércalo e inténtalo de nuevo.", retryable = true)
                // Reintentar a ciegas podría gastar dos objetos: se pide revisar primero.
                CommandOutcome.Timeout -> ActionState.Failure(
                    "El teléfono tardó en responder. Revisa tu vida y tus objetos antes de volver a intentarlo.",
                    retryable = false,
                )
                is CommandOutcome.Rejected -> ActionState.Failure(
                    if (outcome.result.wrongPin) "PIN incorrecto." else rejectionMessage(outcome.result.rejection),
                    retryable = outcome.result.wrongPin,
                )
            }
        }
    }

    private fun rejectionMessage(r: Rejection?) = when (r) {
        Rejection.NO_ITEMS -> "No te quedan objetos de este tipo."
        Rejection.FULL_HEALTH -> "Tu vida ya está llena."
        Rejection.GAME_OVER -> "No se puede usar durante el Game Over."
        Rejection.NOT_GAME_OVER -> "Revivir solo funciona en Game Over."
        Rejection.NOT_AVAILABLE -> "Revivir ya se usó esta semana."
        Rejection.UNKNOWN_MONSTER -> "Ese monstruo ya no existe."
        Rejection.NEEDS_CONNECTION -> "Revivir necesita que tu teléfono tenga internet."
        Rejection.REGEN_CAP, null -> "No se pudo completar la acción."
    }
}
