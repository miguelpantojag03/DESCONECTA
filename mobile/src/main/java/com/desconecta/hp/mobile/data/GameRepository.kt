package com.desconecta.hp.mobile.data

import com.desconecta.hp.mobile.cloud.CloudBridge
import com.desconecta.hp.mobile.cloud.ServerSnapshot
import com.desconecta.hp.mobile.data.db.AchievementRecord
import com.desconecta.hp.mobile.data.db.DayRecord
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.mobile.sync.SyncOp
import com.desconecta.hp.mobile.sync.SyncQueue
import com.desconecta.hp.mobile.system.ChipTune
import com.desconecta.hp.mobile.system.DeviceLocker
import com.desconecta.hp.shared.datalayer.CommandResult
import com.desconecta.hp.shared.datalayer.WireCommand
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.datalayer.toWire
import com.desconecta.hp.shared.domain.BossCatalog
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.domain.GameEvent
import com.desconecta.hp.shared.domain.HealSource
import com.desconecta.hp.shared.domain.Outcome
import com.desconecta.hp.shared.domain.Rejection
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.Ally
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Inventory
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.Player
import com.desconecta.hp.shared.model.Progression
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.IsoFields
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Única fuente de verdad del teléfono (offline-first). Toda modificación pasa por
 * [mutate]: aplica una regla del [GameEngine] bajo un mutex, guarda en local, publica al
 * reloj y, si hay cuenta en la nube, ENCOLA el cambio para Supabase. El conteo de HP y el
 * bloqueo de apps nunca esperan a la red.
 *
 * Resolución de conflictos con el servidor:
 *  · Estado del día (HP minuto a minuto): gana el teléfono (se sube como daily_sessions).
 *  · XP, nivel, racha, logros: gana el servidor (los calcula close_day, anti-trampas).
 *  · Inventario: gana el servidor, recalculado desde item_events, descontando los usos
 *    locales que aún no se han subido.
 */
@Singleton
class GameRepository @Inject constructor(
    private val store: GameStore,
    private val db: HpDatabase,
    private val sync: PhoneSync,
    private val locker: DeviceLocker,
    private val chipTune: ChipTune,
    private val queue: SyncQueue,
    private val cloud: CloudBridge,
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
            cloud.start()
        }
    }

    private suspend fun awaitLoaded() {
        loaded.first { it }
    }

    suspend fun mutate(op: (GameState) -> Outcome): Outcome? = mutex.withLock {
        awaitLoaded()
        val current = _state.value ?: return null
        val out = op(current)
        if (out.state != current) commit(out.state, previous = current)
        out.events.forEach { handle(it, out.state, previous = current) }
        out
    }

    private suspend fun commit(state: GameState, previous: GameState?) {
        _state.value = state
        store.saveState(state)
        sync.publishState(state)
        if (cloud.isCloudActive()) {
            if (previous == null || sessionChanged(previous, state)) queue.enqueue(sessionOp(state))
            if (previous == null || monstersConfig(previous) != monstersConfig(state) || previous.allies != state.allies) {
                queue.enqueue(SyncOp.Monsters(state.monsters, state.allies))
            }
        }
    }

    /** Solo la configuración (nombre, ilustración, límite), no el uso del día. */
    private fun monstersConfig(s: GameState) =
        s.monsters.map { listOf(it.packageName, it.name, it.art, it.limitMinutes, it.appLabel) }

    private fun sessionChanged(a: GameState, b: GameState) =
        a.dayKey != b.dayKey || a.currentHp != b.currentHp || a.regenToday != b.regenToday ||
            a.potionsUsedToday != b.potionsUsedToday || a.phase != b.phase || a.maxHp != b.maxHp

    private fun sessionOp(s: GameState) = SyncOp.Session(
        dayKey = s.dayKey,
        hpStart = s.maxHp,
        hpEnd = s.currentHp,
        regen = s.regenToday,
        potions = s.potionsUsedToday,
        gameOver = s.phase == GamePhase.GAME_OVER,
        gameOverAt = if (s.phase == GamePhase.GAME_OVER) s.updatedAt else null,
    )

    private suspend fun handle(event: GameEvent, state: GameState, previous: GameState) {
        _events.emit(event)
        event.toWire(state)?.let { sync.sendEvent(it) }
        when (event) {
            GameEvent.GameOver -> locker.lockNow()
            is GameEvent.AchievementUnlocked -> {
                val at = state.unlockedAchievements[event.id] ?: System.currentTimeMillis()
                db.achievements().insert(AchievementRecord(event.id.name, at))
            }
            is GameEvent.Respawn -> {
                db.history().upsert(
                    DayRecord(
                        dayKey = event.summary.dayKey,
                        maxHp = event.summary.maxHp,
                        hpLeft = event.summary.hpLeft,
                        gameOver = event.summary.gameOver,
                        xpEarned = event.summary.xpEarned,
                        usageByApp = previous.monsters.joinToString(";") { "${it.packageName}=${it.usedMinutes}" },
                    ),
                )
                if (cloud.isCloudActive()) {
                    // El día anterior se cierra en el SERVIDOR (XP, racha, logros: anti-trampas).
                    queue.enqueue(sessionOp(previous))
                    queue.enqueue(SyncOp.CloseDay(previous.dayKey))
                    queue.enqueue(SyncOp.StartWeek(state.dayKey))
                }
            }
            is GameEvent.Healed -> if (event.source == HealSource.POTION && cloud.isCloudActive()) {
                queue.enqueue(SyncOp.UseItem("pocion", "reloj", previous.dayKey, UUID.randomUUID().toString()))
            }
            is GameEvent.ShieldActivated -> if (cloud.isCloudActive()) {
                queue.enqueue(SyncOp.UseItem("escudo", "reloj", previous.dayKey, UUID.randomUUID().toString(), event.packageName))
            }
            else -> Unit
        }
        if (state.settings.sound) chipTune.play(event)
    }

    // ---------------------------------------------------------------------------------
    // Nube
    // ---------------------------------------------------------------------------------

    /**
     * Al iniciar sesión: sube el estado actual (día + monstruos + semana). El perfil (nombre,
     * avatar, vida máxima) solo se sube si la cuenta es nueva; si ya tenía datos, mandan los suyos.
     */
    suspend fun enqueueFullState(includeProfile: Boolean = true) {
        val s = _state.value ?: return
        queue.enqueue(sessionOp(s))
        queue.enqueue(SyncOp.Monsters(s.monsters, s.allies))
        if (includeProfile) queue.enqueue(SyncOp.Profile(profileFields(s.player, s.maxHp)))
        queue.enqueue(SyncOp.StartWeek(s.dayKey))
    }

    /**
     * Entrar con una cuenta distinta de la que hay en caché (o pasar de invitado a cuenta):
     * el progreso de invitado NO se fusiona. XP, nivel, racha, logros, inventario e historial
     * se sustituyen por los de la cuenta (o se ponen a cero si aún no se pudieron descargar;
     * el siguiente pullAndMerge los completa). Se conservan la configuración del teléfono
     * (monstruos, aliadas, límites) y el HP del día, que refleja el uso real de hoy.
     * Sin celebraciones: no son logros nuevos.
     */
    suspend fun adoptAccount(snapshot: ServerSnapshot?) {
        db.history().clear()
        db.achievements().clear()
        db.aiInsights().clear()
        db.usage().keepLocalBefore(LocalDate.now().toString())
        mutate { s ->
            val p = snapshot?.profile
            val existing = p != null && !isFreshProfile(snapshot)
            var player = Player(name = s.player.name, avatar = s.player.avatar)
            if (p != null) {
                player = player.copy(totalXp = p.xp, streakDays = p.streak, bestStreak = p.bestStreak)
                if (existing) {
                    player = player.copy(
                        name = p.playerName,
                        avatar = com.desconecta.hp.shared.model.AvatarStyle.entries
                            .firstOrNull { it.name.equals(p.avatarId, ignoreCase = true) } ?: s.player.avatar,
                    )
                }
            }
            var inventory = Inventory(
                reviveAvailableAt = p?.let { runCatching { Instant.parse(it.reviveAvailableAt).toEpochMilli() }.getOrNull() } ?: 0L,
            )
            snapshot?.inventory?.forEach { item ->
                when (item.type) {
                    "pocion" -> inventory = inventory.copy(potions = item.cantidad.coerceIn(0, Inventory.MAX_POTIONS))
                    "escudo" -> inventory = inventory.copy(shields = item.cantidad.coerceIn(0, Inventory.MAX_SHIELDS))
                }
            }
            val unlocked = snapshot?.achievements.orEmpty().mapNotNull { a ->
                val id = AchievementId.entries.firstOrNull { it.name.equals(a.id, ignoreCase = true) } ?: return@mapNotNull null
                id to (runCatching { Instant.parse(a.unlockedAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis()))
            }.toMap()
            Outcome(s.copy(player = player, inventory = inventory, unlockedAchievements = unlocked, updatedAt = System.currentTimeMillis()))
        }
        _state.value?.unlockedAchievements?.forEach { (id, at) -> db.achievements().insert(AchievementRecord(id.name, at)) }
    }

    /** Cuenta recién creada: sin XP ni logros (el registro solo crea el perfil por defecto). */
    fun isFreshProfile(snapshot: ServerSnapshot?): Boolean =
        snapshot?.profile?.let { it.xp == 0 && it.bestStreak == 0 && snapshot.achievements.isEmpty() } ?: true

    private fun profileFields(p: Player, maxHp: Int) = buildJsonObject {
        put("nombre_jugador", JsonPrimitive(p.name.take(16)))
        put("avatar_id", JsonPrimitive(p.avatar.name.lowercase()))
        put("vida_maxima", JsonPrimitive(maxHp.coerceIn(30, 600)))
    }

    /**
     * Fusiona lo que decide el servidor. Si sube de nivel o hay logros nuevos, se emiten
     * los eventos y llegan al reloj como cualquier otro (celebración al instante).
     */
    suspend fun mergeServer(snapshot: ServerSnapshot, pendingPotions: Int, pendingShields: Int, keepLocalProgress: Boolean = false) = mutate { s ->
        val events = mutableListOf<GameEvent>()
        var player = s.player
        var inventory = s.inventory
        snapshot.profile?.let { p ->
            if (!keepLocalProgress) {
                if (Progression.levelFor(p.xp) > s.player.level) events += GameEvent.LevelUp(Progression.levelFor(p.xp))
                player = player.copy(totalXp = p.xp, streakDays = p.streak, bestStreak = p.bestStreak)
            }
            runCatching { Instant.parse(p.reviveAvailableAt).toEpochMilli() }.getOrNull()?.let {
                inventory = inventory.copy(reviveAvailableAt = it)
            }
        }
        snapshot.inventory.forEach { item ->
            when (item.type) {
                "pocion" -> inventory = inventory.copy(potions = (item.cantidad - pendingPotions).coerceIn(0, Inventory.MAX_POTIONS))
                "escudo" -> inventory = inventory.copy(shields = (item.cantidad - pendingShields).coerceIn(0, Inventory.MAX_SHIELDS))
            }
        }
        var unlocked = s.unlockedAchievements
        snapshot.achievements.forEach { a ->
            val id = AchievementId.entries.firstOrNull { it.name.equals(a.id, ignoreCase = true) } ?: return@forEach
            if (id !in unlocked) {
                val at = runCatching { Instant.parse(a.unlockedAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
                unlocked = unlocked + (id to at)
                events += GameEvent.AchievementUnlocked(id)
            }
        }
        Outcome(s.copy(player = player, inventory = inventory, unlockedAchievements = unlocked), events)
    }

    /** Línea de la IA que verá el reloj (solo se publica si cambia). */
    suspend fun setAiLine(line: String?) = mutate { s -> Outcome(if (s.aiLine == line) s else s.copy(aiLine = line)) }

    /** Aviso para el reloj ("Inicia sesión en tu teléfono") o null si todo va bien. */
    suspend fun setSyncIssue(issue: String?) = mutate { s -> Outcome(if (s.syncIssue == issue) s else s.copy(syncIssue = issue)) }

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
        commit(state, previous = null)
        if (cloud.isCloudActive()) queue.enqueue(SyncOp.Profile(profileFields(player, maxHp)))
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

    suspend fun onUsage(pkg: String, minutes: Int): Outcome? {
        // Uso agregado por hora en Room (lo usan la IA local y la subida a app_usage).
        val now = LocalDateTime.now(ZoneId.systemDefault())
        db.usage().add(now.toLocalDate().toString(), now.hour, pkg, minutes, isAlly = _state.value?.isAlly(pkg) == true)
        return mutate { engine.onUsage(it, pkg, minutes) }
    }

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

    suspend fun updatePlayer(transform: (Player) -> Player) {
        val s = mutate { Outcome(it.copy(player = transform(it.player))) }?.state ?: return
        if (cloud.isCloudActive()) queue.enqueue(SyncOp.Profile(profileFields(s.player, s.maxHp)))
    }

    suspend fun updateSettings(transform: (UiSettings) -> UiSettings) = mutate { Outcome(it.copy(settings = transform(it.settings))) }

    suspend fun setMaxHp(weekday: Int, weekendBonus: Int) {
        store.setMaxHp(weekday, weekendBonus)
        // El cambio aplica hoy mismo: el máximo de HOY incluye el extra si es fin de semana, y
        // se conserva lo ya gastado. Si lo gastado supera el nuevo máximo, es Game Over (no un
        // estado "0 HP jugando" que el resto de reglas no espera).
        val today = LocalDate.now().dayOfWeek
        val todayMax = weekday + if (today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY) weekendBonus else 0
        mutate { s ->
            if (s.phase == GamePhase.GAME_OVER || todayMax == s.maxHp) return@mutate Outcome(s)
            val used = s.maxHp - s.currentHp
            val hp = (todayMax - used).coerceAtLeast(0)
            if (hp == 0) {
                Outcome(s.copy(maxHp = todayMax, hp = 0, phase = GamePhase.GAME_OVER, activeAttacker = null), listOf(GameEvent.GameOver))
            } else {
                Outcome(s.copy(maxHp = todayMax, hp = hp))
            }
        } ?: return
        if (cloud.isCloudActive()) {
            queue.enqueue(SyncOp.Profile(buildJsonObject {
                put("vida_maxima", JsonPrimitive(weekday.coerceIn(30, 600)))
                put("bonus_fin_semana", JsonPrimitive(weekendBonus.coerceIn(0, 120)))
            }))
        }
    }

    suspend fun setLimit(pkg: String, minutes: Int) = mutate { engine.setMonsterLimit(it, pkg, minutes) }

    /** PIN para revivir: siempre en local (modo sin cuenta) y en el servidor si hay cuenta. */
    suspend fun setPin(pin: String): Result<Unit> {
        store.setPin(pin)
        return if (cloud.isCloudActive()) runCatching { cloud.setPinOnline(pin) } else Result.success(Unit)
    }

    // ---------------------------------------------------------------------------------
    // Comandos del reloj
    // ---------------------------------------------------------------------------------

    suspend fun handleCommand(command: WireCommand, fromNode: String) {
        sync.setConnected(true)
        sync.sendResult(fromNode, execute(command))
    }

    suspend fun execute(command: WireCommand): CommandResult = when (command) {
        is WireCommand.RequestSync -> {
            _state.value?.let { sync.publishState(it) }
            CommandResult(command.requestId, ok = true)
        }
        is WireCommand.UsePotion -> outcomeResult(command.requestId, mutate { engine.usePotion(it, command.wakeMonster) })
        is WireCommand.UseShield -> outcomeResult(command.requestId, mutate { engine.useShield(it, command.packageName) })
        is WireCommand.SetLimit -> outcomeResult(command.requestId, setLimit(command.packageName, command.minutes))
        is WireCommand.Revive -> revive(command)
    }

    /**
     * Revivir es la acción más sensible: con cuenta, el PIN y la regla "1 vez por semana"
     * los valida el SERVIDOR y por eso requiere conexión. Sin cuenta, se valida en local.
     */
    private suspend fun revive(command: WireCommand.Revive): CommandResult {
        val s = _state.value ?: return CommandResult(command.requestId, ok = false)
        if (!cloud.isCloudActive()) {
            return if (!store.checkPin(command.pin)) CommandResult(command.requestId, ok = false, wrongPin = true)
            else outcomeResult(command.requestId, mutate { engine.revive(it) })
        }
        val remote = runCatching {
            cloud.uploadSessionNow(sessionOp(s)) // el servidor debe conocer el Game Over de hoy
            cloud.reviveOnline(command.pin, s.dayKey, command.requestId)
        }.getOrElse { return CommandResult(command.requestId, ok = false, rejection = Rejection.NEEDS_CONNECTION) }
        return when {
            remote.ok -> outcomeResult(command.requestId, mutate { engine.revive(it) })
            remote.reason == "pin_incorrecto" || remote.reason == "sin_pin" -> CommandResult(command.requestId, ok = false, wrongPin = true)
            remote.reason == "no_disponible" -> CommandResult(command.requestId, ok = false, rejection = Rejection.NOT_AVAILABLE)
            remote.reason == "no_es_game_over" -> CommandResult(command.requestId, ok = false, rejection = Rejection.NOT_GAME_OVER)
            else -> CommandResult(command.requestId, ok = false)
        }
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
        queue.clear()
    }
}
