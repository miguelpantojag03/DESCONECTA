package com.desconecta.hp.shared.ai.safety

import com.desconecta.hp.shared.ai.model.AiSettings
import java.text.Normalizer

/**
 * Validador de TODO texto generado (plantillas, Gemini Nano o nube) antes de mostrarlo.
 * Si una frase incumple cualquier regla se descarta y se usa una plantilla.
 *
 * Reglas (sección 3.4): amable, sin culpa ni vergüenza, sin diagnósticos de salud mental,
 * sin hablar de peso o cuerpo, sin comparaciones con otras personas, máximo 80
 * caracteres en el reloj.
 */
object OutputValidator {

    const val WATCH_MAX = 80
    const val PHONE_MAX = 280

    enum class Violation { EMPTY, TOO_LONG, BLAME_OR_SHAME, MENTAL_HEALTH, BODY, COMPARISON, LINK, MARKUP }

    /** Raíces normalizadas (sin acentos, minúsculas). Se buscan como prefijo de palabra. */
    private val blame = listOf(
        "culpa", "verguenza", "avergonz", "fracas", "patetic", "inutil", "perdedor", "flojo", "vago",
        "deberias sentirte", "decepcion", "mal jugador", "debil", "patetico", "ridicul", "estupid", "tonto",
    )
    private val mentalHealth = listOf(
        "adict", "adiccion", "depresi", "deprimid", "ansiedad", "ansios", "trastorno", "diagnost", "terapia",
        "psicolog", "psiquiatr", "tdah", "suicid", "autoles", "enfermedad mental", "salud mental",
    )
    private val body = listOf(
        "peso", "gord", "delgad", "flac", "obes", "dieta", "calori", "cuerpo", "kilos", "figura", "abdomen",
    )
    private val comparison = listOf(
        "que los demas", "que otras personas", "que tus amigos", "que el resto", "otros jugadores",
        "la mayoria de la gente", "todo el mundo", "mas que nadie", "peor que", "mejor que otros",
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    /** Longitud real en caracteres visibles (un emoji cuenta como 1). */
    fun visibleLength(text: String): Int = text.codePointCount(0, text.length)

    fun violations(text: String, maxLength: Int = WATCH_MAX): Set<Violation> {
        val out = mutableSetOf<Violation>()
        val t = text.trim()
        if (t.isEmpty()) return setOf(Violation.EMPTY)
        if (visibleLength(t) > maxLength) out += Violation.TOO_LONG
        val n = normalize(t)
        val words = Regex("[a-z0-9ñ]+").findAll(n).map { it.value }.toList()
        fun hits(stems: List<String>) = stems.any { stem ->
            if (" " in stem) n.contains(stem) else words.any { it.startsWith(stem) }
        }
        if (hits(blame)) out += Violation.BLAME_OR_SHAME
        if (hits(mentalHealth)) out += Violation.MENTAL_HEALTH
        if (hits(body)) out += Violation.BODY
        if (hits(comparison)) out += Violation.COMPARISON
        if (Regex("https?://|www\\.").containsMatchIn(n)) out += Violation.LINK
        if (Regex("[<>{}*#`]").containsMatchIn(t)) out += Violation.MARKUP
        return out
    }

    fun isValid(text: String, maxLength: Int = WATCH_MAX) = violations(text, maxLength).isEmpty()

    /** Devuelve [text] si es válido; si no, [fallback] (una plantilla ya revisada). */
    fun orFallback(text: String?, fallback: String, maxLength: Int = WATCH_MAX): String =
        text?.trim()?.takeIf { isValid(it, maxLength) } ?: fallback
}

/**
 * Contención (sección 5): como máximo un mensaje del coach cada [AiSettings.coachEveryMinutes]
 * (mínimo 45) y ninguno durante el modo descanso o el Game Over.
 */
object CoachThrottle {
    fun canSpeak(now: Long, lastSpokeAt: Long, settings: AiSettings, restMode: Boolean, gameOver: Boolean): Boolean {
        if (restMode || gameOver) return false
        val every = settings.coachEveryMinutes.coerceAtLeast(AiSettings.MIN_COACH_MINUTES) * 60_000L
        return lastSpokeAt == 0L || now - lastSpokeAt >= every
    }
}
