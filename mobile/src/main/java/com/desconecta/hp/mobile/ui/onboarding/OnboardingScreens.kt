package com.desconecta.hp.mobile.ui.onboarding

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.system.HpPermission
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthAttacker
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBar
import com.desconecta.hp.shared.designsystem.component.toMood
import com.desconecta.hp.shared.designsystem.icon.AvatarMood
import com.desconecta.hp.shared.designsystem.icon.AvatarSprites
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.AvatarStyle
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.formatMinutes
import kotlinx.coroutines.launch

// =====================================================================================
// Intro narrativa (4 pasos)
// =====================================================================================

@Composable
fun IntroScreen(
    watchConnected: Boolean,
    permissions: Map<HpPermission, Boolean>,
    onRetryWatch: () -> Unit,
    onGrant: (HpPermission) -> Unit,
    onFinish: () -> Unit,
) {
    val pages = 4
    val pager = rememberPagerState { pages }
    val scope = rememberCoroutineScope()
    val c = Hp.colors

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(HpSpacing.lg), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${pager.currentPage + 1} DE $pages",
                style = Hp.type.gameLabel,
                color = c.textSecondary,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Paso ${pager.currentPage + 1} de $pages" },
            )
            if (pager.currentPage < pages - 1) TextButton(onClick = { scope.launch { pager.animateScrollToPage(pages - 1) } }) {
                Text("Saltar", style = Hp.type.label, color = c.textSecondary)
            }
        }
        StepDots(pager.currentPage, pages)
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = HpSpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(HpSpacing.lg, Alignment.CenterVertically),
            ) {
                when (page) {
                    0 -> ConceptPage()
                    1 -> MonstersPage()
                    2 -> WatchPage(watchConnected, onRetryWatch)
                    else -> PermissionsPage(permissions, onGrant)
                }
            }
        }
        Column(Modifier.padding(HpSpacing.lg)) {
            if (pager.currentPage < pages - 1) {
                PrimaryButton("Siguiente", onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            } else {
                val required = listOf(HpPermission.USAGE, HpPermission.ACCESSIBILITY, HpPermission.OVERLAY)
                val ready = required.all { permissions[it] == true }
                PrimaryButton(if (ready) "Crear mi personaje" else "Continuar sin todos los permisos", onClick = onFinish)
            }
        }
    }
}

@Composable
private fun StepDots(current: Int, total: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        repeat(total) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = if (i == current) 24.dp else 8.dp, height = 8.dp)
                    .clip(Hp.shapes.bar)
                    .background(if (i <= current) Hp.colors.hpHealthy else Hp.colors.hpTrack),
            )
        }
    }
}

@Composable
private fun PageTitle(title: String, body: String) {
    Text(title, style = Hp.type.gameTitle, color = Hp.colors.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
    Text(body, style = Hp.type.body, color = Hp.colors.textSecondary, textAlign = TextAlign.Center)
}

/** Paso 1: la barra de vida baja sola en bucle para explicar el concepto sin palabras. */
@Composable
private fun ConceptPage() {
    val hp = if (Hp.motion.reduced) 84 else rememberInfiniteTransition(label = "demo").animateFloat(
        120f, 30f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "demoHp",
    ).value.toInt()
    PageTitle(
        "TU TIEMPO ES TU VIDA",
        "Cada día empiezas con la vida llena. 1 HP = 1 minuto de pantalla. Cuando usas el teléfono, tu barra recibe daño.",
    )
    HpCard { HealthBar(Health(hp, 120), attacker = HealthAttacker.of(MonsterArt.SCROLLZILLA)) }
    Text("A medianoche haces respawn: vida llena otra vez.", style = Hp.type.caption, color = Hp.colors.textSecondary)
}

@Composable
private fun MonstersPage() {
    PageTitle(
        "CUIDADO CON LOS MONSTRUOS",
        "Las apps más adictivas son monstruos con su propio límite. Si agotan su vida, descansan hasta mañana.",
    )
    Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
        listOf(MonsterArt.SCROLLZILLA, MonsterArt.MEDUSA, MonsterArt.REY_AUTOPLAY, MonsterArt.GOBLIN).forEach {
            Image(MonsterSprites.of(it), it.defaultName, Modifier.size(64.dp))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
        Avatar(AvatarStyle.INDIGO, AvatarMood.HAPPY, size = 64.dp)
        Text(
            "Tu avatar te acompaña: sonríe con vida, se preocupa cuando bajas y cae K.O. en Game Over.",
            style = Hp.type.body,
            color = Hp.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WatchPage(connected: Boolean, onRetry: () -> Unit) {
    val c = Hp.colors
    PageTitle(
        "TU RELOJ ES EL MANDO",
        "En el reloj ves tu vida en tiempo real, usas pociones y escudos, y sientes las advertencias.",
    )
    Image(PixelIcons.Watch, null, Modifier.size(96.dp), colorFilter = ColorFilter.tint(if (connected) c.hpHealthy else c.textSecondary))
    HpCard(border = if (connected) c.hpHealthy else c.outline) {
        Text(
            if (connected) "¡Reloj conectado!" else "Buscando tu reloj…",
            style = Hp.type.title,
            color = if (connected) c.hpHealthy else c.textPrimary,
        )
        Text(
            if (connected) "Todo listo para jugar con el reloj."
            else "Abre HP en tu reloj Wear OS y mantenlo cerca. Puedes continuar sin él: el teléfono aplicará las reglas igual.",
            style = Hp.type.caption,
            color = c.textSecondary,
        )
        if (!connected) SecondaryButton("Buscar de nuevo", onClick = onRetry, icon = PixelIcons.Watch)
    }
}

private data class PermissionCopy(val title: String, val why: String, val required: Boolean)

private fun copyFor(p: HpPermission) = when (p) {
    HpPermission.USAGE -> PermissionCopy(
        "Acceso a datos de uso",
        "Para que los monstruos no te ataquen a escondidas, necesito ver qué app está abierta y cuánto tiempo.",
        required = true,
    )
    HpPermission.ACCESSIBILITY -> PermissionCopy(
        "Servicio de accesibilidad",
        "Para detener a un monstruo derrotado, necesito saber cuándo se abre su app y mostrar la pantalla de descanso. No leo lo que escribes.",
        required = true,
    )
    HpPermission.OVERLAY -> PermissionCopy(
        "Mostrar sobre otras apps",
        "Para que la pantalla de \"Monstruo derrotado\" aparezca encima de la app bloqueada.",
        required = true,
    )
    HpPermission.NOTIFICATIONS -> PermissionCopy(
        "Notificaciones",
        "Para mostrar tu vida en la barra de notificaciones mientras te protejo.",
        required = false,
    )
    HpPermission.DEVICE_ADMIN -> PermissionCopy(
        "Bloqueo de pantalla",
        "Solo para apagar la pantalla cuando llegas a Game Over. Nada más.",
        required = false,
    )
}

/** Paso 4: permisos uno por uno (el siguiente pendiente se destaca). */
@Composable
private fun PermissionsPage(permissions: Map<HpPermission, Boolean>, onGrant: (HpPermission) -> Unit) {
    val c = Hp.colors
    val next = HpPermission.entries.firstOrNull { permissions[it] != true }
    PageTitle("PERMISOS", "Te explico cada uno. Puedes cambiarlos cuando quieras en Configuración.")
    HpPermission.entries.forEach { p ->
        val granted = permissions[p] == true
        val copy = copyFor(p)
        val focused = p == next
        HpCard(
            container = if (focused) c.surfaceElevated else c.surface,
            border = when {
                granted -> c.hpHealthy
                focused -> c.outlineStrong
                else -> null
            },
            modifier = Modifier.semantics { stateDescription = if (granted) "Concedido" else "Pendiente" },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                Image(
                    if (granted) PixelIcons.Star else PixelIcons.Lock, null, Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(if (granted) c.hpHealthy else c.hpHurt),
                )
                Column(Modifier.weight(1f)) {
                    Text(copy.title, style = Hp.type.bodyStrong, color = c.textPrimary)
                    Text(
                        if (granted) "Concedido" else if (copy.required) "Necesario" else "Opcional",
                        style = Hp.type.caption,
                        color = if (granted) c.hpHealthy else c.textSecondary,
                    )
                }
            }
            if (!granted && focused) {
                Text(copy.why, style = Hp.type.body, color = c.textSecondary)
                PrimaryButton("Conceder", onClick = { onGrant(p) })
            }
        }
    }
}

// =====================================================================================
// Creación de personaje
// =====================================================================================

@Composable
fun CharacterScreen(onContinue: (name: String, avatar: AvatarStyle, maxHp: Int, weekendBonus: Int, pin: String?) -> Unit) {
    val c = Hp.colors
    var name by remember { mutableStateOf("") }
    var style by remember { mutableStateOf(AvatarStyle.INDIGO) }
    var maxHp by remember { mutableIntStateOf(120) }
    var weekend by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(HpSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(HpSpacing.lg),
        ) {
            Text("CREA TU PERSONAJE", style = Hp.type.gameTitle, color = c.textPrimary, modifier = Modifier.semantics { heading() })
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Avatar(style, Health(maxHp, maxHp).status.toMood(), size = 128.dp, name = name.ifBlank { null })
            }
            Text("Estilo", style = Hp.type.bodyStrong, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                AvatarStyle.entries.forEach { s ->
                    val selected = s == style
                    Column(
                        Modifier
                            .clip(Hp.shapes.md)
                            .selectable(selected, role = Role.RadioButton, onClick = { style = s })
                            .padding(HpSpacing.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(56.dp)
                                .clip(Hp.shapes.slot)
                                .background(c.surfaceElevated)
                                .border(if (selected) 3.dp else 1.dp, if (selected) AvatarSprites.styleColor(s) else c.outline, Hp.shapes.slot),
                            contentAlignment = Alignment.Center,
                        ) { Avatar(s, AvatarMood.HAPPY, size = 48.dp) }
                        Text(s.label, style = Hp.type.caption, color = if (selected) c.textPrimary else c.textSecondary)
                    }
                }
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(16) },
                label = { Text("Nombre de jugador") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            Text("Vida máxima diaria", style = Hp.type.bodyStrong, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                Health.MAX_PRESETS.forEach { v ->
                    FilterChip(
                        selected = v == maxHp,
                        onClick = { maxHp = v },
                        label = { Text("$v HP") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.healthyContainer, selectedLabelColor = c.hpHealthy),
                    )
                }
            }
            Text("= ${formatMinutes(maxHp)} de pantalla al día", style = Hp.type.caption, color = c.textSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Más vida el fin de semana", style = Hp.type.bodyStrong, color = c.textPrimary)
                    Text("+30 HP sábados y domingos", style = Hp.type.caption, color = c.textSecondary)
                }
                Switch(checked = weekend, onCheckedChange = { weekend = it })
            }
            OutlinedTextField(
                value = pin,
                onValueChange = { v -> pin = v.filter(Char::isDigit).take(4) },
                label = { Text("PIN para Revivir (4 dígitos)") },
                supportingText = { Text("Lo pedirá el reloj si usas Revivir en Game Over.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
        }
        Column(Modifier.padding(HpSpacing.lg)) {
            PrimaryButton(
                "Elegir monstruos",
                onClick = { onContinue(name, style, maxHp, if (weekend) 30 else 0, pin.takeIf { it.length == 4 }) },
                enabled = pin.isEmpty() || pin.length == 4,
            )
        }
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Hp.colors.hpHealthy,
    unfocusedBorderColor = Hp.colors.outlineStrong,
    focusedLabelColor = Hp.colors.hpHealthy,
    unfocusedLabelColor = Hp.colors.textSecondary,
    focusedTextColor = Hp.colors.textPrimary,
    unfocusedTextColor = Hp.colors.textPrimary,
    cursorColor = Hp.colors.hpHealthy,
)

/** Reutilizado por "Revisar permisos" desde el panel o Configuración. */
@Composable
fun PermissionsOnlyScreen(permissions: Map<HpPermission, Boolean>, onGrant: (HpPermission) -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(HpSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.lg),
    ) { PermissionsPage(permissions, onGrant) }
}

