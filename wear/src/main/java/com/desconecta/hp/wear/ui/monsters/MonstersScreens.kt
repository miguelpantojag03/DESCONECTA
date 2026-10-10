package com.desconecta.hp.wear.ui.monsters

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.designsystem.component.HpStateMessage
import com.desconecta.hp.shared.designsystem.component.MonsterCard
import com.desconecta.hp.shared.designsystem.component.ScreenStateKind
import com.desconecta.hp.shared.designsystem.component.healthbar.HealthBarFrame
import com.desconecta.hp.shared.designsystem.component.healthbar.SegmentedBar
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.formatMinutes
import com.desconecta.hp.shared.model.formatDuration
import com.desconecta.hp.shared.model.millisUntilRespawn

/**
 * Lista de monstruos. Orden: el que ataca ahora → los que tienen menos margen → los
 * derrotados al final. Lo urgente arriba (ley de Hick: menos búsqueda).
 */
@Composable
fun MonstersScreen(game: GameState, now: Long, onOpen: (String) -> Unit) {
    val listState = rememberScalingLazyListState()
    val sorted = remember(game.monsters, game.activeAttacker) {
        game.monsters.sortedWith(
            compareBy<Monster> { it.packageName != game.activeAttacker }
                .thenBy { it.isDefeated }
                .thenBy { it.remainingMinutes },
        )
    }
    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxWidth()) {
            item {
                ListHeader { Text("Monstruos", modifier = Modifier.semantics { heading() }) }
            }
            if (sorted.isEmpty()) {
                item {
                    HpStateMessage(
                        ScreenStateKind.EMPTY,
                        title = "Aún no hay monstruos",
                        body = "Elige qué apps limitar desde tu teléfono.",
                    )
                }
            }
            items(sorted, key = { it.packageName }) { monster ->
                MonsterCard(
                    monster,
                    now = now,
                    attacking = monster.packageName == game.activeAttacker,
                    onClick = { onOpen(monster.packageName) },
                )
            }
        }
    }
}

/** Detalle: daño de hoy, límite ajustable con la corona, gráfica semanal y escudo. */
@Composable
fun MonsterDetailScreen(
    monster: Monster,
    shields: Int,
    now: Long,
    onLimitChange: (Int) -> Unit,
    onShield: () -> Unit,
) {
    val c = Hp.colors
    val t = Hp.type
    val listState = rememberScalingLazyListState()
    var limit by remember(monster.packageName) { mutableIntStateOf(monster.limitMinutes) }
    val shielded = monster.isShielded(now)

    fun change(delta: Int) {
        val next = (limit + delta).coerceIn(GameEngine.LIMIT_MIN, GameEngine.LIMIT_MAX)
        if (next != limit) {
            limit = next
            onLimitChange(next)
        }
    }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Image(MonsterSprites.of(monster.art), null, Modifier.size(48.dp))
            }
            item {
                BasicText(
                    monster.name,
                    style = t.headline.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                    modifier = Modifier.semantics { heading() },
                )
            }
            item { BasicText(monster.appLabel, style = t.caption.copy(color = c.textSecondary)) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth(0.8f)) {
                    SegmentedBar(
                        HealthBarFrame.still(monster.health, if (monster.isDefeated) c.textSecondary else c.forStatus(monster.health.status)),
                        monster.limitMinutes,
                        height = 8.dp,
                    )
                    BasicText(
                        when {
                            monster.isDefeated -> "Derrotado · vuelve en ${formatDuration(millisUntilRespawn(now))}"
                            else -> "Daño hoy: ${formatMinutes(monster.usedMinutes)}"
                        },
                        style = t.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
                    )
                }
            }
            item { LimitStepper(limit, onMinus = { change(-GameEngine.LIMIT_STEP) }, onPlus = { change(GameEngine.LIMIT_STEP) }) }
            item { WeekChart(monster.week, limit) }
            item {
                FilledTonalButton(
                    onClick = onShield,
                    enabled = shields > 0 && !shielded && !monster.isDefeated,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Image(PixelIcons.Shield, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(c.regen)) },
                    label = { Text(if (shielded) "Escudo activo" else "Escudo (x$shields)") },
                    secondaryLabel = {
                        Text(
                            when {
                                monster.isDefeated -> "Ya está derrotado"
                                shields == 0 -> "Sin escudos"
                                else -> "15 min sin daño"
                            },
                        )
                    },
                )
            }
        }
    }
}

/**
 * "− 30 min +" controlable con la corona (paso de 5 min) o con los botones.
 * La corona da precisión sin tapar la pantalla con el dedo.
 */
@Composable
private fun LimitStepper(limit: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = Hp.colors
    val focus = remember { FocusRequester() }
    var accumulated by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText("LÍMITE DIARIO", style = Hp.type.gameLabel.copy(color = c.textSecondary))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm),
            modifier = Modifier
                .onRotaryScrollEvent { e ->
                    // Cada ~"clic" de la corona (≈ 30 px) = 5 minutos.
                    accumulated += e.verticalScrollPixels.toInt()
                    while (accumulated >= ROTARY_STEP_PX) { onPlus(); accumulated -= ROTARY_STEP_PX }
                    while (accumulated <= -ROTARY_STEP_PX) { onMinus(); accumulated += ROTARY_STEP_PX }
                    true
                }
                .focusRequester(focus)
                .focusable(),
        ) {
            FilledTonalIconButton(onClick = onMinus, modifier = Modifier.size(48.dp)) {
                BasicText("−", style = Hp.type.headline.copy(color = c.textPrimary))
            }
            BasicText(
                formatMinutes(limit),
                style = Hp.type.title.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                modifier = Modifier
                    .width(76.dp)
                    .clearAndSetSemantics { contentDescription = "Límite: ${formatMinutes(limit)}. Gira la corona para ajustar" },
            )
            FilledTonalIconButton(onClick = onPlus, modifier = Modifier.size(48.dp)) {
                BasicText("+", style = Hp.type.headline.copy(color = c.textPrimary))
            }
        }
    }
}

private const val ROTARY_STEP_PX = 30

/** 7 barras (L–D) con la línea del límite: de un vistazo, qué días se pasó. */
@Composable
private fun WeekChart(week: List<Int>, limit: Int) {
    val c = Hp.colors
    val values = (List((7 - week.size).coerceAtLeast(0)) { 0 } + week).takeLast(7)
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(limit).coerceAtLeast(1)
    val over = values.count { it > limit }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText("ÚLTIMOS 7 DÍAS", style = Hp.type.gameLabel.copy(color = c.textSecondary))
        Canvas(
            Modifier
                .fillMaxWidth(0.7f)
                .height(48.dp)
                .clearAndSetSemantics {
                    contentDescription = "Uso de los últimos 7 días: ${values.joinToString { "$it min" }}. " +
                        "Superó el límite $over días"
                },
        ) {
            val gap = 4.dp.toPx()
            val w = (size.width - gap * 6) / 7
            values.forEachIndexed { i, v ->
                val h = size.height * v / max
                val today = i == 6
                drawRect(
                    color = when {
                        v > limit -> c.hpCritical
                        today -> c.textPrimary
                        else -> c.textSecondary
                    },
                    topLeft = Offset(i * (w + gap), size.height - h),
                    size = Size(w, h),
                )
            }
            val y = size.height - size.height * limit / max
            drawLine(
                c.hpHurt, Offset(0f, y), Offset(size.width, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
            )
        }
        BasicText(
            if (over == 0) "Sin excesos esta semana" else "Superado $over de 7 días",
            style = Hp.type.caption.copy(color = if (over == 0) c.hpHealthy else c.hpHurt),
        )
    }
}
