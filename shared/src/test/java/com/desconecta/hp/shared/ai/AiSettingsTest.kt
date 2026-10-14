package com.desconecta.hp.shared.ai

import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSettingsTest {

    @Test fun cloudIsOffByDefault() {
        val s = AiSettings()
        assertFalse(s.cloudAllowed)
        assertFalse(s.cloudEffective)
        assertFalse("contexto empieza apagado", s.isOn(AiFeature.CONTEXT))
    }

    @Test fun enablingCloudRecordsConsent() {
        val s = AiSettings().withCloud(true, now = 1_000)
        assertTrue(s.cloudEffective)
        assertEquals(1_000, s.cloudConsentAt)
        assertEquals(1_000, s.updatedAt)
    }

    @Test fun familyModeBlocksCloud() {
        val family = AiSettings().withFamilyMode(true, now = 1)
        assertFalse("no se puede activar la nube en modo familiar", family.withCloud(true, now = 2).cloudEffective)
        val wasCloud = AiSettings().withCloud(true, 1).withFamilyMode(true, 2)
        assertFalse("activar el modo familiar apaga la nube en el mismo cambio", wasCloud.cloudAllowed)
    }

    @Test fun coachFrequencyNeverBelow45() {
        assertEquals(45, AiSettings().withCoachEvery(10, now = 1).coachEveryMinutes)
        assertEquals(90, AiSettings().withCoachEvery(90, now = 1).coachEveryMinutes)
    }

    @Test fun learningDays() {
        assertEquals(1, AiSettings.learningDay(0))
        assertEquals(4, AiSettings.learningDay(3))
        assertEquals(7, AiSettings.learningDay(30))
        assertTrue(AiSettings.isLearning(6))
        assertFalse(AiSettings.isLearning(7))
    }

    @Test fun conflictLatestWins() {
        val local = AiSettings().toggle(AiFeature.COACH, false, now = 100)
        val server = AiSettings().toggle(AiFeature.JOURNAL, false, now = 200)
        val r = AiSettings.resolve(local, server, serverFamilyMode = false)
        assertTrue("gana el servidor (más reciente)", r.isOn(AiFeature.COACH) && !r.isOn(AiFeature.JOURNAL))

        val r2 = AiSettings.resolve(local.copy(updatedAt = 300), server, serverFamilyMode = false)
        assertFalse("gana el local (más reciente)", r2.isOn(AiFeature.COACH))
    }

    @Test fun serverFamilyModeAlwaysApplies() {
        val local = AiSettings().withCloud(true, now = 500)
        val r = AiSettings.resolve(local, AiSettings(updatedAt = 1), serverFamilyMode = true)
        assertTrue(r.familyMode)
        assertFalse("aunque gane el local, el modo familiar apaga la nube", r.cloudEffective)
    }

    @Test fun learningRestartIsKept() {
        // "Borrar mis datos de IA" en otro dispositivo reinicia el aprendizaje en el servidor.
        val local = AiSettings(learningStartedAt = 10, updatedAt = 900)
        val r = AiSettings.resolve(local, AiSettings(learningStartedAt = 50, updatedAt = 1), serverFamilyMode = false)
        assertEquals(50, r.learningStartedAt)
    }
}
