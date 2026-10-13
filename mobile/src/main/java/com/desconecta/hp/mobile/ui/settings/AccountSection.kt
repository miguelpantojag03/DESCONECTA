package com.desconecta.hp.mobile.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.desconecta.hp.mobile.cloud.AuthState
import com.desconecta.hp.mobile.sync.SyncStatus
import com.desconecta.hp.mobile.ui.components.HpCard
import com.desconecta.hp.mobile.ui.components.InfoRow
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SecondaryButton
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.formatAgo

/**
 * Cuenta y sincronización. Dice siempre en palabras dónde están los datos (solo en el
 * teléfono / en la nube) y si hay algo pendiente: el usuario nunca duda de si su
 * progreso está a salvo (Nielsen nº 1).
 */
@Composable
fun AccountSection(
    auth: AuthState,
    sync: SyncStatus,
    pending: Int,
    now: Long,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onExport: () -> Unit,
    onSignOut: () -> Unit,
) {
    val c = Hp.colors
    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmSignIn by remember { mutableStateOf(false) }
    HpCard {
        when (auth) {
            AuthState.Unconfigured -> InfoRow(
                PixelIcons.Lock, c.textSecondary, "Solo en este dispositivo",
                "Esta versión no tiene la nube configurada. Todo se guarda en tu teléfono.",
            )
            AuthState.Loading -> InfoRow(PixelIcons.Crystal, c.textSecondary, "Comprobando tu cuenta…", null)
            AuthState.LocalOnly, AuthState.SignedOut -> {
                InfoRow(
                    PixelIcons.Lock, c.textSecondary, "Solo en este dispositivo",
                    "Tus datos no salen del teléfono. Crea una cuenta para tener copia en la nube.",
                )
                PrimaryButton("Crear cuenta o iniciar sesión", onClick = { confirmSignIn = true })
            }
            is AuthState.Expired -> {
                InfoRow(PixelIcons.HeartCracked, c.hpHurt, "Tu sesión caducó", "El juego sigue funcionando; los cambios se guardan y se subirán al volver a entrar.")
                PrimaryButton("Volver a iniciar sesión", onClick = onSignIn, container = c.hpHurt)
            }
            is AuthState.SignedIn -> {
                InfoRow(PixelIcons.Star, c.hpHealthy, auth.email ?: "Cuenta conectada", syncLine(sync, pending, now))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(HpSpacing.sm)) {
                    SecondaryButton("Sincronizar ahora", onClick = onSyncNow, modifier = Modifier.weight(1f))
                    SecondaryButton("Exportar mis datos", onClick = onExport, modifier = Modifier.weight(1f))
                }
                SecondaryButton("Cerrar sesión", onClick = { confirmSignOut = true }, icon = PixelIcons.Lock, iconTint = c.textSecondary)
            }
        }
    }
    // Prevención de errores (Nielsen nº 5): el progreso sin cuenta no pasa a la cuenta; se
    // avisa ANTES, con lo que se pierde y lo que se conserva, no después.
    if (confirmSignIn) {
        AlertDialog(
            onDismissRequest = { confirmSignIn = false },
            title = { Text("Tu progreso sin cuenta no se pasa") },
            text = {
                Text(
                    "Al entrar empezarás con el progreso de la cuenta: el nivel, la XP, la racha, " +
                        "los logros y los objetos de este teléfono se borran. Tus monstruos, límites y " +
                        "la vida de hoy se mantienen.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmSignIn = false; onSignIn() }) { Text("Entendido, continuar") } },
            dismissButton = { TextButton(onClick = { confirmSignIn = false }) { Text("Seguir sin cuenta") } },
            containerColor = c.surfaceElevated,
        )
    }
    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Seguirás jugando en modo \"solo en este dispositivo\". Lo pendiente de subir se conserva para cuando vuelvas a entrar.") },
            confirmButton = { TextButton(onClick = { confirmSignOut = false; onSignOut() }) { Text("Cerrar sesión") } },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Cancelar") } },
            containerColor = c.surfaceElevated,
        )
    }
}

private fun syncLine(sync: SyncStatus, pending: Int, now: Long): String = when {
    sync.error != null -> sync.error + if (pending > 0) " · $pending pendientes" else ""
    sync.running -> "Sincronizando…"
    pending > 0 -> "$pending cambios pendientes de subir"
    sync.lastSyncAt > 0 -> "Sincronizado ${formatAgo(now, sync.lastSyncAt)}"
    else -> "Sincronizado"
}

/** Indicador discreto para el panel: solo aparece si hay algo pendiente o un error. */
@Composable
fun SyncIndicator(sync: SyncStatus, pending: Int, signedIn: Boolean) {
    if (!signedIn || (pending == 0 && sync.error == null)) return
    val c = Hp.colors
    val error = sync.error != null
    Row(
        Modifier
            .background(if (error) c.hurtContainer else c.surfaceElevated, Hp.shapes.button)
            .padding(horizontal = HpSpacing.sm, vertical = HpSpacing.xs)
            .clearAndSetSemantics {
                contentDescription = if (error) "Error de sincronización: ${sync.error}" else "$pending cambios pendientes de sincronizar"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            if (error) PixelIcons.HeartCracked else PixelIcons.Crystal, null, Modifier.size(14.dp),
            colorFilter = ColorFilter.tint(if (error) c.hpHurt else c.textSecondary),
        )
        Spacer(Modifier.width(HpSpacing.xs))
        Text(if (error) "!" else "$pending", style = Hp.type.caption, color = if (error) c.hpHurt else c.textSecondary)
    }
}
