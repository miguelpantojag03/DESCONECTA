package com.desconecta.hp.shared.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.desconecta.hp.shared.model.Accessory
import com.desconecta.hp.shared.model.AvatarStyle

/** Expresión del avatar según el estado de vida. */
enum class AvatarMood { HAPPY, WORRIED, EXHAUSTED, KO }

/**
 * Héroe pixel de 16×16 con capucha. Capas: capa (detrás) → cuerpo + cara → gorro →
 * espada. El aura se dibuja con Canvas (resplandor), no como sprite.
 *
 * Tintas: 'H' capucha (color del estilo) · 'D' tono oscuro del estilo · 'S' piel ·
 * 'K' rasgos · 'W' blanco · 'C' gota de sudor (cian) · 'A' accesorio · 'G' oro
 */
object AvatarSprites {

    fun body(style: AvatarStyle, mood: AvatarMood): ImageVector =
        bodies.getValue(style to mood)

    fun accessory(accessory: Accessory): ImageVector? = when (accessory) {
        Accessory.GORRO -> hat
        Accessory.CAPA -> cape
        Accessory.ESPADA -> sword
        Accessory.AURA -> null // se dibuja como resplandor
    }

    /** Color principal de cada estilo, para la selección en "Crear personaje". */
    fun styleColor(style: AvatarStyle): Color = Color(palette(style).first)

    private fun palette(style: AvatarStyle): Pair<Long, Long> = when (style) {
        AvatarStyle.INDIGO -> 0xFF6C7BFF to 0xFF3F49B8
        AvatarStyle.LILA -> 0xFFB57BFF to 0xFF7442C2
        AvatarStyle.CORAL -> 0xFFFF8A73 to 0xFFC24E3A
        AvatarStyle.ARENA -> 0xFFE3C08D to 0xFF9C7A4A
    }

    private val bodies: Map<Pair<AvatarStyle, AvatarMood>, ImageVector> by lazy {
        AvatarStyle.entries.flatMap { s -> AvatarMood.entries.map { m -> s to m } }.associateWith { (s, m) ->
            val (main, dark) = palette(s)
            PixelArt.build(
                "Avatar.${s.name}.${m.name}",
                face(m),
                mapOf(
                    'H' to PixelArt.Ink(Color(main)),
                    'D' to PixelArt.Ink(Color(dark)),
                    'S' to PixelArt.Ink(Color(0xFFFFD9B8)),
                    'K' to PixelArt.Ink(Color(0xFF15151C)),
                    'W' to PixelArt.Ink(Color.White),
                    'C' to PixelArt.Ink(Color(0xFF4DD8FF)),
                ),
            )
        }
    }

    private val BASE = listOf(
        "................",
        ".....HHHHHH.....",
        "....HHHHHHHH....",
        "...HHHHHHHHHH...",
        "...HHSSSSSSHH...",
        "...HSSSSSSSSH...",
        "...HSSSSSSSSH...",
        "...HSSSSSSSSH...",
        "...HSSSSSSSSH...",
        "....SSSSSSSS....",
        ".....DDDDDD.....",
        "....DDHHHHDD....",
        "...SDDHHHHDDS...",
        "....DDDDDDDD....",
        "....DD....DD....",
        "...DDD....DDD...",
    )

    /** Rasgos (fila, columna, tinta) que se pintan sobre la cara base. */
    private fun face(mood: AvatarMood): List<String> {
        val features: List<Triple<Int, Int, Char>> = when (mood) {
            // Ojos abiertos + sonrisa
            AvatarMood.HAPPY -> listOf(
                Triple(6, 5, 'K'), Triple(6, 10, 'K'),
                Triple(8, 6, 'K'), Triple(8, 9, 'K'), Triple(9, 7, 'K'), Triple(9, 8, 'K'),
            )
            // Cejas levantadas + boca ondulada
            AvatarMood.WORRIED -> listOf(
                Triple(5, 5, 'K'), Triple(5, 10, 'K'), Triple(4, 6, 'K'), Triple(4, 9, 'K'),
                Triple(6, 5, 'K'), Triple(6, 10, 'K'),
                Triple(8, 6, 'K'), Triple(8, 7, 'K'), Triple(9, 8, 'K'), Triple(9, 9, 'K'),
            )
            // Párpados caídos, boca abierta (jadeo) y gota de sudor
            AvatarMood.EXHAUSTED -> listOf(
                Triple(6, 4, 'K'), Triple(6, 5, 'K'), Triple(6, 6, 'K'),
                Triple(6, 9, 'K'), Triple(6, 10, 'K'), Triple(6, 11, 'K'),
                Triple(8, 7, 'K'), Triple(8, 8, 'K'), Triple(9, 7, 'K'), Triple(9, 8, 'K'),
                Triple(4, 12, 'C'), Triple(5, 12, 'C'),
            )
            // Ojos en X (K.O.) y boca plana
            AvatarMood.KO -> listOf(
                Triple(5, 4, 'K'), Triple(5, 6, 'K'), Triple(6, 5, 'K'), Triple(7, 4, 'K'), Triple(7, 6, 'K'),
                Triple(5, 9, 'K'), Triple(5, 11, 'K'), Triple(6, 10, 'K'), Triple(7, 9, 'K'), Triple(7, 11, 'K'),
                Triple(9, 6, 'K'), Triple(9, 7, 'K'), Triple(9, 8, 'K'), Triple(9, 9, 'K'),
            )
        }
        val grid = BASE.map { it.toCharArray() }
        features.forEach { (r, c, ink) -> grid[r][c] = ink }
        return grid.map(::String)
    }

    private val accessoryInks = mapOf(
        'A' to PixelArt.Ink(Color(0xFFFF5E7A)),
        'W' to PixelArt.Ink(Color.White),
        'G' to PixelArt.Ink(Color(0xFFFFD166)),
        'K' to PixelArt.Ink(Color(0xFF454556)),
        'P' to PixelArt.Ink(Color(0xFFB57BFF)),
        'Q' to PixelArt.Ink(Color(0xFF7442C2)),
        'S' to PixelArt.Ink(Color(0xFFC9C9D3)),
    )

    private val hat: ImageVector by lazy {
        PixelArt.build(
            "Avatar.Gorro",
            listOf(
                ".......WW.......",
                ".....AAAAAA.....",
                "....AAWAAWAA....",
                "...WWWWWWWWWW...",
            ) + List(12) { "................" },
            accessoryInks,
        )
    }

    private val cape: ImageVector by lazy {
        PixelArt.build(
            "Avatar.Capa",
            List(10) { "................" } + listOf(
                "...PP......PP...",
                "..PPP......PPP..",
                "..PQ........QP..",
                "..PPQ......QPP..",
                "..PPPQ....QPPP..",
                ".PPPPP....PPPPP.",
            ),
            accessoryInks,
        )
    }

    private val sword: ImageVector by lazy {
        PixelArt.build(
            "Avatar.Espada",
            listOf(
                "................",
                "................",
                "................",
                "..............S.",
                "..............S.",
                "..............S.",
                "..............S.",
                "..............S.",
                "..............S.",
                "..............S.",
                ".............GGG",
                "..............K.",
                "..............K.",
                "................",
                "................",
                "................",
            ),
            accessoryInks,
        )
    }
}
