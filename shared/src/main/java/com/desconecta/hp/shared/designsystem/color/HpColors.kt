package com.desconecta.hp.shared.designsystem.color

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.desconecta.hp.shared.model.HealthStatus

/**
 * Roles semánticos de color. Las pantallas piden "el color de vida crítica", no "rojo".
 */
@Immutable
data class HpColors(
    // Fondos y superficies
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val outline: Color,
    val outlineStrong: Color,

    // Texto
    val textPrimary: Color,
    val textSecondary: Color,
    /** Texto/ícono sobre un relleno de color de acento (botones verdes, chips dorados…). */
    val onAccent: Color,

    // Barra de vida
    val hpHealthy: Color,
    val hpHurt: Color,
    val hpCritical: Color,
    val hpTrack: Color,
    /** Barra fantasma que se queda atrás al recibir daño. */
    val hpDamageGhost: Color,
    /** Destello al recibir daño fuerte. */
    val hpHitFlash: Color,

    // Recursos del juego
    val regen: Color,
    val xp: Color,
    val gold: Color,

    // Contenedores tintados (fondos de tarjetas de estado)
    val healthyContainer: Color,
    val hurtContainer: Color,
    val criticalContainer: Color,
    val regenContainer: Color,
    val xpContainer: Color,
    val goldContainer: Color,

    /** true en Ambient Mode: el componente debe dibujarse solo en contorno. */
    val isAmbient: Boolean = false,
    val isHighContrast: Boolean = false,
) {
    fun forStatus(status: HealthStatus): Color = when (status) {
        HealthStatus.HEALTHY -> hpHealthy
        HealthStatus.HURT -> hpHurt
        HealthStatus.CRITICAL -> hpCritical
        HealthStatus.GAME_OVER -> textSecondary
    }

    fun containerForStatus(status: HealthStatus): Color = when (status) {
        HealthStatus.HEALTHY -> healthyContainer
        HealthStatus.HURT -> hurtContainer
        HealthStatus.CRITICAL -> criticalContainer
        HealthStatus.GAME_OVER -> surfaceElevated
    }
}

object HpColorSchemes {

    /** Reloj: negro puro. En OLED cada píxel negro está apagado → ahorro de batería. */
    val WatchDark = HpColors(
        background = HpPalette.Black,
        surface = HpPalette.Ink900,
        surfaceElevated = HpPalette.Ink800,
        outline = HpPalette.Ink700,
        outlineStrong = HpPalette.Ink600,
        textPrimary = HpPalette.Snow,
        textSecondary = HpPalette.Gray400,
        onAccent = HpPalette.Black,
        hpHealthy = HpPalette.Green,
        hpHurt = HpPalette.Amber,
        hpCritical = HpPalette.Red,
        hpTrack = HpPalette.Ink700,
        hpDamageGhost = HpPalette.White.copy(alpha = 0.6f),
        hpHitFlash = HpPalette.White,
        regen = HpPalette.Cyan,
        xp = HpPalette.Purple,
        gold = HpPalette.Gold,
        healthyContainer = HpPalette.GreenContainer,
        hurtContainer = HpPalette.AmberContainer,
        criticalContainer = HpPalette.RedContainer,
        regenContainer = HpPalette.CyanContainer,
        xpContainer = HpPalette.PurpleContainer,
        goldContainer = HpPalette.GoldContainer,
    )

    /**
     * Teléfono: casi negro (#0B0B10) en lugar de negro puro. En pantallas grandes el
     * negro absoluto provoca "black smearing" al hacer scroll y deja muy poca
     * diferencia visual con las superficies (#15151C), que pierden su elevación.
     */
    val PhoneDark = WatchDark.copy(background = HpPalette.Ink950)

    /**
     * Alto contraste: texto secundario más claro, contornos blancos y pista de la barra
     * más visible. Los colores de vida no cambian (ya superan 4.5:1), lo que cambia es
     * que el componente añade bordes y patrones (ver HealthBar).
     */
    fun highContrast(base: HpColors) = base.copy(
        textSecondary = HpPalette.Gray200,
        outline = HpPalette.Gray400,
        outlineStrong = HpPalette.White,
        hpTrack = HpPalette.Ink600,
        hpDamageGhost = HpPalette.White.copy(alpha = 0.85f),
        isHighContrast = true,
    )

    /**
     * Ambient Mode (siempre activo): solo grises sobre negro. Menos del 10–15 % de los
     * píxeles encendidos, sin rellenos grandes, para cumplir las guías de Wear OS
     * sobre consumo y quemado de pantalla.
     */
    val WatchAmbient = WatchDark.copy(
        surface = HpPalette.Black,
        surfaceElevated = HpPalette.Black,
        outline = HpPalette.Ink600,
        outlineStrong = HpPalette.Gray400,
        textSecondary = HpPalette.Gray400,
        hpHealthy = HpPalette.Snow,
        hpHurt = HpPalette.Snow,
        hpCritical = HpPalette.Snow,
        hpTrack = HpPalette.Ink600,
        hpDamageGhost = Color.Transparent,
        hpHitFlash = Color.Transparent,
        regen = HpPalette.Snow,
        xp = HpPalette.Snow,
        gold = HpPalette.Snow,
        healthyContainer = HpPalette.Black,
        hurtContainer = HpPalette.Black,
        criticalContainer = HpPalette.Black,
        regenContainer = HpPalette.Black,
        xpContainer = HpPalette.Black,
        goldContainer = HpPalette.Black,
        isAmbient = true,
    )
}
