package com.desconecta.hp.shared.ai.engine

import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiOrigin
import com.desconecta.hp.shared.ai.model.BattleJournal
import com.desconecta.hp.shared.ai.model.BossProposal
import com.desconecta.hp.shared.ai.model.CoachLine
import com.desconecta.hp.shared.ai.model.CompulsiveSignal
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.ai.model.ContextSuggestion
import com.desconecta.hp.shared.ai.model.HpPrediction
import com.desconecta.hp.shared.ai.model.LimitSuggestion
import com.desconecta.hp.shared.ai.model.VoiceIntent
import com.desconecta.hp.shared.model.GameState

// =====================================================================================
// Resultado común a toda función de IA. Cubre los 9 estados de la sección 9 del diseño:
// ninguna pantalla de IA puede quedar en blanco.
// =====================================================================================

sealed interface AiResult<out T> {

    /** Resultado listo. [reason] alimenta el "¿Por qué?"; [confidence], el lenguaje de certeza. */
    data class Ready<T>(
        val value: T,
        val origin: AiOrigin,
        val confidence: Confidence,
        val reason: String,
    ) : AiResult<T> {
        val lowConfidence: Boolean get() = confidence == Confidence.LOW
    }

    /** Pocos datos todavía: "La IA está conociéndote: día 3 de 7". */
    data class Learning(val day: Int, val of: Int) : AiResult<Nothing>

    /** Calculando (animación "pensando" del avatar). */
    data object Thinking : AiResult<Nothing>

    /** El modelo falló; si hay [fallback] (reglas/plantilla) se muestra ese. */
    data class Failed<T>(val message: String, val fallback: Ready<T>? = null) : AiResult<T>

    /** Se pidió la nube pero no hay red. */
    data object Offline : AiResult<Nothing>

    /** La nube no está autorizada (consentimiento) o está bloqueada por modo familiar. */
    data object CloudNotAllowed : AiResult<Nothing>

    /** El jugador desactivó esta función. */
    data class Disabled(val feature: AiFeature) : AiResult<Nothing>

    /** Falta un permiso del sistema (uso de apps, micrófono, calendario). */
    data class MissingPermission(val permission: String) : AiResult<Nothing>
}

/**
 * Datos de entrada de la IA: SOLO agregados del juego (nombres de apps, minutos, horas,
 * HP). Nunca contenido de mensajes, fotos ni pantallas.
 */
data class AiInput(
    val now: Long,
    val state: GameState,
    /** Minutos por app y por hora de HOY: hourly[hora][paquete] = minutos. */
    val todayHourly: List<Map<String, Int>>,
    /** Mismo formato para días anteriores, del más reciente al más antiguo. */
    val history: List<DayUsage>,
    /** Días con datos (para el periodo de aprendizaje). */
    val daysOfData: Int,
)

data class DayUsage(
    val dayKey: String,
    /** 1 = lunes … 7 = domingo. */
    val dayOfWeek: Int,
    val hourly: List<Map<String, Int>>,
    val hpLeft: Int,
    val gameOver: Boolean,
)

// =====================================================================================
// Interfaces de AiEngine. Cada una tiene 3 implementaciones posibles (reglas,
// dispositivo, nube) y el AiRouter elige cuál usar en cada llamada.
// =====================================================================================

/** 3.1 Oráculo: predicción de consumo de HP. 100 % local. */
interface PredictionService {
    suspend fun predict(input: AiInput): AiResult<HpPrediction>
}

/** 3.2 "Modo berserker". 100 % local, se evalúa en cada tick del monitor. */
interface CompulsiveUseDetector {
    /** null si no hay patrón compulsivo en este momento. */
    fun evaluate(input: AiInput, foregroundPackage: String?, sessionMinutes: Int, opensLast10Min: Int): CompulsiveSignal?
}

/** 3.4 Coach del avatar. */
interface CoachService {
    suspend fun line(input: AiInput): AiResult<CoachLine>
}

/** 3.5 Comandos de voz: texto → intención estructurada (nunca ejecuta nada). */
interface VoiceCommandService {
    suspend fun interpret(text: String, input: AiInput): AiResult<VoiceIntent>
}

/** 3.3 Límites inteligentes + 3.6 diario de batalla. */
interface InsightService {
    suspend fun suggestLimits(input: AiInput): AiResult<List<LimitSuggestion>>
    suspend fun dailyJournal(input: AiInput, day: DayUsage): AiResult<BattleJournal>
    suspend fun weeklySummary(input: AiInput): AiResult<BattleJournal>
}

/** 3.7 Jefes finales. */
interface ChallengeService {
    suspend fun weeklyBoss(input: AiInput, previousFailed: Boolean): AiResult<BossProposal>
}

/** 3.8 Detección de contexto (horarios de estudio y descanso). 100 % local. */
interface ContextService {
    suspend fun suggestion(input: AiInput): AiResult<ContextSuggestion>
}
