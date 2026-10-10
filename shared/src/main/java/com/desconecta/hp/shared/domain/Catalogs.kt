package com.desconecta.hp.shared.domain

import com.desconecta.hp.shared.model.Accessory
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.WeeklyBoss

/**
 * Sugerencias al elegir monstruos: las apps más adictivas ya traen nombre, ilustración
 * y un límite razonable. Reduce decisiones en el onboarding (ley de Hick) y deja claro
 * desde el principio qué apps "atacan" más.
 */
object MonsterCatalog {

    data class Suggestion(val art: MonsterArt, val name: String, val limitMinutes: Int, val category: Category)

    enum class Category { SOCIAL, VIDEO, GAMES, CHAT, OTHER }

    private val known = mapOf(
        "com.zhiliaoapp.musically" to Suggestion(MonsterArt.SCROLLZILLA, "Scrollzilla", 30, Category.SOCIAL),
        "com.ss.android.ugc.trill" to Suggestion(MonsterArt.SCROLLZILLA, "Scrollzilla", 30, Category.SOCIAL),
        "com.instagram.android" to Suggestion(MonsterArt.MEDUSA, "La Medusa del Feed", 40, Category.SOCIAL),
        "com.facebook.katana" to Suggestion(MonsterArt.MEDUSA, "La Medusa Azul", 30, Category.SOCIAL),
        "com.twitter.android" to Suggestion(MonsterArt.MEDUSA, "El Cuervo del Timeline", 30, Category.SOCIAL),
        "com.snapchat.android" to Suggestion(MonsterArt.FANTASMA, "Fantasma Efímero", 30, Category.SOCIAL),
        "com.reddit.frontpage" to Suggestion(MonsterArt.MEDUSA, "Hilo Infinito", 30, Category.SOCIAL),
        "com.google.android.youtube" to Suggestion(MonsterArt.REY_AUTOPLAY, "Rey Autoplay", 45, Category.VIDEO),
        "com.netflix.mediaclient" to Suggestion(MonsterArt.REY_AUTOPLAY, "Siguiente Episodio", 60, Category.VIDEO),
        "tv.twitch.android.app" to Suggestion(MonsterArt.REY_AUTOPLAY, "El Streamer Eterno", 45, Category.VIDEO),
        "com.whatsapp" to Suggestion(MonsterArt.FANTASMA, "Fantasma del Chat", 60, Category.CHAT),
        "org.telegram.messenger" to Suggestion(MonsterArt.FANTASMA, "Fantasma Mensajero", 45, Category.CHAT),
        "com.supercell.clashroyale" to Suggestion(MonsterArt.GOBLIN, "Goblin del Grind", 30, Category.GAMES),
        "com.supercell.brawlstars" to Suggestion(MonsterArt.GOBLIN, "Goblin del Grind", 20, Category.GAMES),
        "com.roblox.client" to Suggestion(MonsterArt.GOBLIN, "Goblin de Bloques", 30, Category.GAMES),
        "com.mojang.minecraftpe" to Suggestion(MonsterArt.GOBLIN, "Goblin Minero", 30, Category.GAMES),
    )

    fun suggestionFor(packageName: String): Suggestion? = known[packageName]

    fun isKnownAddictive(packageName: String) = packageName in known

    /** Monstruo por defecto para cualquier app (las desconocidas son "Fantasmas"). */
    fun monsterFor(packageName: String, appLabel: String, limitMinutes: Int? = null): Monster {
        val s = known[packageName]
        return Monster(
            packageName = packageName,
            appLabel = appLabel,
            name = s?.name ?: "Fantasma de $appLabel",
            art = s?.art ?: MonsterArt.FANTASMA,
            limitMinutes = limitMinutes ?: s?.limitMinutes ?: 30,
        )
    }

    fun categoryOf(monster: Monster): Category =
        known[monster.packageName]?.category ?: when (monster.art) {
            MonsterArt.SCROLLZILLA, MonsterArt.MEDUSA -> Category.SOCIAL
            MonsterArt.REY_AUTOPLAY -> Category.VIDEO
            MonsterArt.GOBLIN -> Category.GAMES
            MonsterArt.FANTASMA -> Category.CHAT
        }
}

/** Jefes semanales que rotan cada semana del año. */
object BossCatalog {

    private data class Template(
        val name: String,
        val description: String,
        val art: MonsterArt,
        val category: MonsterCatalog.Category?,
        val budgetMinutes: Int,
        val reward: Accessory,
    )

    private val templates = listOf(
        Template("Hidra Social", "Usa redes sociales menos de 5 horas esta semana", MonsterArt.MEDUSA,
            MonsterCatalog.Category.SOCIAL, 300, Accessory.AURA),
        Template("Rey del Maratón", "Menos de 4 horas de video esta semana", MonsterArt.REY_AUTOPLAY,
            MonsterCatalog.Category.VIDEO, 240, Accessory.CAPA),
        Template("Señor del Grind", "Menos de 3 horas de juegos esta semana", MonsterArt.GOBLIN,
            MonsterCatalog.Category.GAMES, 180, Accessory.ESPADA),
        Template("Gran Fantasma", "Menos de 8 horas en todos tus monstruos", MonsterArt.FANTASMA,
            null, 480, Accessory.GORRO),
    )

    /**
     * Jefe para la semana [weekOfYear] usando los monstruos del jugador. Si el jugador no
     * tiene apps de la categoría del jefe de esa semana, se usa el "Gran Fantasma" (todas).
     */
    fun forWeek(weekOfYear: Int, monsters: List<Monster>): WeeklyBoss? {
        if (monsters.isEmpty()) return null
        val t0 = templates[weekOfYear.mod(templates.size)]
        val pkgs = monsters.filter { t0.category == null || MonsterCatalog.categoryOf(it) == t0.category }.map { it.packageName }
        val t = if (pkgs.isEmpty()) templates.last() else t0
        val packages = if (pkgs.isEmpty()) monsters.map { it.packageName } else pkgs
        return WeeklyBoss(
            name = t.name,
            description = t.description,
            art = t.art,
            packages = packages.toSet(),
            budgetMinutes = t.budgetMinutes,
            dayOfWeek = 1,
            rewardAccessory = t.reward,
        )
    }
}
