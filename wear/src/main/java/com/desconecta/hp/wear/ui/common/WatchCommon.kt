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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Milisegundos hasta la próxima medianoche local (el "respawn"). */
fun millisUntilRespawn(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val midnight = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone).plusDays(1).atStartOfDay(zone)
    return midnight.toInstant().toEpochMilli() - now
}

/** "5:20:12" — cuenta regresiva con segundos para que se perciba movimiento. */
fun formatCountdown(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/** "5 h 20 min" — versión legible (para TalkBack y textos). */
fun formatDuration(millis: Long): String {
    val minutes = (millis / 60_000).coerceAtLeast(0)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** "hace 5 min" para la última sincronización. */
fun formatAgo(now: Long, then: Long): String {
    val min = ((now - then) / 60_000).coerceAtLeast(0)
    return when {
        then == 0L -> "nunca"
        min < 1 -> "hace un momento"
        min < 60 -> "hace $min min"
        else -> "hace ${min / 60} h"
    }
}

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
