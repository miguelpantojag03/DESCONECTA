package com.desconecta.hp.mobile.ui.block

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarFrame
import com.desconecta.hp.shared.designsystem.component.healthbar.SegmentedBar
import com.desconecta.hp.shared.designsystem.icon.AvatarMood
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.formatMinutes

/** Estado del botón "Usar poción desde el reloj". */
enum class PotionButtonState { READY, NO_POTIONS, WATCH_OFFLINE, SENT }

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

// =====================================================================================
// Monstruo derrotado (app bloqueada)
// =====================================================================================

@Composable
fun MonsterDefeatedScreen(
    monster: Monster,
    untilRespawn: String,
    potionState: PotionButtonState,
    onHome: () -> Unit,
    onPotion: () -> Unit,
) {
    val c = Hp.colors
    val t = Hp.type
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = HpSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Box(contentAlignment = Alignment.Center) {
            Image(
                MonsterSprites.of(monster.art), null,
                Modifier.size(160.dp).alpha(0.6f),
                colorFilter = Grayscale,
            )
            FloatingZz(Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(HpSpacing.xl))
        Text(
            "¡DERROTADO!",
            style = t.gameTitle,
            color = c.textPrimary,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.height(HpSpacing.sm))
        Text("${monster.name} descansa… por hoy", style = t.headline, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(HpSpacing.md))
        Text(
            "Ya usaste ${monster.appLabel} ${formatMinutes(monster.usedMinutes)} hoy, tu límite. " +
                "Tú también te mereces un descanso.",
            style = t.body,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(HpSpacing.xl))
        HpCard(container = c.surface) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                Image(PixelIcons.Heart, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(c.regen))
                Column {
                    Text("Reaparece en", style = t.caption, color = c.textSecondary)
                    Text(untilRespawn, style = t.title, color = c.textPrimary)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Volver al inicio", onClick = onHome)
        Spacer(Modifier.height(HpSpacing.md))
        SecondaryButton(
            text = when (potionState) {
                PotionButtonState.READY -> "Usar poción desde el reloj"
                PotionButtonState.NO_POTIONS -> "Sin pociones (gánalas con rachas)"
                PotionButtonState.WATCH_OFFLINE -> "Reloj desconectado"
                PotionButtonState.SENT -> "¡Revisa tu reloj!"
            },
            onClick = onPotion,
            enabled = potionState == PotionButtonState.READY,
            icon = PixelIcons.Potion,
        )
        Text(
            when (potionState) {
                PotionButtonState.READY -> "Una poción despierta a ${monster.name} 10 min y te da +10 HP."
                PotionButtonState.WATCH_OFFLINE -> "Acerca tu reloj al teléfono para usar objetos."
                PotionButtonState.SENT -> "Confirma deslizando en el reloj."
                PotionButtonState.NO_POTIONS -> "Consigue una poción cada 3 días de racha."
            },
            style = t.caption,
            color = c.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = HpSpacing.sm, bottom = HpSpacing.lg)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

@Composable
private fun FloatingZz(modifier: Modifier = Modifier) {
    val y = if (Hp.motion.reduced) 0f else rememberInfiniteTransition(label = "zz").animateFloat(
        0f, -12f, infiniteRepeatable(tween(1400), RepeatMode.Reverse), label = "zzY",
    ).value
    Text(
        "Zz",
        style = Hp.type.gameTitle,
        color = Hp.colors.textPrimary,
        modifier = modifier.graphicsLayer { translationY = y * density },
    )
}

// =====================================================================================
// Modo estudio
// =====================================================================================

@Composable
fun StudyModeScreen(appLabel: String, remaining: String, onHome: () -> Unit) {
    val c = Hp.colors
    Column(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding().padding(HpSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HpSpacing.md, Alignment.CenterVertically),
    ) {
        Image(PixelIcons.Shield, null, Modifier.size(96.dp), colorFilter = ColorFilter.tint(c.xp))
        Text("MODO ESTUDIO", style = Hp.type.gameTitle, color = c.xp, modifier = Modifier.semantics { heading() })
        Text("$appLabel espera a que termines.", style = Hp.type.headline, color = c.textPrimary, textAlign = TextAlign.Center)
        Text("Quedan $remaining de concentración.", style = Hp.type.body, color = c.textSecondary)
        Spacer(Modifier.height(HpSpacing.xl))
        PrimaryButton("Volver al inicio", onClick = onHome, container = c.xp)
    }
}

// =====================================================================================
// GAME OVER (bloqueo total)
// =====================================================================================

@Composable
fun PhoneGameOverScreen(
    game: GameState,
    untilRespawn: String,
    countdown: String,
    reviveText: String,
    onEmergency: () -> Unit,
    onDialer: () -> Unit,
) {
    val c = Hp.colors
    val t = Hp.type
    val top = game.monsters.filter { it.usedMinutes > 0 }.sortedByDescending { it.usedMinutes }.take(3)

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = HpSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(HpSpacing.xxl))
            Avatar(game.player.avatar, AvatarMood.KO, size = 128.dp, accessories = game.player.equipped, name = game.player.name)
            Spacer(Modifier.height(HpSpacing.lg))
            Text(
                "GAME OVER",
                style = t.gameTitle,
                color = c.hpCritical,
                modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Assertive },
            )
            Spacer(Modifier.height(HpSpacing.sm))
            Text(
                "Hoy ya jugaste suficiente. Es una pausa, no un castigo.",
                style = t.headline.copy(fontSize = t.title.fontSize),
                color = c.textPrimary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(HpSpacing.xl))

            HpCard {
                Text("RESPAWN EN", style = t.gameLabel, color = c.textSecondary)
                Text(
                    countdown,
                    style = t.hpNumber.copy(fontSize = t.gameTitle.fontSize),
                    color = c.regen,
                    modifier = Modifier.semantics { contentDescription = "Respawn en $untilRespawn" },
                )
                Text("Tu vida se rellena a medianoche.", style = t.caption, color = c.textSecondary)
            }
            Spacer(Modifier.height(HpSpacing.md))
            HpCard {
                Text("RESUMEN DE HOY", style = t.gameLabel, color = c.textSecondary)
                Text("${formatMinutes(game.usedMinutesToday)} de pantalla", style = t.title, color = c.textPrimary)
                top.forEach { m ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                        Image(MonsterSprites.of(m.art), null, Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${m.name} · ${formatMinutes(m.usedMinutes)}", style = t.caption, color = c.textPrimary)
                            SegmentedBar(
                                HealthBarFrame.still(
                                    com.desconecta.hp.shared.model.Health(m.usedMinutes.coerceAtMost(game.maxHp), game.maxHp),
                                    c.hpCritical,
                                ),
                                game.maxHp,
                                height = 6.dp,
                                segmented = false,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(HpSpacing.md))
            HpCard(container = c.surface, border = c.outline) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                    Image(PixelIcons.Revive, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(c.hpCritical))
                    Text(reviveText, style = t.body, color = c.textPrimary)
                }
            }
            Spacer(Modifier.height(HpSpacing.lg))
        }

        // Barra fija: emergencias SIEMPRE visibles, nunca dentro del scroll.
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(HpSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
        ) {
            OutlinedButton(
                onClick = onEmergency,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = Hp.shapes.button,
            ) {
                Icon(Icons.Rounded.Call, contentDescription = null, tint = c.hpCritical)
                Spacer(Modifier.size(HpSpacing.sm))
                Text("Emergencia", style = t.label, color = c.textPrimary)
            }
            OutlinedButton(
                onClick = onDialer,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = Hp.shapes.button,
            ) {
                Icon(Icons.Rounded.Dialpad, contentDescription = null, tint = c.textPrimary)
                Spacer(Modifier.size(HpSpacing.sm))
                Text("Marcador", style = t.label, color = c.textPrimary)
            }
        }
    }
}
