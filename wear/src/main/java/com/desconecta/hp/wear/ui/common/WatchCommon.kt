package com.desconecta.hp.wear.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Text
import com.desconecta.hp.shared.designsystem.component.HpSkeleton
import com.desconecta.hp.shared.designsystem.component.HpStateMessage
import com.desconecta.hp.shared.designsystem.component.ScreenStateKind
import com.desconecta.hp.shared.designsystem.component.SkeletonBlock
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp

/** Fondo de pantalla del reloj (negro puro) + contenido centrado. */
@Composable
fun WatchSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Hp.colors.background), contentAlignment = Alignment.Center) {
        content()
    }
}

/** Estado de carga con skeleton pixelado en forma de arco + bloques. */
@Composable
fun WatchLoading() = WatchSurface {
    Column(
        Modifier.padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
    ) {
        SkeletonBlock(Modifier.size(32.dp))
        SkeletonBlock(Modifier.size(width = 72.dp, height = 40.dp), index = 1)
        HpSkeleton(lines = 2, lineHeight = 12.dp)
    }
}

/** Primer uso: el reloj aún no recibió ninguna partida del teléfono. */
@Composable
fun WatchNoGame(connected: Boolean, onRetry: () -> Unit) = WatchSurface {
    HpStateMessage(
        kind = if (connected) ScreenStateKind.EMPTY else ScreenStateKind.OFFLINE,
        title = if (connected) "Aún no hay partida" else "Sin teléfono",
        body = if (connected) "Abre HP en tu teléfono para crear tu personaje."
        else "Conecta tu teléfono para empezar.",
        action = { Button(onClick = onRetry) { Text("Reintentar") } },
    )
}

/**
 * Indicador de "teléfono desconectado": ícono + texto (no solo color). Se muestra
 * junto con la antigüedad de los datos para que el jugador sepa cuánto confiar en ellos.
 */
@Composable
fun ConnectionChip(lastSyncText: String, modifier: Modifier = Modifier) {
    val c = Hp.colors
    Row(
        modifier
            .background(c.surfaceElevated, Hp.shapes.button)
            .padding(horizontal = HpSpacing.sm, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Teléfono desconectado. Datos de $lastSyncText" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(PixelIcons.Watch, null, Modifier.size(14.dp), colorFilter = ColorFilter.tint(c.hpHurt))
        Spacer(Modifier.width(HpSpacing.xs))
        BasicText("Sin conexión", style = Hp.type.caption.copy(color = c.textPrimary))
    }
}
