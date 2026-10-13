package com.desconecta.hp.mobile.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.desconecta.hp.mobile.cloud.AuthState
import com.desconecta.hp.mobile.system.HpPermission
import com.desconecta.hp.mobile.ui.account.AccountNotice
import com.desconecta.hp.mobile.ui.account.AccountViewModel
import com.desconecta.hp.mobile.ui.account.AuthMode
import com.desconecta.hp.mobile.ui.account.AuthScreen
import com.desconecta.hp.mobile.ui.account.ConsentScreen
import com.desconecta.hp.mobile.ui.account.DeleteAccountScreen
import com.desconecta.hp.mobile.ui.account.NewPasswordScreen
import com.desconecta.hp.mobile.ui.bestiary.BestiaryScreen
import com.desconecta.hp.mobile.ui.bestiary.MonsterEditScreen
import com.desconecta.hp.mobile.ui.components.HpTopBar
import com.desconecta.hp.mobile.ui.home.HomeScreen
import com.desconecta.hp.mobile.ui.onboarding.CharacterScreen
import com.desconecta.hp.mobile.ui.onboarding.IntroScreen
import com.desconecta.hp.mobile.ui.onboarding.MonsterSelectionScreen
import com.desconecta.hp.mobile.ui.onboarding.PermissionsOnlyScreen
import com.desconecta.hp.mobile.ui.progress.AchievementsScreen
import com.desconecta.hp.mobile.ui.progress.AvatarScreen
import com.desconecta.hp.mobile.ui.progress.HistoryScreen
import com.desconecta.hp.mobile.ui.settings.SettingsScreen
import com.desconecta.hp.mobile.ui.theme.MobileHpTheme
import com.desconecta.hp.shared.designsystem.component.HpSkeleton
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.model.AvatarStyle
import com.desconecta.hp.shared.model.MonsterArt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object Routes {
    const val LOADING = "loading"
    const val INTRO = "intro"
    const val CHARACTER = "character"
    const val PICK_MONSTERS = "pick-monsters"
    const val HOME = "home"
    const val BESTIARY = "bestiary"
    const val ACHIEVEMENTS = "achievements"
    const val HISTORY = "history"
    const val MONSTER = "monster/{pkg}"
    const val ADD_MONSTER = "add-monster"
    const val ALLIES = "allies"
    const val AVATAR = "avatar"
    const val SETTINGS = "settings"
    const val PERMISSIONS = "permissions"
    const val ACCOUNT_CONSENT = "account/consent"
    const val ACCOUNT_AUTH = "account/auth/{mode}"
    const val ACCOUNT_NEW_PASSWORD = "account/new-password"
    const val ACCOUNT_DELETE = "account/delete"
    fun auth(mode: AuthMode) = "account/auth/${mode.name}"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private data class CharacterDraft(val name: String, val avatar: AvatarStyle, val maxHp: Int, val weekendBonus: Int, val pin: String?)

@Composable
fun PhoneApp(viewModel: MainViewModel) {
    val game by viewModel.game.collectAsStateWithLifecycle()
    val loaded by viewModel.loaded.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val permissions by viewModel.permissions.collectAsStateWithLifecycle()
    val watchConnected by viewModel.watchConnected.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val pendingSync by viewModel.pendingSync.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    MobileHpTheme(
        highContrast = game?.settings?.highContrast ?: false,
        reducedMotion = game?.settings?.reducedMotion,
        hapticsEnabled = game?.settings?.haptics ?: true,
    ) {
        val nav = rememberNavController()
        var draft by remember { mutableStateOf<CharacterDraft?>(null) }
        val grant: (HpPermission) -> Unit = { p -> context.startActivity(viewModel.permissionIntent(p)) }
        val toast: (String) -> Unit = { android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show() }
        val exportData: () -> Unit = {
            scope.launch {
                viewModel.exportData()
                    .onSuccess { data ->
                        val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                            .setType("application/json")
                            .putExtra(android.content.Intent.EXTRA_TEXT, data)
                        context.startActivity(android.content.Intent.createChooser(send, "Exportar mis datos de HP"))
                    }
                    .onFailure { toast("No se pudo exportar: revisa tu conexión") }
            }
        }

        // Enlace de "olvidé mi contraseña" abierto desde el correo → contraseña nueva.
        val linkError by viewModel.linkError.collectAsStateWithLifecycle()
        LaunchedEffect(linkError) {
            linkError?.let {
                android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_LONG).show()
                viewModel.linkErrorShown()
            }
        }
        val recovery by viewModel.recovery.collectAsStateWithLifecycle()
        LaunchedEffect(recovery, loaded) {
            if (recovery && loaded && nav.currentDestination?.route != Routes.ACCOUNT_NEW_PASSWORD) {
                nav.navigate(Routes.ACCOUNT_NEW_PASSWORD) { launchSingleTop = true }
            }
        }

        // Decide adónde ir cuando termina de cargar o se reinicia la partida.
        LaunchedEffect(loaded, game == null) {
            if (!loaded) return@LaunchedEffect
            val target = if (game == null) Routes.INTRO else Routes.HOME
            val current = nav.currentDestination?.route
            val inOnboarding = current in setOf(Routes.INTRO, Routes.CHARACTER, Routes.PICK_MONSTERS)
            if (current == Routes.LOADING || (game == null && !inOnboarding) || (game != null && inOnboarding)) {
                nav.navigate(target) { popUpTo(0) }
            }
        }

        val tabs = listOf(
            Tab(Routes.HOME, "Inicio", PixelIcons.Heart),
            Tab(Routes.BESTIARY, "Bestiario", MonsterSprites.of(MonsterArt.FANTASMA)),
            Tab(Routes.ACHIEVEMENTS, "Logros", PixelIcons.Medal),
            Tab(Routes.HISTORY, "Historial", PixelIcons.Flame),
        )
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val showTabs = tabs.any { it.route == route }

        Scaffold(
            containerColor = Hp.colors.background,
            topBar = {
                when (route) {
                    Routes.BESTIARY -> HpTopBar("Bestiario")
                    Routes.ACHIEVEMENTS -> HpTopBar("Salón de logros")
                    Routes.HISTORY -> HpTopBar("Historial")
                    Routes.AVATAR -> HpTopBar("Tu avatar", onBack = { nav.popBackStack() })
                    Routes.SETTINGS -> HpTopBar("Configuración", onBack = { nav.popBackStack() })
                    Routes.PERMISSIONS -> HpTopBar("Permisos", onBack = { nav.popBackStack() })
                    Routes.ACCOUNT_CONSENT, Routes.ACCOUNT_AUTH -> HpTopBar("Tu cuenta", onBack = { nav.popBackStack() })
                    Routes.ACCOUNT_DELETE -> HpTopBar("Borrar cuenta", onBack = { nav.popBackStack() })
                    Routes.ACCOUNT_NEW_PASSWORD -> HpTopBar("Contraseña nueva")
                    else -> Unit
                }
            },
            bottomBar = { if (showTabs) TabBar(nav, tabs, route) },
        ) { padding ->
            NavHost(nav, startDestination = Routes.LOADING, modifier = Modifier.padding(padding)) {
                composable(Routes.LOADING) {
                    Column(Modifier.fillMaxSize().background(Hp.colors.background).padding(HpSpacing.xl)) { HpSkeleton(lines = 8, lineHeight = 24.dp) }
                }

                // --- Onboarding --------------------------------------------------------
                composable(Routes.INTRO) {
                    IntroScreen(
                        watchConnected = watchConnected,
                        permissions = permissions,
                        onRetryWatch = { viewModel.refreshWatch() },
                        onGrant = grant,
                        onFinish = { nav.navigate(Routes.CHARACTER) },
                    )
                }
                composable(Routes.CHARACTER) {
                    CharacterScreen { name, avatar, maxHp, weekend, pin ->
                        draft = CharacterDraft(name, avatar, maxHp, weekend, pin)
                        nav.navigate(Routes.PICK_MONSTERS)
                    }
                }
                composable(Routes.PICK_MONSTERS) {
                    MonsterSelectionScreen(
                        apps = apps,
                        onLoad = { viewModel.loadApps() },
                        onBack = { nav.popBackStack() },
                    ) { monsters, allies ->
                        val d = draft ?: CharacterDraft("Jugador", AvatarStyle.INDIGO, 120, 0, null)
                        viewModel.createGame(d.name, d.avatar, d.maxHp, d.weekendBonus, d.pin, monsters, allies)
                    }
                }

                // --- Principal ---------------------------------------------------------
                composable(Routes.HOME) {
                    game?.let { g ->
                        HomeScreen(
                            game = g,
                            history = history,
                            watchConnected = watchConnected,
                            syncIndicator = {
                                com.desconecta.hp.mobile.ui.settings.SyncIndicator(
                                    syncStatus, pendingSync, authState is com.desconecta.hp.mobile.cloud.AuthState.SignedIn,
                                )
                            },
                            missingPermissions = listOf(HpPermission.USAGE, HpPermission.ACCESSIBILITY, HpPermission.OVERLAY).count { permissions[it] != true },
                            now = now,
                            onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                            onOpenBestiary = { nav.navigate(Routes.BESTIARY) { launchSingleTop = true } },
                            onOpenAvatar = { nav.navigate(Routes.AVATAR) },
                            onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                            onStudyMode = viewModel::setStudyMode,
                        )
                    }
                }
                composable(Routes.BESTIARY) {
                    game?.let { g ->
                        BestiaryScreen(g, now, onOpen = { nav.navigate("monster/$it") }, onAdd = { nav.navigate(Routes.ADD_MONSTER) })
                    }
                }
                composable(Routes.MONSTER) { entry ->
                    val monster = game?.monster(entry.arguments?.getString("pkg").orEmpty())
                    if (monster == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                    } else {
                        MonsterEditScreen(
                            monster,
                            onBack = { nav.popBackStack() },
                            onSave = { viewModel.saveMonster(it); nav.popBackStack() },
                            onDelete = { viewModel.removeMonster(monster.packageName); nav.popBackStack() },
                        )
                    }
                }
                composable(Routes.ADD_MONSTER) {
                    game?.let { g ->
                        MonsterSelectionScreen(
                            apps = apps,
                            onLoad = { viewModel.loadApps() },
                            title = "Añadir monstruos",
                            confirmLabel = "Guardar",
                            initialMonsters = g.monsters,
                            onlyMonsters = true,
                            onBack = { nav.popBackStack() },
                        ) { monsters, _ ->
                            monsters.forEach(viewModel::saveMonster)
                            g.monsters.filter { old -> monsters.none { it.packageName == old.packageName } }
                                .forEach { viewModel.removeMonster(it.packageName) }
                            nav.popBackStack()
                        }
                    }
                }
                composable(Routes.ALLIES) {
                    game?.let { g ->
                        MonsterSelectionScreen(
                            apps = apps,
                            onLoad = { viewModel.loadApps() },
                            title = "Apps aliadas",
                            confirmLabel = "Guardar",
                            initialMonsters = g.monsters,
                            initialAllies = g.allies,
                            onBack = { nav.popBackStack() },
                        ) { _, allies ->
                            viewModel.setAllies(allies)
                            nav.popBackStack()
                        }
                    }
                }
                composable(Routes.ACHIEVEMENTS) {
                    val dates by viewModel.achievementDates.collectAsStateWithLifecycle()
                    AchievementsScreen(game?.unlockedAchievements.orEmpty() + dates)
                }
                composable(Routes.HISTORY) { HistoryScreen(history, game?.player?.bestStreak ?: 0) }
                composable(Routes.AVATAR) {
                    game?.let { g -> AvatarScreen(g.player, onStyle = viewModel::setAvatar, onToggle = viewModel::toggleAccessory) }
                }
                composable(Routes.SETTINGS) {
                    val p = prefs
                    val g = game
                    if (g != null && p != null) {
                        SettingsScreen(
                            game = g,
                            prefs = p,
                            permissions = permissions,
                            onMaxHp = viewModel::setMaxHp,
                            onSettings = { viewModel.updateSettings(it) },
                            onPin = viewModel::setPin,
                            onOpenAllies = { nav.navigate(Routes.ALLIES) },
                            onOpenPermissions = { nav.navigate(Routes.PERMISSIONS) },
                            onReset = viewModel::resetGame,
                            cloudSection = {
                                com.desconecta.hp.mobile.ui.settings.AccountSection(
                                    auth = authState,
                                    sync = syncStatus,
                                    pending = pendingSync,
                                    now = now,
                                    onSignIn = { nav.navigate(Routes.ACCOUNT_CONSENT) },
                                    onSyncNow = viewModel::syncNow,
                                    onExport = exportData,
                                    onSignOut = viewModel::signOut,
                                    onReauth = { nav.navigate(Routes.auth(AuthMode.SIGN_IN)) },
                                    onDeleteAccount = { nav.navigate(Routes.ACCOUNT_DELETE) },
                                )
                            },
                        )
                    }
                }
                composable(Routes.PERMISSIONS) { PermissionsOnlyScreen(permissions, grant) }

                // --- Cuenta (paso 6) ---------------------------------------------------
                composable(Routes.ACCOUNT_CONSENT) {
                    val vm: AccountViewModel = hiltViewModel()
                    ConsentScreen(
                        onAccept = {
                            vm.acceptCloud()
                            nav.navigate(Routes.auth(AuthMode.SIGN_UP)) { popUpTo(Routes.ACCOUNT_CONSENT) { inclusive = true } }
                        },
                        onStayLocal = { vm.stayLocal(); nav.popBackStack() },
                    )
                }
                composable(Routes.ACCOUNT_AUTH) { entry ->
                    val vm: AccountViewModel = hiltViewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    val mode = runCatching { AuthMode.valueOf(entry.arguments?.getString("mode").orEmpty()) }.getOrDefault(AuthMode.SIGN_IN)
                    // Con sesión (formulario, Google o enlace del correo) la pantalla se cierra sola.
                    val signedIn = authState is AuthState.SignedIn
                    LaunchedEffect(signedIn) {
                        if (signedIn) {
                            toast("Sesión iniciada. Tu progreso se guarda en la nube.")
                            nav.popBackStack()
                        }
                    }
                    AuthScreen(
                        ui = ui,
                        initialMode = mode,
                        googleAvailable = vm.googleAvailable,
                        onSignIn = { e, p -> vm.signIn(e, p) },
                        onSignUp = { e, p -> vm.signUp(e, p) },
                        onGoogle = { vm.google(context) },
                        onForgot = { vm.sendReset(it) },
                        onResend = { vm.resendConfirmation(it) },
                        onBackToForm = { vm.clearMessages() },
                    )
                }
                composable(Routes.ACCOUNT_NEW_PASSWORD) {
                    val vm: AccountViewModel = hiltViewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    LaunchedEffect(ui.notice) {
                        if (ui.notice == AccountNotice.Done) {
                            toast("Contraseña actualizada")
                            nav.popBackStack()
                        }
                    }
                    NewPasswordScreen(ui, onSave = { vm.updatePassword(it) }, onCancel = { vm.cancelRecovery(); nav.popBackStack() })
                }
                composable(Routes.ACCOUNT_DELETE) {
                    val vm: AccountViewModel = hiltViewModel()
                    val ui by vm.ui.collectAsStateWithLifecycle()
                    LaunchedEffect(ui.notice) {
                        if (ui.notice == AccountNotice.Done) {
                            toast("Cuenta borrada. Tus datos ya no están en la nube.")
                            // Si también se borró el teléfono, la navegación general lleva a la bienvenida.
                            nav.popBackStack()
                        }
                    }
                    DeleteAccountScreen(
                        ui = ui,
                        email = (authState as? AuthState.SignedIn)?.email,
                        onExport = exportData,
                        onDelete = { vm.deleteAccount(it) },
                        onCancel = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBar(nav: NavHostController, tabs: List<Tab>, route: String?) {
    val c = Hp.colors
    NavigationBar(containerColor = c.surface) {
        tabs.forEach { tab ->
            val selected = tab.route == route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Box(Modifier.size(24.dp)) {
                        Image(
                            tab.icon, null, Modifier.size(24.dp),
                            colorFilter = if (tab.route == Routes.BESTIARY && selected) null
                            else ColorFilter.tint(if (selected) c.hpHealthy else c.textSecondary),
                        )
                    }
                },
                label = { Text(tab.label, style = Hp.type.caption) },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = c.healthyContainer,
                    selectedTextColor = c.textPrimary,
                    unselectedTextColor = c.textSecondary,
                ),
            )
        }
    }
}

