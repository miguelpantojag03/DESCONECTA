package com.desconecta.hp.shared.datalayer

/**
 * Rutas de la Wearable Data Layer API, compartidas por reloj y teléfono para que
 * nunca se desincronicen.
 *
 * - MessageClient: eventos instantáneos y efímeros (si el otro lado no está, se pierden;
 *   no importa, porque el estado completo llega por DataClient).
 * - DataClient: estado completo, persistente y sincronizado al reconectar.
 */
object DataLayerPaths {
    // DataClient (estado)
    const val GAME_STATE = "/hp/state"           // GameState serializado (JSON)
    const val ACHIEVEMENTS = "/hp/achievements"

    // MessageClient: teléfono → reloj
    const val MSG_DAMAGE = "/hp/event/damage"
    const val MSG_WARNING = "/hp/event/warning"
    const val MSG_MONSTER_DEFEATED = "/hp/event/monster-defeated"
    const val MSG_GAME_OVER = "/hp/event/game-over"
    const val MSG_LEVEL_UP = "/hp/event/level-up"
    const val MSG_ACHIEVEMENT = "/hp/event/achievement"

    // MessageClient: reloj → teléfono (comandos; el teléfono es la fuente de verdad)
    const val CMD_USE_ITEM = "/hp/cmd/use-item"
    const val CMD_SET_LIMIT = "/hp/cmd/set-limit"
    const val CMD_REVIVE = "/hp/cmd/revive"

    /** Capacidad declarada en wear.xml para descubrir el nodo del otro dispositivo. */
    const val CAPABILITY_PHONE = "hp_phone_app"
    const val CAPABILITY_WATCH = "hp_watch_app"
}
