package com.desconecta.hp.shared.ai.rules

import com.desconecta.hp.shared.ai.model.BattleJournal
import com.desconecta.hp.shared.model.DaySummary

/**
 * Diario de batalla por plantilla: cuenta el día como una batalla con los datos reales.
 * Tono amable siempre: un Game Over se cuenta como "los monstruos ganaron esta vez", nunca
 * como un fracaso del jugador.
 */
object JournalRules {

    /** Uso de un monstruo ese día. */
    data class Fight(val monsterName: String, val minutes: Int, val limit: Int)

    /** @param worstHour hora con más minutos de monstruos (0–23), si se conoce. */
    fun write(day: DaySummary, fights: List<Fight>, worstHour: Int?): BattleJournal {
        val rival = fights.filter { it.minutes > 0 }.maxByOrNull { it.minutes }
        val kept = fights.count { it.minutes <= it.limit }

        val opening = if (day.gameOver) {
            "Los monstruos ganaron esta vez: Game Over antes de medianoche."
        } else {
            "¡Victoria! Terminaste el día con ${day.hpLeft} de ${day.maxHp} HP y ganaste ${day.xpEarned} XP."
        }
        val rivalLine = rival?.let { "${it.monsterName} fue tu rival más duro: ${it.minutes} min de combate." }
        val keptLine = if (fights.isNotEmpty()) "Mantuviste a raya a $kept de ${fights.size} monstruos." else null
        val hourLine = worstHour?.let { "La batalla más intensa fue hacia las ${"%02d".format(it)}:00." }
        val closing = when {
            day.gameOver -> "Hoy empiezas con la vida llena: es tu revancha."
            day.hpLeft * 2 >= day.maxHp -> "Un día de leyenda. Sigue así."
            else -> "Llegaste a medianoche en pie, y eso cuenta."
        }

        val short = if (day.gameOver) {
            "Game Over" + (rival?.let { ". Rival: ${it.monsterName}" } ?: "") + ". Hoy, revancha."
        } else {
            "Victoria con ${day.hpLeft} HP" + (rival?.let { ". Rival: ${it.monsterName}" } ?: "") + "."
        }
        return BattleJournal(
            dayKey = day.dayKey,
            short = short,
            full = listOfNotNull(opening, rivalLine, keptLine, hourLine, closing).joinToString(" "),
            worstHour = worstHour,
        )
    }
}
