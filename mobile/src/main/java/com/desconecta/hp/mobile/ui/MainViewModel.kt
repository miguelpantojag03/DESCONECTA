package com.desconecta.hp.mobile.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.data.GameStore
import com.desconecta.hp.mobile.data.PhonePrefs
import com.desconecta.hp.mobile.data.db.HpDatabase
import com.desconecta.hp.mobile.service.GameServices
import com.desconecta.hp.mobile.system.AppCatalog
import com.desconecta.hp.mobile.system.HpPermission
import com.desconecta.hp.mobile.system.InstalledApp
import com.desconecta.hp.mobile.system.PermissionChecker
import com.desconecta.hp.shared.model.Accessory
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.Ally
import com.desconecta.hp.shared.model.AvatarStyle
import com.desconecta.hp.shared.model.DaySummary
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.Player
import com.desconecta.hp.shared.model.UiSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Código para vincular un reloj por Wi‑Fi. */
sealed interface LinkCodeState {
    data object Idle : LinkCodeState
    data object Loading : LinkCodeState
    data class Ready(val code: String, val expiresAt: Long) : LinkCodeState
    data class Error(val message: String) : LinkCodeState
}

/** Carga de la lista de apps instaladas (con estados de carga / error). */
sealed interface AppsState {
    data object Loading : AppsState
    data class Ready(val apps: List<InstalledApp>) : AppsState
    data object Error : AppsState
}

@HiltViewModel
class MainViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: GameRepository,
    private val store: GameStore,
    private val permissionChecker: PermissionChecker,
    private val appCatalog: AppCatalog,
    private val services: GameServices,
    db: HpDatabase,
) : ViewModel() {

    val game: StateFlow<GameState?> = repository.state
    val loaded: StateFlow<Boolean> = repository.loaded
    val watchConnected: StateFlow<Boolean> = repository.watchConnected
    val cloudStatus: StateFlow<com.desconecta.hp.mobile.data.CloudStatus> = repository.cloudStatus

    private val _linkCode = MutableStateFlow<LinkCodeState>(LinkCodeState.Idle)
    val linkCode: StateFlow<LinkCodeState> = _linkCode.asStateFlow()
    val prefs: StateFlow<PhonePrefs?> = store.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val history: StateFlow<List<DaySummary>?> = db.history().recent()
        .map { list -> list.map { it.toSummary() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val achievementDates: StateFlow<Map<AchievementId, Long>> = db.achievements().all()
        .map { list -> list.mapNotNull { r -> runCatching { AchievementId.valueOf(r.id) }.getOrNull()?.let { it to r.unlockedAt } }.toMap() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _permissions = MutableStateFlow(checkPermissions())
    val permissions: StateFlow<Map<HpPermission, Boolean>> = _permissions.asStateFlow()

    private val _apps = MutableStateFlow<AppsState>(AppsState.Loading)
    val apps: StateFlow<AppsState> = _apps.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshWatchConnection() }
    }

    // --- Permisos y reloj ----------------------------------------------------------------

    private fun checkPermissions() = HpPermission.entries.associateWith(permissionChecker::isGranted)
    fun refreshPermissions() { _permissions.value = checkPermissions() }
    fun permissionIntent(p: HpPermission) = permissionChecker.settingsIntent(p)
    fun refreshWatch() = viewModelScope.launch { repository.refreshWatchConnection() }

    // --- Apps ----------------------------------------------------------------------------

    fun loadApps() = viewModelScope.launch {
        _apps.value = AppsState.Loading
        _apps.value = runCatching { AppsState.Ready(appCatalog.launchableApps()) }.getOrDefault(AppsState.Error)
    }

    fun appIcon(pkg: String) = appCatalog.iconOf(pkg)

    // --- Crear partida -------------------------------------------------------------------

    fun createGame(
        name: String,
        avatar: AvatarStyle,
        maxHp: Int,
        weekendBonus: Int,
        pin: String?,
        monsters: List<Monster>,
        allies: List<Ally>,
    ) = viewModelScope.launch {
        if (!pin.isNullOrBlank()) store.setPin(pin)
        store.setMaxHp(maxHp, weekendBonus)
        repository.createGame(Player(name = name.ifBlank { "Jugador" }, avatar = avatar), maxHp, monsters, allies)
        services.startAll(context)
    }

    /** Asegura que el monitoreo esté corriendo (p. ej. tras conceder permisos). */
    fun ensureServices() {
        if (repository.state.value != null) services.startAll(context)
    }

    // --- Edición -------------------------------------------------------------------------

    fun saveMonster(monster: Monster) = viewModelScope.launch {
        val current = repository.state.value?.monsters ?: return@launch
        val exists = current.any { it.packageName == monster.packageName }
        repository.updateMonsters(if (exists) current.map { if (it.packageName == monster.packageName) monster else it } else current + monster)
    }

    fun removeMonster(pkg: String) = viewModelScope.launch {
        repository.state.value?.monsters?.let { list -> repository.updateMonsters(list.filterNot { it.packageName == pkg }) }
    }

    fun setAllies(allies: List<Ally>) = viewModelScope.launch { repository.updateAllies(allies) }

    fun setAvatar(style: AvatarStyle) = viewModelScope.launch { repository.updatePlayer { it.copy(avatar = style) } }

    fun toggleAccessory(a: Accessory) = viewModelScope.launch {
        repository.updatePlayer { p -> p.copy(equipped = if (a in p.equipped) p.equipped - a else p.equipped + a) }
    }

    fun updateSettings(transform: (UiSettings) -> UiSettings) = viewModelScope.launch { repository.updateSettings(transform) }

    fun setMaxHp(weekday: Int, weekendBonus: Int) = viewModelScope.launch { repository.setMaxHp(weekday, weekendBonus) }

    fun setPin(pin: String) = viewModelScope.launch { store.setPin(pin) }

    fun setStudyMode(minutes: Int) = viewModelScope.launch { repository.setStudyMode(minutes) }

    fun resetGame() = viewModelScope.launch { repository.reset() }

    fun createLinkCode() = viewModelScope.launch {
        _linkCode.value = LinkCodeState.Loading
        _linkCode.value = repository.createWatchLinkCode().fold(
            onSuccess = { LinkCodeState.Ready(it, System.currentTimeMillis() + 10 * 60_000L) },
            onFailure = { LinkCodeState.Error(it.message ?: "No se pudo generar el código") },
        )
    }

    fun dismissLinkCode() { _linkCode.value = LinkCodeState.Idle }

    fun unlinkCloudWatches() = viewModelScope.launch { repository.unlinkCloudWatches() }
}
