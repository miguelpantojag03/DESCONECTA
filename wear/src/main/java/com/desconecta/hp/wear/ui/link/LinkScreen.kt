package com.desconecta.hp.wear.ui.link

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.wear.ui.ActionState

private const val CODE_LENGTH = 6

/**
 * Vincular el reloj por Wi‑Fi: el teléfono muestra un código de 6 dígitos y aquí se
 * escribe. Se reutiliza el teclado de 3×4 del PIN (mismo patrón en toda la app).
 */
@Composable
fun LinkScreen(action: ActionState, onSubmit: (String) -> Unit, onDone: () -> Unit) {
    val c = Hp.colors
    val listState = rememberScalingLazyListState()
    var code by remember { mutableStateOf("") }

    ScreenScaffold(scrollState = listState) { padding ->
        ScalingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (action is ActionState.Success) {
                item { Image(PixelIcons.Watch, null, Modifier.size(40.dp), colorFilter = ColorFilter.tint(c.hpHealthy)) }
                item {
                    BasicText(
                        action.message,
                        style = Hp.type.title.copy(color = c.textPrimary, textAlign = TextAlign.Center),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                item { Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Listo") } }
                return@ScalingLazyColumn
            }
            item { ListHeader { Text("Vincular por Wi‑Fi") } }
            item {
                BasicText(
                    "En el teléfono: Configuración → Nube y reloj → Vincular reloj.",
                    style = Hp.type.caption.copy(color = c.textSecondary, textAlign = TextAlign.Center),
                )
            }
            item {
                BasicText(
                    (0 until CODE_LENGTH).joinToString(" ") { code.getOrNull(it)?.toString() ?: "_" },
                    style = Hp.type.headline.copy(color = c.textPrimary),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Código: ${code.toList().joinToString(" ")}" },
                )
            }
            when (action) {
                is ActionState.Failure -> item {
                    BasicText(
                        action.message,
                        style = Hp.type.caption.copy(color = c.hpCritical, textAlign = TextAlign.Center),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                    )
                }
                ActionState.Sending -> item { BasicText("Vinculando…", style = Hp.type.caption.copy(color = c.textSecondary)) }
                else -> Unit
            }
            listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("⌫", "0", "OK")).forEach { row ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.xs)) {
                        row.forEach { key ->
                            val enabled = when (key) {
                                "OK" -> code.length == CODE_LENGTH && action != ActionState.Sending
                                "⌫" -> code.isNotEmpty()
                                else -> code.length < CODE_LENGTH
                            }
                            FilledTonalButton(
                                onClick = {
                                    when (key) {
                                        "OK" -> onSubmit(code)
                                        "⌫" -> code = code.dropLast(1)
                                        else -> code += key
                                    }
                                },
                                enabled = enabled,
                                modifier = Modifier
                                    .size(52.dp, 48.dp)
                                    .semantics {
                                        contentDescription = when (key) {
                                            "⌫" -> "Borrar"
                                            "OK" -> "Vincular"
                                            else -> key
                                        }
                                    },
                            ) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { Text(key) }
                            }
                        }
                    }
                }
            }
        }
    }
}
