package com.desconecta.hp.shared.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.desconecta.hp.shared.model.MonsterArt

/**
 * Sprites a color de los monstruos (16×16).
 *
 * Decisión de color: ningún monstruo usa el verde, amarillo, rojo o cian de la barra
 * de vida, para que nunca se confunda "un monstruo" con "un estado de vida". Todos
 * comparten la misma anatomía (cuerpo redondo, ojos grandes, patas cortas): parecen
 * una familia y se ven traviesos, no aterradores — el tono es divertido, no punitivo.
 *
 * Tintas: 'B' cuerpo · 'D' cuerpo oscuro · 'W' blanco · 'K' negro · 'G' oro · 'P' acento
 */
object MonsterSprites {

    fun of(art: MonsterArt): ImageVector = full.getValue(art)

    /** Silueta en contorno para Ambient Mode (se tiñe en gris con ColorFilter). */
    fun ambientOf(art: MonsterArt): ImageVector = ambient.getValue(art)

    private val full: Map<MonsterArt, ImageVector> by lazy {
        MonsterArt.entries.associateWith { art ->
            val (rows, inks) = artOf(art)
            PixelArt.build("Monster.${art.name}", rows, inks)
        }
    }

    private val ambient: Map<MonsterArt, ImageVector> by lazy {
        MonsterArt.entries.associateWith { art ->
            PixelArt.build(
                "Monster.${art.name}.Ambient",
                PixelArt.outline(artOf(art).first),
                mapOf('X' to PixelArt.Ink(Color.White)),
            )
        }
    }

    private fun artOf(art: MonsterArt): Pair<List<String>, Map<Char, PixelArt.Ink>> = when (art) {
        MonsterArt.SCROLLZILLA -> SCROLLZILLA to inks(body = 0xFFFF8A3D, dark = 0xFFC2551B)
        MonsterArt.MEDUSA -> MEDUSA to inks(body = 0xFFFF7AC6, dark = 0xFFC2448F)
        MonsterArt.REY_AUTOPLAY -> REY_AUTOPLAY to inks(body = 0xFF7A8CFF, dark = 0xFF4A57C2)
        MonsterArt.GOBLIN -> GOBLIN to inks(body = 0xFFA3B83A, dark = 0xFF6B7A1E, accent = 0xFFFF7AC6)
        MonsterArt.FANTASMA -> FANTASMA to inks(body = 0xFFDCDCE6, dark = 0xFF9A9AA8)
    }

    private fun inks(body: Long, dark: Long, accent: Long = 0xFFFFFFFF) = mapOf(
        'B' to PixelArt.Ink(Color(body)),
        'D' to PixelArt.Ink(Color(dark)),
        'W' to PixelArt.Ink(Color(0xFFFFFFFF)),
        'K' to PixelArt.Ink(Color(0xFF15151C)),
        'G' to PixelArt.Ink(Color(0xFFFFD166)),
        'P' to PixelArt.Ink(Color(accent)),
    )

    /** TikTok: dinosaurio bizco de tanto mirar la pantalla, boca llena de dientes. */
    private val SCROLLZILLA = listOf(
        "................",
        "...D...DD...D...",
        "..DDD.DDDD.DDD..",
        "..BBBBBBBBBBBB..",
        ".BBBBBBBBBBBBBB.",
        ".BBWWBBBBBBWWBB.",
        ".BBWKBBBBBBKWBB.",
        ".BBBBBBBBBBBBBB.",
        ".BKWKWKWWKWKWKB.",
        ".BKKKKKKKKKKKKB.",
        ".BKWKWKWWKWKWKB.",
        ".BBBBBBBBBBBBBB.",
        ".BBBBBBBBBBBBBB.",
        "..BBBBBBBBBBBB..",
        "..BBB......BBB..",
        ".DDDD......DDDD.",
    )

    /** Instagram: medusa hipnótica con tentáculos que "atrapan" el dedo. */
    private val MEDUSA = listOf(
        "................",
        ".....BBBBBB.....",
        "...BBBBBBBBBB...",
        "..BBBBBBBBBBBB..",
        ".BBBWWBBBBWWBBB.",
        ".BBBWKBBBBKWBBB.",
        ".BBBBBBBBBBBBBB.",
        ".BBBBBDDDDBBBBB.",
        ".DDDDDDDDDDDDDD.",
        "..B.B.B..B.B.B..",
        "..B.B.B..B.B.B..",
        ".B.B.B....B.B.B.",
        ".B.B.B....B.B.B.",
        "..B.B.B..B.B.B..",
        "...B.B....B.B...",
        "................",
    )

    /** YouTube: rey con corona, ojos entrecerrados (hipnotizado) y un "play" en la panza. */
    private val REY_AUTOPLAY = listOf(
        "...G..G..G..G...",
        "...GGGGGGGGGG...",
        "...GGGGPPGGGG...",
        "..BBBBBBBBBBBB..",
        ".BBBBBBBBBBBBBB.",
        ".BBDDBBBBBBDDBB.",
        ".BBKWBBBBBBWKBB.",
        ".BBBBBBBBBBBBBB.",
        ".BBBBBWBBBBBBBB.",
        ".BBBBBWWBBBBBBB.",
        ".BBBBBWWWBBBBBB.",
        ".BBBBBWWBBBBBBB.",
        ".BBBBBWBBBBBBBB.",
        "..BBBBBBBBBBBB..",
        "..BBB......BBB..",
        ".DDDD......DDDD.",
    )

    /** Juegos: goblin de orejas puntiagudas que nunca suelta el mando (rosa). */
    private val GOBLIN = listOf(
        "................",
        "................",
        "D..BBBBBBBBBB..D",
        "DD.BBBBBBBBBB.DD",
        ".DBBBBBBBBBBBBD.",
        "..BBWWBBBBWWBB..",
        "..BBWKBBBBKWBB..",
        "..BBBBBBBBBBBB..",
        "..BBBKKKKKKBBB..",
        "...BBBWBBWBBB...",
        "..PPPPPPPPPPPP..",
        ".PPKPPPPPPPWPPP.",
        ".PKKKPPPPPWPWPP.",
        ".PPKPPPPPPPWPPP.",
        "..PPPPP..PPPPP..",
        "................",
    )

    /** Mensajería (ferocidad baja): fantasmita que aparece y desaparece. */
    private val FANTASMA = listOf(
        "................",
        ".....BBBBBB.....",
        "...BBBBBBBBBB...",
        "..BBBBBBBBBBBB..",
        "..BBBBBBBBBBBB..",
        ".BBBKKBBBBKKBBB.",
        ".BBBKKBBBBKKBBB.",
        ".BBBBBBBBBBBBBB.",
        ".BBBBBBKKBBBBBB.",
        ".BBBBBBKKBBBBBB.",
        ".BBBBBBBBBBBBBB.",
        ".BBBBBBBBBBBBBB.",
        ".DBBBBBBBBBBBBD.",
        ".BB.BBB..BBB.BB.",
        ".B...BB..BB...B.",
        "................",
    )
}
