package com.desconecta.hp.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.data.PhonePrefs
import com.desconecta.hp.mobile.system.HpPermission
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.InfoRow
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.mobile.ui.components.SectionLabel
import com.desconecta.hp.mobile.ui.onboarding.fieldColors
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.UiSettings
import com.desconecta.hp.shared.model.formatMinutes

/**
 * Configuración agrupada por intención (Gestalt: proximidad), con la acción destructiva
 * al final y confirmada.
 */
@Composable
fun SettingsScreen(
    game: GameState,
    prefs: PhonePrefs,
    permissions: Map<HpPermission, Boolean>,
    onMaxHp: (weekday: Int, weekendBonus: Int) -> Unit,
    onSettings: ((UiSettings) -> UiSettings) -> Unit,
    onPin: (String) -> Unit,
    onOpenAllies: () -> Unit,
    onOpenPermissions: () -> Unit,
    onReset: () -> Unit,
    cloudSection: @Composable () -> Unit = {},
) {
    val c = Hp.colors
    val s = game.settings
    var pinDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        SectionLabel("Vida")
        HpCard {
            Text("Vida máxima entre semana", style = Hp.type.bodyStrong, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                Health.MAX_PRESETS.forEach { v ->
                    FilterChip(
                        selected = prefs.maxHpWeekday == v,
                        onClick = { onMaxHp(v, prefs.weekendBonus) },
                        label = { Text("$v") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.healthyContainer, selectedLabelColor = c.hpHealthy),
                    )
                }
            }
            Text("= ${formatMinutes(prefs.maxHpWeekday)} al día", style = Hp.type.caption, color = c.textSecondary)
            ToggleRow(
                "Más vida el fin de semana",
                "+30 HP sábados y domingos",
                checked = prefs.weekendBonus > 0,
                onChange = { onMaxHp(prefs.maxHpWeekday, if (it) 30 else 0) },
            )
        }

        SectionLabel("Apps")
        HpCard {
            InfoRow(PixelIcons.Heart, c.hpHealthy, "Apps aliadas", "${game.allies.size} apps que no hacen daño (Maps, banco, trabajo…)")
            SecondaryButton("Gestionar aliadas", onClick = onOpenAllies)
        }

        SectionLabel("Nube y reloj")
        cloudSection()

        SectionLabel("Seguridad")
        HpCard {
            InfoRow(PixelIcons.Revive, c.hpCritical, "PIN para Revivir", if (prefs.hasPin) "Configurado" else "Sin PIN: Revivir no estará disponible")
            SecondaryButton(if (prefs.hasPin) "Cambiar PIN" else "Crear PIN", onClick = { pinDialog = true })
        }

        SectionLabel("Experiencia")
        HpCard {
            ToggleRow("Vibraciones del reloj", "Advertencias, latido crítico y celebraciones", s.haptics) { v -> onSettings { it.copy(haptics = v) } }
            ToggleRow("Sonido chiptune", "Efectos cortos en level up, pociones y logros", s.sound) { v -> onSettings { it.copy(sound = v) } }
            ToggleRow("Alto contraste", "Bordes en la barra de vida y texto más claro", s.highContrast) { v -> onSettings { it.copy(highContrast = v) } }
            Text("Reducir animaciones", style = Hp.type.bodyStrong, color = c.textPrimary)
            val options = listOf<Pair<String, Boolean?>>("Sistema" to null, "Sí" to true, "No" to false)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { i, (label, value) ->
                    SegmentedButton(
                        selected = s.reducedMotion == value,
                        onClick = { onSettings { it.copy(reducedMotion = value) } },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                    ) { Text(label) }
                }
            }
            Text("Sin sacudidas, destellos ni partículas. La barra sigue cambiando de valor.", style = Hp.type.caption, color = c.textSecondary)
        }

        SectionLabel("Permisos")
        HpCard {
            HpPermission.entries.forEach { p ->
                val granted = permissions[p] == true
                InfoRow(
                    if (granted) PixelIcons.Star else PixelIcons.Lock,
                    if (granted) c.hpHealthy else c.hpHurt,
                    title = permissionName(p),
                    subtitle = if (granted) "Concedido" else "Pendiente",
                )
            }
            SecondaryButton("Revisar permisos", onClick = onOpenPermissions)
        }

        SectionLabel("Partida")
        HpCard(border = c.hpCritical) {
            Text("Empezar de nuevo borra tu progreso de hoy y vuelve al tutorial.", style = Hp.type.body, color = c.textSecondary)
            SecondaryButton("Empezar de nuevo", onClick = { resetDialog = true }, icon = PixelIcons.Skull, iconTint = c.hpCritical)
        }
    }

    if (pinDialog) PinDialog(onDismiss = { pinDialog = false }, onSave = { onPin(it); pinDialog = false })
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text("¿Empezar de nuevo?") },
            text = { Text("Se borrará la partida actual. Tu historial y logros se conservan.") },
            confirmButton = { TextButton(onClick = { resetDialog = false; onReset() }) { Text("Empezar de nuevo", color = c.hpCritical) } },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text("Cancelar") } },
            containerColor = c.surfaceElevated,
        )
    }
}

fun permissionName(p: HpPermission) = when (p) {
    HpPermission.USAGE -> "Acceso a datos de uso"
    HpPermission.ACCESSIBILITY -> "Servicio de accesibilidad"
    HpPermission.OVERLAY -> "Mostrar sobre otras apps"
    HpPermission.NOTIFICATIONS -> "Notificaciones"
    HpPermission.DEVICE_ADMIN -> "Bloqueo de pantalla"
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) {
                toggleableState = if (checked) ToggleableState.On else ToggleableState.Off
                stateDescription = if (checked) "Activado" else "Desactivado"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = Hp.type.bodyStrong, color = Hp.colors.textPrimary)
            Text(subtitle, style = Hp.type.caption, color = Hp.colors.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PinDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.length == 4 && pin != confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("PIN para Revivir") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                OutlinedTextField(
                    value = pin, onValueChange = { v -> pin = v.filter(Char::isDigit).take(4) },
                    label = { Text("Nuevo PIN") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = fieldColors(),
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { v -> confirm = v.filter(Char::isDigit).take(4) },
                    label = { Text("Repite el PIN") }, singleLine = true,
                    isError = mismatch,
                    supportingText = { if (mismatch) Text("Los PIN no coinciden") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    colors = fieldColors(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(pin) }, enabled = pin.length == 4 && pin == confirm) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        containerColor = Hp.colors.surfaceElevated,
    )
}

