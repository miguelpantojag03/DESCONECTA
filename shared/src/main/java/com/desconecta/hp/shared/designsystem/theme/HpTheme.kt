package com.desconecta.hp.shared.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.desconecta.hp.shared.R
import com.desconecta.hp.shared.designsystem.color.HpColorSchemes
import com.desconecta.hp.shared.designsystem.color.HpColors
import com.desconecta.hp.shared.designsystem.haptics.HpHapticEvent
import com.desconecta.hp.shared.designsystem.haptics.HpHapticPlayer
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.motion.HpMotion
import com.desconecta.hp.shared.designsystem.motion.rememberSystemReducedMotion
import com.desconecta.hp.shared.designsystem.shape.HpShapes
import com.desconecta.hp.shared.designsystem.type.HpTypeScale
import com.desconecta.hp.shared.designsystem.type.HpTypography
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.HealthStatus
import com.desconecta.hp.shared.model.formatMinutes

enum class HpFormFactor { WATCH, PHONE }

val LocalHpFormFactor = staticCompositionLocalOf { HpFormFactor.WATCH }
val LocalHpColors = staticCompositionLocalOf { HpColorSchemes.WatchDark }
val LocalHpTypography = staticCompositionLocalOf { HpTypeScale.Watch }
val LocalHpShapes = staticCompositionLocalOf { HpShapes() }
val LocalHpMotion = staticCompositionLocalOf { HpMotion() }
val LocalHpHaptics = staticCompositionLocalOf<HpHapticPlayer?> { null }

/**
 * Tema base de HP, independiente de Material. Las apps lo envuelven con su propio
 * MaterialTheme (Wear M3 o M3) para que los componentes estándar también hereden la
 * paleta: ver `WearHpTheme` y `MobileHpTheme`.
 *
 * @param ambient Ambient Mode del reloj: paleta en grises y contornos.
 * @param highContrast ajuste de accesibilidad de la app.
 * @param reducedMotion `null` = seguir al sistema; true/false = preferencia del jugador.
 */
@Composable
fun HpTheme(
    formFactor: HpFormFactor,
    ambient: Boolean = false,
    highContrast: Boolean = false,
    reducedMotion: Boolean? = null,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val base = when {
        ambient -> HpColorSchemes.WatchAmbient
        formFactor == HpFormFactor.WATCH -> HpColorSchemes.WatchDark
        else -> HpColorSchemes.PhoneDark
    }
    val colors = if (highContrast && !ambient) HpColorSchemes.highContrast(base) else base
    val typography = if (formFactor == HpFormFactor.WATCH) HpTypeScale.Watch else HpTypeScale.Phone

    val systemReduced = rememberSystemReducedMotion()
    // En ambient no hay animaciones de ningún tipo (la pantalla se refresca 1 vez/min).
    val motion = HpMotion(reduced = ambient || (reducedMotion ?: systemReduced))

    val context = LocalContext.current
    val haptics = remember(context) { HpHapticPlayer(context.applicationContext) }
    SideEffect { haptics.enabled = hapticsEnabled }

    CompositionLocalProvider(
        LocalHpFormFactor provides formFactor,
        LocalHpColors provides colors,
        LocalHpTypography provides typography,
        LocalHpShapes provides HpShapes(),
        LocalHpMotion provides motion,
        LocalHpHaptics provides haptics,
        content = content,
    )
}

/** Acceso corto: `Hp.colors.hpCritical`, `Hp.type.hpNumber`, `Hp.motion.reduced`… */
object Hp {
    val formFactor: HpFormFactor
        @Composable @ReadOnlyComposable get() = LocalHpFormFactor.current
    val colors: HpColors
        @Composable @ReadOnlyComposable get() = LocalHpColors.current
    val type: HpTypography
        @Composable @ReadOnlyComposable get() = LocalHpTypography.current
    val shapes: HpShapes
        @Composable @ReadOnlyComposable get() = LocalHpShapes.current
    val motion: HpMotion
        @Composable @ReadOnlyComposable get() = LocalHpMotion.current
    val haptics: HpHapticPlayer?
        @Composable @ReadOnlyComposable get() = LocalHpHaptics.current
}

/**
 * Todo lo que define visualmente un estado de vida. Garantiza la regla de
 * accesibilidad "el color nunca es el único indicador": cada estado trae color,
 * ícono distinto, texto y patrón háptico propios.
 */
@Immutable
data class HealthStatusVisual(
    val color: Color,
    val container: Color,
    val icon: ImageVector,
    val label: String,
    val haptic: HpHapticEvent?,
)

@Composable
fun rememberStatusVisual(status: HealthStatus): HealthStatusVisual {
    val colors = Hp.colors
    val label = stringResource(
        when (status) {
            HealthStatus.HEALTHY -> R.string.hp_status_healthy
            HealthStatus.HURT -> R.string.hp_status_hurt
            HealthStatus.CRITICAL -> R.string.hp_status_critical
            HealthStatus.GAME_OVER -> R.string.hp_status_game_over
        },
    )
    return remember(status, colors, label) {
        HealthStatusVisual(
            color = colors.forStatus(status),
            container = colors.containerForStatus(status),
            icon = when (status) {
                HealthStatus.HEALTHY -> PixelIcons.Heart
                HealthStatus.HURT -> PixelIcons.HeartHalf
                HealthStatus.CRITICAL -> PixelIcons.HeartCracked
                HealthStatus.GAME_OVER -> PixelIcons.Skull
            },
            label = label,
            haptic = when (status) {
                HealthStatus.CRITICAL -> HpHapticEvent.CRITICAL_HEARTBEAT
                HealthStatus.GAME_OVER -> HpHapticEvent.GAME_OVER
                else -> null
            },
        )
    }
}

/** "Vida: 84 de 120, estado Saludable. Te quedan 1 h 24 min de uso" */
@Composable
fun healthContentDescription(health: Health): String {
    val visual = rememberStatusVisual(health.status)
    return stringResource(R.string.hp_cd_health, health.clamped, health.max, visual.label) +
        ". " + stringResource(R.string.hp_cd_health_time, formatMinutes(health.clamped))
}
