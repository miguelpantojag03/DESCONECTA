package com.desconecta.hp.mobile.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desconecta.hp.mobile.ai.AiCoordinator
import com.desconecta.hp.mobile.ai.data.AiInsightRepository
import com.desconecta.hp.mobile.ai.data.AiSettingsRepository
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiFeedback
import com.desconecta.hp.shared.ai.model.AiInsight
import com.desconecta.hp.shared.ai.model.InsightType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Lo que la IA muestra en el panel: la frase del coach de hoy y las sugerencias pendientes. */
@HiltViewModel
class AiHomeViewModel @Inject constructor(
    private val insights: AiInsightRepository,
    private val coordinator: AiCoordinator,
    settings: AiSettingsRepository,
) : ViewModel() {

    val coach: StateFlow<AiInsight?> = combine(insights.latest(InsightType.COACH_MESSAGE), settings.settings) { latest, cfg ->
        latest?.takeIf { cfg.isOn(AiFeature.COACH) && it.isToday() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val limits: StateFlow<List<AiInsight>> = combine(insights.pending(InsightType.LIMIT_SUGGESTION), settings.settings) { list, cfg ->
        if (cfg.isOn(AiFeature.LIMITS)) list else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun feedback(insight: AiInsight, value: AiFeedback) = viewModelScope.launch { insights.feedback(insight.id, value) }
    fun accept(insight: AiInsight, minutes: Int) = viewModelScope.launch { coordinator.acceptLimit(insight, minutes) }
    fun reject(insight: AiInsight) = viewModelScope.launch { coordinator.rejectLimit(insight) }

    private fun AiInsight.isToday() =
        Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
}
