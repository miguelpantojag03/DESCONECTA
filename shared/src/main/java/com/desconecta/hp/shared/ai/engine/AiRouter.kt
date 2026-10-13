package com.desconecta.hp.shared.ai.engine

import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiSettings

/** Nivel de IA que atiende una petición. */
enum class AiTier { RULES, DEVICE, CLOUD }

/** Lo que el teléfono puede hacer ahora mismo. */
data class AiCapabilities(
    /** Modelo de lenguaje en el dispositivo (Gemini Nano vía ML Kit GenAI) disponible. */
    val onDeviceLlm: Boolean,
    /** Modelo LiteRT de predicción entrenado y cargado. */
    val onDevicePredictor: Boolean,
    val online: Boolean,
    /** Hay sesión de Supabase (no "Modo solo en este dispositivo"). */
    val signedIn: Boolean,
    /** Modo familiar (menores): la nube queda desactivada SIEMPRE. */
    val familyMode: Boolean,
)

/** Decisión del router: usar un nivel o mostrar un estado sin resultado. */
sealed interface Route {
    data class Use(val tier: AiTier, val fallback: AiTier = AiTier.RULES) : Route
    data class Learning(val day: Int, val of: Int) : Route
    data class Disabled(val feature: AiFeature) : Route
}

/**
 * Elige quién responde cada función. Es una función pura (sin Android): se prueba con
 * tablas de casos. El orden de preferencia es nube → dispositivo → reglas, pero la nube
 * solo se usa si el jugador la autorizó, no es modo familiar, hay sesión y hay red; y
 * las funciones marcadas como "solo locales" nunca salen del teléfono.
 */
object AiRouter {

    /** Funciones que por diseño nunca usan la nube (privacidad y latencia). */
    private val localOnly = setOf(AiFeature.ORACLE, AiFeature.COMPULSIVE, AiFeature.VOICE, AiFeature.CONTEXT)

    /** Funciones que necesitan historial antes de opinar. */
    private val needsHistory = setOf(AiFeature.ORACLE, AiFeature.LIMITS, AiFeature.CONTEXT)

    fun cloudUsable(settings: AiSettings, caps: AiCapabilities) =
        settings.cloudAllowed && !caps.familyMode && caps.signedIn && caps.online

    fun route(feature: AiFeature, settings: AiSettings, caps: AiCapabilities, daysOfData: Int): Route {
        if (!settings.isOn(feature)) return Route.Disabled(feature)
        if (feature in needsHistory && daysOfData < AiSettings.LEARNING_DAYS) {
            return Route.Learning(daysOfData.coerceAtLeast(1), AiSettings.LEARNING_DAYS)
        }
        val device = when (feature) {
            AiFeature.ORACLE -> caps.onDevicePredictor
            AiFeature.COACH, AiFeature.VOICE, AiFeature.JOURNAL -> caps.onDeviceLlm
            // Estadística local (sin modelo de lenguaje): siempre disponible en el dispositivo.
            AiFeature.COMPULSIVE, AiFeature.LIMITS, AiFeature.CONTEXT -> true
            AiFeature.BOSSES -> false
        }
        return when {
            feature !in localOnly && cloudUsable(settings, caps) ->
                Route.Use(AiTier.CLOUD, fallback = if (device) AiTier.DEVICE else AiTier.RULES)
            device -> Route.Use(AiTier.DEVICE)
            else -> Route.Use(AiTier.RULES)
        }
    }
}
