package com.desconecta.hp.mobile.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.InfoRow
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.mobile.ui.components.SectionLabel
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.ItemSlot
import com.desconecta.hp.shared.designsystem.component.MonsterCard
import com.desconecta.hp.shared.designsystem.component.StreakPill
import com.desconecta.hp.shared.designsystem.component.XpBar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthAttacker
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarFrame
import com.desconecta.hp.shared.designsystem.component.healthbar.SegmentedBar
import com.desconecta.hp.shared.designsystem.component.toMood
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.model.DaySummary
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.ItemType
import com.desconecta.hp.shared.model.formatDuration
import com.desconecta.hp.shared.model.formatMinutes

@Composable
fun HomeScreen(
    game: GameState,
    history: List<DaySummary>?,
    watchConnected: Boolean,
    missingPermissions: Int,
    now: Long,
    onOpenPermissions: () -> Unit,
    onOpenBestiary: () -> Unit,
    onOpenAvatar: () -> Unit,
    onOpenSettings: () -> Unit,
    onStudyMode: (Int) -> Unit,
    syncIndicator: @Composable () -> Unit = {},
) {
    val c = Hp.colors
    val p = game.player
    LazyColumn(
        Modifier.fillMaxSize().background(c.background),
        contentPadding = PaddingValues(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        // --- Encabezado: quién soy + conexión -------------------------------------------
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(Hp.shapes.md)
                        .clickable(role = Role.Button, onClickLabel = "Personalizar avatar", onClick = onOpenAvatar),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
                ) {
                    Avatar(p.avatar, game.health.status.toMood(), size = 48.dp, accessories = p.equipped, name = p.name)
                    Column {
                        Text(p.name, style = Hp.type.title, color = c.textPrimary, modifier = Modifier.semantics { heading() })
                        Text("NV ${p.level} · ${p.title}", style = Hp.type.caption, color = c.xp)
                    }
                }
                syncIndicator()
                WatchStatus(watchConnected)
                IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, contentDescription = "Configuración", tint = c.textPrimary) }
            }
        }

        // --- Sin permisos ----------------------------------------------------------------
        if (missingPermissions > 0) {
            item {
                HpCard(container = c.hurtContainer, border = c.hpHurt) {
                    InfoRow(
                        PixelIcons.Lock, c.hpHurt,
                        title = "HP no puede ver a los monstruos",
                        subtitle = "Faltan $missingPermissions permiso${if (missingPermissions > 1) "s" else ""}: el daño no se está contando.",
                    )
                    PrimaryButton("Revisar permisos", onClick = onOpenPermissions, container = c.hpHurt)
                }
            }
        }

        // --- Vida -----------------------------------------------------------------------
        item {
            HpCard {
                if (game.phase == GamePhase.GAME_OVER) {
                    Text("GAME OVER", style = Hp.type.gameTitle, color = c.hpCritical)
                    Text("Respawn a medianoche. Descansa: mañana empiezas con la vida llena.", style = Hp.type.body, color = c.textSecondary)
                } else {
                    HealthBar(
                        game.health,
                        attacker = game.attacker?.let { HealthAttacker.of(it.art, it.name) },
                        regenerating = game.regenerating,
                    )
                    val attacker = game.attacker
                    Text(
                        when {
                            attacker != null -> "${attacker.name} (${attacker.appLabel}) te está atacando"
                            game.regenerating -> "Regenerando: +5 HP cada 30 min sin usar el teléfono"
                            else -> "Nadie te ataca ahora mismo"
                        },
                        style = Hp.type.caption,
                        color = if (attacker != null) c.hpCritical else c.textSecondary,
                    )
                }
            }
        }

        // --- Progreso --------------------------------------------------------------------
        item {
            HpCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreakPill(p.streakDays)
                    Text("  días sin Game Over", style = Hp.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
                    Image(PixelIcons.Heart, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.regen))
                    Text(" ${game.regenToday}/${GameEngine.REGEN_DAILY_CAP} regen", style = Hp.type.caption, color = c.textSecondary)
                }
                XpBar(p.totalXp)
            }
        }

        // --- Monstruos -------------------------------------------------------------------
        item {
            SectionLabel("Monstruos de hoy") {
                TextButton(onClick = onOpenBestiary) { Text("Bestiario", style = Hp.type.label, color = c.hpHealthy) }
            }
        }
        val top = game.monsters.sortedWith(compareBy({ it.packageName != game.activeAttacker }, { -it.usedMinutes })).take(3)
        if (top.isEmpty()) {
            item {
                HpCard {
                    Text("Aún no hay monstruos", style = Hp.type.title, color = c.textPrimary)
                    Text("Elige qué apps limitar en el bestiario.", style = Hp.type.body, color = c.textSecondary)
                    SecondaryButton("Abrir bestiario", onClick = onOpenBestiary)
                }
            }
        }
        top.forEach { m ->
            item(key = m.packageName) {
                MonsterCard(m, now = now, attacking = m.packageName == game.activeAttacker, onClick = onOpenBestiary)
            }
        }

        // --- Semana ----------------------------------------------------------------------
        item { SectionLabel("Vida perdida esta semana") }
        item { HpCard { WeeklyLossChart(history, game) } }

        // --- Objetos (solo lectura) ------------------------------------------------------
        item { SectionLabel("Inventario") }
        item {
            HpCard {
                Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                    ItemType.entries.forEach { ItemSlot(it, game.inventory.count(it, now)) }
                }
                Text("Los objetos se usan desde tu reloj, con confirmación.", style = Hp.type.caption, color = c.textSecondary)
            }
        }

        // --- Jefe semanal ----------------------------------------------------------------
        game.boss?.let { boss ->
            item { SectionLabel("Jefe final de la semana") }
            item {
                HpCard(border = c.hpCritical) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                        Image(MonsterSprites.of(boss.art), null, Modifier.size(48.dp))
                        Column(Modifier.weight(1f)) {
                            Text(boss.name, style = Hp.type.title, color = c.textPrimary)
                            Text(boss.description, style = Hp.type.caption, color = c.textSecondary)
                        }
                    }
                    SegmentedBar(HealthBarFrame.still(boss.bossHealth, c.hpCritical), boss.bossHealth.max, height = 12.dp)
                    Text(
                        "${formatMinutes(boss.usedMinutes)} de ${formatMinutes(boss.budgetMinutes)} · día ${boss.dayOfWeek} de 7" +
                            (boss.rewardAccessory?.let { " · premio: ${it.label}" } ?: ""),
                        style = Hp.type.caption,
                        color = if (boss.onTrack) c.hpHealthy else c.hpHurt,
                    )
                }
            }
        }

        // --- Modo estudio ----------------------------------------------------------------
        item { SectionLabel("Modo estudio") }
        item {
            HpCard {
                if (game.studyModeActive(now)) {
                    InfoRow(PixelIcons.Shield, c.xp, "Modo estudio activo", "Quedan ${formatDuration(game.studyModeUntil - now)}. Los monstruos están dormidos.")
                    SecondaryButton("Terminar ahora", onClick = { onStudyMode(0) })
                } else {
                    Text("Bloquea todos los monstruos mientras te concentras.", style = Hp.type.body, color = c.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                        SecondaryButton("25 min", onClick = { onStudyMode(25) }, modifier = Modifier.weight(1f), icon = PixelIcons.Shield, iconTint = c.xp)
                        SecondaryButton("50 min", onClick = { onStudyMode(50) }, modifier = Modifier.weight(1f), icon = PixelIcons.Shield, iconTint = c.xp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(HpSpacing.xl)) }
    }
}

@Composable
private fun WatchStatus(connected: Boolean) {
    val c = Hp.colors
    Row(
        Modifier
            .background(if (connected) c.healthyContainer else c.surfaceElevated, Hp.shapes.button)
            .padding(horizontal = HpSpacing.sm, vertical = HpSpacing.xs)
            .clearAndSetSemantics { contentDescription = if (connected) "Reloj conectado" else "Reloj desconectado" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(PixelIcons.Watch, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(if (connected) c.hpHealthy else c.textSecondary))
        Spacer(Modifier.width(HpSpacing.xs))
        Text(if (connected) "Reloj" else "Sin reloj", style = Hp.type.caption, color = if (connected) c.hpHealthy else c.textSecondary)
    }
}

/**
 * 7 barras (L–D): HP perdidos cada día. Hoy se dibuja en vivo; los días con Game Over
 * se marcan en rojo y con texto en la descripción (no solo color).
 */
@Composable
private fun WeeklyLossChart(history: List<DaySummary>?, game: GameState) {
    val c = Hp.colors
    if (history == null) {
        com.desconecta.hp.shared.designsystem.component.HpSkeleton(lines = 2)
        return
    }
    val days = (history.take(6).reversed().map { (it.maxHp - it.hpLeft) to it.gameOver } +
        ((game.maxHp - game.currentHp) to (game.phase == GamePhase.GAME_OVER)))
    val padded = List((7 - days.size).coerceAtLeast(0)) { 0 to false } + days.takeLast(7)
    val max = (padded.maxOfOrNull { it.first } ?: 0).coerceAtLeast(game.maxHp)
    val avg = padded.map { it.first }.filter { it > 0 }.average().takeIf { !it.isNaN() }?.toInt() ?: 0
    val gameOvers = padded.count { it.second }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clearAndSetSemantics {
                contentDescription = "Vida perdida en los últimos 7 días: ${padded.joinToString { "${it.first} HP" }}. " +
                    "$gameOvers días con Game Over"
            },
    ) {
        val gap = 8.dp.toPx()
        val w = (size.width - gap * 6) / 7
        padded.forEachIndexed { i, (lost, over) ->
            val h = (size.height * lost / max).coerceAtLeast(2.dp.toPx())
            drawRect(
                when {
                    over -> c.hpCritical
                    i == 6 -> c.hpHealthy
                    else -> c.textSecondary
                },
                Offset(i * (w + gap), size.height - h),
                Size(w, h),
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        listOf("6d", "5d", "4d", "3d", "2d", "ayer", "hoy").forEach {
            Text(it, style = Hp.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
    Text(
        "Promedio: $avg HP/día" + if (gameOvers > 0) " · $gameOvers Game Over" else " · sin Game Over",
        style = Hp.type.caption,
        color = c.textSecondary,
    )
}
