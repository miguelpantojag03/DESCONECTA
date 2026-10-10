package com.desconecta.hp.shared.designsystem.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.icon.AvatarMood
import com.desconecta.hp.shared.designsystem.icon.AvatarSprites
import com.desconecta.hp.shared.designsystem.icon.PixelArt
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.Accessory
import com.desconecta.hp.shared.model.AvatarStyle
import com.desconecta.hp.shared.model.HealthStatus

fun HealthStatus.toMood(): AvatarMood = when (this) {
    HealthStatus.HEALTHY -> AvatarMood.HAPPY
    HealthStatus.HURT -> AvatarMood.WORRIED
    HealthStatus.CRITICAL -> AvatarMood.EXHAUSTED
    HealthStatus.GAME_OVER -> AvatarMood.KO
}

private fun AvatarMood.description() = when (this) {
    AvatarMood.HAPPY -> "feliz"
    AvatarMood.WORRIED -> "preocupado"
    AvatarMood.EXHAUSTED -> "agotado"
    AvatarMood.KO -> "K.O."
}

/**
 * Compañero del jugador. Su cara refleja el estado de vida: es un indicador emocional
 * que se entiende sin leer nada (y refuerza el estado sin depender del color).
 *
 * @param size múltiplos de 16 dp para pixeles nítidos (32 / 48 / 64 / 96).
 */
@Composable
fun Avatar(
    style: AvatarStyle,
    mood: AvatarMood,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    accessories: Set<Accessory> = emptySet(),
    name: String? = null,
) {
    val colors = Hp.colors
    val ambient = colors.isAmbient
    val tint = if (ambient) ColorFilter.tint(colors.textSecondary) else null
    val label = buildString {
        append("Avatar ")
        if (name != null) append("de $name ")
        append(mood.description())
        if (accessories.isNotEmpty()) append(", con ${accessories.joinToString { it.label.lowercase() }}")
    }

    Box(modifier.size(size).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (Accessory.AURA in accessories && !ambient) Aura(colors.gold)
        val layers = buildList {
            if (Accessory.CAPA in accessories) add(AvatarSprites.accessory(Accessory.CAPA)!!)
            add(AvatarSprites.body(style, mood))
            if (Accessory.GORRO in accessories) add(AvatarSprites.accessory(Accessory.GORRO)!!)
            if (Accessory.ESPADA in accessories) add(AvatarSprites.accessory(Accessory.ESPADA)!!)
        }
        layers.forEach { Image(it, contentDescription = null, Modifier.fillMaxSize(), colorFilter = tint) }
    }
}

/** Resplandor que "respira" (se queda fijo con movimiento reducido). */
@Composable
private fun Aura(color: Color) {
    val reduced = Hp.motion.reduced
    val glow = if (reduced) 0.5f else rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "auraAlpha",
    ).value
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Brush.radialGradient(listOf(color.copy(alpha = glow), Color.Transparent)),
            radius = size.minDimension / 2f,
        )
    }
}

/** Tamaño de un "píxel" del arte para un avatar de [size]. */
fun avatarPixel(size: Dp): Dp = size / PixelArt.GRID
