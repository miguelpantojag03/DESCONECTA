package com.desconecta.hp.mobile

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.desconecta.hp.mobile.data.PhonePrefs
import com.desconecta.hp.mobile.system.HpPermission
import com.desconecta.hp.mobile.ui.AppsState
import com.desconecta.hp.mobile.ui.bestiary.BestiaryScreen
import com.desconecta.hp.mobile.ui.bestiary.MonsterEditScreen
import com.desconecta.hp.mobile.ui.block.MonsterDefeatedScreen
import com.desconecta.hp.mobile.ui.block.PhoneGameOverScreen
import com.desconecta.hp.mobile.ui.block.PotionButtonState
import com.desconecta.hp.mobile.ui.home.HomeScreen
import com.desconecta.hp.mobile.ui.onboarding.CharacterScreen
import com.desconecta.hp.mobile.ui.onboarding.IntroScreen
import com.desconecta.hp.mobile.ui.onboarding.MonsterSelectionScreen
import com.desconecta.hp.mobile.ui.progress.AchievementsScreen
import com.desconecta.hp.mobile.ui.progress.AvatarScreen
import com.desconecta.hp.mobile.ui.progress.HistoryScreen
import com.desconecta.hp.mobile.ui.settings.SettingsScreen
import com.desconecta.hp.mobile.ui.theme.MobileHpTheme
import com.desconecta.hp.shared.designsystem.catalog.DesignSystemCatalog
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.SampleData
import com.desconecta.hp.mobile.system.InstalledApp
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OUT = "../docs/screenshots/phone"

/** Capturas de las pantallas del teléfono. Generar: ./gradlew :mobile:recordRoborazziDebug */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PhoneScreenshots {

    @get:Rule val compose = createComposeRule()

    private val now = SampleData.NOW
    private val game = SampleData.gameState()
    private val allGranted = HpPermission.entries.associateWith { true }

    private fun shot(name: String, content: @Composable () -> Unit) {
        compose.setContent { MobileHpTheme { content() } }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNode(isRoot()).captureRoboImage("$OUT/$name.png")
    }

    @Test fun intro() = shot("01_intro") { IntroScreen(false, allGranted, {}, {}, {}) }
    @Test fun personaje() = shot("02_crear_personaje") { CharacterScreen { _, _, _, _, _ -> } }
    @Test fun seleccion() = shot("03_seleccion_monstruos") {
        MonsterSelectionScreen(
            AppsState.Ready(game.monsters.map { InstalledApp(it.packageName, it.appLabel, null) } + InstalledApp("com.google.android.apps.maps", "Maps", null)),
            onLoad = {},
        ) { _, _ -> }
    }
    @Test fun seleccion_cargando() = shot("04_seleccion_cargando") { MonsterSelectionScreen(AppsState.Loading, onLoad = {}) { _, _ -> } }
    @Test fun panel() = shot("05_panel_principal") {
        HomeScreen(game, SampleData.history, true, 0, now, {}, {}, {}, {}, {})
    }
    @Test fun panel_sin_permisos() = shot("06_panel_sin_permisos") {
        HomeScreen(game, SampleData.history, false, 2, now, {}, {}, {}, {}, {})
    }
    @Test fun bloqueo() = shot("07_bloqueo_app") {
        MonsterDefeatedScreen(game.monsters[2], "5 h 20 min", PotionButtonState.READY, {}, {})
    }
    @Test fun bloqueo_sin_reloj() = shot("08_bloqueo_sin_reloj") {
        MonsterDefeatedScreen(game.monsters[2], "5 h 20 min", PotionButtonState.WATCH_OFFLINE, {}, {})
    }
    @Test fun game_over() = shot("09_game_over") {
        PhoneGameOverScreen(
            SampleData.gameState(hp = 0, phase = GamePhase.GAME_OVER).copy(usedMinutesToday = 132),
            "5 h 20 min", "5:20:12",
            "¿Emergencia de tiempo? Usa Revivir desde tu reloj (1 vez por semana, con PIN).", {}, {},
        )
    }
    @Test fun bestiario() = shot("10_bestiario") { BestiaryScreen(game, now, {}, {}) }
    @Test fun monstruo_editar() = shot("11_editar_monstruo") { MonsterEditScreen(game.monsters[0], {}, {}, {}) }
    @Test fun logros() = shot("12_logros") { AchievementsScreen(game.unlockedAchievements) }
    @Test fun historial() = shot("13_historial") {
        HistoryScreen(SampleData.history.map { it.copy(dayKey = "2026-10-" + it.dayKey.takeLast(2)) }, 15)
    }
    @Test fun historial_vacio() = shot("14_historial_vacio") { HistoryScreen(emptyList(), 0) }
    @Test fun avatar() = shot("15_avatar") { AvatarScreen(game.player, {}, {}) }
    @Test fun ajustes() = shot("16_configuracion") {
        SettingsScreen(game, PhonePrefs(true, 120, 30, true), allGranted, { _, _ -> }, {}, {}, {}, {}, {})
    }
    @Test fun catalogo() = shot("17_sistema_de_diseno") { DesignSystemCatalog() }
}
