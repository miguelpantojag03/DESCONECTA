package com.desconecta.hp.shared.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.unit.dp

/**
 * Íconos pixel art de 16×16, definidos como "mapas de bits" de texto:
 *   'X' = tinta plena   'o' = tinta al 45 % (sombra)   '.' = transparente
 *
 * Ventajas frente a PNG: escalan sin pérdida, se tiñen con cualquier token de color
 * (el mismo corazón sirve en verde, amarillo, rojo o gris de ambient), pesan casi nada
 * y la versión de contorno para Ambient Mode se genera automáticamente.
 *
 * Tamaños recomendados: múltiplos de 16 dp (16 / 32 / 48) para que cada "píxel" del
 * arte caiga en un número entero de píxeles físicos en la mayoría de densidades.
 */
object PixelIcons {

    val Heart: ImageVector by lazy { pixelIcon("Heart", HEART) }
    /** Estado "herido": mitad llena, mitad en contorno. */
    val HeartHalf: ImageVector by lazy { pixelIcon("HeartHalf", heartHalf()) }
    /** Estado "crítico": corazón agrietado. */
    val HeartCracked: ImageVector by lazy { pixelIcon("HeartCracked", HEART.carve(CRACK)) }
    val HeartOutline: ImageVector by lazy { pixelIcon("HeartOutline", HEART.outline()) }
    val Potion: ImageVector by lazy { pixelIcon("Potion", POTION) }
    val Shield: ImageVector by lazy { pixelIcon("Shield", SHIELD) }
    val Revive: ImageVector by lazy { pixelIcon("Revive", HEART.carve(PLUS)) }
    val Flame: ImageVector by lazy { pixelIcon("Flame", FLAME) }
    val Medal: ImageVector by lazy { pixelIcon("Medal", MEDAL) }
    val MedalLocked: ImageVector by lazy { pixelIcon("MedalLocked", MEDAL.outline()) }
    val Skull: ImageVector by lazy { pixelIcon("Skull", SKULL) }
    val Star: ImageVector by lazy { pixelIcon("Star", STAR) }

    /** Todos, para el catálogo del sistema de diseño. */
    val all: List<Pair<String, ImageVector>> by lazy {
        listOf(
            "Corazón" to Heart, "Herido" to HeartHalf, "Crítico" to HeartCracked,
            "Contorno" to HeartOutline, "Poción" to Potion, "Escudo" to Shield,
            "Revivir" to Revive, "Racha" to Flame, "Medalla" to Medal,
            "Bloqueada" to MedalLocked, "Game Over" to Skull, "XP" to Star,
        )
    }

    // ---------------------------------------------------------------------------------
    // Arte
    // ---------------------------------------------------------------------------------

    private val HEART = listOf(
        "................",
        "................",
        "..XXXX....XXXX..",
        ".XXXXXX..XXXXXX.",
        "XXXXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXXXX",
        "XXXXXXXXXXXXXXXX",
        ".XXXXXXXXXXXXXX.",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        ".....XXXXXX.....",
        "......XXXX......",
        ".......XX.......",
        "................",
    )

    /** Grieta en zigzag (fila, columna). */
    private val CRACK = listOf(
        3 to 8, 4 to 8, 5 to 7, 6 to 7, 7 to 8, 8 to 8, 9 to 7, 10 to 7, 11 to 8,
    )

    /** Cruz de "revivir" calada en el corazón. */
    private val PLUS = listOf(
        5 to 7, 5 to 8, 6 to 7, 6 to 8,
        7 to 5, 7 to 6, 7 to 7, 7 to 8, 7 to 9, 7 to 10,
        8 to 5, 8 to 6, 8 to 7, 8 to 8, 8 to 9, 8 to 10,
        9 to 7, 9 to 8, 10 to 7, 10 to 8,
    )

    private val POTION = listOf(
        "................",
        "......XXXX......",
        "......oooo......",
        ".......XX.......",
        ".......XX.......",
        ".....XXXXXX.....",
        "....XX....XX....",
        "...XX......XX...",
        "...XXXXXXXXXX...",
        "..XXXXXXXXXXXX..",
        "..XXoXXXXXXXXX..",
        "..XXooXXXXXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "................",
        "................",
    )

    private val SHIELD = listOf(
        "................",
        ".XXXXXXXXXXXXXX.",
        ".XXXXXXXooooooX.",
        ".XXXXXXXooooooX.",
        ".XXXXXXXooooooX.",
        ".XXXXXXXooooooX.",
        ".XXXXXXXooooooX.",
        ".XXXXXXXooooooX.",
        "..XXXXXXoooooX..",
        "..XXXXXXoooooX..",
        "...XXXXXooooX...",
        "....XXXXoooX....",
        ".....XXXooX.....",
        "......XXoX......",
        ".......XX.......",
        "................",
    )

    private val FLAME = listOf(
        ".......X........",
        ".......XX.......",
        "......XXX.......",
        "......XXXX......",
        ".....XXXXX..X...",
        ".....XXXXXX.XX..",
        "....XXXXXXXXXX..",
        "...XXXXXoXXXXX..",
        "...XXXXooXXXXXX.",
        "..XXXXoooXXXXXX.",
        "..XXXXooooXXXXX.",
        "..XXXoooooXXXXX.",
        "..XXXooooooXXXX.",
        "...XXXooooXXXX..",
        "....XXXXXXXXX...",
        "......XXXXX.....",
    )

    private val MEDAL = listOf(
        "...XXX....XXX...",
        "...XXX....XXX...",
        "....XXX..XXX....",
        "....XXX..XXX....",
        ".....XXXXXX.....",
        "......XXXX......",
        ".....XXXXXX.....",
        "....XXXXXXXX....",
        "...XXXooooXXX...",
        "...XXooXXooXX...",
        "...XXoXXXXoXX...",
        "...XXoXXXXoXX...",
        "...XXooXXooXX...",
        "...XXXooooXXX...",
        "....XXXXXXXX....",
        ".....XXXXXX.....",
    )

    private val SKULL = listOf(
        "................",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "..XXXXXXXXXXXX..",
        "..XXXXXXXXXXXX..",
        "..XX...XX...XX..",
        "..XX...XX...XX..",
        "..XX...XX...XX..",
        "..XXXXXXXXXXXX..",
        "..XXXXX..XXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "....XX.XX.XX....",
        "....XXXXXXXX....",
        "................",
        "................",
    )

    private val STAR = listOf(
        ".......XX.......",
        ".......XX.......",
        "......XXXX......",
        "......XXXX......",
        "XXXXXXXXXXXXXXXX",
        ".XXXXXXXXXXXXXX.",
        "..XXXXXXXXXXXX..",
        "...XXXXXXXXXX...",
        "....XXXXXXXX....",
        "....XXXXXXXX....",
        "...XXXXXXXXXX...",
        "...XXXX..XXXX...",
        "..XXXX....XXXX..",
        "..XXX......XXX..",
        ".XX..........XX.",
        "................",
    )

    // ---------------------------------------------------------------------------------
    // Operaciones sobre los mapas
    // ---------------------------------------------------------------------------------

    private fun List<String>.carve(points: List<Pair<Int, Int>>): List<String> {
        val grid = map { it.toCharArray() }
        points.forEach { (r, c) -> grid[r][c] = '.' }
        return grid.map(::String)
    }

    /** Conserva solo los píxeles del borde de la silueta (vecino vacío en 4 direcciones). */
    private fun List<String>.outline(): List<String> {
        fun filled(r: Int, c: Int) = r in indices && c in this[r].indices && this[r][c] != '.'
        return mapIndexed { r, row ->
            row.mapIndexed { c, _ ->
                val edge = filled(r, c) &&
                    (!filled(r - 1, c) || !filled(r + 1, c) || !filled(r, c - 1) || !filled(r, c + 1))
                if (edge) 'X' else '.'
            }.joinToString("")
        }
    }

    /** Mitad izquierda llena, mitad derecha en contorno. */
    private fun heartHalf(): List<String> {
        val border = HEART.outline()
        return HEART.mapIndexed { r, row ->
            row.mapIndexed { c, ch -> if (c < 8) ch else border[r][c] }.joinToString("")
        }
    }

    private fun pixelIcon(name: String, rows: List<String>): ImageVector {
        require(rows.size == 16 && rows.all { it.length == 16 }) { "$name debe ser 16×16" }
        return ImageVector.Builder(
            name = "Pixel.$name",
            defaultWidth = 16.dp,
            defaultHeight = 16.dp,
            viewportWidth = 16f,
            viewportHeight = 16f,
        ).apply {
            // Un único path por tinta: los rectángulos contiguos se rasterizan juntos y
            // no aparecen "costuras" de antialiasing entre filas al escalar.
            addPath(pathData = runsOf(rows, 'X'), fill = SolidColor(Color.Black))
            addPath(pathData = runsOf(rows, 'o'), fill = SolidColor(Color.Black), fillAlpha = 0.45f)
        }.build()
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
