package com.desconecta.hp.mobile.ui.progress

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.SectionLabel
import com.desconecta.hp.shared.designsystem.component.AchievementBadge
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.HpSkeleton
import com.desconecta.hp.shared.designsystem.component.HpStateMessage
import com.desconecta.hp.shared.designsystem.component.ScreenStateKind
import com.desconecta.hp.shared.designsystem.component.XpBar
import com.desconecta.hp.shared.designsystem.icon.AvatarMood
import com.desconecta.hp.shared.designsystem.icon.AvatarSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.Accessory
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.AvatarStyle
import com.desconecta.hp.shared.model.DaySummary
import com.desconecta.hp.shared.model.Player
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

// =====================================================================================
// Salón de logros
// =====================================================================================

@Composable
fun AchievementsScreen(unlocked: Map<AchievementId, Long>) {
    val c = Hp.colors
    var selected by remember { mutableStateOf<AchievementId?>(null) }
    val sorted = AchievementId.entries.sortedBy { it !in unlocked }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 100.dp),
        modifier = Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(HpSpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.lg),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            HpCard {
                Text(
                    "${unlocked.size} de ${AchievementId.entries.size} medallas",
                    style = Hp.type.title,
                    color = c.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                com.desconecta.hp.shared.designsystem.component.healthbar.SegmentedBar(
                    com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarFrame.still(
                        com.desconecta.hp.shared.model.Health(unlocked.size.coerceAtLeast(0), AchievementId.entries.size),
                        c.gold,
                    ),
                    AchievementId.entries.size,
                    height = 8.dp,
                    segmented = false,
                )
            }
        }
        items(sorted, key = { it.name }) { id ->
            AchievementBadge(
                id,
                unlocked = id in unlocked,
                modifier = Modifier
                    .clip(Hp.shapes.md)
                    .clickable(role = Role.Button, onClickLabel = "Ver detalle") { selected = id }
                    .padding(HpSpacing.xs),
            )
        }
    }

    selected?.let { id ->
        val date = unlocked[id]
        AlertDialog(
            onDismissRequest = { selected = null },
            icon = {
                Image(
                    if (date != null) PixelIcons.Medal else PixelIcons.MedalLocked, null, Modifier.size(48.dp),
                    colorFilter = ColorFilter.tint(if (date != null) c.gold else c.textSecondary),
                )
            },
            title = { Text(id.title, textAlign = TextAlign.Center) },
            text = {
                Text(
                    id.description + "\n\n" + if (date != null) "Desbloqueada el ${LocalDate.ofEpochDay(date / 86_400_000)}." else "Aún bloqueada. ¡Tú puedes!",
                    textAlign = TextAlign.Center,
                )
            },
            confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } },
            containerColor = c.surfaceElevated,
        )
    }
}

// =====================================================================================
// Historial: calendario mapa de calor
// =====================================================================================

/**
 * Cada día del mes es una casilla cuyo verde es más intenso cuanta más vida sobró. Los
 * días con Game Over llevan calavera (no solo color). Leyenda debajo: un mapa de calor
 * sin leyenda no se puede interpretar.
 */
@Composable
fun HistoryScreen(history: List<DaySummary>?, bestStreak: Int) {
    val c = Hp.colors
    var month by remember { mutableStateOf(YearMonth.now()) }

    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        if (history == null) {
            HpSkeleton(lines = 6, lineHeight = 32.dp)
            return@Column
        }
        if (history.isEmpty()) {
            HpStateMessage(
                ScreenStateKind.EMPTY,
                title = "Tu historial empieza hoy",
                body = "Después de tu primer respawn verás aquí cuánta vida te sobró cada día.",
            )
            return@Column
        }
        val byDay = history.associateBy { it.dayKey }
        HpCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Mes anterior", tint = c.textPrimary)
                }
                Text(
                    month.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")).replaceFirstChar { it.uppercase() } + " ${month.year}",
                    style = Hp.type.title,
                    color = c.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Mes siguiente", tint = c.textPrimary)
                }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEach {
                    Text(it, style = Hp.type.caption, color = c.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = List(offset) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (week + List(7 - week.size) { null }).forEach { date ->
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (date != null) DayCell(date, byDay[date.toString()])
                        }
                    }
                }
            }
            Legend()
        }
        val monthDays = history.filter { it.dayKey.startsWith(month.toString()) }
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
            StatCard("Vida media", if (monthDays.isEmpty()) "—" else "${monthDays.map { it.hpLeft }.average().toInt()} HP", Modifier.weight(1f))
            StatCard("Game Over", "${monthDays.count { it.gameOver }}", Modifier.weight(1f))
            StatCard("Mejor racha", "$bestStreak d", Modifier.weight(1f))
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, day: DaySummary?) {
    val c = Hp.colors
    val bg = when {
        day == null -> c.surfaceElevated
        day.gameOver -> c.criticalContainer
        else -> c.hpHealthy.copy(alpha = 0.2f + 0.8f * day.fractionLeft)
    }
    val description = when {
        day == null -> "$date, sin datos"
        day.gameOver -> "$date, Game Over"
        else -> "$date, sobraron ${day.hpLeft} de ${day.maxHp} HP"
    }
    Box(
        Modifier
            .fillMaxSize()
            .clip(Hp.shapes.xs)
            .background(bg)
            .then(if (date == LocalDate.now()) Modifier.border(2.dp, c.textPrimary, Hp.shapes.xs) else Modifier)
            .clearAndSetSemantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (day?.gameOver == true) {
            Image(PixelIcons.Skull, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.hpCritical))
        } else {
            Text(
                "${date.dayOfMonth}",
                style = Hp.type.caption,
                color = if (day != null && day.fractionLeft > 0.5f) c.onAccent else c.textSecondary,
            )
        }
    }
}

@Composable
private fun Legend() {
    val c = Hp.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
        Text("Menos vida", style = Hp.type.caption, color = c.textSecondary)
        listOf(0.2f, 0.45f, 0.7f, 1f).forEach {
            Box(Modifier.size(16.dp).clip(Hp.shapes.xs).background(c.hpHealthy.copy(alpha = it)))
        }
        Text("Más vida", style = Hp.type.caption, color = c.textSecondary)
        Spacer(Modifier.weight(1f))
        Image(PixelIcons.Skull, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(c.hpCritical))
        Text("Game Over", style = Hp.type.caption, color = c.textSecondary)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    HpCard(modifier) {
        Text(label, style = Hp.type.caption, color = Hp.colors.textSecondary)
        Text(value, style = Hp.type.title, color = Hp.colors.textPrimary)
    }
}

// =====================================================================================
// Personalización del avatar
// =====================================================================================

@Composable
fun AvatarScreen(player: Player, onStyle: (AvatarStyle) -> Unit, onToggle: (Accessory) -> Unit) {
    val c = Hp.colors
    var previewMood by remember { mutableStateOf(AvatarMood.HAPPY) }
    Column(
        Modifier.fillMaxSize().background(c.background).verticalScroll(rememberScrollState()).padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.lg),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Avatar(player.avatar, previewMood, size = 160.dp, accessories = player.equipped, name = player.name)
        }
        // Ver las 4 expresiones ayuda a entender que el avatar comunica el estado de vida.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            AvatarMood.entries.forEach { m ->
                androidx.compose.material3.FilterChip(
                    selected = m == previewMood,
                    onClick = { previewMood = m },
                    label = {
                        Text(
                            when (m) {
                                AvatarMood.HAPPY -> "Feliz"
                                AvatarMood.WORRIED -> "Preocupado"
                                AvatarMood.EXHAUSTED -> "Agotado"
                                AvatarMood.KO -> "K.O."
                            },
                        )
                    },
                )
            }
        }
        HpCard {
            Text(player.title, style = Hp.type.title, color = c.xp)
            XpBar(player.totalXp)
        }
        SectionLabel("Estilo")
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
            AvatarStyle.entries.forEach { s ->
                val selected = s == player.avatar
                Column(
                    Modifier.clip(Hp.shapes.md).clickable(role = Role.RadioButton) { onStyle(s) }.padding(HpSpacing.xs),
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
        SectionLabel("Accesorios")
        Accessory.entries.forEach { a ->
            val unlocked = player.level >= a.unlockLevel
            HpCard(container = if (unlocked) c.surface else c.background, border = if (unlocked) null else c.outline) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        if (unlocked) Avatar(player.avatar, AvatarMood.HAPPY, size = 48.dp, accessories = setOf(a))
                        else Image(PixelIcons.Lock, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(c.textSecondary))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(a.label, style = Hp.type.bodyStrong, color = if (unlocked) c.textPrimary else c.textSecondary)
                        Text(
                            if (unlocked) "Desbloqueado" else "Se desbloquea en el nivel ${a.unlockLevel}",
                            style = Hp.type.caption,
                            color = c.textSecondary,
                        )
                    }
                    Switch(checked = a in player.equipped, onCheckedChange = { onToggle(a) }, enabled = unlocked)
                }
            }
        }
    }
}

