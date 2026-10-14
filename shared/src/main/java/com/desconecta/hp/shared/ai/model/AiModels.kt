package com.desconecta.hp.shared.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =====================================================================================
// Vocabulario común de la IA. Los nombres serializados coinciden con los valores de los
// CHECK de Supabase (ai_insights, ai_settings), así viajan igual a la nube y al reloj.
// =====================================================================================

/** Las funciones de IA que el jugador puede activar o desactivar una a una. */
@Serializable
enum class AiFeature {
    @SerialName("oraculo") ORACLE,
    @SerialName("deteccion_compulsiva") COMPULSIVE,
    @SerialName("coach") COACH,
    @SerialName("voz") VOICE,
    @SerialName("resumenes") JOURNAL,
    @SerialName("jefes_ia") BOSSES,
    @SerialName("limites") LIMITS,
    @SerialName("contexto") CONTEXT,
}

@Serializable
enum class InsightType {
    @SerialName("resumen_diario") DAILY_SUMMARY,
    @SerialName("resumen_semanal") WEEKLY_SUMMARY,
    @SerialName("sugerencia_limite") LIMIT_SUGGESTION,
    @SerialName("prediccion") PREDICTION,
    @SerialName("alerta_compulsiva") COMPULSIVE_ALERT,
    @SerialName("mensaje_coach") COACH_MESSAGE,
}

/**
 * Certeza expresada en palabras, nunca en porcentajes (People + AI: comunicar la
 * confianza de forma que se entienda sin conocimientos técnicos).
 */
@Serializable
enum class Confidence(val phrase: String) {
    @SerialName("baja") LOW("Quizá"),
    @SerialName("media") MEDIUM("Probablemente"),
    @SerialName("alta") HIGH("Casi seguro"),
}

/** Quién produjo el resultado (se muestra en el Centro de transparencia). */
@Serializable
enum class AiOrigin(val label: String) {
    @SerialName("reglas") RULES("Reglas"),
    @SerialName("dispositivo") DEVICE("IA en tu teléfono"),
    @SerialName("nube") CLOUD("IA en la nube"),
}

@Serializable
enum class UserResponse {
    @SerialName("aceptada") ACCEPTED,
    @SerialName("ajustada") ADJUSTED,
    @SerialName("rechazada") REJECTED,
    @SerialName("sin_respuesta") NONE,
}

@Serializable
enum class AiFeedback {
    @SerialName("positivo") POSITIVE,
    @SerialName("negativo") NEGATIVE,
}

@Serializable
enum class CoachTone(val label: String) {
    @SerialName("motivador") MOTIVATOR("Motivador"),
    @SerialName("divertido") FUNNY("Divertido"),
    @SerialName("sabio") WISE("Sabio"),
    @SerialName("directo") DIRECT("Directo"),
}

/** Configuración de IA del jugador (espejo de la tabla ai_settings). */
@Serializable
data class AiSettings(
    val enabled: Set<AiFeature> = AiFeature.entries.toSet() - AiFeature.CONTEXT,
    val tone: CoachTone = CoachTone.MOTIVATOR,
    /** Mínimo 45: la IA no debe convertirse en otra fuente de distracción. */
    val coachEveryMinutes: Int = 45,
    /** IA en la nube: desactivada por defecto y con consentimiento propio. */
    val cloudAllowed: Boolean = false,
    /** Epoch ms del primer día con datos (para el periodo de aprendizaje de 7 días). */
    val learningStartedAt: Long = 0L,
    /** Modo familiar (menores): la nube queda desactivada SIEMPRE, la decida quien la decida. */
    val familyMode: Boolean = false,
    /** Epoch ms del consentimiento para la IA en la nube (0 = nunca dado). */
    val cloudConsentAt: Long = 0L,
    /** Epoch ms del último cambio: resuelve conflictos con el servidor (gana el más reciente). */
    val updatedAt: Long = 0L,
) {
    fun isOn(feature: AiFeature) = feature in enabled

    /** La nube solo puede estar activa con consentimiento y fuera del modo familiar. */
    val cloudEffective: Boolean get() = cloudAllowed && !familyMode && cloudConsentAt > 0

    fun toggle(feature: AiFeature, on: Boolean, now: Long) =
        copy(enabled = if (on) enabled + feature else enabled - feature, updatedAt = now)

    /** Activar la nube registra el consentimiento; el modo familiar lo impide. */
    fun withCloud(allowed: Boolean, now: Long): AiSettings = when {
        allowed && familyMode -> this
        allowed -> copy(cloudAllowed = true, cloudConsentAt = now, updatedAt = now)
        else -> copy(cloudAllowed = false, updatedAt = now)
    }

    /** El modo familiar apaga la nube en el mismo cambio (nunca queda un estado intermedio). */
    fun withFamilyMode(on: Boolean, now: Long): AiSettings =
        copy(familyMode = on, cloudAllowed = if (on) false else cloudAllowed, updatedAt = now)

    fun withCoachEvery(minutes: Int, now: Long) =
        copy(coachEveryMinutes = minutes.coerceAtLeast(MIN_COACH_MINUTES), updatedAt = now)

    companion object {
        const val MIN_COACH_MINUTES = 45
        const val LEARNING_DAYS = 7
        val COACH_FREQUENCIES = listOf(45, 60, 90, 120)

        /**
         * Día del periodo de aprendizaje: 1 el primer día, 7 al empezar el séptimo.
         * [completedDays] = días ya cerrados con datos desde que empezó (o se reinició) el aprendizaje.
         */
        fun learningDay(completedDays: Int): Int = (completedDays + 1).coerceIn(1, LEARNING_DAYS)

        fun isLearning(completedDays: Int): Boolean = completedDays < LEARNING_DAYS

        /**
         * Conflicto con el servidor: gana el cambio más reciente (updated_at). El modo familiar
         * siempre viene del perfil del servidor y, si está activo, la nube queda apagada.
         */
        fun resolve(local: AiSettings, server: AiSettings, serverFamilyMode: Boolean): AiSettings {
            val winner = if (server.updatedAt > local.updatedAt) server else local
            return winner.copy(
                familyMode = serverFamilyMode,
                cloudAllowed = winner.cloudAllowed && !serverFamilyMode,
                learningStartedAt = maxOf(local.learningStartedAt, server.learningStartedAt),
            )
        }
    }
}

/**
 * Un resultado de IA tal como se guarda (Room → ai_insights) y se muestra.
 *
 * @param reason el "¿Por qué?" en una línea ("Basado en tus últimos 7 martes").
 */
@Serializable
data class AiInsight(
    val id: String,
    val type: InsightType,
    val content: String,
    val reason: String,
    val confidence: Confidence,
    val origin: AiOrigin,
    val createdAt: Long,
    val response: UserResponse = UserResponse.NONE,
    val feedback: AiFeedback? = null,
    /** Datos estructurados del resultado (p. ej. el límite propuesto), en JSON. */
    val payload: String? = null,
)

// --- Resultados de cada función ------------------------------------------------------

/** 3.1 Oráculo: cuándo llegará a 0 HP y dónde estará la vida dentro de 1 hora. */
@Serializable
data class HpPrediction(
    /** Epoch ms del Game Over previsto, o null si al ritmo actual no llega. */
    val gameOverAt: Long?,
    val hpInOneHour: Int,
    /** Monstruo que más vida quitará en la próxima hora. */
    val mostDangerous: String?,
    /** Aviso de ritmo anormal ("Hoy Scrollzilla ataca el doble que de costumbre"). */
    val anomaly: String? = null,
)

/** 3.2 Patrón compulsivo detectado ("Modo berserker"). */
@Serializable
data class CompulsiveSignal(
    val kind: Kind,
    val packageName: String,
    val minutes: Int,
    val opens: Int = 0,
) {
    @Serializable
    enum class Kind {
        @SerialName("sesion_larga") LONG_SESSION,
        @SerialName("aperturas_repetidas") REPEATED_OPENS,
        @SerialName("uso_nocturno") LATE_NIGHT,
    }
}

/** 3.3 Propuesta de límite (nunca se aplica sin aprobación). */
@Serializable
data class LimitSuggestion(
    /** null = vida máxima diaria; si no, el paquete del monstruo. */
    val packageName: String?,
    val currentMinutes: Int,
    val suggestedMinutes: Int,
    val weekendMinutes: Int? = null,
)

/** 3.4 Frase del coach (≤ 80 caracteres en el reloj, ya validada). */
@Serializable
data class CoachLine(val text: String, val tone: CoachTone)

/** 3.5 Lo que la IA entendió de un comando de voz. */
@Serializable
sealed interface VoiceIntent {
    /** Si true, se muestra "¿Usar 1 poción? +10 HP" antes de ejecutar. */
    val needsConfirmation: Boolean

    @Serializable @SerialName("consultar_vida")
    data object CheckHealth : VoiceIntent { override val needsConfirmation = false }

    @Serializable @SerialName("usar_objeto")
    data class UseItem(val item: String, val quantity: Int = 1) : VoiceIntent { override val needsConfirmation = true }

    @Serializable @SerialName("activar_escudo")
    data class ActivateShield(val packageName: String, val minutes: Int = 15) : VoiceIntent { override val needsConfirmation = true }

    @Serializable @SerialName("consultar_monstruo")
    data object TopMonster : VoiceIntent { override val needsConfirmation = false }

    @Serializable @SerialName("cambiar_limite")
    data class ChangeLimit(val packageName: String, val minutes: Int) : VoiceIntent { override val needsConfirmation = true }

    /** No se entendió: se ofrecen sugerencias tocables. */
    @Serializable @SerialName("desconocida")
    data class Unknown(val suggestions: List<String>) : VoiceIntent { override val needsConfirmation = false }
}

/** 3.6 Diario de batalla. */
@Serializable
data class BattleJournal(
    val dayKey: String,
    /** 2–3 líneas para el reloj. */
    val short: String,
    /** Versión completa para el teléfono. */
    val full: String,
    val worstHour: Int? = null,
)

/** 3.7 Jefe final semanal propuesto. */
@Serializable
data class BossProposal(
    val name: String,
    val description: String,
    /** Meta medible, p. ej. "minutos_despues_23h <= 0 durante 5 noches". */
    val goal: String,
    val target: Int,
    val reward: String,
    /** 1 (fácil) … 5 (difícil): baja si el jugador falló el jefe anterior. */
    val difficulty: Int,
)

/** 3.8 Sugerencia de modo según el contexto aprendido. */
@Serializable
data class ContextSuggestion(val mode: Mode, val startsAt: Long, val minutes: Int) {
    @Serializable
    enum class Mode {
        @SerialName("estudio") STUDY,
        @SerialName("descanso") REST,
    }
}
