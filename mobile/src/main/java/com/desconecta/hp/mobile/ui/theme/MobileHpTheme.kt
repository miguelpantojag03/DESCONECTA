package com.desconecta.hp.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.designsystem.theme.HpTheme

/**
 * Tema del teléfono: tokens de HP + Material 3 (solo modo oscuro, por coherencia con el
 * reloj y porque la app se consulta a menudo de noche, cerca del "respawn").
 */
@Composable
fun MobileHpTheme(
    highContrast: Boolean = false,
    reducedMotion: Boolean? = null,
    hapticsEnabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    HpTheme(
        formFactor = HpFormFactor.PHONE,
        highContrast = highContrast,
        reducedMotion = reducedMotion,
        hapticsEnabled = hapticsEnabled,
    ) {
        val c = Hp.colors
        val t = Hp.type
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = c.hpHealthy,
                onPrimary = c.onAccent,
                primaryContainer = c.healthyContainer,
                onPrimaryContainer = c.hpHealthy,
                secondary = c.regen,
                onSecondary = c.onAccent,
                secondaryContainer = c.regenContainer,
                onSecondaryContainer = c.regen,
                tertiary = c.xp,
                onTertiary = c.onAccent,
                tertiaryContainer = c.xpContainer,
                onTertiaryContainer = c.xp,
                background = c.background,
                onBackground = c.textPrimary,
                surface = c.background,
                onSurface = c.textPrimary,
                surfaceVariant = c.surfaceElevated,
                onSurfaceVariant = c.textSecondary,
                surfaceContainerLowest = c.background,
                surfaceContainerLow = c.surface,
                surfaceContainer = c.surface,
                surfaceContainerHigh = c.surfaceElevated,
                surfaceContainerHighest = c.surfaceElevated,
                outline = c.outlineStrong,
                outlineVariant = c.outline,
                error = c.hpCritical,
                onError = c.onAccent,
                errorContainer = c.criticalContainer,
                onErrorContainer = c.hpCritical,
            ),
            typography = Typography(
                headlineMedium = t.headline,
                titleLarge = t.headline,
                titleMedium = t.title,
                bodyLarge = t.body,
                bodyMedium = t.body,
                bodySmall = t.caption,
                labelLarge = t.label,
                labelMedium = t.label,
                labelSmall = t.caption,
            ),
            shapes = Shapes(
                extraSmall = RoundedCornerShape(4.dp),
                small = RoundedCornerShape(8.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp),
                extraLarge = RoundedCornerShape(24.dp),
            ),
            content = content,
        )
    }
}
