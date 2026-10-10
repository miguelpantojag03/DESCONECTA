package com.desconecta.hp.wear.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.VerticalPagerScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.ItemType
import com.desconecta.hp.wear.data.Connection
import com.desconecta.hp.wear.ui.alerts.AlertOverlay
import com.desconecta.hp.wear.ui.common.WatchLoading
import com.desconecta.hp.wear.ui.common.WatchNoGame
import com.desconecta.hp.shared.model.formatAgo
import com.desconecta.hp.wear.ui.gameover.GameOverScreen
import com.desconecta.hp.wear.ui.hud.HudScreen
import com.desconecta.hp.wear.ui.inventory.ConfirmItemScreen
import com.desconecta.hp.wear.ui.inventory.InventoryScreen
import com.desconecta.hp.wear.ui.inventory.PinScreen
import com.desconecta.hp.wear.ui.inventory.ShieldTargetScreen
import com.desconecta.hp.wear.ui.monsters.MonsterDetailScreen
import com.desconecta.hp.wear.ui.monsters.MonstersScreen
import com.desconecta.hp.wear.ui.profile.BossScreen
import com.desconecta.hp.wear.ui.profile.ProfileScreen
import com.desconecta.hp.wear.ui.theme.WearHpTheme

private object Routes {
    const val HOME = "home"
    const val MONSTER = "monster/{pkg}"
    const val SHIELD_TARGET = "shield-target"
    const val CONFIRM = "confirm/{item}?pkg={pkg}"
    const val PIN = "pin"
    fun monster(pkg: String) = "monster/$pkg"
    fun confirm(item: ItemType, pkg: String? = null) = "confirm/${item.name}" + (pkg?.let { "?pkg=$it" } ?: "")
}

/** Páginas del paginador vertical: deslizar arriba/abajo desde el HUD. */
private enum class Page { HUD, MONSTERS, INVENTORY, BOSS, PROFILE }

@Composable
fun WearApp(
    viewModel: WatchViewModel,
    ambient: Boolean,
    openRequest: String? = null,
    onOpenHandled: () -> Unit = {},
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val alert by viewModel.alert.collectAsStateWithLifecycle()
    val game = snapshot.game
    val settings = game?.settings

    WearHpTheme(
        ambient = ambient,
        highContrast = settings?.highContrast ?: false,
        reducedMotion = settings?.reducedMotion,
    ) {
        val connected = snapshot.connection != Connection.DISCONNECTED
        val lastSync = formatAgo(now, snapshot.lastSyncAt)
        when {
            game == null && snapshot.loading -> WatchLoading()
            game == null -> WatchNoGame(connected, onRetry = { viewModel.retryConnection() })
            // Ambient: solo el HUD simplificado, sin navegación ni alertas.
            ambient -> HudScreen(game, connected, lastSync, ambient = true)
            else -> AppScaffold {
                Box(Modifier.fillMaxSize()) {
                    GameNavigation(game, now, connected, lastSync, viewModel, openRequest, onOpenHandled)
                    alert?.let { AlertOverlay(it, now, onDismiss = viewModel::dismissAlert) }
                }
            }
        }
    }
}

@Composable
private fun GameNavigation(
    game: GameState,
    now: Long,
    connected: Boolean,
    lastSync: String,
    viewModel: WatchViewModel,
    openRequest: String?,
    onOpenHandled: () -> Unit,
) {
    val nav = rememberSwipeDismissableNavController()
    LaunchedEffect(openRequest) {
        if (openRequest?.startsWith(com.desconecta.hp.wear.MainActivity.OPEN_POTION) == true) {
            viewModel.requestPotion(openRequest.substringAfter(":", "").ifEmpty { null })
        }
        if (openRequest != null) onOpenHandled()
    }
    val action by viewModel.action.collectAsStateWithLifecycle()
    val potionRequest by viewModel.potionRequest.collectAsStateWithLifecycle()
    LaunchedEffect(potionRequest) {
        val req = potionRequest ?: return@LaunchedEffect
        if (game.phase == GamePhase.PLAYING) {
            viewModel.resetAction()
            nav.navigate(Routes.confirm(ItemType.POTION, req.wakeMonster))
        }
        viewModel.consumePotionRequest()
    }
    val done: () -> Unit = {
        viewModel.resetAction()
        nav.popBackStack(Routes.HOME, inclusive = false)
    }

    SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            if (game.phase == GamePhase.GAME_OVER) {
                GameOverScreen(game, now, onRevive = { nav.navigate(Routes.PIN) })
            } else {
                HomePager(game, now, connected, lastSync, onOpenMonster = { nav.navigate(Routes.monster(it)) }) { item ->
                    viewModel.resetAction()
                    when (item) {
                        ItemType.SHIELD -> nav.navigate(Routes.SHIELD_TARGET)
                        ItemType.REVIVE -> if (game.phase == GamePhase.GAME_OVER) nav.navigate(Routes.PIN)
                        ItemType.POTION -> nav.navigate(Routes.confirm(item))
                    }
                }
            }
        }
        composable(Routes.MONSTER) { entry ->
            val pkg = entry.arguments?.getString("pkg")
            val monster = game.monster(pkg.orEmpty())
            if (monster == null) {
                LaunchedEffect(Unit) { nav.popBackStack() }
            } else {
                MonsterDetailScreen(
                    monster = monster,
                    shields = game.inventory.shields,
                    now = now,
                    onLimitChange = { viewModel.setLimit(monster.packageName, it) },
                    onShield = {
                        viewModel.resetAction()
                        nav.navigate(Routes.confirm(ItemType.SHIELD, monster.packageName))
                    },
                )
            }
        }
        composable(Routes.SHIELD_TARGET) {
            ShieldTargetScreen(game, now, onPick = { nav.navigate(Routes.confirm(ItemType.SHIELD, it)) })
        }
        composable(Routes.CONFIRM) { entry ->
            val type = runCatching { ItemType.valueOf(entry.arguments?.getString("item").orEmpty()) }.getOrDefault(ItemType.POTION)
            val pkg = entry.arguments?.getString("pkg")
            ConfirmItemScreen(
                type = type,
                game = game,
                targetName = pkg?.let { game.monster(it)?.appLabel },
                action = action,
                onConfirm = {
                    when (type) {
                        ItemType.POTION -> viewModel.usePotion(pkg)
                        ItemType.SHIELD -> pkg?.let(viewModel::useShield)
                        ItemType.REVIVE -> nav.navigate(Routes.PIN)
                    }
                },
                onCancel = { nav.popBackStack() },
                onDone = done,
            )
        }
        composable(Routes.PIN) {
            PinScreen(action = action, onSubmit = viewModel::revive, onDone = done)
        }
    }
}

@Composable
private fun HomePager(
    game: GameState,
    now: Long,
    connected: Boolean,
    lastSync: String,
    onOpenMonster: (String) -> Unit,
    onSelectItem: (ItemType) -> Unit,
) {
    val pagerState = rememberPagerState { Page.entries.size }
    VerticalPagerScaffold(pagerState = pagerState) {
        VerticalPager(state = pagerState) { page ->
            when (Page.entries[page]) {
                Page.HUD -> HudScreen(game, connected, lastSync)
                Page.MONSTERS -> MonstersScreen(game, now, onOpenMonster)
                Page.INVENTORY -> InventoryScreen(game, now, onSelectItem)
                Page.BOSS -> BossScreen(game)
                Page.PROFILE -> ProfileScreen(game)
            }
        }
    }
}
