package com.desconecta.hp.shared.designsystem.component.healthbar

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import com.desconecta.hp.shared.designsystem.color.HpColors
import com.desconecta.hp.shared.model.Health
import kotlin.math.cos
import kotlin.math.sin

/**
 * Funciones de dibujo puras (sin estado). Reciben un [HealthBarFrame] y pintan.
 *
 * Segmentos proporcionales: cada segmento representa 10 HP, salvo el último si la vida
 * máxima no es múltiplo de 10 (p. ej. 125 HP → 12 segmentos de 10 + 1 de 5, este
 * último más corto). Así el largo dibujado siempre es fiel a los minutos reales.
 */
internal object HealthBarDrawing {

    /** HP que representa cada segmento. */
    fun segmentSizes(max: Int): List<Int> {
        val full = max / Health.HP_PER_SEGMENT
        val rest = max % Health.HP_PER_SEGMENT
        return List(full) { Health.HP_PER_SEGMENT } + if (rest > 0) listOf(rest) else emptyList()
    }

    /** Cuánto de un segmento [i] cubre una fracción global [f] (0–1). */
    private fun localFill(f: Float, startHp: Int, sizeHp: Int, max: Int): Float =
        ((f * max - startHp) / sizeHp).coerceIn(0f, 1f)

    private fun fillColor(frame: HealthBarFrame, colors: HpColors): Color =
        if (frame.flash > 0f) lerp(frame.color, colors.hpHitFlash, frame.flash * 0.8f) else frame.color

    private fun trailColor(frame: HealthBarFrame, colors: HpColors): Color = when (frame.trailKind) {
        TrailKind.DAMAGE -> colors.hpDamageGhost
        TrailKind.HEAL -> colors.regen
        TrailKind.NONE -> Color.Transparent
    }

    // ---------------------------------------------------------------------------------
    // Barra lineal
    // ---------------------------------------------------------------------------------

    fun DrawScope.drawLinearBar(
        frame: HealthBarFrame,
        max: Int,
        colors: HpColors,
        gapPx: Float,
        segmented: Boolean,
    ) {
        val sizes = if (segmented) segmentSizes(max) else listOf(max)
        val gaps = gapPx * (sizes.size - 1)
        val usable = size.width - gaps
        val h = size.height
        val fill = fillColor(frame, colors)
        val trail = trailColor(frame, colors)
        val lo = minOf(frame.fill, frame.trail)
        val hi = maxOf(frame.fill, frame.trail)
        val hairline = 1f * density

        var x = 0f
        var startHp = 0
        sizes.forEach { sizeHp ->
            val w = usable * sizeHp / max
            val local = localFill(frame.fill, startHp, sizeHp, max)
            val lowLocal = localFill(lo, startHp, sizeHp, max)
            val highLocal = localFill(hi, startHp, sizeHp, max)

            if (colors.isAmbient) {
                // Ambient: solo contorno. Lleno = contorno brillante; vacío = contorno tenue.
                val c = if (local > 0f) colors.hpHealthy else colors.hpTrack
                val inset = hairline / 2
                drawRect(c, Offset(x + inset, inset), Size(w - hairline, h - hairline), style = Stroke(hairline))
                if (local in 0.01f..0.99f) {
                    // Segmento parcial: marca vertical donde termina la vida.
                    drawRect(c, Offset(x + w * local - hairline / 2, 0f), Size(hairline, h))
                }
            } else {
                drawRect(colors.hpTrack, Offset(x, 0f), Size(w, h))
                if (highLocal > lowLocal && frame.trailKind != TrailKind.NONE) {
                    drawRect(trail, Offset(x + w * lowLocal, 0f), Size(w * (highLocal - lowLocal), h))
                }
                if (local > 0f) {
                    drawRect(fill, Offset(x, 0f), Size(w * local, h))
                    // Brillo superior de 1 "píxel": volumen de sprite de 16 bits.
                    drawRect(Color.White.copy(alpha = 0.18f), Offset(x, 0f), Size(w * local, h * 0.25f))
                }
                if (colors.isHighContrast) {
                    drawRect(colors.outlineStrong, Offset(x, 0f), Size(w, h), style = Stroke(hairline * 1.5f))
                }
            }
            x += w + gapPx
            startHp += sizeHp
        }
    }

    // ---------------------------------------------------------------------------------
    // Arco (reloj redondo)
    // ---------------------------------------------------------------------------------

    data class ArcGeometry(val center: Offset, val radius: Float, val stroke: Float)

    fun DrawScope.arcGeometry(strokePx: Float, insetPx: Float): ArcGeometry {
        val r = minOf(size.width, size.height) / 2f - insetPx - strokePx / 2f
        return ArcGeometry(center, r, strokePx)
    }

    /** Ángulo (grados, sistema de Compose) donde termina la fracción [f] de la vida. */
    fun angleFor(f: Float, max: Int, startDeg: Float, sweepDeg: Float, gapDeg: Float): Float {
        val sizes = segmentSizes(max)
        val usable = sweepDeg - gapDeg * (sizes.size - 1)
        var a = startDeg
        var startHp = 0
        sizes.forEach { sizeHp ->
            val segSweep = usable * sizeHp / max
            val local = localFill(f, startHp, sizeHp, max)
            if (local < 1f) return a + segSweep * local
            a += segSweep + gapDeg
            startHp += sizeHp
        }
        return startDeg + sweepDeg
    }

    fun DrawScope.drawArcBar(
        frame: HealthBarFrame,
        max: Int,
        colors: HpColors,
        geo: ArcGeometry,
        startDeg: Float,
        sweepDeg: Float,
        gapDeg: Float,
    ) {
        val sizes = segmentSizes(max)
        val usable = sweepDeg - gapDeg * (sizes.size - 1)
        val rect = Rect(geo.center, geo.radius)
        val fill = fillColor(frame, colors)
        val trail = trailColor(frame, colors)
        val lo = minOf(frame.fill, frame.trail)
        val hi = maxOf(frame.fill, frame.trail)
        val stroke = Stroke(geo.stroke, cap = StrokeCap.Butt) // extremos rectos: estética pixel
        val hairline = 1.5f * density

        var a = startDeg
        var startHp = 0
        sizes.forEach { sizeHp ->
            val seg = usable * sizeHp / max
            val local = localFill(frame.fill, startHp, sizeHp, max)
            val lowLocal = localFill(lo, startHp, sizeHp, max)
            val highLocal = localFill(hi, startHp, sizeHp, max)

            if (colors.isAmbient) {
                val c = if (local > 0f) colors.hpHealthy else colors.hpTrack
                drawPath(segmentPath(geo, a, seg), c, style = Stroke(hairline))
                if (local in 0.01f..0.99f) {
                    drawPath(segmentPath(geo, a + seg * local, 0.01f), c, style = Stroke(hairline))
                }
            } else {
                drawArc(colors.hpTrack, a, seg, false, rect.topLeft, rect.size, style = stroke)
                if (highLocal > lowLocal && frame.trailKind != TrailKind.NONE) {
                    drawArc(trail, a + seg * lowLocal, seg * (highLocal - lowLocal), false, rect.topLeft, rect.size, style = stroke)
                }
                if (local > 0f) {
                    drawArc(fill, a, seg * local, false, rect.topLeft, rect.size, style = stroke)
                }
                if (colors.isHighContrast) {
                    drawPath(segmentPath(geo, a, seg), colors.outlineStrong, style = Stroke(hairline))
                }
            }
            a += seg + gapDeg
            startHp += sizeHp
        }
    }

    /** Contorno de un segmento de anillo (para Ambient y alto contraste). */
    private fun segmentPath(geo: ArcGeometry, startDeg: Float, sweepDeg: Float): Path {
        val outer = Rect(geo.center, geo.radius + geo.stroke / 2)
        val inner = Rect(geo.center, geo.radius - geo.stroke / 2)
        return Path().apply {
            arcTo(outer, startDeg, sweepDeg, forceMoveTo = true)
            arcTo(inner, startDeg + sweepDeg, -sweepDeg, forceMoveTo = false)
            close()
        }
    }

    fun pointOnArc(geo: ArcGeometry, deg: Float, radius: Float = geo.radius): Offset {
        val rad = Math.toRadians(deg.toDouble())
        return Offset(geo.center.x + radius * cos(rad).toFloat(), geo.center.y + radius * sin(rad).toFloat())
    }

    // ---------------------------------------------------------------------------------
    // Partículas de curación
    // ---------------------------------------------------------------------------------

    private class Particle(val along: Float, val delay: Float, val rise: Float, val drift: Float, val size: Float)

    /** Semilla fija: las partículas son iguales en cada curación (y en los previews). */
    private val particles: List<Particle> = run {
        val rnd = java.util.Random(0x48_50) // "HP"
        List(14) {
            Particle(
                along = rnd.nextFloat(),
                delay = rnd.nextFloat() * 0.45f,
                rise = 0.6f + rnd.nextFloat() * 0.4f,
                drift = (rnd.nextFloat() - 0.5f) * 0.5f,
                size = if (rnd.nextBoolean()) 3f else 4f,
            )
        }
    }

    /**
     * Cuadraditos (píxeles) cian que suben y se desvanecen.
     *
     * @param origin posición de nacimiento para una fracción a lo largo del tramo curado.
     * @param risePx altura total que recorre una partícula.
     */
    fun DrawScope.drawHealParticles(
        progress: Float,
        color: Color,
        risePx: Float,
        origin: (along: Float) -> Offset,
    ) {
        particles.forEach { p ->
            val t = ((progress - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val o = origin(p.along)
            val s = p.size * density
            val y = o.y - risePx * p.rise * t
            val x = o.x + risePx * p.drift * t
            // Se encaja en una rejilla de 1 dp para que se muevan "a saltos", como sprites.
            val snap = density
            drawRect(
                color = color.copy(alpha = (1f - t) * color.alpha),
                topLeft = Offset((x / snap).toInt() * snap - s / 2, (y / snap).toInt() * snap - s / 2),
                size = Size(s, s),
            )
        }
    }
}
