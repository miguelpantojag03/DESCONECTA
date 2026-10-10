package com.desconecta.hp.shared.designsystem.motion

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

/** Duraciones en ms. Tres "velocidades" fáciles de recordar: micro, transición, celebración. */
object HpDurations {
    const val MICRO = 150             // pulsar, toggles, cambio de ícono
    const val TRANSITION = 300        // navegación, cambio de color de la barra
    const val CELEBRATION = 650       // poción, regeneración (rango 500–800)
    const val CELEBRATION_LONG = 800  // level up, logro, confeti

    // Barra de vida
    const val GHOST_DELAY = 500       // la barra fantasma espera antes de encogerse
    const val GHOST_SHRINK = 300
    const val HIT_FLASH = 120
    const val SHAKE = 280
    const val HEARTBEAT_PERIOD = 1000 // ≈ 60 lpm, un ritmo de reposo: alerta sin agobiar
}

object HpEasings {
    /** Estándar para transiciones de interfaz: sale rápido, frena suave. */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Decelerate: Easing = CubicBezierEasing(0f, 0f, 0f, 1f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /**
     * Drenaje de vida: lineal. El tiempo pasa a velocidad constante, así que la barra
     * también; un easing aquí "mentiría" sobre el ritmo real del consumo.
     */
    val Drain: Easing = LinearEasing

    /**
     * "Rebote suave" para recompensas (easeOutBack): sobrepasa ~10 % y vuelve.
     * Comunica alegría sin el exceso de un rebote elástico completo.
     */
    val SoftBounce: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** Latido: subida rápida (sístole) y bajada lenta. */
    val Heartbeat: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}

/**
 * Especificaciones de animación listas para usar, conscientes de "Reducir animaciones".
 *
 * Con [reduced] = true se desactivan sacudidas, destellos, partículas, latido y confeti,
 * y se conservan SOLO los cambios de valor (la barra sigue bajando, el número cambia),
 * porque esos transmiten información y no movimiento decorativo.
 */
@Immutable
data class HpMotion(val reduced: Boolean = false) {

    val shakeEnabled get() = !reduced
    val flashEnabled get() = !reduced
    val particlesEnabled get() = !reduced
    val heartbeatEnabled get() = !reduced
    val confettiEnabled get() = !reduced

    fun <T> micro(): AnimationSpec<T> = tween(HpDurations.MICRO, easing = HpEasings.Standard)

    fun <T> transition(): AnimationSpec<T> = tween(HpDurations.TRANSITION, easing = HpEasings.Standard)

    /** Cambio de valor de la barra (daño). Se mantiene con movimiento reducido, más corto. */
    fun <T> drain(): AnimationSpec<T> =
        tween(if (reduced) HpDurations.MICRO else HpDurations.TRANSITION, easing = HpEasings.Drain)

    /** Barra fantasma: espera y luego se encoge. Con movimiento reducido desaparece de golpe. */
    fun <T> ghost(): AnimationSpec<T> =
        if (reduced) snap()
        else tween(HpDurations.GHOST_SHRINK, delayMillis = HpDurations.GHOST_DELAY, easing = HpEasings.Standard)

    /** Curación (poción / regeneración): llenado con rebote suave. */
    fun <T> heal(): AnimationSpec<T> =
        if (reduced) tween(HpDurations.MICRO, easing = HpEasings.Standard)
        else tween(HpDurations.CELEBRATION, easing = HpEasings.SoftBounce)

    /** Entrada de recompensas (medallas, "LEVEL UP!"). */
    fun <T> reward(): AnimationSpec<T> =
        if (reduced) tween(HpDurations.MICRO)
        else spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow)

    /** Transición de color verde → amarillo → rojo. */
    fun <T> statusColor(): AnimationSpec<T> = tween(HpDurations.TRANSITION, easing = LinearEasing)
}

/**
 * Lee el ajuste del sistema "Quitar animaciones" (Accesibilidad), que Android expone
 * como `ANIMATOR_DURATION_SCALE = 0`, y se actualiza si el usuario lo cambia en vivo.
 */
@Composable
fun rememberSystemReducedMotion(): Boolean {
    // En @Preview no hay ajustes del sistema reales: animaciones normales.
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    val resolver = context.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = read()
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}
