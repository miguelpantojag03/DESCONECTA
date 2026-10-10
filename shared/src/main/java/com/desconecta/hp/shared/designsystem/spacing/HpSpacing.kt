package com.desconecta.hp.shared.designsystem.spacing

import androidx.compose.ui.unit.dp

/**
 * Retícula de 4 dp. Todos los márgenes, rellenos y separaciones son múltiplos de 4,
 * lo que da un ritmo vertical consistente (Gestalt: proximidad → lo que está a 4–8 dp
 * se percibe como un grupo; lo que está a 16–24 dp, como grupos distintos).
 */
object HpSpacing {
    val none = 0.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp

    /** Área táctil mínima (WCAG 2.5.5 / Material). Se aplica también en el reloj. */
    val touchTarget = 48.dp

    /** Margen lateral estándar del teléfono. */
    val phoneGutter = 16.dp
}

/**
 * Márgenes del reloj redondo, expresados como fracción del diámetro (las guías de Wear OS
 * usan porcentajes porque los relojes van de ~192 a ~240 dp). El contenido rectangular
 * que entra dentro del círculo sin cortarse está a ≈14.6 % de cada borde (1 − 1/√2) / 2.
 */
object WatchInsets {
    const val TOP_FRACTION = 0.052f
    const val BOTTOM_FRACTION = 0.10f
    const val SIDE_FRACTION = 0.052f
    /** Inscripción de un cuadrado en el círculo. */
    const val SAFE_SQUARE_FRACTION = 0.146f
}

/** Dimensiones de la barra de vida (componente estrella, se usan en la fase 2). */
object HealthBarDimens {
    /** Arco en reloj redondo: 240° que abrazan el bisel, con el hueco abajo. */
    const val ARC_SWEEP_DEGREES = 240f
    /** 0° = las 3 en punto, sentido horario → 150° deja el hueco centrado abajo. */
    const val ARC_START_DEGREES = 150f
    /** Separación entre segmentos del arco. */
    const val ARC_SEGMENT_GAP_DEGREES = 2.5f

    val arcStrokeWatch = 12.dp
    val arcInsetWatch = 4.dp

    val linearHeightWatch = 12.dp
    val linearHeightPhone = 24.dp
    val linearHeightMini = 6.dp // mini barra de cada monstruo
    val linearSegmentGap = 2.dp

    /** Ícono del monstruo que ataca, junto a la barra. */
    val attackerIconWatch = 24.dp
    val attackerIconPhone = 32.dp
}
