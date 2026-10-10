package com.desconecta.hp.wear.ui.hud

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.StreakPill
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthAttacker
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarVariant
import com.desconecta.hp.shared.designsystem.component.toMood
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.wear.ui.common.ConnectionChip
import com.desconecta.hp.wear.ui.common.WatchSurface
import androidx.wear.compose.material3.ScreenScaffold

/**
 * HUD principal: el "marcador" del juego.
 *
 * @param connected false → aparece el indicador de "sin conexión" y la antigüedad de los datos.
 * @param ambient versión de bajo consumo: contornos, sin animaciones, sin avatar a color.
 */
@Composable
fun HudScreen(
    game: GameState,
    connected: Boolean,
    lastSyncText: String,
    ambient: Boolean = false,
) {
    val round = LocalConfiguration.current.isScreenRound
    val attacker = game.attacker?.let { HealthAttacker.of(it.art, it.name) }
    // Sin hora del sistema: el arco ocupa el bisel superior y aquí la "hora" relevante
    // es el tiempo de uso que queda. El resto de pantallas sí la muestran.
    ScreenScaffold(timeText = {}) {
    WatchSurface {
        if (round) {
            HealthBar(
                health = game.health,
                variant = HealthBarVariant.ARC,
                attacker = attacker,
                regenerating = game.regenerating,
                heartbeatHaptics = !ambient,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(top = 30.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TopChip(game, connected, lastSyncText, ambient)
                Spacer(Modifier.weight(1f))
                PlayerStrip(game, ambient)
            }
        } else {
            // Reloj cuadrado: barra recta arriba, el resto en columna.
            Column(
                Modifier.fillMaxSize().padding(HpSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if ((!connected || game.regenerating) && !ambient) TopChip(game, connected, lastSyncText, ambient) else Spacer(Modifier.height(1.dp))
                HealthBar(
                    health = game.health,
                    variant = HealthBarVariant.LINEAR,
                    attacker = attacker,
                    regenerating = game.regenerating,
                    heartbeatHaptics = !ambient,
                )
                PlayerStrip(game, ambient)
            }
        }
    }
    }
}

/**
 * Un solo aviso arriba, por prioridad: sin conexión > regenerando. El estado se dice con
 * palabras, no solo con las partículas (que no ve quien usa TalkBack o movimiento reducido).
 */
@Composable
private fun TopChip(game: GameState, connected: Boolean, lastSyncText: String, ambient: Boolean) {
    if (ambient) return
    when {
        !connected -> ConnectionChip(lastSyncText)
        game.regenerating -> RegenChip()
    }
}

@Composable
private fun RegenChip() {
    val c = Hp.colors
    Row(
        Modifier
            .background(c.regenContainer, Hp.shapes.button)
            .padding(horizontal = HpSpacing.sm, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Regenerando vida por no usar el teléfono" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(PixelIcons.Heart, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(c.regen))
        BasicText(" Regenerando", style = Hp.type.caption.copy(color = c.regen))
    }
}

/** Avatar pequeño flanqueado por nivel y racha, en el hueco inferior del arco. */
@Composable
private fun PlayerStrip(game: GameState, ambient: Boolean) {
    val c = Hp.colors
    val p = game.player
    Row(
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Nivel ${p.level}, ${p.title}. Racha de ${p.streakDays} días"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!ambient) Image(PixelIcons.Star, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(c.xp))
            BasicText(" ${p.level}", style = Hp.type.gameLabel.copy(color = if (ambient) c.textSecondary else c.xp))
        }
        // En ambient el avatar sería un bloque gris encendido: se omite (consumo y quemado).
        if (!ambient) Box(Modifier.size(32.dp)) {
            Avatar(p.avatar, game.health.status.toMood(), size = 32.dp, accessories = p.equipped)
        }
        StreakPill(p.streakDays)
    }
}
