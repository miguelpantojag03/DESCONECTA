package com.desconecta.hp.shared.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.model.MonsterArt

/**
 * Estados que TODA pantalla debe tener diseñados (ninguna queda en blanco). Mismo
 * esquema siempre: ilustración → qué pasa → qué puedo hacer (acción). Es la heurística
 * nº 9 de Nielsen: ayudar a reconocer, diagnosticar y recuperarse de errores, en
 * lenguaje del usuario y sin códigos técnicos.
 */
enum class ScreenStateKind { EMPTY, ERROR, OFFLINE, NO_PERMISSION, SUCCESS }

@Composable
fun HpStateMessage(
    kind: ScreenStateKind,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Hp.colors
    val watch = Hp.formFactor == HpFormFactor.WATCH
    val (art, tint) = stateArt(kind)
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = if (watch) HpSpacing.md else HpSpacing.xl, vertical = HpSpacing.lg)
            .semantics(mergeDescendants = true) {
                // Errores y sin conexión se anuncian solos en TalkBack.
                if (kind == ScreenStateKind.ERROR || kind == ScreenStateKind.OFFLINE) liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
    ) {
        Image(
            art, null,
            Modifier.size(if (watch) 32.dp else 64.dp),
            colorFilter = tint?.let { ColorFilter.tint(it) },
        )
        BasicText(
            title,
            style = (if (watch) Hp.type.title else Hp.type.headline).copy(color = colors.textPrimary, textAlign = TextAlign.Center),
        )
        BasicText(body, style = Hp.type.body.copy(color = colors.textSecondary, textAlign = TextAlign.Center))
        if (action != null) {
            Spacer(Modifier.height(HpSpacing.sm))
            action()
        }
    }
}

@Composable
private fun stateArt(kind: ScreenStateKind): Pair<ImageVector, Color?> {
    val c = Hp.colors
    return when (kind) {
        // Vacío: un fantasmita amable, no un error.
        ScreenStateKind.EMPTY -> MonsterSprites.of(MonsterArt.FANTASMA) to null
        ScreenStateKind.ERROR -> PixelIcons.HeartCracked to c.hpCritical
        ScreenStateKind.OFFLINE -> PixelIcons.Watch to c.textSecondary
        ScreenStateKind.NO_PERMISSION -> PixelIcons.Lock to c.hpHurt
        ScreenStateKind.SUCCESS -> PixelIcons.Star to c.gold
    }
}

/**
 * Skeleton de carga "8 bits": bloques que se encienden en secuencia, como la barra de
 * carga de una consola. Comunica "está cargando" sin un spinner genérico. Con movimiento
 * reducido los bloques quedan estáticos a media intensidad.
 */
@Composable
fun HpSkeleton(modifier: Modifier = Modifier, lines: Int = 3, lineHeight: Dp = 16.dp) {
    Column(
        modifier.clearAndSetSemantics { contentDescription = "Cargando" },
        verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
    ) {
        repeat(lines) { i ->
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(if (i == lines - 1) 0.6f else 1f)
                    .height(lineHeight),
                index = i,
            )
        }
    }
}

@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, index: Int = 0) {
    val colors = Hp.colors
    val alpha = if (Hp.motion.reduced) 0.6f else rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1200
                // Saltos discretos (sin interpolación suave): parpadeo tipo "pixel".
                0.35f at 0 using LinearEasing
                0.35f at 299
                0.8f at 300
                0.8f at 599
                0.35f at 600
            },
            repeatMode = RepeatMode.Restart,
            initialStartOffset = StartOffset(index * 150),
        ),
        label = "skeletonAlpha",
    ).value
    Box(modifier.alpha(alpha).background(colors.surfaceElevated, Hp.shapes.bar))
}

/** Fila de 3 bloques de carga, p. ej. para la lista de monstruos. */
@Composable
fun HpSkeletonCard(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Hp.colors.surface, Hp.shapes.card)
            .padding(HpSpacing.md),
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp), index = 1)
            SkeletonBlock(Modifier.fillMaxWidth().height(6.dp), index = 2)
        }
    }
}
