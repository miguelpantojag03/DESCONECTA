package com.desconecta.hp.shared.datalayer

/**
 * Rutas de la Wearable Data Layer API, compartidas por reloj y teléfono para que
 * nunca se desincronicen.
 *
 * - DataClient: estado completo, persistente y sincronizado al reconectar.
 * - MessageClient: eventos instantáneos y efímeros (si el otro lado no está, se pierden;
 *   no importa, porque el estado completo llega por DataClient al reconectar).
 */
object DataLayerPaths {
    /** DataItem con el GameState serializado (JSON) en la clave [KEY_STATE]. */
    const val GAME_STATE = "/hp/state"
    const val KEY_STATE = "state"
    const val KEY_UPDATED_AT = "updatedAt"

    /** Teléfono → reloj: [WireEvent] serializado. */
    const val EVENT = "/hp/event"

    /** Reloj → teléfono: [WireCommand]. */
    const val COMMAND = "/hp/cmd"

    /** Teléfono → reloj: [CommandResult] de un comando. */
    const val COMMAND_RESULT = "/hp/cmd-result"

    /** Capacidades declaradas en res/values/wear.xml para descubrir el otro dispositivo. */
    const val CAPABILITY_PHONE = "hp_phone_app"
    const val CAPABILITY_WATCH = "hp_watch_app"
}
