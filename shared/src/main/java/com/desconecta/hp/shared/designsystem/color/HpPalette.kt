package com.desconecta.hp.shared.designsystem.color

import androidx.compose.ui.graphics.Color

/**
 * Paleta "cruda": los únicos valores hexadecimales del proyecto.
 *
 * Las pantallas NUNCA usan estos valores directamente; usan los roles semánticos de
 * [HpColors] (p. ej. `hpCritical`, `textSecondary`). Así, cambiar un color o activar el
 * modo de alto contraste no obliga a tocar ninguna pantalla.
 *
 * Contraste WCAG 2.1 medido contra los tres fondos (ver docs/01-sistema-de-diseno.md):
 *
 * | Color          | #000000 | #15151C | #22222D |
 * |----------------|---------|---------|---------|
 * | Text  F5F5F7   | 19.3    | 16.7    | 14.5    |
 * | Text2 9A9AA8   |  7.6    |  6.5    |  5.7    |
 * | Green 3DDC84   | 11.8    | 10.2    |  8.8    |
 * | Amber FFC93C   | 13.7    | 11.8    | 10.2    |
 * | Red   FF4D5E   |  6.5    |  5.6    |  4.9    |
 * | Cyan  4DD8FF   | 12.6    | 10.9    |  9.4    |
 * | Purple B57BFF  |  7.2    |  6.3    |  5.4    |
 * | Gold  FFD166   | 14.6    | 12.6    | 10.9    |
 *
 * Todas las combinaciones superan AA (4.5:1) para texto normal.
 */
object HpPalette {
    // Neutros
    val Black = Color(0xFF000000)
    val Ink950 = Color(0xFF0B0B10)   // fondo del teléfono
    val Ink900 = Color(0xFF15151C)   // superficie
    val Ink800 = Color(0xFF22222D)   // superficie elevada
    val Ink700 = Color(0xFF2E2E3B)   // pista vacía de la barra, divisores
    val Ink600 = Color(0xFF454556)   // contornos
    val Gray400 = Color(0xFF9A9AA8)  // texto secundario
    val Gray200 = Color(0xFFC9C9D3)  // texto secundario en alto contraste
    val White = Color(0xFFFFFFFF)
    val Snow = Color(0xFFF5F5F7)     // texto principal

    // Vida
    val Green = Color(0xFF3DDC84)
    val Amber = Color(0xFFFFC93C)
    val Red = Color(0xFFFF4D5E)

    // Recursos
    val Cyan = Color(0xFF4DD8FF)     // regeneración / pociones
    val Purple = Color(0xFFB57BFF)   // XP / niveles
    val Gold = Color(0xFFFFD166)     // logros

    // IA ("cristal mágico"). Contraste AAA: 15,9 sobre negro, 11,9 sobre superficie elevada.
    // ΔE ≈ 37 frente al cian de regeneración y al verde de vida: distinto, pero de la misma
    // familia; por eso el contenido de IA SIEMPRE lleva además el ícono de cristal.
    val Mint = Color(0xFF7CF5D9)

    // Variantes oscuras para fondos de contenedores (tinte de ~15 % sobre Ink900).
    val GreenContainer = Color(0xFF1C3A2E)
    val AmberContainer = Color(0xFF3D3420)
    val RedContainer = Color(0xFF3D1E26)
    val CyanContainer = Color(0xFF1C3540)
    val PurpleContainer = Color(0xFF2E2445)
    val GoldContainer = Color(0xFF3D3626)
    val MintContainer = Color(0xFF17332E)
}
