package com.desconecta.hp.shared.model

/**
 * Datos de prueba realistas para @Preview y para el modo demostración.
 * Un martes a las 18:40: el jugador lleva 1 h 13 min de pantalla de 2 h permitidas.
 */
object SampleData {

    const val NOW = 1_791_650_400_000L // 2026-10-10 18:40 aprox.

    val monsters = listOf(
        Monster("com.zhiliaoapp.musically", "TikTok", "Scrollzilla", MonsterArt.SCROLLZILLA,
            limitMinutes = 30, usedMinutes = 22, history = listOf(41, 35, 30, 28, 33, 19)),
        Monster("com.instagram.android", "Instagram", "La Medusa del Feed", MonsterArt.MEDUSA,
            limitMinutes = 40, usedMinutes = 31, history = listOf(52, 44, 38, 40, 36, 29)),
        Monster("com.google.android.youtube", "YouTube", "Rey Autoplay", MonsterArt.REY_AUTOPLAY,
            limitMinutes = 45, usedMinutes = 45, history = listOf(60, 58, 45, 45, 50, 38)),
        Monster("com.supercell.brawlstars", "Brawl Stars", "Goblin del Grind", MonsterArt.GOBLIN,
            limitMinutes = 20, usedMinutes = 6, shieldUntil = NOW + 9 * 60_000L, history = listOf(25, 20, 18, 20, 12, 15)),
        Monster("com.whatsapp", "WhatsApp", "Fantasma del Chat", MonsterArt.FANTASMA,
            limitMinutes = 60, usedMinutes = 12, history = listOf(30, 25, 40, 22, 18, 27)),
    )

    val allies = listOf(
        Ally("com.google.android.apps.maps", "Maps"),
        Ally("com.bbva.bbvacontigo", "Banco"),
        Ally("com.slack", "Slack"),
    )

    val player = Player(
        name = "Miguel",
        avatar = AvatarStyle.INDIGO,
        totalXp = 1_480,           // nivel 13: "Guerrero Offline"
        streakDays = 12,
        bestStreak = 15,
        equipped = setOf(Accessory.GORRO, Accessory.CAPA),
    )

    val boss = WeeklyBoss(
        name = "Hidra Social",
        description = "Usa redes sociales menos de 5 horas esta semana",
        art = MonsterArt.MEDUSA,
        packages = setOf("com.zhiliaoapp.musically", "com.instagram.android"),
        budgetMinutes = 300,
        usedMinutes = 148,
        dayOfWeek = 4,
        rewardAccessory = Accessory.AURA,
    )

    fun gameState(hp: Int = 84, phase: GamePhase = GamePhase.PLAYING) = GameState(
        dayKey = "2026-10-10",
        maxHp = 120,
        hp = hp,
        usedMinutesToday = 120 - hp,
        regenToday = 5,
        phase = phase,
        monsters = monsters,
        allies = allies,
        inventory = Inventory(potions = 2, shields = 1, reviveAvailableAt = 0L),
        player = player,
        boss = boss,
        unlockedAchievements = mapOf(
            AchievementId.FIRST_DAY to NOW - 20 * DAY,
            AchievementId.STREAK_3 to NOW - 10 * DAY,
            AchievementId.STREAK_7 to NOW - 5 * DAY,
            AchievementId.HALF_LIFE to NOW - 3 * DAY,
            AchievementId.FIRST_POTION to NOW - 8 * DAY,
            AchievementId.LEVEL_10 to NOW - 2 * DAY,
        ),
        activeAttacker = if (phase == GamePhase.PLAYING) "com.instagram.android" else null,
        updatedAt = NOW,
    )

    /** Últimas 5 semanas para el mapa de calor del historial. */
    val history: List<DaySummary> = List(35) { i ->
        val day = i + 1
        val left = listOf(54, 38, 0, 71, 12, 66, 90, 45, 23, 0, 80, 61, 33, 57, 98, 40, 7, 64, 72, 50,
            29, 88, 0, 46, 59, 77, 35, 62, 84, 51, 43, 69, 36, 58, 47)[i]
        DaySummary("2026-09-%02d".format(day.coerceAtMost(30)), 120, left, left == 0, left)
    }

    private const val DAY = 24L * 60 * 60 * 1000
}
