package com.desconecta.hp.shared.ai.rules

import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import kotlin.math.roundToInt

/**
 * Oráculo simple: extrapola el ritmo de daño de HOY (minutos de monstruos desde las 7:00)
 * hasta medianoche. No usa historial ni modelos: es una estimación y se dice como tal.
 */
object OracleRules {

    const val DAY_START_HOUR = 7

    /** [gameOverAt] null = al ritmo actual llega a medianoche con vida ([hpAtMidnight]). */
    data class Forecast(val gameOverAt: Long?, val hpAtMidnight: Int, val confidence: Confidence, val reason: String)

    /**
     * @param minuteOfDay minutos desde la medianoche (hora local).
     * @param midnight epoch ms de la próxima medianoche.
     */
    fun forecast(state: GameState, now: Long, minuteOfDay: Int, midnight: Long): Forecast? {
        if (state.phase == GamePhase.GAME_OVER) return null
        val awake = minuteOfDay - DAY_START_HOUR * 60
        // Con menos de 10 min de uso o antes de las 7:00 no hay ritmo del que fiarse.
        if (awake <= 0 || state.usedMinutesToday < 10) return null
        val perMinute = state.usedMinutesToday.toDouble() / awake.coerceAtLeast(60)
        val minutesLeft = ((midnight - now) / 60_000).toInt()
        val hpAtMidnight = (state.currentHp - perMinute * minutesLeft).roundToInt()
        val gameOverAt = if (hpAtMidnight <= 0) now + (state.currentHp / perMinute * 60_000).toLong() else null
        // Por la mañana el ritmo cambia mucho: menos certeza.
        val confidence = if (minuteOfDay < 12 * 60) Confidence.LOW else Confidence.MEDIUM
        val hours = awake / 60
        val reason = "Calculado con tu ritmo de hoy: ${state.usedMinutesToday} min de monstruos en " +
            (if (hours >= 1) "$hours h." else "$awake min.")
        return Forecast(gameOverAt, hpAtMidnight.coerceAtLeast(0), confidence, reason)
    }

    /** Frase para el panel. [clock] da "21:40" a partir de un epoch ms. */
    fun sentence(f: Forecast, clock: (Long) -> String): String =
        if (f.gameOverAt != null) "A este ritmo te quedarías sin vida a las ${clock(f.gameOverAt)}."
        else "A este ritmo llegas a medianoche con unos ${f.hpAtMidnight} HP."
}
