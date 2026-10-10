package com.desconecta.hp.wear.ui.profile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.designsystem.component.AchievementBadge
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.HpStateMessage
import com.desconecta.hp.shared.designsystem.component.ScreenStateKind
import com.desconecta.hp.shared.designsystem.component.StreakPill
import com.desconecta.hp.shared.designsystem.component.XpBar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarFrame
import com.desconecta.hp.shared.designsystem.component.healthbar.SegmentedBar
import com.desconecta.hp.shared.designsystem.component.toMood
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.formatMinutes

/** Perfil: quién soy en el juego (nivel, título, XP, racha y últimas medallas). */
@Composable
fun ProfileScreen(game: GameState) {
    val c = Hp.colors
    val t = Hp.type
    val p = game.player
    val listState = rememberScalingLazyListState()
    val recent = game.unlockedAchievements.entries.sortedByDescending { it.value }.take(3).map { it.key }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Avatar(p.avatar, game.health.status.toMood(), size = 64.dp, accessories = p.equipped, name = p.name) }
            item {
                BasicText(p.name, style = t.headline.copy(color = c.textPrimary), modifier = Modifier.semantics { heading() })
            }
            item { BasicText(p.title, style = t.caption.copy(color = c.xp)) }
            item { XpBar(p.totalXp, Modifier.fillMaxWidth(0.8f)) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HpSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StreakPill(p.streakDays)
                    BasicText("Mejor: ${p.bestStreak}", style = t.caption.copy(color = c.textSecondary))
                }
            }
            item { ListHeader { Text("Medallas recientes") } }
            item {
                if (recent.isEmpty()) {
                    BasicText(
                        "Termina tu primer día con vida para ganar una.",
                        style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                        recent.forEach { AchievementBadge(it, unlocked = true, compact = true) }
                    }
                }
            }
            item {
                BasicText(
                    "${game.unlockedAchievements.size} medallas · ver todas en el teléfono",
                    style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/**
 * Reto semanal "Jefe Final". La barra del jefe es la MISMA barra de vida, en rojo de
 * "enemigo": el jugador ya sabe leerla (consistencia), y aquí bajarla es lo bueno.
 */
@Composable
fun BossScreen(game: GameState) {
    val c = Hp.colors
    val t = Hp.type
    val listState = rememberScalingLazyListState()
    val boss = game.boss

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Jefe semanal") } }
            if (boss == null) {
                item {
                    HpStateMessage(
                        ScreenStateKind.EMPTY,
                        title = "Sin jefe esta semana",
                        body = "El próximo reto aparece el lunes.",
                    )
                }
                return@ScalingLazyColumn
            }
            item { Image(MonsterSprites.of(boss.art), null, Modifier.size(56.dp)) }
            item {
                BasicText(
                    boss.name.uppercase(),
                    style = t.gameTitle.copy(color = c.hpCritical, textAlign = TextAlign.Center),
                    modifier = Modifier.semantics { heading() },
                )
            }
            item {
                BasicText(boss.description, style = t.body.copy(color = c.textPrimary, textAlign = TextAlign.Center))
            }
            item {
                val h = boss.bossHealth
                Column(
                    Modifier
                        .fillMaxWidth(0.85f)
                        .clearAndSetSemantics {
                            contentDescription = "Vida del jefe: ${h.clamped} de ${h.max}. " +
                                "Usado ${formatMinutes(boss.usedMinutes)} de ${formatMinutes(boss.budgetMinutes)}"
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SegmentedBar(HealthBarFrame.still(h, if (boss.failed) c.textSecondary else c.hpCritical), h.max, height = 10.dp)
                    BasicText(
                        "${formatMinutes(boss.usedMinutes)} / ${formatMinutes(boss.budgetMinutes)}",
                        style = t.caption.copy(color = c.textSecondary),
                    )
                }
            }
            item {
                BasicText(
                    when {
                        boss.failed -> "Esta vez ganó el jefe. ¡La próxima semana!"
                        boss.onTrack -> "¡Vas bien! Día ${boss.dayOfWeek} de 7"
                        else -> "Vas por encima del ritmo. Día ${boss.dayOfWeek} de 7"
                    },
                    style = t.bodyStrong.copy(
                        color = when {
                            boss.failed -> c.textSecondary
                            boss.onTrack -> c.hpHealthy
                            else -> c.hpHurt
                        },
                        textAlign = TextAlign.Center,
                    ),
                )
            }
            boss.rewardAccessory?.let { reward ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
                        Image(PixelIcons.Medal, null, Modifier.size(16.dp), colorFilter = ColorFilter.tint(c.gold))
                        BasicText("Premio: medalla + ${reward.label}", style = t.caption.copy(color = c.gold))
                    }
                }
            }
        }
    }
}
