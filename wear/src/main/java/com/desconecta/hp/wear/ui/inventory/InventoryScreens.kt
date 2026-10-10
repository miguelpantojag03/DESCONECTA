package com.desconecta.hp.wear.ui.inventory

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.designsystem.component.ItemSlot
import com.desconecta.hp.shared.designsystem.component.effect
import com.desconecta.hp.shared.designsystem.component.icon
import com.desconecta.hp.shared.designsystem.component.label
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Inventory
import com.desconecta.hp.shared.model.ItemType
import com.desconecta.hp.wear.ui.ActionState
import com.desconecta.hp.shared.model.formatDuration

// =====================================================================================
// Inventario
// =====================================================================================

@Composable
fun InventoryScreen(game: GameState, now: Long, onSelect: (ItemType) -> Unit) {
    val listState = rememberScalingLazyListState()
    val inv = game.inventory
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("Inventario", modifier = Modifier.semantics { heading() }) } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                    ItemType.entries.forEach { type ->
                        ItemSlot(type, inv.count(type, now), onClick = { onSelect(type) })
                    }
                }
            }
            items(ItemType.entries.toList()) { type ->
                ItemRow(type, game, now, onClick = { onSelect(type) })
            }
        }
    }
}

@Composable
private fun ItemRow(type: ItemType, game: GameState, now: Long, onClick: () -> Unit) {
    val c = Hp.colors
    val count = game.inventory.count(type, now)
    val note = when (type) {
        ItemType.POTION -> if (count == 0) "Gana pociones con rachas de 3 días" else "Máx. ${Inventory.MAX_POTIONS}"
        ItemType.SHIELD -> if (count == 0) "Gana escudos con rachas de 7 días" else "Para trabajo o estudio"
        ItemType.REVIVE -> when {
            !game.inventory.reviveReady(now) -> "Disponible en ${formatDuration(game.inventory.reviveAvailableAt - now)}"
            game.phase != GamePhase.GAME_OVER -> "Solo en Game Over · requiere PIN"
            else -> "Requiere PIN"
        }
    }
    FilledTonalButton(
        onClick = onClick,
        enabled = count > 0,
        modifier = Modifier.fillMaxWidth(),
        icon = {
            Image(type.icon(), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(if (type == ItemType.REVIVE) c.hpCritical else c.regen))
        },
        label = { Text("${type.label()} · ${type.effect()}") },
        secondaryLabel = { Text(note) },
    )
}

// =====================================================================================
// Elegir monstruo para el escudo
// =====================================================================================

@Composable
fun ShieldTargetScreen(game: GameState, now: Long, onPick: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val targets = game.monsters.filter { !it.isDefeated && !it.isShielded(now) }
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
            item { ListHeader { Text("¿Qué app proteges?") } }
            if (targets.isEmpty()) {
                item {
                    BasicText(
                        "No hay monstruos que proteger ahora.",
                        style = Hp.type.body.copy(color = Hp.colors.textSecondary, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(HpSpacing.md),
                    )
                }
            }
            items(targets, key = { it.packageName }) { m ->
                Button(
                    onClick = { onPick(m.packageName) },
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Image(MonsterSprites.of(m.art), null, Modifier.size(24.dp)) },
                    label = { Text(m.appLabel) },
                    secondaryLabel = { Text(m.name) },
                )
            }
        }
    }
}

// =====================================================================================
// Confirmación de uso (deslizar)
// =====================================================================================

@Composable
fun ConfirmItemScreen(
    type: ItemType,
    game: GameState,
    targetName: String?,
    action: ActionState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val c = Hp.colors
    val t = Hp.type
    val listState = rememberScalingLazyListState()
    val accent = if (type == ItemType.REVIVE) c.hpCritical else c.regen
    val preview = when (type) {
        ItemType.POTION -> {
            val after = (game.currentHp + Inventory.POTION_HP).coerceAtMost(game.maxHp)
            if (targetName != null) "Despierta $targetName 10 min · ${game.currentHp} → $after HP"
            else "${game.currentHp} → $after HP"
        }
        ItemType.SHIELD -> "${targetName ?: "La app"} no hará daño durante 15 min"
        ItemType.REVIVE -> "Vuelves con ${Inventory.REVIVE_HP} HP"
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (action) {
                is ActionState.Success -> {
                    item { Image(PixelIcons.Star, null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(c.gold)) }
                    item {
                        BasicText(
                            action.message,
                            style = t.title.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                    }
                    item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Listo") } }
                }

                is ActionState.Failure -> {
                    item { Image(PixelIcons.HeartCracked, null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(c.hpCritical)) }
                    item {
                        BasicText(
                            action.message,
                            style = t.body.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                        )
                    }
                    if (action.retryable) item { Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) { Text("Reintentar") } }
                    item { OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Volver") } }
                }

                else -> {
                    item { Image(type.icon(), null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(accent)) }
                    item {
                        BasicText(
                            "¿Usar ${type.label().lowercase()}?",
                            style = t.headline.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                    item { BasicText(preview, style = t.bodyStrong.copy(color = accent, textAlign = TextAlign.Center)) }
                    item { Spacer(Modifier.height(HpSpacing.xs)) }
                    item {
                        if (action == ActionState.Sending) {
                            BasicText(
                                "Enviando al teléfono…",
                                style = t.caption.copy(color = c.textSecondary),
                                modifier = Modifier
                                    .height(56.dp)
                                    .clearAndSetSemantics { contentDescription = "Enviando al teléfono" },
                            )
                        } else {
                            SwipeToConfirm(
                                label = "Desliza para usar",
                                icon = type.icon(),
                                accent = accent,
                                enabled = true,
                                onConfirm = onConfirm,
                            )
                        }
                    }
                    item { OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancelar") } }
                }
            }
        }
    }
}

// =====================================================================================
// PIN para revivir
// =====================================================================================

/**
 * Teclado numérico de 3×4 con teclas de 48 dp. Se eligió el teclado clásico (y no
 * ruedas) porque es un patrón que todos conocen (Jakob: los usuarios esperan que tu
 * interfaz funcione como las que ya usan). Los dígitos se ocultan con "●".
 */
@Composable
fun PinScreen(action: ActionState, onSubmit: (String) -> Unit, onDone: () -> Unit) {
    val c = Hp.colors
    val listState = rememberScalingLazyListState()
    var pin by remember { mutableStateOf("") }

    if (action is ActionState.Success || (action is ActionState.Failure && !action.retryable)) {
        ConfirmItemScreen(ItemType.REVIVE, GameState(""), null, action, onConfirm = {}, onCancel = onDone, onDone = onDone)
        return
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { ListHeader { Text("PIN para revivir") } }
            item {
                BasicText(
                    (0 until 4).joinToString(" ") { if (it < pin.length) "●" else "○" },
                    style = Hp.type.headline.copy(color = c.textPrimary),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "${pin.length} de 4 dígitos" },
                )
            }
            if (action is ActionState.Failure) {
                item {
                    BasicText(
                        action.message,
                        style = Hp.type.caption.copy(color = c.hpCritical),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
            }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK")).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
                        row.forEach { key ->
                            val enabled = when (key) {
                                "OK" -> pin.length == 4 && action != ActionState.Sending
                                "⌫" -> pin.isNotEmpty()
                                else -> pin.length < 4
                            }
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "OK" -> onSubmit(pin)
                                        "⌫" -> pin = pin.dropLast(1)
                                        else -> pin += key
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier
                                    .size(52.dp, 48.dp)
                                    .semantics {
                                        contentDescription = when (key) {
                                            "⌫" -> "Borrar"
                                            "OK" -> "Confirmar PIN"
                                            else -> key
                                        }
                                    },
                            ) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(key)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
