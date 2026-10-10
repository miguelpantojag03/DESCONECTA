package com.desconecta.hp.shared.model

import kotlinx.serialization.Serializable

/**
 * App con límite propio. Cada monstruo tiene su mini barra de vida: su límite diario.
 *
 * @param packageName id de la app (clave única).
 * @param limitMinutes límite diario; cuando [usedMinutes] lo alcanza, el monstruo queda
 *   "derrotado... por hoy" y la app se bloquea.
 * @param shieldUntil instante (epoch ms) hasta el que un escudo lo protege: no hace daño.
 */
@Serializable
data class Monster(
    val packageName: String,
    val appLabel: String,
    val name: String,
    val art: MonsterArt,
    val limitMinutes: Int,
    val usedMinutes: Int = 0,
    val shieldUntil: Long? = null,
    /** Minutos usados los 6 días anteriores (el más reciente al final), para la gráfica semanal. */
    val history: List<Int> = emptyList(),
    /** Minutos extra solo por hoy (poción usada desde la pantalla de bloqueo). */
    val bonusMinutes: Int = 0,
) {
    /** Últimos 7 días incluyendo hoy. */
    val week: List<Int> get() = history.takeLast(6) + usedMinutes
    val effectiveLimit: Int get() = limitMinutes + bonusMinutes
    val remainingMinutes: Int get() = (effectiveLimit - usedMinutes).coerceAtLeast(0)
    val isDefeated: Boolean get() = usedMinutes >= effectiveLimit
    val health: Health get() = Health(remainingMinutes, effectiveLimit.coerceAtLeast(1))
    fun isShielded(now: Long): Boolean = shieldUntil != null && now < shieldUntil
}

/** App aliada: no hace daño (Maps, banco, mensajes de trabajo…). */
@Serializable
data class Ally(val packageName: String, val appLabel: String)

@Serializable
enum class ItemType { POTION, SHIELD, REVIVE }

@Serializable
data class Inventory(
    val potions: Int = 1,
    val shields: Int = 1,
    /** Epoch ms desde el que se puede volver a revivir (1 vez por semana). */
    val reviveAvailableAt: Long = 0L,
) {
    fun reviveReady(now: Long) = now >= reviveAvailableAt
    fun count(type: ItemType, now: Long): Int = when (type) {
        ItemType.POTION -> potions
        ItemType.SHIELD -> shields
        ItemType.REVIVE -> if (reviveReady(now)) 1 else 0
    }

    companion object {
        const val MAX_POTIONS = 3
        const val MAX_SHIELDS = 3
        const val POTION_HP = 10
        const val SHIELD_MINUTES = 15
        const val REVIVE_HP = 15
        const val REVIVE_COOLDOWN_MS = 7L * 24 * 60 * 60 * 1000
    }
}

@Serializable
enum class AvatarStyle(val label: String) {
    INDIGO("Índigo"),
    LILA("Lila"),
    CORAL("Coral"),
    ARENA("Arena"),
}

@Serializable
enum class Accessory(val label: String, val unlockLevel: Int) {
    GORRO("Gorro", 3),
    CAPA("Capa", 8),
    ESPADA("Espada", 15),
    AURA("Aura", 25),
}

@Serializable
data class Player(
    val name: String = "Jugador",
    val avatar: AvatarStyle = AvatarStyle.INDIGO,
    val totalXp: Int = 0,
    val streakDays: Int = 0,
    val bestStreak: Int = 0,
    val equipped: Set<Accessory> = emptySet(),
) {
    val level: Int get() = Progression.levelFor(totalXp)
    val title: String get() = Progression.titleFor(level)
    val unlockedAccessories: List<Accessory> get() = Accessory.entries.filter { level >= it.unlockLevel }
}

/** Reto semanal "Jefe Final": su vida es el margen que queda para cumplir el reto. */
@Serializable
data class WeeklyBoss(
    val name: String,
    val description: String,
    val art: MonsterArt,
    /** Paquetes que cuentan para el reto (p. ej. todas las redes sociales). */
    val packages: Set<String>,
    val budgetMinutes: Int,
    val usedMinutes: Int = 0,
    /** Día de la semana actual (1 = lunes … 7 = domingo). */
    val dayOfWeek: Int = 1,
    val rewardAccessory: Accessory? = null,
) {
    /**
     * La vida del jefe baja a medida que el jugador CUMPLE: cada día que pasa sin
     * pasarse del presupuesto proporcional le quita una séptima parte.
     */
    val bossHealth: Health
        get() {
            val daysWon = if (onTrack) dayOfWeek - 1 else 0
            return Health((7 - daysWon).coerceIn(0, 7) * 10, 70)
        }
    val onTrack: Boolean get() = usedMinutes <= budgetMinutes * dayOfWeek / 7
    val failed: Boolean get() = usedMinutes > budgetMinutes
}

/** Preferencias de interfaz que el teléfono comparte con el reloj. */
@Serializable
data class UiSettings(
    val haptics: Boolean = true,
    val sound: Boolean = false,
    /** null = seguir al sistema. */
    val reducedMotion: Boolean? = null,
    val highContrast: Boolean = false,
)

@Serializable
enum class GamePhase { PLAYING, GAME_OVER }

/**
 * Estado completo del día. El teléfono es la fuente de verdad; el reloj recibe copias
 * por DataClient. Es serializable para poder viajar por la Data Layer como JSON.
 */
@Serializable
data class GameState(
    val dayKey: String,                    // "2026-10-10"
    val maxHp: Int = 120,
    /** Vida actual. Se guarda (no se calcula) para que curar y dañar nunca "desborden". */
    val hp: Int = maxHp,
    val usedMinutesToday: Int = 0,         // estadística: minutos de uso que hicieron daño
    val regenToday: Int = 0,               // HP recuperados por regeneración hoy (máx 20)
    val phase: GamePhase = GamePhase.PLAYING,
    val monsters: List<Monster> = emptyList(),
    val allies: List<Ally> = emptyList(),
    val inventory: Inventory = Inventory(),
    val player: Player = Player(),
    val boss: WeeklyBoss? = null,
    val unlockedAchievements: Map<AchievementId, Long> = emptyMap(),
    val potionsUsedToday: Int = 0,
    val revivedToday: Boolean = false,
    val activeAttacker: String? = null,    // packageName del monstruo que ataca ahora
    val settings: UiSettings = UiSettings(),
    /** Modo estudio: hasta este instante todos los monstruos están bloqueados. */
    val studyModeUntil: Long = 0L,
    /** El usuario está en pausa (sin usar el teléfono) y acumulando regeneración. */
    val regenerating: Boolean = false,
    val updatedAt: Long = 0L,
) {
    val currentHp: Int get() = hp.coerceIn(0, maxHp)
    val health: Health get() = Health(currentHp, maxHp)
    val attacker: Monster? get() = monsters.firstOrNull { it.packageName == activeAttacker }
    fun monster(pkg: String) = monsters.firstOrNull { it.packageName == pkg }
    fun isAlly(pkg: String) = allies.any { it.packageName == pkg }
    fun studyModeActive(now: Long) = now < studyModeUntil
}

/** Resumen de un día terminado (historial / mapa de calor). */
@Serializable
data class DaySummary(
    val dayKey: String,
    val maxHp: Int,
    val hpLeft: Int,
    val gameOver: Boolean,
    val xpEarned: Int,
) {
    val fractionLeft: Float get() = hpLeft.toFloat() / maxHp
}
