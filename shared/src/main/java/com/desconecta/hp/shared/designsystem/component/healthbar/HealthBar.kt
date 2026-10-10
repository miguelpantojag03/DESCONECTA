package com.desconecta.hp.shared.designsystem.component.healthbar

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.R
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarDrawing.drawArcBar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarDrawing.drawHealParticles
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarDrawing.drawLinearBar
import com.desconecta.hp.shared.designsystem.motion.HpDurations
import com.desconecta.hp.shared.designsystem.spacing.HealthBarDimens
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.designsystem.theme.healthContentDescription
import com.desconecta.hp.shared.designsystem.theme.rememberStatusVisual
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.formatMinutes

enum class HealthBarVariant {
    /** Arco en reloj redondo; recta en reloj cuadrado y teléfono. */
    AUTO,
    ARC,
    LINEAR,
}

/**
 * Componente estrella: barra de vida con lectura numérica.
 *
 * @param attacker monstruo que está haciendo daño ahora (se dibuja en la punta de la barra).
 * @param regenerating muestra partículas de regeneración continuas.
 * @param heartbeatHaptics vibra con el latido en estado crítico (solo en el HUD del reloj,
 *   para que no vibren varias barras a la vez).
 * @param showReadout muestra "84 / 120 HP · 1 h 24 min" junto a la barra.
 */
@Composable
fun HealthBar(
    health: Health,
    modifier: Modifier = Modifier,
    variant: HealthBarVariant = HealthBarVariant.AUTO,
    attacker: HealthAttacker? = null,
    regenerating: Boolean = false,
    heartbeatHaptics: Boolean = false,
    showReadout: Boolean = true,
) {
    val frame = rememberHealthBarFrame(health, attacker, regenerating, heartbeatHaptics)
    HealthBarLayout(health, frame, modifier, variant, attacker, showReadout)
}

/**
 * Versión sin estado: dibuja exactamente el [frame] que recibe. Útil para @Preview de
 * momentos intermedios de una animación y para pruebas de captura de pantalla.
 */
@Composable
fun HealthBarLayout(
    health: Health,
    frame: HealthBarFrame,
    modifier: Modifier = Modifier,
    variant: HealthBarVariant = HealthBarVariant.AUTO,
    attacker: HealthAttacker? = null,
    showReadout: Boolean = true,
) {
    val resolved = when (variant) {
        HealthBarVariant.AUTO ->
            if (Hp.formFactor == HpFormFactor.WATCH && LocalConfiguration.current.isScreenRound) HealthBarVariant.ARC
            else HealthBarVariant.LINEAR
        else -> variant
    }
    val description = healthContentDescription(health) +
        (attacker?.let { ". " + stringResource(R.string.hp_cd_attacker, it.name) } ?: "")
    val statusLabel = rememberStatusVisual(health.status).label

    val a11y = Modifier.clearAndSetSemantics {
        contentDescription = description
        stateDescription = statusLabel
        progressBarRangeInfo = ProgressBarRangeInfo(health.clamped.toFloat(), 0f..health.max.toFloat())
    }

    when (resolved) {
        HealthBarVariant.ARC -> ArcLayout(health, frame, attacker, showReadout, modifier.then(a11y))
        else -> LinearLayout(health, frame, attacker, showReadout, modifier.then(a11y))
    }
}

// -------------------------------------------------------------------------------------
// Arco
// -------------------------------------------------------------------------------------

@Composable
private fun ArcLayout(
    health: Health,
    frame: HealthBarFrame,
    attacker: HealthAttacker?,
    showReadout: Boolean,
    modifier: Modifier,
) {
    val colors = Hp.colors
    val attackerPainter = attacker?.let {
        rememberVectorPainter(if (colors.isAmbient) it.ambientSprite else it.sprite)
    }
    Box(
        modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .graphicsLayer { translationX = frame.shakeX },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = frame.pulse
                    scaleY = frame.pulse
                },
        ) {
            val geo = with(HealthBarDrawing) {
                arcGeometry(HealthBarDimens.arcStrokeWatch.toPx(), HealthBarDimens.arcInsetWatch.toPx())
            }
            val start = HealthBarDimens.ARC_START_DEGREES
            val sweep = HealthBarDimens.ARC_SWEEP_DEGREES
            val gap = HealthBarDimens.ARC_SEGMENT_GAP_DEGREES
            drawArcBar(frame, health.max, colors, geo, start, sweep, gap)

            frame.particles?.let { p ->
                val a0 = HealthBarDrawing.angleFor(frame.particlesFrom, health.max, start, sweep, gap)
                val a1 = HealthBarDrawing.angleFor(frame.particlesTo, health.max, start, sweep, gap)
                drawHealParticles(p, colors.regen, risePx = 28.dp.toPx()) { along ->
                    HealthBarDrawing.pointOnArc(geo, a0 + (a1 - a0) * along)
                }
            }

            // El monstruo "muerde" la punta de la barra: causa y efecto juntos.
            if (attackerPainter != null && health.clamped > 0) {
                val iconPx = HealthBarDimens.attackerIconWatch.toPx()
                val tipDeg = HealthBarDrawing.angleFor(frame.fill, health.max, start, sweep, gap)
                val pos = HealthBarDrawing.pointOnArc(geo, tipDeg, geo.radius - geo.stroke / 2 - iconPx / 2 - 2.dp.toPx())
                translate(pos.x - iconPx / 2, pos.y - iconPx / 2) {
                    with(attackerPainter) {
                        draw(
                            size = androidx.compose.ui.geometry.Size(iconPx, iconPx),
                            colorFilter = if (colors.isAmbient) ColorFilter.tint(colors.textSecondary) else null,
                        )
                    }
                }
            }
        }

        if (showReadout) {
            HealthReadout(health, frame, stacked = true)
        }
    }
}

// -------------------------------------------------------------------------------------
// Lineal (reloj cuadrado, teléfono)
// -------------------------------------------------------------------------------------

@Composable
private fun LinearLayout(
    health: Health,
    frame: HealthBarFrame,
    attacker: HealthAttacker?,
    showReadout: Boolean,
    modifier: Modifier,
) {
    val watch = Hp.formFactor == HpFormFactor.WATCH
    val barHeight = if (watch) HealthBarDimens.linearHeightWatch else HealthBarDimens.linearHeightPhone
    val iconSize = if (watch) HealthBarDimens.attackerIconWatch else HealthBarDimens.attackerIconPhone

    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer { translationX = frame.shakeX },
        horizontalAlignment = if (watch) Alignment.CenterHorizontally else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(if (watch) HpSpacing.sm else HpSpacing.md),
    ) {
        if (showReadout) HealthReadout(health, frame, stacked = watch)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (attacker != null) {
                Image(
                    imageVector = if (Hp.colors.isAmbient) attacker.ambientSprite else attacker.sprite,
                    contentDescription = null,
                    modifier = Modifier.size(iconSize),
                    colorFilter = if (Hp.colors.isAmbient) ColorFilter.tint(Hp.colors.textSecondary) else null,
                )
                Spacer(Modifier.width(HpSpacing.sm))
            }
            SegmentedBar(
                frame = frame,
                max = health.max,
                height = barHeight,
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        scaleX = frame.pulse
                        scaleY = frame.pulse
                    },
            )
        }
    }
}

/**
 * Barra recta segmentada, sin lectura. La reutilizan [HealthBar] (variante lineal) y
 * la mini barra de cada monstruo.
 */
@Composable
fun SegmentedBar(
    frame: HealthBarFrame,
    max: Int,
    modifier: Modifier = Modifier,
    height: Dp = HealthBarDimens.linearHeightPhone,
    segmented: Boolean = true,
) {
    val colors = Hp.colors
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawLinearBar(frame, max, colors, HealthBarDimens.linearSegmentGap.toPx(), segmented)
        frame.particles?.let { p ->
            drawHealParticles(p, colors.regen, risePx = height.toPx() * 2.5f) { along ->
                val f = frame.particlesFrom + (frame.particlesTo - frame.particlesFrom) * along
                androidx.compose.ui.geometry.Offset(size.width * f, size.height / 2)
            }
        }
    }
}

// -------------------------------------------------------------------------------------
// Lectura numérica
// -------------------------------------------------------------------------------------

/**
 * "84" grande + "/ 120 HP" + "1 h 24 min" + estado (ícono y texto).
 *
 * Jerarquía: 1) cuánta vida queda (número, lo más grande y con color de estado);
 * 2) cuánto tiempo es; 3) en qué estado está. El estado se repite con ícono y palabra
 * para no depender solo del color.
 *
 * @param stacked apilado (reloj) o en fila (teléfono).
 */
@Composable
fun HealthReadout(
    health: Health,
    frame: HealthBarFrame,
    modifier: Modifier = Modifier,
    stacked: Boolean = true,
) {
    val colors = Hp.colors
    val type = Hp.type
    val visual = rememberStatusVisual(health.status)
    // El número "cuenta" al curarse (recompensa visible); al recibir daño cambia al
    // instante, porque el dato debe ser siempre exacto.
    val shown by animateIntAsState(
        targetValue = health.clamped,
        animationSpec = if (Hp.motion.reduced) snap() else tween(HpDurations.CELEBRATION),
        label = "hpNumber",
    )
    val number = if (health.clamped < shown) health.clamped else shown
    val numberColor = if (colors.isAmbient) colors.textPrimary else frame.color
    val unit = stringResource(R.string.hp_unit)

    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            StatusTag(visual.icon, visual.label, visual.color)
            Spacer(Modifier.height(HpSpacing.xs))
            BasicText("$number", style = type.hpNumber.copy(color = numberColor, textAlign = TextAlign.Center))
            Spacer(Modifier.height(HpSpacing.xs))
            BasicText("de ${health.max} $unit", style = type.hpMax.copy(color = colors.textSecondary))
            BasicText(formatMinutes(health.clamped), style = type.caption.copy(color = colors.textPrimary))
        }
    } else {
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            BasicText("$number", style = type.hpNumber.copy(color = numberColor))
            Spacer(Modifier.width(HpSpacing.sm))
            Column(Modifier.padding(bottom = 2.dp)) {
                BasicText("de ${health.max} $unit", style = type.hpMax.copy(color = colors.textSecondary))
                BasicText(formatMinutes(health.clamped), style = type.caption.copy(color = colors.textPrimary))
            }
            Spacer(Modifier.weight(1f))
            StatusTag(visual.icon, visual.label, visual.color, Modifier.padding(bottom = 4.dp))
        }
    }
}

@Composable
private fun StatusTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val c = if (Hp.colors.isAmbient) Hp.colors.textSecondary else color
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(icon, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c))
        Spacer(Modifier.width(HpSpacing.xs))
        BasicText(label.uppercase(), style = Hp.type.gameLabel.copy(color = c))
    }
}
