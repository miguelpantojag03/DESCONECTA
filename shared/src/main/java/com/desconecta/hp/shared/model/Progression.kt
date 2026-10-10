package com.desconecta.hp.shared.model

import kotlinx.serialization.Serializable

/**
 * Niveles 1–50. La XP necesaria crece suavemente (curva cuadrática ligera): los primeros
 * niveles llegan rápido para enganchar, los últimos premian la constancia.
 * 1 XP = 1 HP que sobró al terminar el día.
 */
object Progression {
    const val MAX_LEVEL = 50

    /** XP total acumulada necesaria para ALCANZAR [level]. */
    fun xpForLevel(level: Int): Int {
        val l = level.coerceIn(1, MAX_LEVEL) - 1
        return 40 * l + 4 * l * l
    }

    fun levelFor(totalXp: Int): Int {
        var level = 1
        while (level < MAX_LEVEL && totalXp >= xpForLevel(level + 1)) level++
        return level
    }

    /** Progreso 0–1 dentro del nivel actual (para la XpBar). */
    fun progressInLevel(totalXp: Int): Float {
        val level = levelFor(totalXp)
        if (level >= MAX_LEVEL) return 1f
        val from = xpForLevel(level)
        val to = xpForLevel(level + 1)
        return (totalXp - from).toFloat() / (to - from)
    }

    fun xpToNextLevel(totalXp: Int): Int {
        val level = levelFor(totalXp)
        return if (level >= MAX_LEVEL) 0 else xpForLevel(level + 1) - totalXp
    }

    fun titleFor(level: Int): String = when {
        level >= 40 -> "Leyenda Zen"
        level >= 25 -> "Maestro del Enfoque"
        level >= 12 -> "Guerrero Offline"
        level >= 5 -> "Aprendiz Desconectado"
        else -> "Novato del Scroll"
    }
}

@Serializable
enum class AchievementId(val title: String, val description: String) {
    FIRST_DAY("Primer día sin Game Over", "Termina un día con vida restante."),
    STREAK_3("Racha de 3", "3 días seguidos sin Game Over."),
    STREAK_7("7 días de racha", "Una semana entera sin Game Over."),
    STREAK_30("Mes imparable", "30 días seguidos sin Game Over."),
    HALF_LIFE("Medio tanque", "Termina un día con más del 50 % de vida."),
    NO_POTIONS_SCROLLZILLA("Cazador puro", "Derrota a Scrollzilla sin usar pociones."),
    FIRST_POTION("Alquimista", "Usa tu primera poción."),
    FIRST_SHIELD("Defensor", "Activa tu primer escudo."),
    REGEN_MAX("Monje del reposo", "Recupera los 20 HP de regeneración en un día."),
    BOSS_SLAYER("Matajefes", "Derrota a un Jefe Final semanal."),
    LEVEL_10("Doble dígito", "Alcanza el nivel 10."),
    COMEBACK("Fénix", "Revive y termina el día sin volver a caer."),
}
