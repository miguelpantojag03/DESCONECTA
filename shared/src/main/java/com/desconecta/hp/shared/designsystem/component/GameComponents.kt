package com.desconecta.hp.shared.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarFrame
import com.desconecta.hp.shared.designsystem.component.healthbar.SegmentedBar
import com.desconecta.hp.shared.designsystem.component.healthbar.rememberHealthBarFrame
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HealthBarDimens
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.ItemType
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.Progression
import com.desconecta.hp.shared.model.formatMinutes

// =====================================================================================
// XpBar
// =====================================================================================

/**
 * Barra de experiencia: morada (color exclusivo de XP) y SIN segmentos, para que nunca
 * se confunda con la barra de vida (Gestalt: semejanza → lo distinto se ve distinto).
 */
@Composable
fun XpBar(totalXp: Int, modifier: Modifier = Modifier, showLabels: Boolean = true) {
    val colors = Hp.colors
    val level = Progression.levelFor(totalXp)
    val progress by animateFloatAsState(Progression.progressInLevel(totalXp), Hp.motion.reward(), label = "xp")
    val toNext = Progression.xpToNextLevel(totalXp)
    val description = "Nivel $level. Faltan $toNext XP para el siguiente nivel"

    Column(modifier.clearAndSetSemantics { contentDescription = description }) {
        if (showLabels) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                BasicText("NV $level", style = Hp.type.gameLabel.copy(color = colors.xp))
                Spacer(Modifier.weight(1f))
                BasicText(
                    if (level >= Progression.MAX_LEVEL) "MAX" else "$toNext XP",
                    style = Hp.type.caption.copy(color = colors.textSecondary),
                )
            }
            Spacer(Modifier.height(HpSpacing.xs))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(Hp.shapes.bar),
        ) {
            drawRect(colors.hpTrack)
            if (colors.isAmbient) {
                drawRect(colors.xp, size = Size(size.width * progress, size.height), style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            } else {
                drawRect(colors.xp, size = Size(size.width * progress, size.height))
                drawRect(Color.White.copy(alpha = 0.2f), size = Size(size.width * progress, size.height * 0.25f))
            }
        }
    }
}

// =====================================================================================
// ItemSlot
// =====================================================================================

fun ItemType.icon(): ImageVector = when (this) {
    ItemType.POTION -> PixelIcons.Potion
    ItemType.SHIELD -> PixelIcons.Shield
    ItemType.REVIVE -> PixelIcons.Revive
}

fun ItemType.label(): String = when (this) {
    ItemType.POTION -> "Poción"
    ItemType.SHIELD -> "Escudo"
    ItemType.REVIVE -> "Revivir"
}

fun ItemType.effect(): String = when (this) {
    ItemType.POTION -> "+10 HP"
    ItemType.SHIELD -> "15 min sin daño"
    ItemType.REVIVE -> "Sal del Game Over con 15 HP"
}

/**
 * Ranura de inventario estilo RPG: marco pixelado, ícono y contador "x2".
 * Vacía = ícono en contorno + contador 0 (se ve que existe pero no hay).
 */
@Composable
fun ItemSlot(
    type: ItemType,
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = if (Hp.formFactor == HpFormFactor.WATCH) 56.dp else 64.dp,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = Hp.colors
    val available = count > 0
    val accent = when (type) {
        ItemType.POTION -> colors.regen
        ItemType.SHIELD -> colors.regen
        ItemType.REVIVE -> colors.hpCritical
    }
    val description = "${type.label()}, ${if (available) "$count disponible${if (count > 1) "s" else ""}" else "no disponible"}. ${type.effect()}"

    Box(
        modifier
            .size(size)
            .clip(Hp.shapes.slot)
            .background(if (available) colors.surfaceElevated else colors.surface)
            .border(if (selected) 3.dp else 2.dp, if (selected) accent else colors.outline, Hp.shapes.slot)
            .then(if (onClick != null) Modifier.clickable(enabled = available, role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) role = Role.Button
                if (!available) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            type.icon(),
            contentDescription = null,
            modifier = Modifier.size(size / 2).alpha(if (available) 1f else 0.35f),
            colorFilter = ColorFilter.tint(if (available) accent else colors.textSecondary),
        )
        BasicText(
            "x$count",
            style = Hp.type.gameLabel.copy(color = if (available) colors.textPrimary else colors.textSecondary),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = HpSpacing.xs, bottom = 2.dp),
        )
    }
}

// =====================================================================================
// AchievementBadge
// =====================================================================================

/**
 * Medalla. Desbloqueada: dorada y a color. Bloqueada: silueta en contorno, pero con el
 * título y la descripción visibles — saber qué se puede conseguir motiva (metas claras),
 * y esconderlo solo generaría frustración.
 */
@Composable
fun AchievementBadge(
    id: AchievementId,
    unlocked: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val colors = Hp.colors
    val description = "${id.title}. ${id.description}. ${if (unlocked) "Desbloqueado" else "Bloqueado"}"
    Column(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(if (compact) 48.dp else 64.dp)
                .clip(Hp.shapes.badge)
                .background(if (unlocked) colors.goldContainer else colors.surface)
                .border(2.dp, if (unlocked) colors.gold else colors.outline, Hp.shapes.badge),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                if (unlocked) PixelIcons.Medal else PixelIcons.MedalLocked,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                colorFilter = ColorFilter.tint(if (unlocked) colors.gold else colors.textSecondary),
            )
        }
        if (!compact) {
            Spacer(Modifier.height(HpSpacing.xs))
            BasicText(
                id.title,
                style = Hp.type.caption.copy(
                    color = if (unlocked) colors.textPrimary else colors.textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// =====================================================================================
// MonsterCard
// =====================================================================================

private val Grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Tarjeta de un monstruo: ilustración, nombre, app real, mini barra de su límite y
 * tiempo usado. Derrotado → sprite en gris "durmiendo" (Zz) y texto de reaparición.
 * Con escudo → ícono de escudo y borde cian.
 */
@Composable
fun MonsterCard(
    monster: Monster,
    modifier: Modifier = Modifier,
    now: Long = System.currentTimeMillis(),
    attacking: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = Hp.colors
    val watch = Hp.formFactor == HpFormFactor.WATCH
    val shielded = monster.isShielded(now)
    val defeated = monster.isDefeated
    val frame: HealthBarFrame = rememberHealthBarFrame(monster.health)
    val spriteSize = if (watch) 32.dp else 48.dp
    val statusText = when {
        defeated -> "Derrotado · vuelve mañana"
        shielded -> "Escudo activo"
        attacking -> "¡Atacando!"
        else -> "${formatMinutes(monster.usedMinutes)} de ${formatMinutes(monster.limitMinutes)}"
    }
    val description = "${monster.name}, ${monster.appLabel}. $statusText. " +
        "Le quedan ${formatMinutes(monster.remainingMinutes)}."

    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = HpSpacing.touchTarget)
            .clip(Hp.shapes.card)
            .background(if (attacking && !defeated) colors.criticalContainer else colors.surface)
            .border(
                width = if (shielded || attacking) 2.dp else 1.dp,
                color = when {
                    shielded -> colors.regen
                    attacking && !defeated -> colors.hpCritical
                    else -> colors.outline
                },
                shape = Hp.shapes.card,
            )
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = description
                if (onClick != null) {
                    role = Role.Button
                    onClick(label = "Ver detalle") { onClick(); true }
                }
            }
            .padding(horizontal = HpSpacing.md, vertical = if (watch) HpSpacing.sm else HpSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        Box(Modifier.size(spriteSize)) {
            Image(
                if (colors.isAmbient) MonsterSprites.ambientOf(monster.art) else MonsterSprites.of(monster.art),
                contentDescription = null,
                modifier = Modifier.size(spriteSize).alpha(if (defeated) 0.55f else 1f),
                colorFilter = when {
                    colors.isAmbient -> ColorFilter.tint(colors.textSecondary)
                    defeated -> Grayscale
                    else -> null
                },
            )
            if (defeated) {
                BasicText("Zz", style = Hp.type.gameLabel.copy(color = colors.textPrimary), modifier = Modifier.align(Alignment.TopEnd))
            } else if (shielded) {
                Image(
                    PixelIcons.Shield, null,
                    Modifier.size(16.dp).align(Alignment.BottomEnd),
                    colorFilter = ColorFilter.tint(colors.regen),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
            BasicText(
                monster.name,
                style = Hp.type.title.copy(color = colors.textPrimary),
                maxLines = if (watch) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!watch) {
                BasicText(monster.appLabel, style = Hp.type.caption.copy(color = colors.textSecondary), maxLines = 1)
            }
            SegmentedBar(frame, monster.limitMinutes.coerceAtLeast(1), height = HealthBarDimens.linearHeightMini)
            BasicText(
                statusText,
                style = Hp.type.caption.copy(
                    color = when {
                        attacking && !defeated -> colors.hpCritical
                        shielded -> colors.regen
                        else -> colors.textSecondary
                    },
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// =====================================================================================
// Píldora de racha
// =====================================================================================

/** "🔥 12" en pixel: racha de días sin Game Over. */
@Composable
fun StreakPill(days: Int, modifier: Modifier = Modifier) {
    val colors = Hp.colors
    Row(
        modifier.clearAndSetSemantics { contentDescription = "Racha de $days día${if (days == 1) "" else "s"} sin Game Over" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            PixelIcons.Flame, null, Modifier.size(16.dp),
            colorFilter = ColorFilter.tint(if (days > 0) colors.hpHurt else colors.textSecondary),
        )
        Spacer(Modifier.width(HpSpacing.xs))
        BasicText("$days", style = Hp.type.gameLabel.copy(color = if (days > 0) colors.textPrimary else colors.textSecondary))
    }
}

/** Línea divisoria pixelada (patrón de guiones de 4 dp). */
@Composable
fun PixelDivider(modifier: Modifier = Modifier, color: Color = Hp.colors.outline) {
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val dash = 4.dp.toPx()
        var x = 0f
        while (x < size.width) {
            drawRect(color, Offset(x, 0f), Size(dash, size.height))
            x += dash * 2
        }
    }
}
