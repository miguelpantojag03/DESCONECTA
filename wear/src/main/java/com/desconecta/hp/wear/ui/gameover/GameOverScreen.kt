package com.desconecta.hp.wear.ui.gameover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.PixelDivider
import com.desconecta.hp.shared.designsystem.icon.AvatarMood
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.formatMinutes
import com.desconecta.hp.shared.model.formatCountdown
import com.desconecta.hp.shared.model.formatDuration
import com.desconecta.hp.shared.model.millisUntilRespawn

@Composable
fun GameOverScreen(
    game: GameState,
    now: Long,
    onRevive: () -> Unit,
) {
    val c = Hp.colors
    val t = Hp.type
    val listState = rememberScalingLazyListState()
    val untilRespawn = millisUntilRespawn(now)
    val reviveReady = game.inventory.reviveReady(now)
    val worst = game.monsters.maxByOrNull { it.usedMinutes }?.takeIf { it.usedMinutes > 0 }

    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            if (reviveReady) {
                EdgeButton(onClick = onRevive) { Text("Revivir") }
            }
        },
    ) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Avatar(game.player.avatar, AvatarMood.KO, size = 48.dp, accessories = game.player.equipped)
            }
            item {
                BasicText(
                    "GAME OVER",
                    style = t.gameTitle.copy(color = c.hpCritical, textAlign = TextAlign.Center),
                    modifier = Modifier.semantics {
                        heading()
                        liveRegion = LiveRegionMode.Assertive
                    },
                )
            }
            item {
                BasicText(
                    "Hoy ya jugaste suficiente.\nEs una pausa, no un castigo.",
                    style = t.body.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                )
            }
            item { Spacer(Modifier.height(HpSpacing.xs)) }
            item {
                // Cuenta regresiva: responde "¿cuándo vuelvo?"
                BasicText(
                    "RESPAWN EN",
                    style = t.gameLabel.copy(color = c.textSecondary),
                )
            }
            item {
                BasicText(
                    formatCountdown(untilRespawn),
                    style = t.hpMax.copy(color = c.regen, fontSize = t.title.fontSize),
                    modifier = Modifier.semantics { contentDescription = "Respawn en ${formatDuration(untilRespawn)}" },
                )
            }
            item { PixelDivider(Modifier.fillMaxWidth(0.6f)) }
            item {
                Summary(
                    usedMinutes = game.usedMinutesToday,
                    worstName = worst?.name,
                    worstMinutes = worst?.usedMinutes ?: 0,
                )
            }
            if (!reviveReady) {
                item {
                    BasicText(
                        "Revivir disponible en ${formatDuration(game.inventory.reviveAvailableAt - now)}",
                        style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
                    )
                }
            }
        }
    }
}

@Composable
private fun Summary(usedMinutes: Int, worstName: String?, worstMinutes: Int) {
    val c = Hp.colors
    val t = Hp.type
    androidx.compose.foundation.layout.Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        BasicText("Resumen de hoy", style = t.caption.copy(color = c.textSecondary))
        BasicText("${formatMinutes(usedMinutes)} de pantalla", style = t.bodyStrong.copy(color = c.textPrimary))
        if (worstName != null) {
            BasicText(
                "Más daño: $worstName (${formatMinutes(worstMinutes)})",
                style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
            )
        }
    }
}
