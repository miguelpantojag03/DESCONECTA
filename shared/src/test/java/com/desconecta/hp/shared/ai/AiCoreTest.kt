package com.desconecta.hp.shared.ai

import com.desconecta.hp.shared.ai.engine.AiCapabilities
import com.desconecta.hp.shared.ai.engine.AiRouter
import com.desconecta.hp.shared.ai.engine.AiTier
import com.desconecta.hp.shared.ai.engine.Route
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiSettings
import com.desconecta.hp.shared.ai.safety.CoachThrottle
import com.desconecta.hp.shared.ai.safety.OutputValidator
import com.desconecta.hp.shared.ai.safety.OutputValidator.Violation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputValidatorTest {

    @Test fun acceptsKindShortLines() {
        listOf(
            "Día nuevo, 120 HP. ¡A cuidarlos!",
            "Vas mejor que ayer a esta hora 💪",
            "Si aguantas 30 min, tu racha llega a 8 días 🔥",
        ).forEach { assertTrue(it, OutputValidator.isValid(it)) }
    }

    @Test fun rejectsTooLongForWatch() {
        val long = "a".repeat(81)
        assertEquals(setOf(Violation.TOO_LONG), OutputValidator.violations(long))
        assertTrue(OutputValidator.isValid(long, OutputValidator.PHONE_MAX))
    }

    @Test fun emojiCountsAsOneCharacter() {
        assertTrue(OutputValidator.isValid("x".repeat(79) + "🔥"))
    }

    @Test fun rejectsBlameShameAndInsults() {
        assertTrue(Violation.BLAME_OR_SHAME in OutputValidator.violations("Deberías sentirte mal, eres un fracaso"))
        assertTrue(Violation.BLAME_OR_SHAME in OutputValidator.violations("Qué vergüenza, otra vez TikTok"))
    }

    @Test fun rejectsMentalHealthDiagnosesEvenWithoutAccents() {
        assertTrue(Violation.MENTAL_HEALTH in OutputValidator.violations("Esto parece una adicción"))
        assertTrue(Violation.MENTAL_HEALTH in OutputValidator.violations("tienes ANSIEDAD por el feed"))
    }

    @Test fun rejectsBodyAndComparisons() {
        assertTrue(Violation.BODY in OutputValidator.violations("Muévete y baja de peso"))
        assertTrue(Violation.COMPARISON in OutputValidator.violations("Usas el móvil más que tus amigos"))
    }

    @Test fun doesNotFlagInnocentWordsThatShareLetters() {
        // Se busca por raíz al inicio de palabra: "pescaste" no debe confundirse con "peso".
        assertTrue(OutputValidator.isValid("Hoy pescaste una racha de 5 días"))
    }

    @Test fun rejectsLinksAndMarkup() {
        assertTrue(Violation.LINK in OutputValidator.violations("Mira https://ejemplo.com"))
        assertTrue(Violation.MARKUP in OutputValidator.violations("**¡Bien!**"))
    }

    @Test fun fallbackIsUsedWhenGeneratedTextFails() {
        assertEquals("Plantilla segura", OutputValidator.orFallback("Eres un perdedor", "Plantilla segura"))
        assertEquals("Plantilla segura", OutputValidator.orFallback(null, "Plantilla segura"))
        assertEquals("¡Bien hecho!", OutputValidator.orFallback("  ¡Bien hecho!  ", "Plantilla segura"))
    }
}

class CoachThrottleTest {
    private val s = AiSettings()

    @Test fun atMostOneMessagePer45Minutes() {
        assertTrue(CoachThrottle.canSpeak(now = 1_000, lastSpokeAt = 0, settings = s, restMode = false, gameOver = false))
        assertFalse(CoachThrottle.canSpeak(44 * 60_000L, 0 + 1, s, false, false))
        assertTrue(CoachThrottle.canSpeak(45 * 60_000L + 1, 1, s, false, false))
    }

    @Test fun neverDuringRestOrGameOver() {
        assertFalse(CoachThrottle.canSpeak(10_000_000, 1, s, restMode = true, gameOver = false))
        assertFalse(CoachThrottle.canSpeak(10_000_000, 1, s, restMode = false, gameOver = true))
    }

    @Test fun frequencyCannotGoBelowMinimum() {
        val pushy = s.copy(coachEveryMinutes = 5)
        assertFalse(CoachThrottle.canSpeak(10 * 60_000L, 1, pushy, false, false))
    }
}

class AiRouterTest {
    private val full = AiCapabilities(onDeviceLlm = true, onDevicePredictor = true, online = true, signedIn = true, familyMode = false)
    private val cloudOn = AiSettings(cloudAllowed = true)

    @Test fun cloudIsOffByDefault() {
        assertEquals(Route.Use(AiTier.DEVICE), AiRouter.route(AiFeature.COACH, AiSettings(), full, daysOfData = 10))
    }

    @Test fun cloudWhenAllowedWithDeviceFallback() {
        assertEquals(Route.Use(AiTier.CLOUD, AiTier.DEVICE), AiRouter.route(AiFeature.COACH, cloudOn, full, 10))
    }

    @Test fun familyModeNeverUsesCloud() {
        val r = AiRouter.route(AiFeature.JOURNAL, cloudOn, full.copy(familyMode = true), 10)
        assertEquals(Route.Use(AiTier.DEVICE), r)
    }

    @Test fun offlineOrNoAccountFallsBackLocally() {
        assertEquals(Route.Use(AiTier.DEVICE), AiRouter.route(AiFeature.COACH, cloudOn, full.copy(online = false), 10))
        assertEquals(Route.Use(AiTier.DEVICE), AiRouter.route(AiFeature.COACH, cloudOn, full.copy(signedIn = false), 10))
    }

    @Test fun withoutOnDeviceModelUsesRules() {
        val noModel = full.copy(onDeviceLlm = false)
        assertEquals(Route.Use(AiTier.RULES), AiRouter.route(AiFeature.COACH, AiSettings(), noModel, 10))
    }

    @Test fun privacySensitiveFeaturesNeverGoToCloud() {
        listOf(AiFeature.VOICE, AiFeature.COMPULSIVE, AiFeature.ORACLE, AiFeature.CONTEXT).forEach { f ->
            val r = AiRouter.route(f, cloudOn.copy(enabled = AiFeature.entries.toSet()), full, 10)
            assertTrue("$f no debe usar la nube", r is Route.Use && r.tier != AiTier.CLOUD)
        }
    }

    @Test fun learningPeriodBeforeSevenDays() {
        assertEquals(Route.Learning(3, 7), AiRouter.route(AiFeature.ORACLE, AiSettings(), full, daysOfData = 3))
        // El coach no necesita historial: habla desde el día 1.
        assertTrue(AiRouter.route(AiFeature.COACH, AiSettings(), full, daysOfData = 1) is Route.Use)
    }

    @Test fun disabledFeature() {
        val off = AiSettings(enabled = emptySet())
        assertEquals(Route.Disabled(AiFeature.COACH), AiRouter.route(AiFeature.COACH, off, full, 10))
    }
}
