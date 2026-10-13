package com.desconecta.hp.mobile.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.desconecta.hp.mobile.data.CloudStatus
import com.desconecta.hp.mobile.ui.LinkCodeState
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.InfoRow
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.formatAgo
import com.desconecta.hp.shared.model.formatCountdown

/**
 * Nube y reloj: estado del respaldo en Supabase y vinculación del reloj por Wi‑Fi con un
 * código de 6 dígitos (el reloj no tiene teclado: 6 números es lo mínimo seguro y fácil
 * de copiar con el teclado de 3×4).
 */
@Composable
fun CloudSection(
    status: CloudStatus,
    linkCode: LinkCodeState,
    now: Long,
    onCreateCode: () -> Unit,
    onDismissCode: () -> Unit,
    onUnlink: () -> Unit,
) {
    val c = Hp.colors
    var confirmUnlink by remember { mutableStateOf(false) }
    val watchActive = status.watchSeenAt > 0 && now - status.watchSeenAt < 150_000

    HpCard {
        when {
            !status.configured -> InfoRow(
                PixelIcons.Lock, c.textSecondary,
                title = "Nube no configurada",
                subtitle = "Añade supabase.url y supabase.key en local.properties y vuelve a compilar.",
            )
            status.error != null -> InfoRow(PixelIcons.HeartCracked, c.hpHurt, "Sin conexión con la nube", "Tus datos se guardan en el teléfono y se suben al volver la red.")
            else -> InfoRow(
                PixelIcons.Star, c.hpHealthy,
                title = "Respaldo activo",
                subtitle = if (status.lastSyncAt == 0L) "Preparando…" else "Última copia: ${formatAgo(now, status.lastSyncAt)}",
            )
        }
        if (status.configured) {
            InfoRow(
                PixelIcons.Watch, if (watchActive) c.hpHealthy else c.textSecondary,
                title = if (watchActive) "Reloj conectado por Wi‑Fi" else "Ningún reloj por Wi‑Fi ahora",
                subtitle = if (watchActive) "Visto ${formatAgo(now, status.watchSeenAt)}" else "Úsalo si tu reloj no está emparejado por Bluetooth.",
            )
            PrimaryButton("Vincular reloj por Wi‑Fi", onClick = onCreateCode, icon = PixelIcons.Watch)
            SecondaryButton("Desvincular relojes", onClick = { confirmUnlink = true })
        }
    }

    when (linkCode) {
        LinkCodeState.Idle -> Unit
        else -> AlertDialog(
            onDismissRequest = onDismissCode,
            title = { Text("Vincular reloj") },
            text = {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HpSpacing.md),
                ) {
                    when (linkCode) {
                        LinkCodeState.Loading -> CircularProgressIndicator(color = c.hpHealthy)
                        is LinkCodeState.Error -> Text(linkCode.message, color = c.hpCritical, textAlign = TextAlign.Center)
                        is LinkCodeState.Ready -> {
                            val left = linkCode.expiresAt - now
                            Text("En el reloj: Vincular con código", style = Hp.type.body, textAlign = TextAlign.Center)
                            // El código, grande y en pixel: se copia de un vistazo.
                            Text(
                                linkCode.code.chunked(3).joinToString(" "),
                                style = Hp.type.hpNumber.copy(fontSize = Hp.type.gameTitle.fontSize),
                                color = if (left > 0) c.hpHealthy else c.textSecondary,
                                modifier = Modifier.semantics { contentDescription = "Código ${linkCode.code.toList().joinToString(" ")}" },
                            )
                            Text(
                                if (left > 0) "Caduca en ${formatCountdown(left).removePrefix("0:")}" else "Caducado: genera otro",
                                style = Hp.type.caption,
                                color = c.textSecondary,
                            )
                        }
                        LinkCodeState.Idle -> Unit
                    }
                }
            },
            confirmButton = {
                if (linkCode is LinkCodeState.Error || (linkCode is LinkCodeState.Ready && linkCode.expiresAt <= now)) {
                    TextButton(onClick = onCreateCode) { Text("Generar otro") }
                } else {
                    TextButton(onClick = onDismissCode) { Text("Listo") }
                }
            },
            dismissButton = { TextButton(onClick = onDismissCode) { Text("Cerrar") } },
            containerColor = c.surfaceElevated,
        )
    }

    if (confirmUnlink) {
        AlertDialog(
            onDismissRequest = { confirmUnlink = false },
            title = { Text("¿Desvincular relojes?") },
            text = { Text("Los relojes vinculados por Wi‑Fi dejarán de recibir tu partida. El emparejamiento por Bluetooth no cambia.") },
            confirmButton = { TextButton(onClick = { confirmUnlink = false; onUnlink() }) { Text("Desvincular", color = c.hpCritical) } },
            dismissButton = { TextButton(onClick = { confirmUnlink = false }) { Text("Cancelar") } },
            containerColor = c.surfaceElevated,
        )
    }
}
