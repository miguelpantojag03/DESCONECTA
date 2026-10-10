package com.desconecta.hp.shared.designsystem.component.healthbar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.haptics.HpHapticEvent
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.motion.HpDurations
import com.desconecta.hp.shared.designsystem.motion.HpEasings
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.HealthStatus
import com.desconecta.hp.shared.model.MonsterArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Qué representa el tramo entre [HealthBarFrame.fill] y [HealthBarFrame.trail]. */
enum class TrailKind {
    NONE,
    /** Barra fantasma blanca: vida que se acaba de perder. */
    DAMAGE,
    /** Avance cian: vida que se está recuperando (poción / regeneración). */
    HEAL,
}

/** Monstruo que está haciendo daño ahora mismo. */
@Immutable
data class HealthAttacker(
    val name: String,
    val sprite: ImageVector,
    val ambientSprite: ImageVector,
    /** Monstruo muy adictivo: cada golpe lleva destello y sacudida. */
    val ferocious: Boolean,
) {
    companion object {
        fun of(art: MonsterArt, name: String = art.defaultName) = HealthAttacker(
            name = name,
            sprite = MonsterSprites.of(art),
            ambientSprite = MonsterSprites.ambientOf(art),
            ferocious = art.ferocious,
        )
    }
}

/**
 * Una "foto" de todo lo que se dibuja en un instante. Los componentes de dibujo son
 * funciones puras de este frame, lo que permite previsualizar cualquier momento de una
 * animación (p. ej. la barra fantasma a medio encoger) con un @Preview estático.
 */
@Immutable
data class HealthBarFrame(
    /** Barra principal, 0–1. */
    val fill: Float,
    /** Extremo de la estela (fantasma o curación), 0–1. */
    val trail: Float,
    val trailKind: TrailKind,
    /** Color de estado (ya interpolado durante la transición verde→amarillo→rojo). */
    val color: Color,
    /** Intensidad del destello de golpe, 0–1. */
    val flash: Float = 0f,
    /** Desplazamiento horizontal de la sacudida, en px. */
    val shakeX: Float = 0f,
    /** Escala del latido en estado crítico (1.0–1.04). */
    val pulse: Float = 1f,
    /** Progreso de las partículas de curación 0–1, o null si no hay. */
    val particles: Float? = null,
    /** Tramo donde nacen las partículas (fracciones de la barra). */
    val particlesFrom: Float = 0f,
    val particlesTo: Float = 0f,
) {
    companion object {
        /** Frame en reposo, sin animación (Ambient, previews). */
        fun still(health: Health, color: Color) = HealthBarFrame(
            fill = health.fraction,
            trail = health.fraction,
            trailKind = TrailKind.NONE,
            color = color,
        )
    }
}

/** Perder de golpe esta cantidad de HP o más cuenta como "daño fuerte". */
private const val HEAVY_HIT_HP = 3

/** Amplitud de la sacudida. */
private val SHAKE_AMPLITUDE = 6.dp

/** Las partículas de regeneración continua nacen en el último ~15 % de la barra llena. */
private const val REGEN_PARTICLE_SPAN = 0.15f

/**
 * Orquesta todas las animaciones de la barra a partir del valor real de vida:
 *
 * - Daño: la barra principal baja (lineal, 300 ms); la fantasma espera 500 ms y se
 *   encoge. Si llega otro golpe antes, la fantasma se queda en el punto más alto
 *   (como los "combos" de los juegos de pelea).
 * - Daño fuerte (≥ 3 HP o monstruo feroz): destello blanco + sacudida.
 * - Curación: el avance cian aparece al instante y la barra lo alcanza con rebote
 *   suave, mientras suben partículas.
 * - Crítico: latido continuo, opcionalmente sincronizado con vibración cada minuto.
 * - Movimiento reducido: solo cambios de valor. Ambient: todo instantáneo.
 */
@Composable
fun rememberHealthBarFrame(
    health: Health,
    attacker: HealthAttacker? = null,
    regenerating: Boolean = false,
    heartbeatHaptics: Boolean = false,
): HealthBarFrame {
    val motion = Hp.motion
    val colors = Hp.colors
    val haptics = Hp.haptics
    val shakePx = with(LocalDensity.current) { SHAKE_AMPLITUDE.toPx() }
    val instant = colors.isAmbient
    val target = health.fraction
    val status = health.status

    val fill = remember { Animatable(target) }
    val trail = remember { Animatable(target) }
    var trailKind by remember { mutableStateOf(TrailKind.NONE) }
    val flash = remember { Animatable(0f) }
    val shake = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    var burstFrom by remember { mutableFloatStateOf(0f) }
    var burstTo by remember { mutableFloatStateOf(0f) }
    var bursting by remember { mutableStateOf(false) }

    LaunchedEffect(target, instant) {
        val from = fill.value
        if (instant) {
            fill.snapTo(target)
            trail.snapTo(target)
            trailKind = TrailKind.NONE
            return@LaunchedEffect
        }
        when {
            target < from -> {
                trailKind = TrailKind.DAMAGE
                if (trail.value < from) trail.snapTo(from)
                val lostHp = (from - target) * health.max
                val heavy = lostHp >= HEAVY_HIT_HP || attacker?.ferocious == true
                launch { fill.animateTo(target, motion.drain()) }
                if (heavy && motion.flashEnabled) launch {
                    flash.snapTo(1f)
                    flash.animateTo(0f, tween(HpDurations.HIT_FLASH, easing = LinearEasing))
                }
                if (heavy && motion.shakeEnabled) launch {
                    shake.animateTo(0f, keyframes {
                        durationMillis = HpDurations.SHAKE
                        -shakePx at 40
                        shakePx at 90
                        -shakePx * 0.6f at 150
                        shakePx * 0.3f at 210
                    })
                }
                trail.animateTo(target, motion.ghost())
                trailKind = TrailKind.NONE
            }

            target > from -> {
                trailKind = TrailKind.HEAL
                trail.snapTo(target)
                haptics?.play(HpHapticEvent.HEAL)
                if (motion.particlesEnabled) launch {
                    burstFrom = from
                    burstTo = target
                    bursting = true
                    burst.snapTo(0f)
                    burst.animateTo(1f, tween(HpDurations.CELEBRATION_LONG, easing = LinearEasing))
                    bursting = false
                }
                fill.animateTo(target, motion.heal())
                fill.snapTo(target) // el rebote puede sobrepasar: se asienta exacto
                trailKind = TrailKind.NONE
            }

            else -> trail.snapTo(target)
        }
    }

    val color by animateColorAsState(
        targetValue = colors.forStatus(status),
        animationSpec = if (instant) snap() else motion.statusColor(),
        label = "hpColor",
    )

    // Latido: "lub-dub" en 1 s. Solo existe mientras se está en crítico.
    val critical = status == HealthStatus.CRITICAL
    val pulse = if (critical && motion.heartbeatEnabled && !instant) {
        val beat = rememberInfiniteTransition(label = "heartbeat")
        beat.animateFloat(
            initialValue = 1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                keyframes {
                    durationMillis = HpDurations.HEARTBEAT_PERIOD
                    1f at 0
                    1.04f at 120 using HpEasings.Heartbeat
                    1f at 260
                    1.025f at 380 using HpEasings.Heartbeat
                    1f at 560
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "pulse",
        ).value
    } else 1f

    // La vibración arranca en el mismo frame que la animación y se repite cada 60
    // latidos exactos, así ambas quedan sincronizadas.
    LaunchedEffect(critical && heartbeatHaptics && !instant) {
        if (!(critical && heartbeatHaptics && !instant)) return@LaunchedEffect
        while (true) {
            haptics?.play(HpHapticEvent.CRITICAL_HEARTBEAT)
            delay(60L * HpDurations.HEARTBEAT_PERIOD)
        }
    }

    // Regeneración continua (p. ej. mientras el reloj muestra "regenerando…").
    val regenLoop = if (regenerating && motion.particlesEnabled && !instant) {
        rememberInfiniteTransition(label = "regen").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(HpDurations.CELEBRATION_LONG * 2, easing = LinearEasing)),
            label = "regenParticles",
        ).value
    } else null

    val particles = when {
        bursting -> burst.value
        regenLoop != null -> regenLoop
        else -> null
    }
    val (pFrom, pTo) = if (bursting) burstFrom to burstTo
    else (fill.value - REGEN_PARTICLE_SPAN).coerceAtLeast(0f) to fill.value

    return HealthBarFrame(
        fill = fill.value.coerceIn(0f, 1f),
        trail = trail.value.coerceIn(0f, 1f),
        trailKind = trailKind,
        color = color,
        flash = flash.value,
        shakeX = shake.value,
        pulse = pulse,
        particles = particles,
        particlesFrom = pFrom,
        particlesTo = pTo,
    )
}
