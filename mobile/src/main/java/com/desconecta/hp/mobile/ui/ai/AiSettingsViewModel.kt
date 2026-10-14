package com.desconecta.hp.mobile.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desconecta.hp.mobile.ai.data.AiSettingsRepository
import com.desconecta.hp.mobile.ai.data.LearningState
import com.desconecta.hp.mobile.cloud.AuthRepository
import com.desconecta.hp.mobile.cloud.AuthState
import com.desconecta.hp.mobile.data.GameStore
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.ai.model.CoachTone
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val repo: AiSettingsRepository,
    private val store: GameStore,
    auth: AuthRepository,
) : ViewModel() {

    val settings: StateFlow<AiSettings> = repo.settings
    val learning: StateFlow<LearningState?> = repo.learning.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val signedIn: StateFlow<Boolean> = auth.state.map { it is AuthState.SignedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), auth.state.value is AuthState.SignedIn)
    val hasPin: StateFlow<Boolean> = store.prefs.map { it.hasPin }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggle(feature: AiFeature, on: Boolean) = viewModelScope.launch { repo.update { s, now -> s.toggle(feature, on, now) } }
    fun setCloud(on: Boolean) = viewModelScope.launch { repo.update { s, now -> s.withCloud(on, now) } }
    fun setTone(tone: CoachTone) = viewModelScope.launch { repo.update { s, now -> s.copy(tone = tone, updatedAt = now) } }
    fun setCoachEvery(minutes: Int) = viewModelScope.launch { repo.update { s, now -> s.withCoachEvery(minutes, now) } }

    fun enableFamilyMode() = viewModelScope.launch { repo.update { s, now -> s.withFamilyMode(true, now) } }

    /** Quitar el modo familiar pide el PIN si hay uno (si no, un menor lo apagaría solo). */
    suspend fun disableFamilyMode(pin: String?): Boolean {
        if (store.currentPrefs().hasPin && (pin == null || !store.checkPin(pin))) return false
        repo.update { s, now -> s.withFamilyMode(false, now) }
        return true
    }

    suspend fun deleteAiData(): Result<Unit> = repo.deleteAiData()
}
