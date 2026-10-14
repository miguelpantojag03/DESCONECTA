package com.desconecta.hp.mobile.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.shared.ai.model.AiFeedback
import com.desconecta.hp.shared.ai.model.AiInsight
import com.desconecta.hp.shared.ai.model.LimitSuggestion
import com.desconecta.hp.shared.ai.rules.LimitRules
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.ai.AiBadge
import com.desconecta.hp.shared.designsystem.component.ai.AiSpeechBubble
import com.desconecta.hp.shared.designsystem.component.ai.BubbleTail
import com.desconecta.hp.shared.designsystem.component.ai.ConfidenceTag
import com.desconecta.hp.shared.designsystem.component.ai.FeedbackButtons
import com.desconecta.hp.shared.designsystem.component.ai.WhyChip
import com.desconecta.hp.shared.designsystem.component.ai.WhyText
import com.desconecta.hp.shared.designsystem.component.toMood
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.Player
import com.desconecta.hp.shared.model.HealthStatus

/* =====================================================================================
 * Tarjetas de IA del panel (fase 2).
 *
 *  · Coach: el avatar "habla" (cola de la burbuja hacia él, Gestalt: conexión). Con "¿Por
 *    qué?", certeza en palabras y 👍/👎; un 👎 hace que esa frase no vuelva a salir.
 *  · Sugerencia de límite: el cambio se lee de un vistazo ("TikTok: 45 → 35 min"), la acción
 *    principal dice exactamente qué pasará ("Aplicar 35 min") y "Ahora no" tiene el mismo
 *    tamaño: rechazar es tan fácil como aceptar (People + AI: el usuario manda).
 * ===================================================================================== */

@Composable
fun CoachCard(
    insight: AiInsight,
    player: Player,
    status: HealthStatus,
    onFeedback: (AiFeedback) -> Unit,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
        Avatar(player.avatar, status.toMood(), size = 40.dp, accessories = player.equipped, name = player.name)
        AiSpeechBubble(
            text = insight.content,
            modifier = Modifier.weight(1f),
            reason = insight.reason,
            confidence = insight.confidence,
            origin = insight.origin,
            feedback = insight.feedback,
            onFeedback = onFeedback,
            tail = BubbleTail.START,
        )
    }
}

fun AiInsight.limitSuggestion(): LimitSuggestion? =
    payload?.let { runCatching { WireCodec.json.decodeFromString(LimitSuggestion.serializer(), it) }.getOrNull() }

@Composable
fun LimitSuggestionCard(
    insight: AiInsight,
    onAccept: (minutes: Int) -> Unit,
    onReject: () -> Unit,
    onFeedback: (AiFeedback) -> Unit,
) {
    val c = Hp.colors
    val s = insight.limitSuggestion() ?: return
    var why by rememberSaveable(insight.id) { mutableStateOf(false) }
    var adjust by rememberSaveable(insight.id) { mutableStateOf(false) }
    val lowering = s.suggestedMinutes < s.currentMinutes

    HpCard(border = c.ai) {
        Column(
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = "Sugerencia de la IA: ${insight.content}"
            },
            verticalArrangement = Arrangement.spacedBy(HpSpacing.xs),
        ) {
            AiBadge(label = "SUGERENCIA DE LA IA")
            Text(insight.content, style = Hp.type.title, color = c.textPrimary)
            Text(
                if (lowering) "Un reto pequeño, basado en lo que ya haces." else "Un límite que puedas cumplir ayuda a mantener la racha.",
                style = Hp.type.caption, color = c.textSecondary,
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            WhyChip(open = why, onToggle = { why = !why })
            ConfidenceTag(insight.confidence)
            Spacer(Modifier.weight(1f))
            FeedbackButtons(insight.feedback, onFeedback)
        }
        AnimatedVisibility(why) { WhyText(insight.reason, insight.origin) }
        PrimaryButton("Aplicar ${s.suggestedMinutes} min", onClick = { onAccept(s.suggestedMinutes) }, container = c.ai)
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            SecondaryButton("Ajustar", onClick = { adjust = true }, modifier = Modifier.weight(1f), iconTint = c.ai)
            SecondaryButton("Ahora no", onClick = onReject, modifier = Modifier.weight(1f))
        }
    }

    if (adjust) {
        AdjustLimitDialog(
            current = s.currentMinutes,
            suggested = s.suggestedMinutes,
            onDismiss = { adjust = false },
            onSave = { adjust = false; onAccept(it) },
        )
    }
}

/** Ajuste en pasos de 5 min: el jugador parte de la propuesta y decide la cifra final. */
@Composable
private fun AdjustLimitDialog(current: Int, suggested: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    val c = Hp.colors
    var value by rememberSaveable { mutableIntStateOf(suggested) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar el límite") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Ahora: $current min · Propuesta: $suggested min", style = Hp.type.caption, color = c.textSecondary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.lg)) {
                    FilledTonalIconButton(
                        onClick = { value = (value - 5).coerceAtLeast(LimitRules.MIN_LIMIT) },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.aiContainer, contentColor = c.ai),
                    ) { Text("−5", style = Hp.type.bodyStrong) }
                    Text(
                        "$value min",
                        style = Hp.type.headline, color = c.textPrimary,
                        modifier = Modifier.heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    FilledTonalIconButton(
                        onClick = { value = (value + 5).coerceAtMost(600) },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = c.aiContainer, contentColor = c.ai),
                    ) { Text("+5", style = Hp.type.bodyStrong) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Guardar $value min", color = c.ai) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = c.surfaceElevated,
    )
}

/**
 * Oráculo dentro de la tarjeta de vida (junto al dato que predice: Gestalt, proximidad).
 * Es una estimación: lleva el cristal, la certeza en palabras y su "¿Por qué?".
 */
@Composable
fun OracleRow(line: OracleLine) {
    val c = Hp.colors
    var why by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
            modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "Predicción de la IA: ${line.text}" },
        ) {
            AiBadge(label = null)
            Text(line.text, style = Hp.type.body, color = if (line.danger) c.hpHurt else c.textPrimary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            WhyChip(open = why, onToggle = { why = !why })
            ConfidenceTag(line.confidence)
        }
        AnimatedVisibility(why) { WhyText(line.reason, com.desconecta.hp.shared.ai.model.AiOrigin.RULES) }
    }
}

/**
 * Modo berserker: una invitación, no un castigo. Dos opciones del mismo tamaño; "Sigo un
 * rato" es tan válido como la pausa (la IA sugiere, el jugador decide).
 */
@Composable
fun BerserkerCard(insight: AiInsight, onAnswer: (pause: Boolean) -> Unit) {
    val c = Hp.colors
    HpCard(container = c.aiContainer, border = c.ai) {
        AiBadge(label = "MODO BERSERKER")
        Text(
            insight.content,
            style = Hp.type.bodyStrong, color = c.textPrimary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Text("Respira hondo tres veces y decide con calma.", style = Hp.type.caption, color = c.textSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            PrimaryButton("Hago una pausa", onClick = { onAnswer(true) }, modifier = Modifier.weight(1f), container = c.ai)
            SecondaryButton("Sigo un rato", onClick = { onAnswer(false) }, modifier = Modifier.weight(1f))
        }
    }
}

/** Diario de batalla del último día, arriba del historial (el dato que resume va con su detalle). */
@Composable
fun JournalCard(insight: AiInsight, onFeedback: (AiFeedback) -> Unit) {
    val c = Hp.colors
    val day = insight.payload
        ?.let { runCatching { WireCodec.json.decodeFromString(com.desconecta.hp.shared.ai.model.BattleJournal.serializer(), it) }.getOrNull() }
        ?.dayKey?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    val label = day?.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", java.util.Locale.forLanguageTag("es")))
    HpCard(border = c.ai) {
        AiBadge(label = "DIARIO DE BATALLA")
        if (label != null) Text(label.replaceFirstChar { it.uppercase() }, style = Hp.type.caption, color = c.textSecondary)
        Text(
            insight.content, style = Hp.type.body, color = c.textPrimary,
            modifier = Modifier.semantics { contentDescription = "Diario de la IA: ${insight.content}" },
        )
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Escrito con tus datos del día · Reglas", style = Hp.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
            FeedbackButtons(insight.feedback, onFeedback)
        }
    }
}
