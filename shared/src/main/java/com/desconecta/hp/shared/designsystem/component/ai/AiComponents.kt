package com.desconecta.hp.shared.designsystem.component.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.ai.model.AiFeedback
import com.desconecta.hp.shared.ai.model.AiOrigin
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.shape.PixelCornerShape
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor

/**
 * Tokens visuales de la IA (extensión del sistema de diseño):
 *
 *  · Color propio [com.desconecta.hp.shared.designsystem.color.HpColors.ai] (#7CF5D9) y su
 *    contenedor. Nunca se usa para barras de HP: la IA opina, no es un dato del juego.
 *  · Ícono [PixelIcons.Crystal] en TODO contenido de IA (identificación, People + AI).
 *  · Forma: burbuja con esquinas pixeladas y "cola" hacia el avatar (habla el compañero).
 *  · Movimiento: 3 puntos que se encienden por pasos ("pensando"), nunca un spinner.
 *  · Accesibilidad: todo contenido de IA se anuncia como "Sugerencia de la IA: …".
 */
object AiTokens {
    const val THINKING_STEP_MS = 300
    const val A11Y_PREFIX = "Sugerencia de la IA: "
    val bubbleShape = PixelCornerShape(step = 3.dp, steps = 2)
    val borderWidth = 2.dp
}

// =====================================================================================
// Marca de IA
// =====================================================================================

/** Cristal + "IA": se coloca junto a cualquier contenido generado. */
@Composable
fun AiBadge(modifier: Modifier = Modifier, label: String? = "IA") {
    val c = Hp.colors
    Row(modifier.semantics(mergeDescendants = true) { }, verticalAlignment = Alignment.CenterVertically) {
        Image(PixelIcons.Crystal, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.ai))
        if (label != null) {
            Spacer(Modifier.width(HpSpacing.xs))
            BasicText(label, style = Hp.type.gameLabel.copy(color = c.ai))
        }
    }
}

// =====================================================================================
// "Pensando": tres puntos pixelados que se encienden en secuencia
// =====================================================================================

@Composable
fun AiThinking(modifier: Modifier = Modifier, label: String = "Pensando") {
    val c = Hp.colors
    val reduced = Hp.motion.reduced
    val phase = if (reduced) -1f else rememberInfiniteTransition(label = "aiThinking").animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = AiTokens.THINKING_STEP_MS * 4
                0f at 0
                0f at AiTokens.THINKING_STEP_MS - 1
                1f at AiTokens.THINKING_STEP_MS
                1f at AiTokens.THINKING_STEP_MS * 2 - 1
                2f at AiTokens.THINKING_STEP_MS * 2
                2f at AiTokens.THINKING_STEP_MS * 3 - 1
                3f at AiTokens.THINKING_STEP_MS * 3
            },
        ),
        label = "dot",
    ).value
    Row(
        modifier.clearAndSetSemantics { contentDescription = "La IA está pensando" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs),
    ) {
        Image(PixelIcons.Crystal, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.ai))
        repeat(3) { i ->
            // Se encienden 1, 2 y 3 puntos por pasos; con movimiento reducido quedan fijos.
            val on = reduced || i < phase.toInt()
            Box(Modifier.size(6.dp).background(if (on) c.ai else c.aiContainer))
        }
        Spacer(Modifier.width(HpSpacing.xs))
        BasicText(label, style = Hp.type.caption.copy(color = c.textSecondary))
    }
}

// =====================================================================================
// Burbuja de diálogo del avatar
// =====================================================================================

/** Hacia dónde apunta la cola de la burbuja: siempre hacia el avatar que habla (Gestalt: conexión). */
enum class BubbleTail { NONE, TOP, START }

/**
 * Lo que dice el compañero. En el reloj: una línea, toca para expandir. En el teléfono:
 * hasta 4 líneas. Debajo, en el teléfono: "¿Por qué?", confianza y 👍/👎.
 *
 * @param reason línea del "¿Por qué?" (campo motivo). null = sin explicación.
 */
@Composable
fun AiSpeechBubble(
    text: String,
    modifier: Modifier = Modifier,
    reason: String? = null,
    confidence: Confidence? = null,
    origin: AiOrigin? = null,
    feedback: AiFeedback? = null,
    onFeedback: ((AiFeedback) -> Unit)? = null,
    tail: BubbleTail = BubbleTail.TOP,
) {
    val c = Hp.colors
    val watch = Hp.formFactor == HpFormFactor.WATCH
    var expanded by rememberSaveable(text) { mutableStateOf(!watch) }
    var showWhy by rememberSaveable(text) { mutableStateOf(false) }

    Row(modifier, verticalAlignment = Alignment.Top) {
    if (tail == BubbleTail.START) TailStart(c.ai, c.aiContainer)
    Column(Modifier.weight(1f)) {
        if (tail == BubbleTail.TOP) TailTop(c.ai, c.aiContainer)
        Column(
            Modifier
                .fillMaxWidth()
                .clip(AiTokens.bubbleShape)
                .background(c.aiContainer)
                .border(AiTokens.borderWidth, c.ai, AiTokens.bubbleShape)
                .then(
                    if (watch) Modifier.clickable(role = Role.Button, onClickLabel = if (expanded) "Contraer" else "Expandir") {
                        expanded = !expanded
                    } else Modifier,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = AiTokens.A11Y_PREFIX + text
                }
                .padding(horizontal = HpSpacing.md, vertical = HpSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(HpSpacing.xs),
        ) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                Image(
                    PixelIcons.Crystal, null,
                    Modifier.padding(top = 2.dp).size(16.dp),
                    colorFilter = ColorFilter.tint(c.ai),
                )
                BasicText(
                    text,
                    style = (if (watch) Hp.type.caption else Hp.type.body).copy(color = c.textPrimary),
                    maxLines = if (expanded) (if (watch) 4 else 6) else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            if (!watch && (reason != null || confidence != null || onFeedback != null)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
                ) {
                    if (reason != null) WhyChip(open = showWhy, onToggle = { showWhy = !showWhy })
                    if (confidence != null) ConfidenceTag(confidence)
                    Spacer(Modifier.weight(1f))
                    if (onFeedback != null) FeedbackButtons(feedback, onFeedback)
                }
                AnimatedVisibility(showWhy && reason != null) {
                    WhyText(reason.orEmpty(), origin)
                }
            }
        }
    }
    }
}

/** Cola hacia arriba (avatar encima, como en el reloj). */
@Composable
private fun TailTop(border: Color, fill: Color) {
    Canvas(Modifier.padding(start = HpSpacing.lg).size(width = 12.dp, height = 8.dp)) {
        val p = Path().apply {
            moveTo(0f, size.height)
            lineTo(size.width / 2, 0f)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(p, border)
        val inset = 2.dp.toPx()
        val q = Path().apply {
            moveTo(inset, size.height + 1)
            lineTo(size.width / 2, inset * 1.6f)
            lineTo(size.width - inset, size.height + 1)
            close()
        }
        drawPath(q, fill)
        drawLine(fill, Offset(inset, size.height), Offset(size.width - inset, size.height), strokeWidth = 2f)
    }
}

/** Cola hacia la izquierda (avatar al lado, como en el teléfono). */
@Composable
private fun TailStart(border: Color, fill: Color) {
    Canvas(Modifier.padding(top = HpSpacing.md).size(width = 8.dp, height = 12.dp)) {
        val p = Path().apply {
            moveTo(size.width, 0f)
            lineTo(0f, size.height / 2)
            lineTo(size.width, size.height)
            close()
        }
        drawPath(p, border)
        val inset = 2.dp.toPx()
        val q = Path().apply {
            moveTo(size.width + 1, inset)
            lineTo(inset * 1.6f, size.height / 2)
            lineTo(size.width + 1, size.height - inset)
            close()
        }
        drawPath(q, fill)
    }
}

// =====================================================================================
// Explicabilidad, confianza y retroalimentación
// =====================================================================================

/** "¿Por qué?" tocable (48 dp de alto). */
@Composable
fun WhyChip(open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = Hp.colors
    Box(
        modifier
            .heightIn(min = HpSpacing.touchTarget)
            .clip(Hp.shapes.button)
            .clickable(role = Role.Button, onClickLabel = if (open) "Ocultar explicación" else "Ver explicación", onClick = onToggle)
            .padding(horizontal = HpSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(if (open) "Ocultar" else "¿Por qué?", style = Hp.type.label.copy(color = c.ai))
    }
}

@Composable
fun WhyText(reason: String, origin: AiOrigin?, modifier: Modifier = Modifier) {
    val c = Hp.colors
    BasicText(
        reason + (origin?.let { " · ${it.label}" } ?: ""),
        style = Hp.type.caption.copy(color = c.textSecondary),
        modifier = modifier.semantics { contentDescription = "Por qué: $reason" },
    )
}

/**
 * Certeza en palabras + pips (nunca porcentajes). Con confianza baja se dice
 * explícitamente "poca certeza" para que el jugador no la tome como un hecho.
 */
@Composable
fun ConfidenceTag(confidence: Confidence, modifier: Modifier = Modifier) {
    val c = Hp.colors
    val level = when (confidence) {
        Confidence.LOW -> 1
        Confidence.MEDIUM -> 2
        Confidence.HIGH -> 3
    }
    val text = if (confidence == Confidence.LOW) "Quizá · poca certeza" else confidence.phrase
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Certeza: $text" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(3) { i ->
            Box(
                Modifier
                    .size(width = 4.dp, height = (6 + i * 3).dp)
                    .background(if (i < level) c.ai else c.hpTrack),
            )
        }
        Spacer(Modifier.width(HpSpacing.xs))
        BasicText(text, style = Hp.type.caption.copy(color = if (confidence == Confidence.LOW) c.hpHurt else c.textSecondary))
    }
}

/** 👍 / 👎 (se guarda en ai_insights.feedback). 48 dp cada uno. */
@Composable
fun FeedbackButtons(current: AiFeedback?, onFeedback: (AiFeedback) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier) {
        FeedbackButton("👍", "Me sirve", current == AiFeedback.POSITIVE) { onFeedback(AiFeedback.POSITIVE) }
        FeedbackButton("👎", "No me sirve", current == AiFeedback.NEGATIVE) { onFeedback(AiFeedback.NEGATIVE) }
    }
}

@Composable
private fun FeedbackButton(emoji: String, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = Hp.colors
    Box(
        Modifier
            .sizeIn(minWidth = HpSpacing.touchTarget, minHeight = HpSpacing.touchTarget)
            .clip(Hp.shapes.button)
            .background(if (selected) c.aiContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = label
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(emoji, style = Hp.type.title, modifier = Modifier.alpha(if (selected) 1f else 0.6f))
    }
}

// =====================================================================================
// Periodo de aprendizaje
// =====================================================================================

/**
 * "La IA está conociéndote: día 3 de 7" con barra de 7 segmentos. Se muestra EN LUGAR de
 * predicciones poco fiables durante la primera semana (People + AI: fijar expectativas).
 */
@Composable
fun AiLearningProgress(day: Int, of: Int, modifier: Modifier = Modifier) {
    val c = Hp.colors
    val watch = Hp.formFactor == HpFormFactor.WATCH
    Column(
        modifier.clearAndSetSemantics {
            contentDescription = "La IA está conociéndote: día $day de $of"
            stateDescription = "Aprendiendo"
        },
        verticalArrangement = Arrangement.spacedBy(HpSpacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            Image(PixelIcons.Crystal, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.ai))
            BasicText(
                if (watch) "Conociéndote: día $day de $of" else "La IA está conociéndote: día $day de $of",
                style = Hp.type.caption.copy(color = c.textPrimary),
            )
        }
        Row(Modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(of) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(if (i < day) c.ai else c.hpTrack),
                )
            }
        }
        if (!watch) {
            BasicText(
                "Mientras tanto verás consejos generales. Las predicciones llegan cuando haya datos suficientes.",
                style = Hp.type.caption.copy(color = c.textSecondary),
            )
        }
    }
}
