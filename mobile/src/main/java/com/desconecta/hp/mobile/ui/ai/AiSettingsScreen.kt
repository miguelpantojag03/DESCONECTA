package com.desconecta.hp.mobile.ui.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.ai.data.LearningState
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.mobile.ui.components.SectionLabel
import com.desconecta.hp.mobile.ui.onboarding.fieldColors
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.ai.model.CoachTone
import com.desconecta.hp.shared.designsystem.component.ai.AiBadge
import com.desconecta.hp.shared.designsystem.component.ai.AiLearningProgress
import com.desconecta.hp.shared.designsystem.component.ai.AiSpeechBubble
import com.desconecta.hp.shared.designsystem.component.ai.BubbleTail
import com.desconecta.hp.shared.designsystem.component.ai.label
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import kotlinx.coroutines.launch

/* =====================================================================================
 * "Compañero IA" (pantalla 1 de 5 de la IA en el teléfono).
 *
 *  · Local por defecto: "Solo en tu teléfono" viene marcado y la nube pide un
 *    consentimiento aparte que dice qué sale y qué no (People + AI: control y confianza).
 *  · Cada función explica en una línea qué hace; las que nunca salen del teléfono lo
 *    dicen con una etiqueta (privacidad visible, Nielsen nº 1).
 *  · Las opciones no disponibles no se ocultan: se muestran deshabilitadas con el motivo
 *    y, si existe, la forma de habilitarlas (sin cuenta → "Crear cuenta").
 *  · Modo familiar: apaga la nube siempre y para quitarlo pide el PIN.
 * ===================================================================================== */

/** Funciones que por diseño nunca usan la nube (igual que AiRouter). */
private val LOCAL_ONLY = setOf(AiFeature.ORACLE, AiFeature.COMPULSIVE, AiFeature.VOICE, AiFeature.CONTEXT)

fun AiFeature.description(): String = when (this) {
    AiFeature.ORACLE -> "Predice a qué hora te quedarías sin vida si sigues a este ritmo."
    AiFeature.COMPULSIVE -> "Te avisa si llevas mucho rato seguido o abres una app una y otra vez."
    AiFeature.COACH -> "Frases cortas de ánimo de tu avatar, como mucho una cada pocos minutos."
    AiFeature.VOICE -> "Pregunta tu vida o usa objetos hablándole al reloj."
    AiFeature.JOURNAL -> "Un resumen de cada día contado como una batalla."
    AiFeature.BOSSES -> "Retos semanales hechos a tu medida."
    AiFeature.LIMITS -> "Propone límites según tu uso real. Tú decides si los aplicas."
    AiFeature.CONTEXT -> "Aprende tus horarios de estudio y descanso para sugerirte modos."
}

/** Ejemplo de cada tono (≤ 80 caracteres, como en el reloj). */
fun CoachTone.sample(): String = when (this) {
    CoachTone.MOTIVATOR -> "¡Vas genial! Guarda esa vida para esta noche."
    CoachTone.FUNNY -> "Scrollzilla te echa de menos. Que espere sentado."
    CoachTone.WISE -> "Cada minuto que guardas hoy es vida para mañana."
    CoachTone.DIRECT -> "Llevas 40 min en TikTok. ¿Lo dejas aquí?"
}

@Composable
fun AiSettingsScreen(
    settings: AiSettings,
    learning: LearningState?,
    signedIn: Boolean,
    hasPin: Boolean,
    onToggle: (AiFeature, Boolean) -> Unit,
    onCloud: (Boolean) -> Unit,
    onTone: (CoachTone) -> Unit,
    onCoachEvery: (Int) -> Unit,
    onFamilyOn: () -> Unit,
    onFamilyOff: suspend (pin: String?) -> Boolean,
    onDeleteAiData: suspend () -> Result<Unit>,
    onCreateAccount: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val c = Hp.colors
    val scope = rememberCoroutineScope()
    var cloudConsent by remember { mutableStateOf(false) }
    var familyPin by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            AiBadge(label = null)
            Text(
                "Te da pistas y predicciones. Nunca cambia nada sin preguntarte ni ve lo que haces dentro de las apps.",
                style = Hp.type.body, color = c.textSecondary,
            )
        }
        if (learning != null && !learning.done) {
            HpCard { AiLearningProgress(learning.day, learning.of) }
        }

        // --- Dónde piensa -------------------------------------------------------------
        SectionLabel("Dónde piensa la IA")
        val cloudBlockedReason = when {
            settings.familyMode -> "Desactivada en modo familiar."
            !signedIn -> "Necesitas una cuenta."
            else -> null
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            PlaceOption(
                selected = !settings.cloudEffective,
                title = "Solo en tu teléfono",
                body = "Todo se calcula aquí y funciona sin conexión. Recomendado.",
                enabled = true,
                onSelect = { onCloud(false) },
            )
            PlaceOption(
                selected = settings.cloudEffective,
                title = "También en la nube",
                body = cloudBlockedReason
                    ?: "Diarios, jefes y límites más elaborados. Solo se envían totales de uso, nunca lo que haces.",
                enabled = cloudBlockedReason == null,
                onSelect = { if (!settings.cloudEffective) cloudConsent = true },
            )
            if (!signedIn && !settings.familyMode) {
                SecondaryButton("Crear cuenta", onClick = onCreateAccount, icon = PixelIcons.Star, iconTint = c.ai)
            }
        }

        // --- Funciones ---------------------------------------------------------------
        SectionLabel("Funciones")
        HpCard {
            AiFeature.entries.forEach { f ->
                FeatureRow(f, checked = settings.isOn(f), onChange = { onToggle(f, it) })
            }
        }

        // --- Coach -------------------------------------------------------------------
        if (settings.isOn(AiFeature.COACH)) {
            SectionLabel("Coach")
            HpCard {
                Text("Tono", style = Hp.type.bodyStrong, color = c.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                    CoachTone.entries.forEach { t ->
                        FilterChip(
                            selected = settings.tone == t,
                            onClick = { onTone(t) },
                            label = { Text(t.label) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.aiContainer, selectedLabelColor = c.ai),
                        )
                    }
                }
                AiSpeechBubble(settings.tone.sample(), tail = BubbleTail.NONE)
                Text("Como mucho, un mensaje cada…", style = Hp.type.bodyStrong, color = c.textPrimary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                    AiSettings.COACH_FREQUENCIES.forEach { m ->
                        FilterChip(
                            selected = settings.coachEveryMinutes == m,
                            onClick = { onCoachEvery(m) },
                            label = { Text(if (m < 60) "$m min" else if (m % 60 == 0) "${m / 60} h" else "${m / 60} h ${m % 60}") },
                            modifier = Modifier.heightIn(min = 48.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.aiContainer, selectedLabelColor = c.ai),
                        )
                    }
                }
                Text("Nunca te hablará durante el modo descanso ni en Game Over.", style = Hp.type.caption, color = c.textSecondary)
            }
        }

        // --- Privacidad --------------------------------------------------------------
        SectionLabel("Privacidad")
        HpCard {
            SwitchRow(
                title = "Modo familiar",
                body = if (hasPin) "Para menores: la IA nunca usa la nube. Para quitarlo se pide el PIN."
                else "Para menores: la IA nunca usa la nube. Pon un PIN en Seguridad para que no se pueda quitar.",
                checked = settings.familyMode,
                onChange = { on -> if (on) onFamilyOn() else if (hasPin) familyPin = true else scope.launch { onFamilyOff(null) } },
            )
            SecondaryButton(
                "Borrar mis datos de IA", onClick = { confirmDelete = true },
                icon = PixelIcons.HeartCracked, iconTint = c.hpCritical,
            )
            Text(
                "Borra sugerencias, resúmenes y comandos de voz. Tu progreso del juego no se toca.",
                style = Hp.type.caption, color = c.textSecondary,
            )
        }
    }

    if (cloudConsent) CloudConsentDialog(onAccept = { cloudConsent = false; onCloud(true) }, onDismiss = { cloudConsent = false })
    if (familyPin) {
        PinCheckDialog(
            onDismiss = { familyPin = false },
            onCheck = { pin ->
                scope.launch {
                    if (onFamilyOff(pin)) familyPin = false else onMessage("PIN incorrecto")
                }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Borrar tus datos de IA?") },
            text = { Text("Se borran sugerencias, resúmenes y comandos de voz, y la IA vuelve a conocerte durante 7 días. Tu nivel, logros e historial se mantienen.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        onDeleteAiData().fold(
                            { onMessage("Datos de IA borrados") },
                            { onMessage("No se pudo borrar: revisa tu conexión") },
                        )
                    }
                }) { Text("Borrar", color = c.hpCritical) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            containerColor = c.surfaceElevated,
        )
    }
}

@Composable
private fun PlaceOption(selected: Boolean, title: String, body: String, enabled: Boolean, onSelect: () -> Unit) {
    val c = Hp.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) c.aiContainer else c.surface, Hp.shapes.card)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.ai else c.outline, Hp.shapes.card)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(HpSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
    ) {
        RadioButton(
            selected = selected, onClick = null, enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = c.ai, unselectedColor = c.outlineStrong),
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = Hp.type.bodyStrong, color = if (enabled) c.textPrimary else c.textSecondary)
            Text(body, style = Hp.type.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun FeatureRow(feature: AiFeature, checked: Boolean, onChange: (Boolean) -> Unit) {
    SwitchRow(
        title = feature.label(),
        body = feature.description(),
        checked = checked,
        onChange = onChange,
        tag = if (feature in LOCAL_ONLY) "Siempre en tu teléfono" else null,
    )
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String? = null) {
    val c = Hp.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .selectable(selected = checked, role = Role.Switch, onClick = { onChange(!checked) })
            .semantics { stateDescription = if (checked) "Activado" else "Desactivado" }
            .padding(vertical = HpSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = Hp.type.bodyStrong, color = c.textPrimary)
            Text(body, style = Hp.type.caption, color = c.textSecondary)
            if (tag != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
                    Image(PixelIcons.Lock, null, Modifier.size(12.dp), colorFilter = ColorFilter.tint(c.hpHealthy))
                    Text(tag, style = Hp.type.caption, color = c.hpHealthy)
                }
            }
        }
        Switch(
            checked = checked, onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = c.ai, checkedThumbColor = c.onAccent),
        )
    }
}

/** Consentimiento propio de la IA en la nube: qué sale, qué no, y que se puede revocar. */
@Composable
private fun CloudConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    val c = Hp.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AiBadge(label = null) },
        title = { Text("¿Usar la IA en la nube?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                Text("Se envía: minutos por app y por hora, tu vida, racha y nivel.", style = Hp.type.body, color = c.textPrimary)
                Text("Nunca se envía: tu voz, tus mensajes, lo que ves en las apps ni tu ubicación.", style = Hp.type.body, color = c.textPrimary)
                Text(
                    "Lo usan el diario, los jefes, los límites y el coach. El oráculo, el modo berserker, la voz y el contexto siguen siempre en tu teléfono. Puedes desactivarlo cuando quieras.",
                    style = Hp.type.caption, color = c.textSecondary,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Activar", color = c.ai) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } },
        containerColor = c.surfaceElevated,
    )
}

@Composable
private fun PinCheckDialog(onDismiss: () -> Unit, onCheck: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quitar modo familiar") },
        text = {
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter(Char::isDigit).take(4) },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = fieldColors(),
            )
        },
        confirmButton = { TextButton(onClick = { onCheck(pin) }, enabled = pin.length == 4) { Text("Quitar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Hp.colors.surfaceElevated,
    )
}

/** Resumen en Configuración: cuántas funciones están activas y dónde piensa la IA. */
@Composable
fun AiSettingsSummary(settings: AiSettings, onOpen: () -> Unit) {
    val c = Hp.colors
    HpCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
            AiBadge(label = null)
            Column(Modifier.weight(1f)) {
                Text("Compañero IA", style = Hp.type.bodyStrong, color = c.textPrimary)
                Text(
                    "${settings.enabled.size} de ${AiFeature.entries.size} funciones activas · " +
                        when {
                            settings.familyMode -> "modo familiar"
                            settings.cloudEffective -> "teléfono y nube"
                            else -> "solo en tu teléfono"
                        },
                    style = Hp.type.caption, color = c.textSecondary,
                )
            }
        }
        SecondaryButton("Ajustes de la IA", onClick = onOpen, icon = PixelIcons.Crystal, iconTint = c.ai)
    }
}
