package com.desconecta.hp.mobile.ui.bestiary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.HpTopBar
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.mobile.ui.components.SectionLabel
import com.desconecta.hp.mobile.ui.onboarding.LimitStepper
import com.desconecta.hp.mobile.ui.onboarding.fieldColors
import com.desconecta.hp.shared.designsystem.component.HpStateMessage
import com.desconecta.hp.shared.designsystem.component.MonsterCard
import com.desconecta.hp.shared.designsystem.component.ScreenStateKind
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.formatMinutes

/** Bestiario: cada monstruo con sus estadísticas históricas (7 días). */
@Composable
fun BestiaryScreen(game: GameState, now: Long, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val c = Hp.colors
    Scaffold(
        containerColor = c.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Añadir monstruo") },
                containerColor = c.hpHealthy,
                contentColor = c.onAccent,
            )
        },
    ) { padding ->
        if (game.monsters.isEmpty()) {
            HpStateMessage(
                ScreenStateKind.EMPTY,
                title = "Aún no hay monstruos",
                body = "Añade las apps que más tiempo te roban. Cada una tendrá su propio límite.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = HpSpacing.lg, end = HpSpacing.lg, top = padding.calculateTopPadding() + HpSpacing.lg, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
        ) {
            item {
                Text(
                    "${game.monsters.size} monstruos · ${game.monsters.count { it.isDefeated }} derrotados hoy",
                    style = Hp.type.caption,
                    color = c.textSecondary,
                )
            }
            items(game.monsters.sortedByDescending { it.week.sum() }, key = { it.packageName }) { m ->
                Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
                    MonsterCard(m, now = now, attacking = m.packageName == game.activeAttacker, onClick = { onOpen(m.packageName) })
                    StatsRow(m)
                }
            }
        }
    }
}

@Composable
private fun StatsRow(m: Monster) {
    val c = Hp.colors
    val week = m.week
    val avg = if (week.isEmpty()) 0 else week.sum() / week.size
    val over = week.count { it > m.limitMinutes }
    Row(Modifier.fillMaxWidth().padding(horizontal = HpSpacing.md), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat("Promedio", "${formatMinutes(avg)}/día")
        Stat("Semana", formatMinutes(week.sum()))
        Stat("Excesos", "$over de 7", if (over > 0) c.hpHurt else c.hpHealthy)
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color = Hp.colors.textPrimary) {
    Column {
        Text(label, style = Hp.type.caption, color = Hp.colors.textSecondary)
        Text(value, style = Hp.type.bodyStrong, color = color)
    }
}

/**
 * Editar monstruo: nombre, ilustración (galería), límite y eliminar.
 * Eliminar pide confirmación (acción destructiva: Nielsen nº 5, prevenir errores).
 */
@Composable
fun MonsterEditScreen(
    monster: Monster,
    onBack: () -> Unit,
    onSave: (Monster) -> Unit,
    onDelete: () -> Unit,
) {
    val c = Hp.colors
    var name by remember { mutableStateOf(monster.name) }
    var art by remember { mutableStateOf(monster.art) }
    var limit by remember { mutableIntStateOf(monster.limitMinutes) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background)) {
        HpTopBar(monster.appLabel, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(HpSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(HpSpacing.lg),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(MonsterSprites.of(art), null, Modifier.size(128.dp))
            }
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(28) },
                label = { Text("Nombre del monstruo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors(),
            )
            SectionLabel("Ilustración")
            Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                MonsterArt.entries.forEach { a ->
                    val selected = a == art
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(Hp.shapes.slot)
                            .background(if (selected) c.surfaceElevated else c.surface)
                            .border(if (selected) 3.dp else 1.dp, if (selected) c.hpHealthy else c.outline, Hp.shapes.slot)
                            .selectable(selected, role = Role.RadioButton, onClick = { art = a }),
                        contentAlignment = Alignment.Center,
                    ) { Image(MonsterSprites.of(a), a.defaultName, Modifier.size(40.dp)) }
                }
            }
            SectionLabel("Límite diario")
            HpCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Hoy lleva ${formatMinutes(monster.usedMinutes)}", style = Hp.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                    LimitStepper(limit) { limit = it }
                }
                Text("También puedes ajustarlo con la corona del reloj.", style = Hp.type.caption, color = c.textSecondary)
            }
            SecondaryButton("Quitar este monstruo", onClick = { confirmDelete = true }, icon = PixelIcons.Skull, iconTint = c.hpCritical)
        }
        Column(Modifier.padding(HpSpacing.lg)) {
            PrimaryButton(
                "Guardar",
                onClick = { onSave(monster.copy(name = name.ifBlank { monster.name }, art = art, limitMinutes = limit)) },
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("¿Quitar ${monster.name}?") },
            text = { Text("${monster.appLabel} dejará de tener límite propio, pero seguirá restando vida como cualquier app.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Quitar", color = c.hpCritical) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
            containerColor = c.surfaceElevated,
        )
    }
}
