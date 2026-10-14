package com.desconecta.hp.mobile.ai

import com.desconecta.hp.mobile.ai.data.AiInsightRepository
import com.desconecta.hp.mobile.ai.data.AiSettingsRepository
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.shared.ai.engine.DayUsage
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiInsight
import com.desconecta.hp.shared.ai.model.InsightType
import com.desconecta.hp.shared.ai.model.LimitSuggestion
import com.desconecta.hp.shared.ai.model.UserResponse
import com.desconecta.hp.shared.ai.rules.CoachRules
import com.desconecta.hp.shared.ai.rules.LimitRules
import com.desconecta.hp.shared.datalayer.WireCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ejecuta las funciones de IA de la fase 2 en el teléfono:
 *  · Coach: se evalúa cada minuto y al cambiar el estado (ataque, regeneración, Game Over).
 *    Las reglas deciden si hay algo útil que decir; el limitador impide hablar más de una
 *    vez cada N minutos (≥ 45) y en el modo descanso.
 *  · Límites inteligentes: como mucho una vez al día, tras 7 días de datos.
 *
 * Por ahora todo usa las reglas (origen "Reglas"): Gemini Nano y la nube llegan en fases
 * posteriores y, si fallan, volverán aquí.
 */
@Singleton
class AiCoordinator @Inject constructor(
    private val game: GameRepository,
    private val settings: AiSettingsRepository,
    private val insights: AiInsightRepository,
    private val db: HpDatabase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            while (true) {
                runCatching { tick() }
                delay(60_000)
            }
        }
        // Reacciona en cuanto empieza un ataque o una pausa, sin esperar al minuto.
        scope.launch {
            game.state.filterNotNull()
                .map { Triple(it.activeAttacker, it.regenerating, it.phase) }
                .distinctUntilChanged()
                .collect { runCatching { tick() } }
        }
    }

    private suspend fun tick() = mutex.withLock {
        coach()
        limits()
    }

    // --- Coach ---------------------------------------------------------------------------

    private suspend fun coach() {
        val s = game.state.value ?: return
        val cfg = settings.settings.value
        if (!cfg.isOn(AiFeature.COACH)) return
        val now = System.currentTimeMillis()
        val hour = LocalDateTime.now().hour
        val pick = CoachRules.pick(
            state = s,
            settings = cfg,
            now = now,
            hour = hour,
            lastSpokeAt = insights.lastAt(InsightType.COACH_MESSAGE),
            recentKeys = insights.recentTemplates(),
            dislikedKeys = insights.dislikedTemplates(),
            seed = (now / 60_000).toInt(),
        ) ?: return
        val r = CoachRules.asResult(pick)
        insights.save(
            AiInsight(
                id = UUID.randomUUID().toString(),
                type = InsightType.COACH_MESSAGE,
                content = r.value.text,
                reason = r.reason,
                confidence = r.confidence,
                origin = r.origin,
                createdAt = now,
            ),
            templateKey = pick.templateKey,
        )
    }

    // --- Límites inteligentes --------------------------------------------------------------

    private suspend fun limits() {
        val s = game.state.value ?: return
        if (!settings.settings.value.isOn(AiFeature.LIMITS)) return
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        // Una vez al día como mucho (también tras reiniciar la app).
        if (insights.since(InsightType.LIMIT_SUGGESTION, startOfDay).isNotEmpty()) return
        if (lastLimitCheckDay == LocalDate.now()) return
        lastLimitCheckDay = LocalDate.now()

        val week = startOfDay - 7 * 86_400_000L
        val skip = (insights.pending(InsightType.LIMIT_SUGGESTION).first() +
            insights.since(InsightType.LIMIT_SUGGESTION, week).filter { it.response == UserResponse.REJECTED })
            .mapNotNull { it.suggestion()?.packageName }.toSet()

        val history = history(s.maxHp)
        LimitRules.split(s, history, skip).forEach { r ->
            val m = s.monster(r.value.packageName.orEmpty()) ?: return@forEach
            insights.save(
                AiInsight(
                    id = UUID.randomUUID().toString(),
                    type = InsightType.LIMIT_SUGGESTION,
                    content = "${m.appLabel}: ${r.value.currentMinutes} → ${r.value.suggestedMinutes} min al día",
                    reason = r.reason,
                    confidence = r.confidence,
                    origin = r.origin,
                    createdAt = System.currentTimeMillis(),
                    payload = WireCodec.json.encodeToString(LimitSuggestion.serializer(), r.value),
                ),
            )
        }
    }

    @Volatile private var lastLimitCheckDay: LocalDate? = null

    /** Días anteriores (más reciente primero) con minutos por hora y app, solo agregados. */
    private suspend fun history(maxHp: Int): List<DayUsage> {
        val today = LocalDate.now()
        val usage = db.usage().sinceOnce(today.minusDays(21).toString()).filter { it.dayKey < today.toString() && !it.isAlly }
        val closed = db.history().recent(30).first().associateBy { it.dayKey }
        val days = (usage.map { it.dayKey } + closed.keys.filter { it < today.toString() }).toSortedSet().reversed()
        return days.map { day ->
            val rows = usage.filter { it.dayKey == day }
            val hourly = (0 until 24).map { h -> rows.filter { it.hour == h }.associate { it.packageName to it.minutes } }
            DayUsage(
                dayKey = day,
                dayOfWeek = LocalDate.parse(day).dayOfWeek.value,
                hourly = hourly,
                hpLeft = closed[day]?.hpLeft ?: maxHp,
                gameOver = closed[day]?.gameOver ?: false,
            )
        }
    }

    // --- Respuestas del jugador ---------------------------------------------------------

    /** Aceptar (o ajustar) un límite: lo aplica el juego y se sincroniza como cualquier cambio. */
    suspend fun acceptLimit(insight: AiInsight, minutes: Int) {
        val suggestion = insight.suggestion() ?: return
        val s = game.state.value ?: return
        val pkg = suggestion.packageName ?: return
        game.updateMonsters(s.monsters.map { if (it.packageName == pkg) it.copy(limitMinutes = minutes) else it })
        insights.respond(insight.id, if (minutes == suggestion.suggestedMinutes) UserResponse.ACCEPTED else UserResponse.ADJUSTED)
    }

    suspend fun rejectLimit(insight: AiInsight) = insights.respond(insight.id, UserResponse.REJECTED)

    private fun AiInsight.suggestion(): LimitSuggestion? =
        payload?.let { runCatching { WireCodec.json.decodeFromString(LimitSuggestion.serializer(), it) }.getOrNull() }
}
