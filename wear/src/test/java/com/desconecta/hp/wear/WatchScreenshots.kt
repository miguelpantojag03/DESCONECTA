package com.desconecta.hp.wear

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.wear.compose.material3.AppScaffold
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.model.AchievementId
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.HpWarning
import com.desconecta.hp.shared.model.ItemType
import com.desconecta.hp.shared.model.MonsterArt
import com.desconecta.hp.shared.model.SampleData
import com.desconecta.hp.wear.ui.ActionState
import com.desconecta.hp.wear.ui.alerts.AlertOverlay
import com.desconecta.hp.wear.ui.common.WatchLoading
import com.desconecta.hp.wear.ui.common.WatchNoGame
import com.desconecta.hp.wear.ui.gameover.GameOverScreen
import com.desconecta.hp.wear.ui.hud.HudScreen
import com.desconecta.hp.wear.ui.inventory.ConfirmItemScreen
import com.desconecta.hp.wear.ui.inventory.InventoryScreen
import com.desconecta.hp.wear.ui.inventory.PinScreen
import com.desconecta.hp.wear.ui.monsters.MonsterDetailScreen
import com.desconecta.hp.wear.ui.monsters.MonstersScreen
import com.desconecta.hp.wear.ui.profile.BossScreen
import com.desconecta.hp.wear.ui.profile.ProfileScreen
import com.desconecta.hp.wear.ui.theme.WearHpTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OUT = "../docs/screenshots/wear"
private const val ROUND = "w227dp-h227dp-round-xhdpi"
private const val SQUARE = "w180dp-h180dp-notround-xhdpi"

/**
 * Capturas de todas las pantallas del reloj con datos realistas.
 * Generar: ./gradlew :wear:recordRoborazziDebug
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = ROUND)
class WatchScreenshots {

    @get:Rule val compose = createComposeRule()

    private val now = SampleData.NOW
    private val game = SampleData.gameState()

    private fun shot(name: String, ambient: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent {
            WearHpTheme(ambient = ambient) { AppScaffold { content() } }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.onRoot().captureRoboImage("$OUT/$name.png")
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onRoot() =
        onNode(androidx.compose.ui.test.isRoot())

    // --- HUD ---------------------------------------------------------------------------
    @Test fun hud_saludable() = shot("01_hud_saludable") { HudScreen(game, connected = true, lastSyncText = "hace un momento") }
    @Test fun hud_herido() = shot("02_hud_herido") { HudScreen(SampleData.gameState(hp = 47), true, "") }
    @Test fun hud_critico() = shot("03_hud_critico") {
        HudScreen(SampleData.gameState(hp = 12).copy(activeAttacker = "com.zhiliaoapp.musically"), true, "")
    }
    @Test fun hud_sin_conexion() = shot("04_hud_sin_conexion") { HudScreen(game, connected = false, lastSyncText = "hace 12 min") }
    @Test fun hud_regenerando() = shot("05_hud_regenerando") {
        HudScreen(SampleData.gameState(hp = 66).copy(regenerating = true, activeAttacker = null), true, "")
    }
    @Test fun hud_ambient() = shot("06_hud_ambient", ambient = true) { HudScreen(game, true, "", ambient = true) }

    @Config(qualifiers = SQUARE)
    @Test fun hud_cuadrado() = shot("07_hud_cuadrado") { HudScreen(game, true, "") }

    // --- Game Over -----------------------------------------------------------------------
    @Test fun game_over() = shot("08_game_over") {
        GameOverScreen(SampleData.gameState(hp = 0, phase = GamePhase.GAME_OVER).copy(usedMinutesToday = 132), now, onRevive = {})
    }

    // --- Monstruos -----------------------------------------------------------------------
    @Test fun monstruos() = shot("09_monstruos") { MonstersScreen(game, now, onOpen = {}) }
    @Test fun monstruo_detalle() = shot("10_monstruo_detalle") {
        MonsterDetailScreen(game.monsters.first(), shields = 1, now = now, onLimitChange = {}, onShield = {})
    }
    @Test fun monstruos_vacio() = shot("11_monstruos_vacio") { MonstersScreen(game.copy(monsters = emptyList()), now, onOpen = {}) }

    // --- Inventario ----------------------------------------------------------------------
    @Test fun inventario() = shot("12_inventario") { InventoryScreen(game, now, onSelect = {}) }
    @Test fun confirmar_pocion() = shot("13_confirmar_pocion") {
        ConfirmItemScreen(ItemType.POTION, game, null, ActionState.Idle, {}, {}, {})
    }
    @Test fun confirmar_error() = shot("14_confirmar_error") {
        ConfirmItemScreen(
            ItemType.POTION, game, null,
            ActionState.Failure("Tu teléfono no está conectado. Acércalo e inténtalo de nuevo.", retryable = true),
            {}, {}, {},
        )
    }
    @Test fun pin() = shot("15_pin_revivir") { PinScreen(ActionState.Idle, onSubmit = {}, onDone = {}) }

    // --- Alertas -------------------------------------------------------------------------
    @Test fun alerta_20() = shot("16_alerta_vida_20") {
        AlertOverlay(WireEvent.Warning(HpWarning.LOW, 24, 120, "Scrollzilla", MonsterArt.SCROLLZILLA), now) {}
    }
    @Test fun alerta_derrotado() = shot("17_monstruo_derrotado") {
        AlertOverlay(WireEvent.MonsterDefeated("com.google.android.youtube", "Rey Autoplay", MonsterArt.REY_AUTOPLAY), now) {}
    }
    @Test fun level_up() = shot("18_level_up") { AlertOverlay(WireEvent.LevelUp(14, "Guerrero Offline"), now) {} }
    @Test fun logro() = shot("19_logro") { AlertOverlay(WireEvent.Achievement(AchievementId.STREAK_7), now) {} }

    // --- Perfil y jefe -------------------------------------------------------------------
    @Test fun perfil() = shot("20_perfil") { ProfileScreen(game) }
    @Test fun jefe() = shot("21_jefe_semanal") { BossScreen(game) }

    // --- Estados generales ---------------------------------------------------------------
    @Test fun cargando() = shot("22_cargando") { WatchLoading() }
    @Test fun sin_partida() = shot("23_sin_partida") { WatchNoGame(connected = false, onRetry = {}) }
}
