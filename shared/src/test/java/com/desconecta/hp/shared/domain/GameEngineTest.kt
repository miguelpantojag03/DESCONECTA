package com.desconecta.hp.shared.domain

import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.Ally
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.HealthStatus
import com.desconecta.hp.shared.model.HpWarning
import com.desconecta.hp.shared.model.Inventory
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.Progression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    private var now = 1_000_000L
    private val engine = GameEngine { now }

    private val tiktok = Monster("com.zhiliaoapp.musically", "TikTok", "Scrollzilla", MonsterArt.SCROLLZILLA, limitMinutes = 30)
    private val base = GameState(
        dayKey = "2026-10-10",
        maxHp = 120,
        monsters = listOf(tiktok),
        allies = listOf(Ally("com.google.android.apps.maps", "Maps")),
    )

    @Test fun usageDealsDamageAndMarksAttacker() {
        val out = engine.onUsage(base, tiktok.packageName, 5)
        assertEquals(115, out.state.currentHp)
        assertEquals(5, out.state.monster(tiktok.packageName)!!.usedMinutes)
        assertEquals(tiktok.packageName, out.state.activeAttacker)
    }

    @Test fun alliesDoNoDamage() {
        val out = engine.onUsage(base, "com.google.android.apps.maps", 30)
        assertEquals(120, out.state.currentHp)
    }

    @Test fun warningsFireOnceWhenCrossingThresholds() {
        val out = engine.onUsage(base.copy(hp = 61), "com.other", 2) // 61 → 59: cruza 50 %
        assertEquals(listOf(HpWarning.HALF), out.events.filterIsInstance<GameEvent.Warning>().map { it.warning })
        val again = engine.onUsage(out.state, "com.other", 1)
        assertTrue(again.events.none { it is GameEvent.Warning })
    }

    @Test fun monsterDefeatedWhenLimitReached() {
        val state = base.copy(monsters = listOf(tiktok.copy(usedMinutes = 28)))
        val out = engine.onUsage(state, tiktok.packageName, 2)
        assertTrue(out.events.contains(GameEvent.MonsterDefeated(tiktok.packageName)))
        // Una vez derrotado, la app está bloqueada: no sigue haciendo daño.
        val after = engine.onUsage(out.state, tiktok.packageName, 5)
        assertEquals(out.state.currentHp, after.state.currentHp)
    }

    @Test fun gameOverAtZero() {
        val out = engine.onUsage(base.copy(hp = 3), "com.other", 10)
        assertEquals(0, out.state.currentHp)
        assertEquals(GamePhase.GAME_OVER, out.state.phase)
        assertEquals(HealthStatus.GAME_OVER, out.state.health.status)
        assertTrue(out.events.contains(GameEvent.GameOver))
    }

    @Test fun potionHealsTenAndNeverOverflows() {
        val out = engine.usePotion(base.copy(hp = 115))
        assertEquals(120, out.state.currentHp)
        assertEquals(0, out.state.inventory.potions)
        // Curar de más no "guarda" vida extra: el siguiente daño se ve de inmediato.
        assertEquals(119, engine.onUsage(out.state, "com.other", 1).state.currentHp)
    }

    @Test fun potionRejectedWithoutItemsOrAtFullHealth() {
        assertEquals(Rejection.FULL_HEALTH, engine.usePotion(base).rejection)
        assertEquals(Rejection.NO_ITEMS, engine.usePotion(base.copy(hp = 50, inventory = Inventory(potions = 0))).rejection)
    }

    @Test fun potionFromBlockScreenWakesMonsterTenMinutes() {
        val blocked = base.copy(hp = 80, monsters = listOf(tiktok.copy(usedMinutes = 30)))
        assertTrue(blocked.monster(tiktok.packageName)!!.isDefeated)
        val out = engine.usePotion(blocked, wakeMonster = tiktok.packageName)
        val awake = out.state.monster(tiktok.packageName)!!
        assertFalse(awake.isDefeated)
        assertEquals(10, awake.remainingMinutes)
        assertEquals(90, out.state.currentHp)
        // Al día siguiente el bono desaparece y el límite vuelve a ser el original.
        val tomorrow = engine.respawn(out.state, "2026-10-11", 7).state.monster(tiktok.packageName)!!
        assertEquals(30, tomorrow.effectiveLimit)
    }

    @Test fun shieldBlocksDamageFor15Minutes() {
        val shielded = engine.useShield(base, tiktok.packageName).state
        assertEquals(120, engine.onUsage(shielded, tiktok.packageName, 5).state.currentHp)
        now += 16 * 60_000L
        assertEquals(115, engine.onUsage(shielded, tiktok.packageName, 5).state.currentHp)
    }

    @Test fun regenerationCapsAtTwentyPerDay() {
        var s = base.copy(hp = 50)
        repeat(6) { s = engine.regenerate(s).state }
        assertEquals(70, s.currentHp)
        assertEquals(Rejection.REGEN_CAP, engine.regenerate(s).rejection)
    }

    @Test fun reviveOncePerWeek() {
        val dead = base.copy(hp = 0, phase = GamePhase.GAME_OVER)
        val revived = engine.revive(dead).state
        assertEquals(15, revived.currentHp)
        assertEquals(GamePhase.PLAYING, revived.phase)
        val deadAgain = revived.copy(hp = 0, phase = GamePhase.GAME_OVER)
        assertEquals(Rejection.NOT_AVAILABLE, engine.revive(deadAgain).rejection)
    }

    @Test fun respawnGivesXpStreakAndResetsDay() {
        val end = base.copy(hp = 70, monsters = listOf(tiktok.copy(usedMinutes = 20)))
        val out = engine.respawn(end, "2026-10-11", newDayOfWeek = 7)
        assertEquals(70, out.state.player.totalXp)
        assertEquals(1, out.state.player.streakDays)
        assertEquals(120, out.state.currentHp)
        assertEquals(0, out.state.monster(tiktok.packageName)!!.usedMinutes)
        assertTrue(AchievementId.FIRST_DAY in out.state.unlockedAchievements)
        assertTrue(AchievementId.HALF_LIFE in out.state.unlockedAchievements)
        assertTrue(AchievementId.NO_POTIONS_SCROLLZILLA in out.state.unlockedAchievements)
        assertTrue(out.events.first() is GameEvent.Respawn)
    }

    @Test fun gameOverBreaksStreak() {
        val dead = base.copy(hp = 0, phase = GamePhase.GAME_OVER, player = base.player.copy(streakDays = 5))
        val out = engine.respawn(dead, "2026-10-11", 7)
        assertEquals(0, out.state.player.streakDays)
        assertEquals(0, out.state.player.totalXp)
        assertFalse(AchievementId.FIRST_DAY in out.state.unlockedAchievements)
    }

    @Test fun levelUpEventWhenCrossingThreshold() {
        val almost = base.copy(hp = 120, player = base.player.copy(totalXp = Progression.xpForLevel(2) - 10))
        val out = engine.respawn(almost, "2026-10-11", 7)
        assertTrue(out.events.any { it is GameEvent.LevelUp })
    }

    @Test fun limitsSnapToFiveMinuteSteps() {
        val out = engine.setMonsterLimit(base, tiktok.packageName, 43)
        assertEquals(40, out.state.monster(tiktok.packageName)!!.limitMinutes)
        assertNull(out.rejection)
    }

    @Test fun wireCodecRoundTripsFullState() {
        val state = com.desconecta.hp.shared.model.SampleData.gameState()
        val bytes = com.desconecta.hp.shared.datalayer.WireCodec.encodeState(state)
        assertEquals(state, com.desconecta.hp.shared.datalayer.WireCodec.decodeState(bytes))
        val cmd = com.desconecta.hp.shared.datalayer.WireCommand.UseShield("r1", "com.x")
        assertEquals(cmd, com.desconecta.hp.shared.datalayer.WireCodec.decodeCommand(com.desconecta.hp.shared.datalayer.WireCodec.encodeCommand(cmd)))
    }

    @Test fun statusThresholdsMatchSpec() {
        assertEquals(HealthStatus.HEALTHY, GameState("d", hp = 62).health.status) // 51,6 %
        assertEquals(HealthStatus.HURT, GameState("d", hp = 60).health.status)    // 50 %
        assertEquals(HealthStatus.HURT, GameState("d", hp = 25).health.status)    // 20,8 %
        assertEquals(HealthStatus.CRITICAL, GameState("d", hp = 24).health.status) // 20 %
    }
}
