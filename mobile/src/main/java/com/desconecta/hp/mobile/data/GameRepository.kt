package com.desconecta.hp.mobile.data

import com.desconecta.hp.mobile.data.db.AchievementRecord
import com.desconecta.hp.mobile.data.db.DayRecord
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.mobile.system.ChipTune
import com.desconecta.hp.mobile.system.DeviceLocker
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.datalayer.toWire
import com.desconecta.hp.shared.domain.BossCatalog
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.domain.GameEvent
import com.desconecta.hp.shared.domain.Outcome
import com.desconecta.hp.shared.model.Ally
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.Player
import com.desconecta.hp.shared.model.UiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.IsoFields
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Única fuente de verdad del teléfono. Toda modificación pasa por [mutate]: aplica una
 * regla del [GameEngine] bajo un mutex, guarda, publica al reloj y ejecuta los efectos
 * de cada evento (bloqueo, historial, logros, sonido). Así el monitor, el reloj y la
 * interfaz nunca se pisan entre sí.
 */
@Singleton
class GameRepository @Inject constructor(
    private val store: GameStore,
    private val db: HpDatabase,
    private val sync: PhoneSync,
    private val locker: DeviceLocker,
    private val chipTune: ChipTune,
) {
    private val engine = GameEngine()
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow<GameState?>(null)
    /** null = no hay partida creada todavía (onboarding pendiente). */
    val state: StateFlow<GameState?> = _state.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<GameEvent> = _events

    val watchConnected: StateFlow<Boolean> = sync.watchConnected

    init {
        scope.launch {
            _state.value = store.loadState()
            _loaded.value = true
            ensureToday()
            sync.refreshConnection()
            _state.value?.let { sync.publishState(it) }
        }
    }

    private suspend fun awaitLoaded() {
        loaded.first { it }
    }

    suspend fun mutate(op: (GameState) -> Outcome): Outcome? = mutex.withLock {
        awaitLoaded()
        val current = _state.value ?: return null
        val out = op(current)
        if (out.state != current) commit(out.state)
        out.events.forEach { handle(it, out.state, previous = current) }
        out
    }

    private suspend fun commit(state: GameState) {
        _state.value = state
        store.saveState(state)
        sync.publishState(state)
    }

    private suspend fun handle(event: GameEvent, state: GameState, previous: GameState) {
        _events.emit(event)
        event.toWire(state)?.let { sync.sendEvent(it) }
        when (event) {
            GameEvent.GameOver -> locker.lockNow()
            is GameEvent.AchievementUnlocked ->
                db.achievements().insert(AchievementRecord(event.id.name, state.unlockedAchievements[event.id] ?: System.currentTimeMillis()))
            is GameEvent.Respawn -> db.history().upsert(
                DayRecord(
                    dayKey = event.summary.dayKey,
                    maxHp = event.summary.maxHp,
                    hpLeft = event.summary.hpLeft,
                    gameOver = event.summary.gameOver,
                    xpEarned = event.summary.xpEarned,
                    usageByApp = previous.monsters.joinToString(";") { "${it.packageName}=${it.usedMinutes}" },
                ),
            )
            else -> Unit
        }
        if (state.settings.sound) chipTune.play(event)
    }

    // ---------------------------------------------------------------------------------
    // Ciclo de vida de la partida
    // ---------------------------------------------------------------------------------

    suspend fun createGame(player: Player, maxHp: Int, monsters: List<Monster>, allies: List<Ally>) = mutex.withLock {
        val today = LocalDate.now()
        val state = GameState(
            dayKey = today.toString(),
            maxHp = maxHp,
            hp = maxHp,
            monsters = monsters,
            allies = allies,
            player = player,
            boss = BossCatalog.forWeek(today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), monsters)
                ?.copy(dayOfWeek = today.dayOfWeek.value),
            updatedAt = System.currentTimeMillis(),
        )
        store.setMaxHp(maxHp, store.currentPrefs().weekendBonus)
        store.setOnboardingDone(true)
        commit(state)
    }

    /** Si cambió el día (la app estuvo cerrada a medianoche), hace el respawn pendiente. */
    suspend fun ensureToday() {
        val s = _state.value ?: return
        if (s.dayKey != LocalDate.now().toString()) respawn()
    }

    suspend fun respawn() {
        val today = LocalDate.now()
        val prefs = store.currentPrefs()
        val weekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        val maxHp = prefs.maxHpWeekday + if (weekend) prefs.weekendBonus else 0
        mutate { s ->
            if (s.dayKey == today.toString()) return@mutate Outcome(s)
            engine.respawn(
                s,
                newDayKey = today.toString(),
                newDayOfWeek = today.dayOfWeek.value,
                newMaxHp = maxHp,
                newWeekBoss = BossCatalog.forWeek(today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR), s.monsters),
            )
        }
    }

    // ---------------------------------------------------------------------------------
    // Reglas
    // ---------------------------------------------------------------------------------

    suspend fun onUsage(pkg: String, minutes: Int) = mutate { engine.onUsage(it, pkg, minutes) }

    suspend fun onIdle() = mutate { s -> if (s.activeAttacker == null) Outcome(s) else Outcome(engine.onIdle(s)) }

    suspend fun setRegenerating(on: Boolean) = mutate { s -> Outcome(if (s.regenerating == on) s else s.copy(regenerating = on)) }

    suspend fun regenerate() = mutate { engine.regenerate(it) }

    suspend fun setStudyMode(minutes: Int) = mutate { s ->
        Outcome(s.copy(studyModeUntil = if (minutes <= 0) 0L else System.currentTimeMillis() + minutes * 60_000L))
    }

    // ---------------------------------------------------------------------------------
    // Edición (bestiario, personaje, ajustes)
    // ---------------------------------------------------------------------------------

    suspend fun updateMonsters(monsters: List<Monster>) = mutate { Outcome(it.copy(monsters = monsters)) }

    suspend fun updateAllies(allies: List<Ally>) = mutate { Outcome(it.copy(allies = allies)) }

    suspend fun updatePlayer(transform: (Player) -> Player) = mutate { Outcome(it.copy(player = transform(it.player))) }

    suspend fun updateSettings(transform: (UiSettings) -> UiSettings) = mutate { Outcome(it.copy(settings = transform(it.settings))) }

    suspend fun setMaxHp(weekday: Int, weekendBonus: Int) {
        store.setMaxHp(weekday, weekendBonus)
        // El cambio aplica hoy mismo si no se ha consumido más de lo que permite el nuevo máximo.
        mutate { s ->
            val used = s.maxHp - s.currentHp
            Outcome(s.copy(maxHp = weekday, hp = (weekday - used).coerceAtLeast(0)))
        }
    }

    suspend fun setLimit(pkg: String, minutes: Int) = mutate { engine.setMonsterLimit(it, pkg, minutes) }

    // ---------------------------------------------------------------------------------
    // Comandos del reloj
    // ---------------------------------------------------------------------------------

    suspend fun handleCommand(command: WireCommand, fromNode: String) {
        sync.setConnected(true)
        val result: CommandResult = when (command) {
            is WireCommand.RequestSync -> {
                _state.value?.let { sync.publishState(it) }
                CommandResult(command.requestId, ok = true)
            }
            is WireCommand.UsePotion -> outcomeResult(command.requestId, mutate { engine.usePotion(it, command.wakeMonster) })
            is WireCommand.UseShield -> outcomeResult(command.requestId, mutate { engine.useShield(it, command.packageName) })
            is WireCommand.SetLimit -> outcomeResult(command.requestId, setLimit(command.packageName, command.minutes))
            is WireCommand.Revive ->
                if (!store.checkPin(command.pin)) CommandResult(command.requestId, ok = false, wrongPin = true)
                else outcomeResult(command.requestId, mutate { engine.revive(it) })
        }
        sync.sendResult(fromNode, result)
    }

    private fun outcomeResult(requestId: String, outcome: Outcome?) = when {
        outcome == null -> CommandResult(requestId, ok = false)
        outcome.rejection != null -> CommandResult(requestId, ok = false, rejection = outcome.rejection)
        else -> CommandResult(requestId, ok = true)
    }

    /** Pide al reloj abrir la confirmación de poción (botón de la pantalla de bloqueo). */
    suspend fun askWatchForPotion(wakeMonster: String?): Boolean {
        if (!sync.refreshConnection()) return false
        sync.sendEvent(WireEvent.OpenPotion(wakeMonster))
        return true
    }

    suspend fun refreshWatchConnection() = sync.refreshConnection()

    /** Borra la partida (Configuración → "Empezar de nuevo"). */
    suspend fun reset() = mutex.withLock {
        _state.value = null
        store.clearState()
        store.setOnboardingDone(false)
    }
}
