package com.desconecta.hp.shared.designsystem.shape

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Dos lenguajes de forma con significado distinto:
 * - Redondeado ([card], [button]…) → "interfaz": cosas que se tocan o se leen.
 * - Pixelado ([PixelCornerShape]) → "juego": barras de vida, ranuras de inventario,
 *   medallas. El usuario aprende a distinguir de un vistazo qué es mecánica de juego.
 */
@Immutable
data class HpShapes(
    val xs: Shape = RoundedCornerShape(4.dp),
    val sm: Shape = RoundedCornerShape(8.dp),
    val md: Shape = RoundedCornerShape(12.dp),
    val card: Shape = RoundedCornerShape(16.dp),
    val sheet: Shape = RoundedCornerShape(24.dp),
    val button: Shape = CircleShape, // píldora: convención de Wear M3 y M3 Expressive
    val bar: Shape = PixelCornerShape(step = 2.dp),
    val slot: Shape = PixelCornerShape(step = 4.dp),
    val badge: Shape = PixelCornerShape(step = 3.dp, steps = 2),
)

/**
 * Rectángulo con esquinas "escalonadas" como en los sprites de 8 bits.
 *
 * @param step tamaño de cada escalón (un "píxel" del arte).
 * @param steps número de escalones por esquina (1 = muesca simple, 2 = curva pixelada).
 */
class PixelCornerShape(
    private val step: Dp = 2.dp,
    private val steps: Int = 1,
) : Shape {

    init {
        require(steps >= 1)
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val n = steps
        // Nunca más grande que la mitad del lado corto, para barras muy finas.
        val s = min(with(density) { step.toPx() }, min(w, h) / (2f * n))
        val path = Path().apply {
            moveTo(n * s, 0f)
            lineTo(w - n * s, 0f)
            for (i in 1..n) { // superior derecha
                lineTo(w - (n - i + 1) * s, i * s)
                lineTo(w - (n - i) * s, i * s)
            }
            lineTo(w, h - n * s)
            for (i in 1..n) { // inferior derecha
                lineTo(w - (i - 1) * s, h - (n - i) * s)
                lineTo(w - i * s, h - (n - i) * s)
            }
            lineTo(n * s, h)
            for (i in 1..n) { // inferior izquierda
                lineTo((n - i + 1) * s, h - i * s)
                lineTo((n - i) * s, h - i * s)
            }
            lineTo(0f, n * s)
            for (i in 1..n) { // superior izquierda
                lineTo((i - 1) * s, (n - i) * s)
                lineTo(i * s, (n - i) * s)
            }
            close()
        }
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) =
        other is PixelCornerShape && other.step == step && other.steps == steps

    override fun hashCode() = 31 * step.hashCode() + steps
}
