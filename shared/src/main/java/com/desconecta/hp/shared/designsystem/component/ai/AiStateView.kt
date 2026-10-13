package com.desconecta.hp.shared.designsystem.component.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.ai.engine.AiResult
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor

/** Nombre de cada función en lenguaje del jugador (para los mensajes de estado). */
fun AiFeature.label(): String = when (this) {
    AiFeature.ORACLE -> "Oráculo"
    AiFeature.COMPULSIVE -> "Modo berserker"
    AiFeature.COACH -> "Coach del avatar"
    AiFeature.VOICE -> "Comandos de voz"
    AiFeature.JOURNAL -> "Diario de batalla"
    AiFeature.BOSSES -> "Jefes de la IA"
    AiFeature.LIMITS -> "Límites inteligentes"
    AiFeature.CONTEXT -> "Detección de contexto"
}

/**
 * Pinta cualquier [AiResult]: si está listo usa [ready]; si no, el estado correspondiente
 * con su propio diseño (sección 9). Ningún estado queda en blanco y todos dicen qué hacer.
 *
 * @param action botón contextual (Reintentar, Abrir configuración, Conceder permiso…),
 *   que lo aporta cada app con su propio Material.
 */
@Composable
fun <T> AiStateView(
    result: AiResult<T>,
    modifier: Modifier = Modifier,
    action: (@Composable (AiResult<T>) -> Unit)? = null,
    ready: @Composable (AiResult.Ready<T>) -> Unit,
) {
    val c = Hp.colors
    when (result) {
        is AiResult.Ready -> Column(modifier, verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
            ready(result)
            // Baja confianza: se dice claramente (no se esconde el resultado, se matiza).
            if (result.lowConfidence) ConfidenceTag(result.confidence)
        }
        AiResult.Thinking -> AiThinking(modifier)
        is AiResult.Learning -> AiLearningProgress(result.day, result.of, modifier)
        is AiResult.Failed -> {
            val fallback = result.fallback
            if (fallback != null) {
                Column(modifier, verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
                    ready(fallback)
                    StateNote(PixelIcons.Crystal, c.textSecondary, "Usando consejos básicos: la IA no respondió.")
                }
            } else {
                StateCard(PixelIcons.HeartCracked, c.hpHurt, "La IA no pudo responder", "Inténtalo más tarde. El juego sigue funcionando igual.", modifier) {
                    action?.invoke(result)
                }
            }
        }
        AiResult.Offline -> StateCard(PixelIcons.Watch, c.textSecondary, "Sin conexión",
            "La IA en la nube no está disponible. Usando la IA de tu teléfono.", modifier) { action?.invoke(result) }
        AiResult.CloudNotAllowed -> StateCard(PixelIcons.Lock, c.ai, "IA en la nube desactivada",
            "Funciona con la IA de tu teléfono. Puedes activar la nube en Configuración de IA.", modifier) { action?.invoke(result) }
        is AiResult.Disabled -> StateCard(PixelIcons.Crystal, c.textSecondary, "${result.feature.label()} desactivado",
            "Actívalo cuando quieras en Configuración de IA.", modifier) { action?.invoke(result) }
        is AiResult.MissingPermission -> StateCard(PixelIcons.Lock, c.hpHurt, "Falta un permiso",
            "Para esta función necesito: ${result.permission}.", modifier) { action?.invoke(result) }
    }
}

@Composable
private fun StateCard(
    icon: ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    modifier: Modifier,
    action: @Composable () -> Unit,
) {
    val c = Hp.colors
    val watch = Hp.formFactor == HpFormFactor.WATCH
    Column(
        modifier
            .fillMaxWidth()
            .clip(AiTokens.bubbleShape)
            .background(c.surface)
            .border(1.dp, c.outline, AiTokens.bubbleShape)
            .padding(HpSpacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(HpSpacing.xs),
        horizontalAlignment = if (watch) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            Image(icon, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(tint))
            BasicText(title, style = Hp.type.bodyStrong.copy(color = c.textPrimary))
        }
        BasicText(body, style = Hp.type.caption.copy(color = c.textSecondary))
        action()
    }
}

@Composable
private fun StateNote(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
        Image(icon, null, Modifier.size(12.dp), colorFilter = ColorFilter.tint(tint))
        BasicText(text, style = Hp.type.caption.copy(color = tint))
    }
}
