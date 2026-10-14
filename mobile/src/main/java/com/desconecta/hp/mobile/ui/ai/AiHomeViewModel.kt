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
import kotlinx.coroutines.flow.map
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
    game: com.desconecta.hp.mobile.data.GameRepository,
) : ViewModel() {

    /** Oráculo: se recalcula cada minuto y cuando cambia el estado del juego. */
    val oracle: StateFlow<OracleLine?> = combine(
        game.state,
        settings.settings,
        kotlinx.coroutines.flow.flow { while (true) { emit(Unit); kotlinx.coroutines.delay(60_000) } },
    ) { s, cfg, _ ->
        if (s == null || !cfg.isOn(AiFeature.ORACLE)) return@combine null
        val zone = ZoneId.systemDefault()
        val nowZ = java.time.ZonedDateTime.now(zone)
        val midnight = nowZ.toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val f = com.desconecta.hp.shared.ai.rules.OracleRules.forecast(
            s, System.currentTimeMillis(), nowZ.hour * 60 + nowZ.minute, midnight,
        ) ?: return@combine null
        val text = com.desconecta.hp.shared.ai.rules.OracleRules.sentence(f) { ms ->
            java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        }
        OracleLine(text, f.reason, f.confidence, danger = f.gameOverAt != null)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Aviso berserker de los últimos 30 min sin responder. */
    val berserker: StateFlow<AiInsight?> = insights.latest(InsightType.COMPULSIVE_ALERT).map { a ->
        a?.takeIf { it.response == com.desconecta.hp.shared.ai.model.UserResponse.NONE && System.currentTimeMillis() - it.createdAt < 30 * 60_000 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun answerBerserker(insight: AiInsight, pause: Boolean) = viewModelScope.launch {
        insights.respond(insight.id, if (pause) com.desconecta.hp.shared.ai.model.UserResponse.ACCEPTED else com.desconecta.hp.shared.ai.model.UserResponse.REJECTED)
    }

    val coach: StateFlow<AiInsight?> = combine(insights.latest(InsightType.COACH_MESSAGE), settings.settings) { latest, cfg ->
        latest?.takeIf { cfg.isOn(AiFeature.COACH) && it.isToday() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val limits: StateFlow<List<AiInsight>> = combine(insights.pending(InsightType.LIMIT_SUGGESTION), settings.settings) { list, cfg ->
        if (cfg.isOn(AiFeature.LIMITS)) list else emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Último diario de batalla (Historial). */
    val journal: StateFlow<AiInsight?> = combine(insights.latest(InsightType.DAILY_SUMMARY), settings.settings) { j, cfg ->
        j?.takeIf { cfg.isOn(AiFeature.JOURNAL) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun feedback(insight: AiInsight, value: AiFeedback) = viewModelScope.launch { insights.feedback(insight.id, value) }
    fun accept(insight: AiInsight, minutes: Int) = viewModelScope.launch { coordinator.acceptLimit(insight, minutes) }
    fun reject(insight: AiInsight) = viewModelScope.launch { coordinator.rejectLimit(insight) }

    private fun AiInsight.isToday() =
        Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
}

/** Frase del Oráculo lista para pintar. [danger] = prevé Game Over antes de medianoche. */
data class OracleLine(val text: String, val reason: String, val confidence: com.desconecta.hp.shared.ai.model.Confidence, val danger: Boolean)
