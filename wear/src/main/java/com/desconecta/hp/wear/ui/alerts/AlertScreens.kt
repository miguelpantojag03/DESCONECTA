package com.desconecta.hp.wear.ui.alerts

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.motion.HpDurations
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.HealthStatus
import com.desconecta.hp.shared.model.HpWarning
import com.desconecta.hp.wear.ui.common.formatDuration
import com.desconecta.hp.wear.ui.common.millisUntilRespawn

/** Despacha la alerta que toca mostrar. */
@Composable
fun AlertOverlay(event: WireEvent, now: Long, onDismiss: () -> Unit) {
    // Pantalla completa: sin hora del sistema encima del mensaje.
    androidx.wear.compose.material3.ScreenScaffold(timeText = {}) {
    when (event) {
        is WireEvent.Warning -> WarningAlert(event, onDismiss)
        is WireEvent.MonsterDefeated -> MonsterDefeatedAlert(event, now, onDismiss)
        is WireEvent.LevelUp -> CelebrationAlert("LEVEL UP!", "Nivel ${event.level}", event.title, PixelIcons.Star, Hp.colors.xp, onDismiss)
        is WireEvent.Achievement -> CelebrationAlert("¡LOGRO!", event.id.title, event.id.description, PixelIcons.Medal, Hp.colors.gold, onDismiss)
        is WireEvent.Respawn -> CelebrationAlert("RESPAWN", "+${event.xpEarned} XP", "Racha: ${event.streak} días. ¡Nuevo día, vida llena!", PixelIcons.Heart, Hp.colors.hpHealthy, onDismiss)
        else -> LaunchedEffect(event) { onDismiss() }
    }
    }
}

// -------------------------------------------------------------------------------------
// Advertencia
// -------------------------------------------------------------------------------------

@Composable
fun WarningAlert(event: WireEvent.Warning, onDismiss: () -> Unit) {
    val c = Hp.colors
    val t = Hp.type
    val status = HealthStatus.from(event.hp, event.maxHp)
    val color = c.forStatus(status)
    val title = when (event.warning) {
        HpWarning.HALF -> "¡VIDA AL 50%!"
        HpWarning.LOW -> "¡VIDA AL 20%!"
        HpWarning.VERY_LOW -> "¡VIDA AL 10%!"
        HpWarning.LAST_FIVE -> "¡ÚLTIMOS 5 HP!"
    }
    val subtitle = event.attackerName?.let { "$it ataca" } ?: "Cuidado con el tiempo"

    AlertFrame(container = c.containerForStatus(status)) {
        Pulsing {
            Image(
                if (status == HealthStatus.CRITICAL) PixelIcons.HeartCracked else PixelIcons.HeartHalf,
                null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(color),
            )
        }
        BasicText(
            title,
            style = t.gameTitle.copy(color = color, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive },
        )
        event.attackerArt?.let { Image(MonsterSprites.of(it), null, Modifier.size(32.dp)) }
        BasicText("$subtitle · ${event.hp} HP", style = t.body.copy(color = c.textPrimary, textAlign = TextAlign.Center))
        Button(onClick = onDismiss) { Text("Entendido") }
    }
}

// -------------------------------------------------------------------------------------
// Monstruo derrotado (app bloqueada)
// -------------------------------------------------------------------------------------

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@Composable
fun MonsterDefeatedAlert(event: WireEvent.MonsterDefeated, now: Long, onDismiss: () -> Unit) {
    val c = Hp.colors
    val t = Hp.type
    AlertFrame(container = c.surface) {
        Box {
            Image(MonsterSprites.of(event.art), null, Modifier.size(56.dp).alpha(0.6f), colorFilter = Grayscale)
            FloatingZ(Modifier.align(Alignment.TopEnd))
        }
        BasicText(
            "${event.name} derrotado",
            style = t.title.copy(color = c.textPrimary, textAlign = TextAlign.Center),
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        BasicText("…por hoy", style = t.gameLabel.copy(color = c.textSecondary))
        BasicText(
            "Esa app descansa. Vuelve en ${formatDuration(millisUntilRespawn(now))}.",
            style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
        )
        Button(onClick = onDismiss) { Text("OK") }
    }
}

/** "Zz" que flota hacia arriba (estático con movimiento reducido). */
@Composable
private fun FloatingZ(modifier: Modifier = Modifier) {
    val reduced = Hp.motion.reduced
    val y = if (reduced) 0f else rememberInfiniteTransition(label = "zz").animateFloat(
        0f, -8f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "zzY",
    ).value
    BasicText(
        "Zz",
        style = Hp.type.gameLabel.copy(color = Hp.colors.textPrimary),
        modifier = modifier.graphicsLayer { translationY = y * density },
    )
}

// -------------------------------------------------------------------------------------
// Celebración (level up, logro, respawn)
// -------------------------------------------------------------------------------------

@Composable
fun CelebrationAlert(
    title: String,
    headline: String,
    body: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onDismiss: () -> Unit,
) {
    val c = Hp.colors
    val t = Hp.type
    val motion = Hp.motion
    val scale = remember { Animatable(if (motion.reduced) 1f else 0.4f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, motion.reward()) }

    Box(Modifier.fillMaxSize().background(c.background)) {
        if (motion.confettiEnabled) PixelConfetti(listOf(color, c.gold, c.regen, c.xp, c.hpHealthy))
        Column(
            Modifier.fillMaxSize().padding(HpSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HpSpacing.xs, Alignment.CenterVertically),
        ) {
            Image(
                icon, null,
                Modifier.size(48.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
                colorFilter = ColorFilter.tint(color),
            )
            BasicText(
                title,
                style = t.gameTitle.copy(color = color, textAlign = TextAlign.Center),
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
            )
            BasicText(headline, style = t.headline.copy(color = c.textPrimary, textAlign = TextAlign.Center))
            BasicText(body, style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center))
            Button(onClick = onDismiss) { Text("¡Genial!") }
        }
    }
}

/** Confeti de píxeles cuadrados que cae una vez (800 ms) con semilla fija. */
@Composable
private fun PixelConfetti(colors: List<Color>) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(HpDurations.CELEBRATION_LONG * 2, easing = LinearEasing)) }
    val pieces = remember {
        val rnd = java.util.Random(7)
        List(36) { Triple(rnd.nextFloat(), rnd.nextFloat() * 0.5f, rnd.nextInt(colors.size)) }
    }
    Canvas(Modifier.fillMaxSize()) {
        val s = 4.dp.toPx()
        pieces.forEach { (x, delay, ci) ->
            val p = ((progress.value - delay) / (1f - delay)).coerceIn(0f, 1f)
            if (p <= 0f || p >= 1f) return@forEach
            val y = -s + (size.height + s) * p
            val wobble = kotlin.math.sin(p * 12f + x * 6f) * 6.dp.toPx()
            drawRect(
                colors[ci].copy(alpha = 1f - p * 0.6f),
                topLeft = Offset(x * size.width + wobble, y),
                size = Size(s, s),
            )
        }
    }
}

// -------------------------------------------------------------------------------------

@Composable
private fun AlertFrame(container: Color, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(container)
            .padding(horizontal = HpSpacing.xl, vertical = HpSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HpSpacing.xs, Alignment.CenterVertically),
    ) { content() }
}

@Composable
private fun Pulsing(content: @Composable () -> Unit) {
    val reduced = Hp.motion.reduced
    val s = if (reduced) 1f else rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 1.12f, infiniteRepeatable(tween(HpDurations.HEARTBEAT_PERIOD / 2), RepeatMode.Reverse), label = "pulseScale",
    ).value
    Box(Modifier.graphicsLayer { scaleX = s; scaleY = s }) { content() }
}
