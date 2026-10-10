package com.desconecta.hp.wear.ui.inventory

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.haptics.HpHapticEvent
import com.desconecta.hp.shared.designsystem.theme.Hp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Pista de "desliza para confirmar". El pulgar (48 dp) arrastra el ícono del objeto
 * hasta el final. Si se suelta antes del 85 %, vuelve al inicio con un rebote:
 * así un roce accidental nunca gasta un objeto.
 *
 * Accesibilidad: con TalkBack el componente expone la acción "Confirmar".
 */
@Composable
fun SwipeToConfirm(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Hp.colors
    val haptics = Hp.haptics
    val motion = Hp.motion
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val thumb = 48.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(thumb + 8.dp)
            .clip(Hp.shapes.button)
            .background(c.surfaceElevated)
            .border(1.dp, c.outline, Hp.shapes.button)
            .alpha(if (enabled) 1f else 0.4f)
            .semantics {
                role = Role.Button
                contentDescription = label
                if (enabled) onClick(label = "Confirmar") { onConfirm(); true }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val maxPx = constraints.maxWidth - thumbPx - with(LocalDensity.current) { 8.dp.toPx() }
        val progress = if (maxPx > 0) offset.value / maxPx else 0f

        // Relleno que sigue al pulgar: refuerza la dirección del gesto.
        Box(
            Modifier
                .fillMaxHeight()
                .width(with(LocalDensity.current) { (offset.value + thumbPx).toDp() } + 4.dp)
                .background(accent.copy(alpha = 0.25f)),
        )
        BasicText(
            "$label  ›››",
            style = Hp.type.label.copy(color = c.textPrimary.copy(alpha = 1f - progress)),
            modifier = Modifier.align(Alignment.Center).padding(start = thumb + 12.dp),
        )
        Box(
            Modifier
                .padding(4.dp)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumb)
                .clip(Hp.shapes.button)
                .background(accent)
                .draggable(
                    enabled = enabled,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxPx)) }
                    },
                    onDragStopped = {
                        if (offset.value >= maxPx * 0.85f) {
                            offset.animateTo(maxPx, motion.micro())
                            haptics?.play(HpHapticEvent.CONFIRM)
                            onConfirm()
                        } else {
                            offset.animateTo(0f, motion.reward())
                        }
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(icon, null, Modifier.size(28.dp), colorFilter = ColorFilter.tint(c.onAccent))
        }
    }
}
