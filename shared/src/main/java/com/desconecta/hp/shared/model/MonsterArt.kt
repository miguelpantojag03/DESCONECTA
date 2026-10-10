package com.desconecta.hp.shared.model

import kotlinx.serialization.Serializable

/**
 * Ilustraciones disponibles en la galería de monstruos. El dominio solo guarda el id;
 * el dibujo vive en el sistema de diseño (MonsterSprites).
 *
 * @param ferocious los monstruos más adictivos hacen "más daño visual": cada golpe
 *   provoca destello y sacudida en la barra de vida.
 */
@Serializable
enum class MonsterArt(val defaultName: String, val ferocious: Boolean) {
    SCROLLZILLA("Scrollzilla", ferocious = true),
    MEDUSA("La Medusa del Feed", ferocious = true),
    REY_AUTOPLAY("Rey Autoplay", ferocious = true),
    GOBLIN("Goblin del Grind", ferocious = true),
    FANTASMA("Fantasma del Chat", ferocious = false),
}
