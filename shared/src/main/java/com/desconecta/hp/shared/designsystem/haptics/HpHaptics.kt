package com.desconecta.hp.shared.designsystem.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi

/**
 * Eventos del juego que pueden vibrar. Cada uno tiene un patrón distinto para que el
 * jugador sepa qué pasó SIN mirar el reloj (feedback multimodal, heurística de Nielsen
 * nº 1: visibilidad del estado del sistema).
 */
enum class HpHapticEvent {
    /** Daño normal: SIN vibración. Ocurre cada pocos segundos; vibrar sería ruido. */
    DAMAGE,
    /** Advertencia 50 % / 20 %: dos pulsos cortos. */
    WARNING,
    /** Advertencia 10 % y 5 HP: tres pulsos, más urgente (escalada de severidad). */
    WARNING_URGENT,
    /** Estado crítico: latido doble, cada minuto, suave. */
    CRITICAL_HEARTBEAT,
    /** Monstruo derrotado (app bloqueada): un golpe firme. */
    MONSTER_DEFEATED,
    /** Game Over: vibración larga que se desvanece. */
    GAME_OVER,
    /** Level up / logro: tres pulsos ascendentes ("¡ta-ta-TAN!"). */
    CELEBRATION,
    /** Confirmación de usar un objeto. */
    CONFIRM,
    /** Curación (poción, regeneración). */
    HEAL,
}

/**
 * Patrón de vibración como dato (independiente del hardware).
 *
 * @param timings ms alternando pausa / vibración, empezando por pausa (formato de
 *   [VibrationEffect.createWaveform]).
 * @param amplitudes 0–255 por cada tramo de [timings].
 * @param primitives versión con primitivas (API 30+). En relojes con motor LRA se
 *   sienten más nítidas; si el motor no las soporta se usa la forma de onda.
 */
class HapticPattern(
    val timings: LongArray,
    val amplitudes: IntArray,
    val primitives: List<Primitive> = emptyList(),
) {
    init {
        require(timings.size == amplitudes.size)
    }

    /** @param scale intensidad 0–1. @param delayMs pausa previa. */
    data class Primitive(val id: Int, val scale: Float, val delayMs: Int = 0)
}

object HpHaptics {

    // IDs de VibrationEffect.Composition (constantes estables, copiadas para no exigir API 30 aquí).
    private const val PRIMITIVE_CLICK = 1
    private const val PRIMITIVE_THUD = 2
    private const val PRIMITIVE_QUICK_RISE = 4
    private const val PRIMITIVE_TICK = 7

    fun patternFor(event: HpHapticEvent): HapticPattern? = when (event) {
        HpHapticEvent.DAMAGE -> null

        HpHapticEvent.WARNING -> HapticPattern(
            timings = longArrayOf(0, 60, 120, 60),
            amplitudes = intArrayOf(0, 200, 0, 200),
            primitives = listOf(
                HapticPattern.Primitive(PRIMITIVE_CLICK, 0.8f),
                HapticPattern.Primitive(PRIMITIVE_CLICK, 0.8f, delayMs = 150),
            ),
        )

        HpHapticEvent.WARNING_URGENT -> HapticPattern(
            timings = longArrayOf(0, 60, 90, 60, 90, 60),
            amplitudes = intArrayOf(0, 230, 0, 230, 0, 230),
            primitives = listOf(
                HapticPattern.Primitive(PRIMITIVE_CLICK, 1f),
                HapticPattern.Primitive(PRIMITIVE_CLICK, 1f, delayMs = 120),
                HapticPattern.Primitive(PRIMITIVE_CLICK, 1f, delayMs = 120),
            ),
        )

        // "Lub-dub": primer golpe más fuerte y largo, segundo más débil. Suave a propósito:
        // se repite cada minuto y no debe convertirse en castigo.
        HpHapticEvent.CRITICAL_HEARTBEAT -> HapticPattern(
            timings = longArrayOf(0, 70, 110, 45),
            amplitudes = intArrayOf(0, 160, 0, 90),
            primitives = listOf(
                HapticPattern.Primitive(PRIMITIVE_THUD, 0.6f),
                HapticPattern.Primitive(PRIMITIVE_THUD, 0.35f, delayMs = 110),
            ),
        )

        HpHapticEvent.MONSTER_DEFEATED -> HapticPattern(
            timings = longArrayOf(0, 120),
            amplitudes = intArrayOf(0, 220),
            primitives = listOf(HapticPattern.Primitive(PRIMITIVE_THUD, 1f)),
        )

        // Larga pero con caída: un "apagarse", no una alarma.
        HpHapticEvent.GAME_OVER -> HapticPattern(
            timings = longArrayOf(0, 450, 250, 300),
            amplitudes = intArrayOf(0, 255, 160, 70),
        )

        HpHapticEvent.CELEBRATION -> HapticPattern(
            timings = longArrayOf(0, 50, 80, 50, 80, 80),
            amplitudes = intArrayOf(0, 90, 0, 170, 0, 255),
            primitives = listOf(
                HapticPattern.Primitive(PRIMITIVE_CLICK, 0.4f),
                HapticPattern.Primitive(PRIMITIVE_CLICK, 0.7f, delayMs = 90),
                HapticPattern.Primitive(PRIMITIVE_QUICK_RISE, 1f, delayMs = 90),
            ),
        )

        HpHapticEvent.CONFIRM -> HapticPattern(
            timings = longArrayOf(0, 30),
            amplitudes = intArrayOf(0, 180),
            primitives = listOf(HapticPattern.Primitive(PRIMITIVE_CLICK, 0.7f)),
        )

        HpHapticEvent.HEAL -> HapticPattern(
            timings = longArrayOf(0, 20, 60, 20, 60, 20),
            amplitudes = intArrayOf(0, 80, 0, 110, 0, 140),
            primitives = listOf(
                HapticPattern.Primitive(PRIMITIVE_TICK, 0.5f),
                HapticPattern.Primitive(PRIMITIVE_TICK, 0.7f, delayMs = 70),
                HapticPattern.Primitive(PRIMITIVE_TICK, 0.9f, delayMs = 70),
            ),
        )
    }
}

/**
 * Reproduce [HpHapticEvent]s. Respeta el ajuste del jugador ([enabled]) además del modo
 * silencio del sistema (lo aplica Android).
 */
class HpHapticPlayer(context: Context) {

    var enabled: Boolean = true

    private val vibrator: Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }

    fun play(event: HpHapticEvent) {
        if (!enabled) return
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val pattern = HpHaptics.patternFor(event) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pattern.primitives.isNotEmpty() &&
            v.areAllPrimitivesSupported(*pattern.primitives.map { it.id }.toIntArray())
        ) {
            v.vibrate(composition(pattern))
            return
        }
        val amplitudes = if (v.hasAmplitudeControl()) pattern.amplitudes
        else IntArray(pattern.amplitudes.size) { if (pattern.amplitudes[it] > 0) 255 else 0 }
        v.vibrate(VibrationEffect.createWaveform(pattern.timings, amplitudes, -1))
    }

    fun cancel() = vibrator?.cancel()

    @RequiresApi(Build.VERSION_CODES.R)
    private fun composition(pattern: HapticPattern): VibrationEffect =
        VibrationEffect.startComposition().apply {
            pattern.primitives.forEach { addPrimitive(it.id, it.scale, it.delayMs) }
        }.compose()
}
