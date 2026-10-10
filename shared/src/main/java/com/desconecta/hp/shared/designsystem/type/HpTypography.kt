package com.desconecta.hp.shared.designsystem.type

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.desconecta.hp.shared.R

/**
 * Tres familias, cada una con un rol único (nunca se mezclan dentro de un mismo rol):
 *
 * - [PixelDisplay] Press Start 2P: SOLO números de HP y títulos de evento ("GAME OVER",
 *   "LEVEL UP!"). Muy expresiva pero ancha (1 em por carácter), por eso se limita a
 *   textos de 1–9 caracteres.
 * - [Pixel] Silkscreen: etiquetas cortas de juego ("NV 12", "HP", "x3"). Más estrecha y
 *   legible que Press Start 2P a 14 sp.
 * - [Sans] Inter (variable): todo texto que se LEE (frases, botones, descripciones).
 *   Diseñada para pantallas, x-height alta, buena legibilidad a tamaños pequeños.
 */
object HpFonts {
    val PixelDisplay = FontFamily(Font(R.font.press_start_2p, FontWeight.Normal))

    val Pixel = FontFamily(
        Font(R.font.silkscreen_regular, FontWeight.Normal),
        Font(R.font.silkscreen_bold, FontWeight.Bold),
    )

    @OptIn(ExperimentalTextApi::class)
    val Sans = FontFamily(
        interWeight(FontWeight.Normal),
        interWeight(FontWeight.Medium),
        interWeight(FontWeight.SemiBold),
        interWeight(FontWeight.Bold),
    )

    @OptIn(ExperimentalTextApi::class)
    private fun interWeight(weight: FontWeight) = Font(
        resId = R.font.inter_variable,
        weight = weight,
        variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
    )
}

/**
 * Roles tipográficos del juego. Son los mismos en reloj y teléfono; solo cambia la
 * escala ([HpTypeScale.Watch] / [HpTypeScale.Phone]).
 */
@Immutable
data class HpTypography(
    /** El número de HP: el elemento más grande de toda la interfaz. */
    val hpNumber: TextStyle,
    /** "/ 120 HP" junto al número principal. */
    val hpMax: TextStyle,
    /** Eventos: "GAME OVER", "LEVEL UP!", "K.O.". */
    val gameTitle: TextStyle,
    /** Etiquetas pixel: "NV 12", "x3", "HP". */
    val gameLabel: TextStyle,

    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    /** Botones. */
    val label: TextStyle,
    /** Tiempos y metadatos ("1 h 24 min", "hace 5 min"). */
    val caption: TextStyle,
)

/** Recorta el espacio extra arriba/abajo de las fuentes pixel para centrarlas ópticamente. */
private val TrimBoth = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

/** Cifras tabulares: el tiempo "1 h 24 min" no "baila" cuando cambian los dígitos. */
private const val TABULAR = "tnum"

object HpTypeScale {

    /**
     * Reloj (≈ 192–225 dp de ancho). Regla: ningún texto por debajo de 14 sp.
     * El HP usa 40 sp: 3 dígitos de Press Start 2P ≈ 120 dp, cabe en el círculo
     * interior que deja libre el arco de la barra.
     */
    val Watch = HpTypography(
        hpNumber = TextStyle(
            fontFamily = HpFonts.PixelDisplay,
            fontSize = 40.sp,
            lineHeight = 40.sp,
            lineHeightStyle = TrimBoth,
        ),
        hpMax = TextStyle(
            fontFamily = HpFonts.Pixel,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.05.em,
        ),
        gameTitle = TextStyle(
            fontFamily = HpFonts.PixelDisplay,
            fontSize = 16.sp, // "GAME OVER" (9 car.) ≈ 144 dp
            lineHeight = 22.sp,
            lineHeightStyle = TrimBoth,
        ),
        gameLabel = TextStyle(
            fontFamily = HpFonts.Pixel,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.05.em,
        ),
        headline = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 22.sp,
        ),
        title = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 20.sp,
        ),
        body = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        bodyStrong = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 20.sp,
        ),
        label = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            lineHeight = 18.sp,
        ),
        caption = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontFeatureSettings = TABULAR,
        ),
    )

    /** Teléfono: más espacio, jerarquía más marcada. */
    val Phone = HpTypography(
        hpNumber = TextStyle(
            fontFamily = HpFonts.PixelDisplay,
            fontSize = 48.sp,
            lineHeight = 48.sp,
            lineHeightStyle = TrimBoth,
        ),
        hpMax = TextStyle(
            fontFamily = HpFonts.Pixel,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.05.em,
        ),
        gameTitle = TextStyle(
            fontFamily = HpFonts.PixelDisplay,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            lineHeightStyle = TrimBoth,
        ),
        gameLabel = TextStyle(
            fontFamily = HpFonts.Pixel,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.05.em,
        ),
        headline = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 30.sp,
        ),
        title = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
        ),
        body = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        bodyStrong = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 24.sp,
        ),
        label = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.01.em,
        ),
        caption = TextStyle(
            fontFamily = HpFonts.Sans,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFeatureSettings = TABULAR,
        ),
    )
}
