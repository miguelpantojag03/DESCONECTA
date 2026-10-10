package com.desconecta.hp.shared.model

import kotlinx.serialization.Serializable

/**
 * Vida del jugador. 1 HP = 1 minuto de uso permitido.
 *
 * Es un modelo de dominio puro (sin Android ni Compose) para que lo compartan el
 * reloj, el teléfono y la capa de sincronización.
 */
@Serializable
data class Health(
    val current: Int,
    val max: Int,
) {
    init {
        require(max > 0) { "La vida máxima debe ser positiva" }
    }

    val clamped: Int get() = current.coerceIn(0, max)
    val fraction: Float get() = clamped.toFloat() / max
    val status: HealthStatus get() = HealthStatus.from(clamped, max)

    /** Segmentos llenos de la barra (cada segmento = [HP_PER_SEGMENT] HP). */
    val segmentCount: Int get() = (max + HP_PER_SEGMENT - 1) / HP_PER_SEGMENT

    companion object {
        const val HP_PER_SEGMENT = 10
        val MAX_PRESETS = listOf(60, 90, 120, 180)
    }
}

/**
 * Estados de la barra. Los umbrales siguen la especificación:
 * 100–51 % saludable, 50–21 % herido, 20–1 % crítico, 0 % game over.
 */
enum class HealthStatus {
    HEALTHY,
    HURT,
    CRITICAL,
    GAME_OVER;

    companion object {
        fun from(current: Int, max: Int): HealthStatus {
            if (current <= 0) return GAME_OVER
            // Comparación entera (current * 100 vs max * umbral) para evitar errores de
            // redondeo justo en el borde: 60/120 es exactamente 50 % → HERIDO.
            return when {
                current * 100 > max * 50 -> HEALTHY
                current * 100 > max * 20 -> HURT
                else -> CRITICAL
            }
        }
    }
}

/** Advertencias automáticas, en orden de severidad creciente. */
enum class HpWarning {
    HALF,       // 50 %
    LOW,        // 20 %
    VERY_LOW,   // 10 %
    LAST_FIVE;  // quedan 5 HP

    fun isReached(health: Health): Boolean = when (this) {
        HALF -> health.clamped * 100 <= health.max * 50
        LOW -> health.clamped * 100 <= health.max * 20
        VERY_LOW -> health.clamped * 100 <= health.max * 10
        LAST_FIVE -> health.clamped <= 5
    }

    companion object {
        /** Advertencias que se cruzaron al pasar de [before] a [after] (para disparar cada una una sola vez). */
        fun crossed(before: Health, after: Health): List<HpWarning> =
            entries.filter { !it.isReached(before) && it.isReached(after) }
    }
}

/** "1 h 24 min", "45 min", "0 min". Formato corto pensado para el reloj. */
fun formatMinutes(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    val h = m / 60
    val rest = m % 60
    return when {
        h == 0 -> "$rest min"
        rest == 0 -> "$h h"
        else -> "$h h $rest min"
    }
}
