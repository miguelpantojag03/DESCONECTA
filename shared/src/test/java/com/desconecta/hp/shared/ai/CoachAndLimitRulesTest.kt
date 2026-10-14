package com.desconecta.hp.shared.ai

import com.desconecta.hp.shared.ai.engine.AiResult
import com.desconecta.hp.shared.ai.engine.DayUsage
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.ai.model.CoachTone
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.ai.rules.CoachRules
import com.desconecta.hp.shared.ai.rules.CoachSituation
import com.desconecta.hp.shared.ai.rules.CoachTemplates
import com.desconecta.hp.shared.ai.rules.LimitRules
import com.desconecta.hp.shared.ai.safety.OutputValidator
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.MonsterArt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachRulesTest {

    private val longName = "Scrollzilla, devorador d" // 24 caracteres
    private val maxName = "Scrollzilla, devorador de t" // 28: el máximo que permite el bestiario
    private val monster = Monster("com.zhiliaoapp.musically", "TikTok", longName, MonsterArt.SCROLLZILLA, limitMinutes = 45)
    private fun state(hp: Int, attacked: Boolean = false, regen: Boolean = false, phase: GamePhase = GamePhase.PLAYING) =
        GameState(dayKey = "2026-10-14", maxHp = 600, hp = hp, monsters = listOf(monster),
            activeAttacker = if (attacked) monster.packageName else null, regenerating = regen, phase = phase)

    private val noon = 1_000_000_000L
    private val settings = AiSettings()

    @Test fun fifteenTemplatesPerSituationAndTone() {
        CoachSituation.entries.forEach { s ->
            CoachTone.entries.forEach { t -> assertTrue("$s/$t", CoachTemplates.of(s, t).size >= 15) }
        }
        assertEquals("sin duplicados", CoachTemplates.all.size, CoachTemplates.all.toSet().size)
    }

    @Test fun everyTemplatePassesTheValidatorWithWorstCaseData() {
        val worst = state(hp = 600, attacked = true)
        CoachTemplates.all.forEach { t ->
            val text = CoachRules.fill(t, worst)
            assertTrue("\"$text\" → ${OutputValidator.violations(text)}", OutputValidator.isValid(text))
            assertFalse("marcador sin sustituir: $text", '{' in text)
        }
    }

    @Test fun longestNamesFallBackToTheAppNameInsteadOfCutting() {
        val m = monster.copy(name = maxName)
        val s = GameState(dayKey = "2026-10-14", maxHp = 600, hp = 600, monsters = listOf(m), activeAttacker = m.packageName)
        CoachTemplates.all.forEach { t ->
            val text = CoachRules.fill(t, s)
            assertTrue("\"$text\"", OutputValidator.isValid(text))
            if ("{m}" in t) assertTrue("nombre completo o nombre de la app", maxName in text || "TikTok" in text)
        }
    }

    @Test fun situations() {
        assertEquals(CoachSituation.LOW_HP, CoachRules.situation(state(100, attacked = true), 16))
        assertEquals(CoachSituation.ATTACKED, CoachRules.situation(state(400, attacked = true), 16))
        assertEquals(CoachSituation.REGENERATING, CoachRules.situation(state(400, regen = true), 16))
        assertEquals(CoachSituation.MORNING, CoachRules.situation(state(600), 9))
        assertEquals(CoachSituation.DOING_WELL, CoachRules.situation(state(450), 17))
        assertNull("nada útil que decir", CoachRules.situation(state(250), 13))
        assertNull("en Game Over calla", CoachRules.situation(state(0, phase = GamePhase.GAME_OVER), 13))
    }

    @Test fun respectsThrottleAndQuietHours() {
        val s = state(400, attacked = true)
        assertNotNull(CoachRules.pick(s, settings, noon, 16, lastSpokeAt = 0, emptySet(), emptySet(), seed = 1))
        assertNull("menos de 45 min desde la última", CoachRules.pick(s, settings, noon, 16, noon - 10 * 60_000, emptySet(), emptySet(), 1))
        assertNull("de noche calla", CoachRules.pick(s, settings, noon, 23, 0, emptySet(), emptySet(), 1))
        assertNull("con modo estudio calla", CoachRules.pick(s.copy(studyModeUntil = noon + 1), settings, noon, 16, 0, emptySet(), emptySet(), 1))
    }

    @Test fun neverRepeatsRecentOrDisliked() {
        val s = state(400, attacked = true)
        val keys = CoachTemplates.of(CoachSituation.ATTACKED, CoachTone.MOTIVATOR).indices.map { "ATTACKED.MOTIVATOR.$it" }
        val disliked = keys.drop(1).toSet()
        repeat(10) { seed ->
            assertEquals(keys[0], CoachRules.pick(s, settings, noon, 16, 0, emptySet(), disliked, seed)!!.templateKey)
        }
        assertNull("todas con 👎: mejor callar", CoachRules.pick(s, settings, noon, 16, 0, emptySet(), keys.toSet(), 3))
        val recent = keys.take(14).toSet()
        assertEquals(keys[14], CoachRules.pick(s, settings, noon, 16, 0, recent, emptySet(), 0)!!.templateKey)
    }

    @Test fun usesChosenTone() {
        val pick = CoachRules.pick(state(400, attacked = true), settings.copy(tone = CoachTone.DIRECT), noon, 16, 0, emptySet(), emptySet(), 2)!!
        assertTrue(pick.templateKey.startsWith("ATTACKED.DIRECT."))
        assertTrue(pick.reason.contains(longName))
    }
}

class LimitRulesTest {

    private val tiktok = Monster("tiktok", "TikTok", "Scrollzilla", MonsterArt.SCROLLZILLA, limitMinutes = 60)
    private val yt = Monster("yt", "YouTube", "Rey Autoplay", MonsterArt.SCROLLZILLA, limitMinutes = 30)
    private val state = GameState(dayKey = "2026-10-14", monsters = listOf(tiktok, yt))

    private fun days(vararg minutes: Pair<String, List<Int>>): List<DayUsage> {
        val n = minutes.first().second.size
        return (0 until n).map { d ->
            DayUsage("2026-10-${13 - d}", 1, listOf(minutes.associate { (pkg, list) -> pkg to list[d] }), 100, false)
        }
    }

    @Test fun learningWithLessThan7Days() {
        val r = LimitRules.suggest(state, days("tiktok" to List(4) { 20 }), emptySet())
        assertEquals(AiResult.Learning(5, 7), r)
    }

    @Test fun lowersLimitWhenUsageIsWellBelow() {
        val r = LimitRules.split(state, days("tiktok" to List(7) { 30 }, "yt" to List(7) { 28 }), emptySet())
        assertEquals(1, r.size)
        assertEquals("tiktok", r[0].value.packageName)
        assertEquals(25, r[0].value.suggestedMinutes) // 30 × 0,9 = 27 → 25
        assertEquals(Confidence.HIGH, r[0].confidence)
        assertTrue(r[0].reason.contains("30 min"))
    }

    @Test fun proposesReachableLimitWhenAlwaysExceeded() {
        val r = LimitRules.split(state, days("tiktok" to List(7) { 40 }, "yt" to listOf(70, 50, 60, 55, 65, 10, 20)), emptySet())
        val y = r.single { it.value.packageName == "yt" }.value
        assertTrue("más alto que el actual", y.suggestedMinutes > 30)
        assertEquals(40, y.suggestedMinutes) // (30 + 47,1) / 2 = 38,6 → 40
    }

    @Test fun skipsPendingOrRejected() {
        val r = LimitRules.split(state, days("tiktok" to List(7) { 30 }), skip = setOf("tiktok"))
        assertTrue(r.isEmpty())
    }

    @Test fun irregularUsageMeansLowConfidence() {
        assertEquals(Confidence.LOW, LimitRules.confidence(listOf(0, 90, 5, 80, 0, 100, 10)))
        assertEquals(Confidence.HIGH, LimitRules.confidence(listOf(30, 32, 28, 31, 29, 30, 30)))
    }
}
