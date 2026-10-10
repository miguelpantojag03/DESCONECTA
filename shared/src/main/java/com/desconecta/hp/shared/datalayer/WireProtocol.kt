package com.desconecta.hp.shared.datalayer

import com.desconecta.hp.shared.domain.GameEvent
import com.desconecta.hp.shared.domain.HealSource
import com.desconecta.hp.shared.domain.Rejection
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.HpWarning
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.Progression
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Eventos que el teléfono anuncia al reloj (pantallas de alerta + vibración). */
@Serializable
sealed interface WireEvent {
    @Serializable @SerialName("warning")
    data class Warning(
        val warning: HpWarning,
        val hp: Int,
        val maxHp: Int,
        val attackerName: String? = null,
        val attackerArt: MonsterArt? = null,
    ) : WireEvent

    @Serializable @SerialName("defeated")
    data class MonsterDefeated(val packageName: String, val name: String, val art: MonsterArt) : WireEvent

    @Serializable @SerialName("game_over")
    data object GameOver : WireEvent

    @Serializable @SerialName("healed")
    data class Healed(val amount: Int, val source: HealSource) : WireEvent

    @Serializable @SerialName("level_up")
    data class LevelUp(val level: Int, val title: String) : WireEvent

    @Serializable @SerialName("achievement")
    data class Achievement(val id: AchievementId) : WireEvent

    /** El teléfono pide al reloj abrir la confirmación de poción (botón de la pantalla de bloqueo). */
    @Serializable @SerialName("open_potion")
    data class OpenPotion(val wakeMonster: String? = null) : WireEvent

    @Serializable @SerialName("respawn")
    data class Respawn(val xpEarned: Int, val streak: Int) : WireEvent
}

/** Comandos que el reloj (el "mando") envía al teléfono (la fuente de verdad). */
@Serializable
sealed interface WireCommand {
    val requestId: String

    @Serializable @SerialName("potion")
    data class UsePotion(override val requestId: String, val wakeMonster: String? = null) : WireCommand

    @Serializable @SerialName("shield")
    data class UseShield(override val requestId: String, val packageName: String) : WireCommand

    /** El PIN viaja por el canal cifrado de la Data Layer y solo lo valida el teléfono. */
    @Serializable @SerialName("revive")
    data class Revive(override val requestId: String, val pin: String) : WireCommand

    @Serializable @SerialName("set_limit")
    data class SetLimit(override val requestId: String, val packageName: String, val minutes: Int) : WireCommand

    @Serializable @SerialName("sync")
    data class RequestSync(override val requestId: String) : WireCommand
}

@Serializable
data class CommandResult(
    val requestId: String,
    val ok: Boolean,
    val rejection: Rejection? = null,
    /** Errores fuera del motor (p. ej. PIN incorrecto). */
    val wrongPin: Boolean = false,
)

object WireCodec {
    val json = Json {
        ignoreUnknownKeys = true // versiones distintas de app en reloj y teléfono
        encodeDefaults = true
        classDiscriminator = "type"
    }

    fun encodeState(state: GameState): ByteArray = json.encodeToString(GameState.serializer(), state).encodeToByteArray()
    fun decodeState(bytes: ByteArray): GameState = json.decodeFromString(GameState.serializer(), bytes.decodeToString())

    fun encodeEvent(e: WireEvent): ByteArray = json.encodeToString(WireEvent.serializer(), e).encodeToByteArray()
    fun decodeEvent(bytes: ByteArray): WireEvent = json.decodeFromString(WireEvent.serializer(), bytes.decodeToString())

    fun encodeCommand(c: WireCommand): ByteArray = json.encodeToString(WireCommand.serializer(), c).encodeToByteArray()
    fun decodeCommand(bytes: ByteArray): WireCommand = json.decodeFromString(WireCommand.serializer(), bytes.decodeToString())

    fun encodeResult(r: CommandResult): ByteArray = json.encodeToString(CommandResult.serializer(), r).encodeToByteArray()
    fun decodeResult(bytes: ByteArray): CommandResult = json.decodeFromString(CommandResult.serializer(), bytes.decodeToString())
}

/**
 * Traduce los eventos del motor a eventos para el reloj. El daño normal NO se envía:
 * el reloj ya ve bajar la barra por la sincronización de estado, y un mensaje por
 * minuto solo gastaría batería.
 */
fun GameEvent.toWire(state: GameState): WireEvent? = when (this) {
    is GameEvent.Warning -> {
        val attacker = attacker?.let { state.monster(it) }
        WireEvent.Warning(warning, state.currentHp, state.maxHp, attacker?.name, attacker?.art)
    }
    is GameEvent.MonsterDefeated -> state.monster(packageName)?.let { WireEvent.MonsterDefeated(packageName, it.name, it.art) }
    GameEvent.GameOver -> WireEvent.GameOver
    is GameEvent.Healed -> WireEvent.Healed(amount, source)
    is GameEvent.LevelUp -> WireEvent.LevelUp(newLevel, Progression.titleFor(newLevel))
    is GameEvent.AchievementUnlocked -> WireEvent.Achievement(id)
    is GameEvent.Respawn -> WireEvent.Respawn(summary.xpEarned, state.player.streakDays)
    is GameEvent.Damage, is GameEvent.ShieldActivated -> null
}
