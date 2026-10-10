package com.desconecta.hp.shared.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/**
 * Motor de pixel art: convierte mapas de caracteres 16×16 en [ImageVector].
 * Lo usan los íconos monocromos ([PixelIcons]) y los sprites a color ([MonsterSprites]).
 */
internal object PixelArt {

    const val GRID = 16

    data class Ink(val color: Color, val alpha: Float = 1f)

    fun build(name: String, rows: List<String>, inks: Map<Char, Ink>): ImageVector {
        require(rows.size == GRID && rows.all { it.length == GRID }) { "$name debe ser 16×16" }
        return ImageVector.Builder(
            name = name,
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = GRID.toFloat(),
            viewportHeight = GRID.toFloat(),
        ).apply {
            // Un único path por tinta: los rectángulos contiguos se rasterizan juntos y
            // no aparecen "costuras" de antialiasing entre filas al escalar.
            inks.forEach { (ch, ink) ->
                val nodes = runsOf(rows, ch)
                if (nodes.isNotEmpty()) {
                    addPath(pathData = nodes, fill = SolidColor(ink.color), fillAlpha = ink.alpha)
                }
            }
        }.build()
    }

    fun carve(rows: List<String>, points: List<Pair<Int, Int>>): List<String> {
        val grid = rows.map { it.toCharArray() }
        points.forEach { (r, c) -> grid[r][c] = '.' }
        return grid.map(::String)
    }

    /** Píxeles del borde de la silueta (cualquier carácter distinto de '.'), como 'X'. */
    fun outline(rows: List<String>): List<String> {
        fun filled(r: Int, c: Int) = r in rows.indices && c in rows[r].indices && rows[r][c] != '.'
        return rows.mapIndexed { r, row ->
            row.mapIndexed { c, _ ->
                val edge = filled(r, c) &&
                    (!filled(r - 1, c) || !filled(r + 1, c) || !filled(r, c - 1) || !filled(r, c + 1))
                if (edge) 'X' else '.'
            }.joinToString("")
        }
    }

    /** Une píxeles consecutivos de una fila en un solo rectángulo. */
    private fun runsOf(rows: List<String>, ink: Char): List<PathNode> {
        val builder = PathBuilder()
        rows.forEachIndexed { y, row ->
            var x = 0
            while (x < row.length) {
                if (row[x] == ink) {
                    val start = x
                    while (x < row.length && row[x] == ink) x++
                    builder.moveTo(start.toFloat(), y.toFloat())
                    builder.horizontalLineTo(x.toFloat())
                    builder.verticalLineTo(y + 1f)
                    builder.horizontalLineTo(start.toFloat())
                    builder.close()
                } else {
                    x++
                }
            }
        }
        return builder.nodes
    }
}
