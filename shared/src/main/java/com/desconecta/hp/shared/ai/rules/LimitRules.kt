package com.desconecta.hp.shared.ai.rules

import com.desconecta.hp.shared.ai.engine.AiResult
import com.desconecta.hp.shared.ai.engine.DayUsage
import com.desconecta.hp.shared.ai.model.AiOrigin
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.ai.model.LimitSuggestion
import com.desconecta.hp.shared.model.GameState
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Límites inteligentes por reglas (sección 3.3). Nunca aplica nada: propone y el jugador
 * acepta, ajusta o dice "ahora no".
 *
 *  · Uso muy por debajo del límite → bajarlo a la media × 0,9 (redondeado a 5 min). Un reto
 *    pequeño y realista, no un recorte brusco.
 *  · Límite superado 5 de los últimos 7 días → proponer uno ALCANZABLE (a medio camino
 *    entre el límite y la media). Un límite imposible solo produce Game Over y abandono.
 *  · Necesita 7 días con datos; antes, el estado es "aprendiendo".
 *  · Como mucho 2 propuestas a la vez, y nunca para un monstruo rechazado hace < 7 días.
 */
object LimitRules {

    const val MIN_LIMIT = 10
    const val MAX_SUGGESTIONS = 2
    const val WINDOW_DAYS = 7

    fun round5(v: Double): Int = ((v / 5.0).roundToInt() * 5)

    /**
     * @param history días anteriores (más reciente primero).
     * @param skip paquetes con una propuesta pendiente o rechazada hace poco.
     */
    fun suggest(state: GameState, history: List<DayUsage>, skip: Set<String>): AiResult<List<LimitSuggestion>> {
        if (history.size < AiSettings.LEARNING_DAYS) {
            return AiResult.Learning(AiSettings.learningDay(history.size), AiSettings.LEARNING_DAYS)
        }
        val window = history.take(WINDOW_DAYS)
        val proposals = state.monsters.filter { it.packageName !in skip }.mapNotNull { m ->
            val totals = window.map { day -> day.hourly.sumOf { it[m.packageName] ?: 0 } }
            // Sin uso en toda la semana no hay en qué basarse (puede ser un monstruo recién añadido).
            if (totals.all { it == 0 }) return@mapNotNull null
            val avg = totals.average()
            val exceeded = totals.count { it > m.limitMinutes }
            val suggested = when {
                exceeded >= 5 -> round5((m.limitMinutes + avg) / 2).coerceAtLeast(m.limitMinutes + 5)
                round5(avg * 0.9) <= m.limitMinutes - 10 -> round5(avg * 0.9).coerceAtLeast(MIN_LIMIT)
                else -> return@mapNotNull null
            }
            if (suggested == m.limitMinutes) return@mapNotNull null
            val reason = if (exceeded >= 5) {
                "Superaste el límite de ${m.appLabel} $exceeded de los últimos 7 días (media ${avg.roundToInt()} min). " +
                    "Un límite alcanzable ayuda a mantener la racha."
            } else {
                "Tu media en ${m.appLabel} fue ${avg.roundToInt()} min al día los últimos 7 días, " +
                    "y tu límite es ${m.limitMinutes} min."
            }
            Triple(LimitSuggestion(m.packageName, m.limitMinutes, suggested), confidence(totals), reason)
        }.sortedByDescending { abs(it.first.suggestedMinutes - it.first.currentMinutes) }.take(MAX_SUGGESTIONS)

        if (proposals.isEmpty()) return AiResult.Ready(emptyList(), AiOrigin.RULES, Confidence.HIGH, "Tus límites encajan con tu uso.")
        // Una sola explicación por propuesta: se guardan por separado (ver [split]).
        return AiResult.Ready(
            proposals.map { it.first },
            AiOrigin.RULES,
            proposals.minOf { it.second },
            proposals.joinToString("\n") { it.third },
        )
    }

    /** Cada propuesta con su propia certeza y su "¿Por qué?". */
    fun split(state: GameState, history: List<DayUsage>, skip: Set<String>): List<AiResult.Ready<LimitSuggestion>> {
        val all = suggest(state, history, skip) as? AiResult.Ready ?: return emptyList()
        val reasons = all.reason.split("\n")
        return all.value.mapIndexed { i, s ->
            val totals = history.take(WINDOW_DAYS).map { day -> day.hourly.sumOf { it[s.packageName] ?: 0 } }
            AiResult.Ready(s, AiOrigin.RULES, confidence(totals), reasons.getOrElse(i) { "" })
        }
    }

    /**
     * Certeza según lo regular que es el uso (coeficiente de variación): días muy distintos
     * entre sí → "Quizá · poca certeza".
     */
    fun confidence(totals: List<Int>): Confidence {
        val mean = totals.average()
        if (mean <= 0.0) return Confidence.MEDIUM
        val sd = sqrt(totals.sumOf { (it - mean) * (it - mean) } / totals.size)
        val cv = sd / mean
        return when {
            cv < 0.3 -> Confidence.HIGH
            cv < 0.6 -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
    }
}
