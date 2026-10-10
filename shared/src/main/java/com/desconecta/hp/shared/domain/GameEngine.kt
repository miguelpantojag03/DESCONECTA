package com.desconecta.hp.shared.domain

import com.desconecta.hp.shared.model.Accessory
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.DaySummary
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.HpWarning
import com.desconecta.hp.shared.model.Inventory
import com.desconecta.hp.shared.model.ItemType
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.WeeklyBoss

/** Algo que pasó y que la interfaz debe comunicar (pantalla, vibración, sonido). */
sealed interface GameEvent {
    data class Damage(val packageName: String, val amount: Int) : GameEvent
    data class Warning(val warning: HpWarning, val attacker: String?) : GameEvent
    data class MonsterDefeated(val packageName: String) : GameEvent
    data object GameOver : GameEvent
    data class Healed(val amount: Int, val source: HealSource) : GameEvent
    data class ShieldActivated(val packageName: String, val until: Long) : GameEvent
    data class LevelUp(val newLevel: Int) : GameEvent
    data class AchievementUnlocked(val id: AchievementId) : GameEvent
    data class Respawn(val summary: DaySummary) : GameEvent
}

enum class HealSource { POTION, REGENERATION, REVIVE }

/** Por qué no se pudo hacer una acción (para mostrar un estado de error claro). */
enum class Rejection { NO_ITEMS, FULL_HEALTH, GAME_OVER, NOT_GAME_OVER, NOT_AVAILABLE, UNKNOWN_MONSTER, REGEN_CAP }

data class Outcome(
    val state: GameState,
    val events: List<GameEvent> = emptyList(),
    val rejection: Rejection? = null,
)

/**
 * Reglas del sistema de control de tiempo, como funciones puras: reciben un estado y
 * devuelven el nuevo estado + los eventos. Sin Android, sin I/O → 100 % testeable.
 * El teléfono es el único que las ejecuta (fuente de verdad); el reloj solo envía
 * comandos y muestra el resultado.
 */
class GameEngine(private val clock: () -> Long = System::currentTimeMillis) {

    // ---------------------------------------------------------------------------------
    // Uso del teléfono → daño
    // ---------------------------------------------------------------------------------

    fun onUsage(state: GameState, packageName: String, minutes: Int): Outcome {
        if (minutes <= 0 || state.phase == GamePhase.GAME_OVER) return Outcome(state)
        if (state.isAlly(packageName)) return Outcome(state.copy(activeAttacker = null))

        val now = clock()
        val monster = state.monster(packageName)
        if (monster != null && (monster.isShielded(now) || monster.isDefeated)) {
            return Outcome(state.copy(activeAttacker = null))
        }

        val events = mutableListOf<GameEvent>()
        val before = state.health
        val damage = minutes.coerceAtMost(state.currentHp)
        var next = state.copy(
            hp = state.currentHp - damage,
            usedMinutesToday = state.usedMinutesToday + minutes,
            activeAttacker = monster?.packageName,
            updatedAt = now,
        )
        events += GameEvent.Damage(packageName, damage)

        if (monster != null) {
            val updated = monster.copy(usedMinutes = monster.usedMinutes + minutes)
            next = next.copy(monsters = next.monsters.map { if (it.packageName == packageName) updated else it })
            if (!monster.isDefeated && updated.isDefeated) {
                events += GameEvent.MonsterDefeated(packageName)
            }
        }

        next.boss?.let { boss ->
            if (packageName in boss.packages) next = next.copy(boss = boss.copy(usedMinutes = boss.usedMinutes + minutes))
        }

        HpWarning.crossed(before, next.health).forEach {
            events += GameEvent.Warning(it, monster?.packageName)
        }

        if (next.currentHp == 0) {
            next = next.copy(phase = GamePhase.GAME_OVER, activeAttacker = null)
            events += GameEvent.GameOver
        }
        return Outcome(next, events)
    }

    /** El usuario dejó de usar el teléfono (pantalla apagada o app aliada). */
    fun onIdle(state: GameState): GameState = state.copy(activeAttacker = null)

    // ---------------------------------------------------------------------------------
    // Regeneración: +5 HP por cada 30 min seguidos sin usar el teléfono (máx. +20/día)
    // ---------------------------------------------------------------------------------

    fun regenerate(state: GameState): Outcome {
        if (state.phase == GamePhase.GAME_OVER) return Outcome(state, rejection = Rejection.GAME_OVER)
        if (state.regenToday >= REGEN_DAILY_CAP) return Outcome(state, rejection = Rejection.REGEN_CAP)
        if (state.currentHp >= state.maxHp) return Outcome(state, rejection = Rejection.FULL_HEALTH)

        val amount = minOf(REGEN_STEP, REGEN_DAILY_CAP - state.regenToday, state.maxHp - state.currentHp)
        var next = state.copy(hp = state.currentHp + amount, regenToday = state.regenToday + amount, updatedAt = clock())
        val events = mutableListOf<GameEvent>(GameEvent.Healed(amount, HealSource.REGENERATION))
        if (next.regenToday >= REGEN_DAILY_CAP) next = unlock(next, AchievementId.REGEN_MAX, events)
        return Outcome(next, events)
    }

    // ---------------------------------------------------------------------------------
    // Objetos (solo se usan desde el reloj)
    // ---------------------------------------------------------------------------------

    /**
     * @param wakeMonster si se usa desde la pantalla de bloqueo de una app, además de curar
     *   "despierta" a ese monstruo [Inventory.POTION_HP] minutos solo por hoy.
     */
    fun usePotion(state: GameState, wakeMonster: String? = null): Outcome {
        if (state.phase == GamePhase.GAME_OVER) return Outcome(state, rejection = Rejection.GAME_OVER)
        if (state.inventory.potions <= 0) return Outcome(state, rejection = Rejection.NO_ITEMS)
        val target = wakeMonster?.let { state.monster(it) }?.takeIf { it.isDefeated }
        if (state.currentHp >= state.maxHp && target == null) return Outcome(state, rejection = Rejection.FULL_HEALTH)

        val amount = minOf(Inventory.POTION_HP, state.maxHp - state.currentHp)
        var next = state.copy(
            hp = state.currentHp + amount,
            inventory = state.inventory.copy(potions = state.inventory.potions - 1),
            potionsUsedToday = state.potionsUsedToday + 1,
            monsters = if (target == null) state.monsters else state.monsters.map {
                if (it.packageName == target.packageName) it.copy(bonusMinutes = it.usedMinutes - it.limitMinutes + Inventory.POTION_HP) else it
            },
            updatedAt = clock(),
        )
        val events = mutableListOf<GameEvent>(GameEvent.Healed(amount, HealSource.POTION))
        next = unlock(next, AchievementId.FIRST_POTION, events)
        return Outcome(next, events)
    }

    fun useShield(state: GameState, packageName: String): Outcome {
        val monster = state.monster(packageName) ?: return Outcome(state, rejection = Rejection.UNKNOWN_MONSTER)
        if (state.phase == GamePhase.GAME_OVER) return Outcome(state, rejection = Rejection.GAME_OVER)
        if (state.inventory.shields <= 0) return Outcome(state, rejection = Rejection.NO_ITEMS)

        val now = clock()
        val until = maxOf(now, monster.shieldUntil ?: 0L) + Inventory.SHIELD_MINUTES * 60_000L
        var next = state.copy(
            monsters = state.monsters.map { if (it.packageName == packageName) it.copy(shieldUntil = until) else it },
            inventory = state.inventory.copy(shields = state.inventory.shields - 1),
            activeAttacker = if (state.activeAttacker == packageName) null else state.activeAttacker,
            updatedAt = now,
        )
        val events = mutableListOf<GameEvent>(GameEvent.ShieldActivated(packageName, until))
        next = unlock(next, AchievementId.FIRST_SHIELD, events)
        return Outcome(next, events)
    }

    /** Salir del Game Over con 15 HP. El PIN lo valida quien llama (nunca viaja en el estado). */
    fun revive(state: GameState): Outcome {
        if (state.phase != GamePhase.GAME_OVER) return Outcome(state, rejection = Rejection.NOT_GAME_OVER)
        val now = clock()
        if (!state.inventory.reviveReady(now)) return Outcome(state, rejection = Rejection.NOT_AVAILABLE)

        val hp = minOf(Inventory.REVIVE_HP, state.maxHp)
        val next = state.copy(
            hp = hp,
            phase = GamePhase.PLAYING,
            inventory = state.inventory.copy(reviveAvailableAt = now + Inventory.REVIVE_COOLDOWN_MS),
            revivedToday = true,
            updatedAt = now,
        )
        return Outcome(next, listOf(GameEvent.Healed(hp, HealSource.REVIVE)))
    }

    // ---------------------------------------------------------------------------------
    // Configuración de límites
    // ---------------------------------------------------------------------------------

    fun setMonsterLimit(state: GameState, packageName: String, minutes: Int): Outcome {
        if (state.monster(packageName) == null) return Outcome(state, rejection = Rejection.UNKNOWN_MONSTER)
        val limit = (minutes / LIMIT_STEP * LIMIT_STEP).coerceIn(LIMIT_MIN, LIMIT_MAX)
        return Outcome(
            state.copy(
                monsters = state.monsters.map { if (it.packageName == packageName) it.copy(limitMinutes = limit) else it },
                updatedAt = clock(),
            ),
        )
    }

    // ---------------------------------------------------------------------------------
    // Respawn de medianoche: XP, racha, recompensas, logros, nuevo día
    // ---------------------------------------------------------------------------------

    /**
     * @param newDayKey fecha del nuevo día ("2026-10-11").
     * @param newDayOfWeek 1 = lunes … 7 = domingo.
     * @param newMaxHp vida máxima del nuevo día (puede ser mayor el fin de semana).
     * @param newWeekBoss jefe de la nueva semana, si empieza una.
     */
    fun respawn(
        state: GameState,
        newDayKey: String,
        newDayOfWeek: Int,
        newMaxHp: Int = state.maxHp,
        newWeekBoss: WeeklyBoss? = null,
    ): Outcome {
        val events = mutableListOf<GameEvent>()
        val gameOver = state.phase == GamePhase.GAME_OVER
        val hpLeft = if (gameOver) 0 else state.currentHp
        val xp = hpLeft
        val summary = DaySummary(state.dayKey, state.maxHp, hpLeft, gameOver, xp)

        val oldLevel = state.player.level
        val streak = if (gameOver) 0 else state.player.streakDays + 1
        val player = state.player.copy(
            totalXp = state.player.totalXp + xp,
            streakDays = streak,
            bestStreak = maxOf(state.player.bestStreak, streak),
        )

        // Recompensas por racha: poción cada 3 días, escudo cada 7.
        var inventory = state.inventory
        if (streak > 0 && streak % 3 == 0) inventory = inventory.copy(potions = (inventory.potions + 1).coerceAtMost(Inventory.MAX_POTIONS))
        if (streak > 0 && streak % 7 == 0) inventory = inventory.copy(shields = (inventory.shields + 1).coerceAtMost(Inventory.MAX_SHIELDS))

        var next = state.copy(player = player, inventory = inventory)

        if (!gameOver) next = unlock(next, AchievementId.FIRST_DAY, events)
        if (streak >= 3) next = unlock(next, AchievementId.STREAK_3, events)
        if (streak >= 7) next = unlock(next, AchievementId.STREAK_7, events)
        if (streak >= 30) next = unlock(next, AchievementId.STREAK_30, events)
        if (hpLeft * 2 > state.maxHp) next = unlock(next, AchievementId.HALF_LIFE, events)
        if (state.revivedToday && !gameOver) next = unlock(next, AchievementId.COMEBACK, events)
        val scrollzilla = state.monsters.firstOrNull { it.art == MonsterArt.SCROLLZILLA }
        if (scrollzilla != null && !scrollzilla.isDefeated && state.potionsUsedToday == 0 && !gameOver) {
            next = unlock(next, AchievementId.NO_POTIONS_SCROLLZILLA, events)
        }

        // Jefe semanal: se derrota si el domingo termina dentro del presupuesto.
        var boss = state.boss
        var bossReward: Accessory? = null
        if (boss != null && boss.dayOfWeek >= 7 && !boss.failed) {
            next = unlock(next, AchievementId.BOSS_SLAYER, events)
            bossReward = boss.rewardAccessory
        }
        boss = if (newDayOfWeek == 1) newWeekBoss else boss?.copy(dayOfWeek = newDayOfWeek)
        if (bossReward != null) next = next.copy(player = next.player.copy(equipped = next.player.equipped + bossReward))

        if (next.player.level > oldLevel) {
            events += GameEvent.LevelUp(next.player.level)
            if (next.player.level >= 10) next = unlock(next, AchievementId.LEVEL_10, events)
        }

        next = next.copy(
            dayKey = newDayKey,
            maxHp = newMaxHp,
            hp = newMaxHp,
            usedMinutesToday = 0,
            regenToday = 0,
            phase = GamePhase.PLAYING,
            monsters = next.monsters.map { it.copy(usedMinutes = 0, shieldUntil = null, bonusMinutes = 0, history = (it.history + it.usedMinutes).takeLast(6)) },
            boss = boss,
            potionsUsedToday = 0,
            revivedToday = false,
            activeAttacker = null,
            studyModeUntil = 0L,
            updatedAt = clock(),
        )
        events.add(0, GameEvent.Respawn(summary))
        return Outcome(next, events)
    }

    private fun unlock(state: GameState, id: AchievementId, events: MutableList<GameEvent>): GameState {
        if (id in state.unlockedAchievements) return state
        events += GameEvent.AchievementUnlocked(id)
        return state.copy(unlockedAchievements = state.unlockedAchievements + (id to clock()))
    }

    companion object {
        const val REGEN_STEP = 5
        const val REGEN_DAILY_CAP = 20
        const val REGEN_IDLE_MINUTES = 30
        const val LIMIT_STEP = 5
        const val LIMIT_MIN = 5
        const val LIMIT_MAX = 600
    }
}
