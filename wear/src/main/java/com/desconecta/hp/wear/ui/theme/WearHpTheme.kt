package com.desconecta.hp.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Typography
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.designsystem.theme.HpTheme

/**
 * Tema del reloj: tokens de HP + Wear Material 3. Así los componentes estándar de Wear
 * (botones, listas, diálogos, indicadores de scroll) usan automáticamente nuestra
 * paleta y tipografía, y los componentes propios (HealthBar…) usan `Hp.*`.
 */
@Composable
fun WearHpTheme(
    ambient: Boolean = false,
    highContrast: Boolean = false,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    HpTheme(
        formFactor = HpFormFactor.WATCH,
        ambient = ambient,
        highContrast = highContrast,
        reducedMotion = reducedMotion,
    ) {
        val c = Hp.colors
        val t = Hp.type
        MaterialTheme(
            colorScheme = ColorScheme(
                // Acción principal = verde de vida: "seguir sano" es la acción positiva.
                primary = c.hpHealthy,
                onPrimary = c.onAccent,
                primaryContainer = c.healthyContainer,
                onPrimaryContainer = c.hpHealthy,
                // Secundario = cian de pociones/regeneración (acciones de objeto).
                secondary = c.regen,
                onSecondary = c.onAccent,
                secondaryContainer = c.regenContainer,
                onSecondaryContainer = c.regen,
                tertiary = c.xp,
                onTertiary = c.onAccent,
                tertiaryContainer = c.xpContainer,
                onTertiaryContainer = c.xp,
                surfaceContainerLow = c.surface,
                surfaceContainer = c.surface,
                surfaceContainerHigh = c.surfaceElevated,
                onSurface = c.textPrimary,
                onSurfaceVariant = c.textSecondary,
                outline = c.outlineStrong,
                outlineVariant = c.outline,
                background = c.background,
                onBackground = c.textPrimary,
                error = c.hpCritical,
                onError = c.onAccent,
                errorContainer = c.criticalContainer,
                onErrorContainer = c.hpCritical,
            ),
            typography = Typography().copy(
                titleLarge = t.headline,
                titleMedium = t.title,
                titleSmall = t.bodyStrong,
                bodyLarge = t.body,
                bodyMedium = t.body,
                bodySmall = t.caption,
                labelLarge = t.label,
                labelMedium = t.label,
                labelSmall = t.caption,
            ),
            content = content,
        )
    }
}
