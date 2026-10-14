package com.desconecta.hp.mobile

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import com.desconecta.hp.mobile.data.PhonePrefs
import com.desconecta.hp.mobile.system.HpPermission
import com.desconecta.hp.mobile.ui.AppsState
import com.desconecta.hp.mobile.cloud.AuthState
import com.desconecta.hp.mobile.sync.SyncStatus
import com.desconecta.hp.mobile.ui.account.AccountNotice
import com.desconecta.hp.mobile.ui.account.AccountUiState
import com.desconecta.hp.mobile.ui.account.AuthMode
import com.desconecta.hp.mobile.ui.account.AuthScreen
import com.desconecta.hp.mobile.ui.account.ConsentScreen
import com.desconecta.hp.mobile.ui.account.DeleteAccountScreen
import com.desconecta.hp.mobile.ui.account.NewPasswordScreen
import com.desconecta.hp.mobile.ui.settings.AccountSection
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
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

    @Config(qualifiers = "w411dp-h1600dp-xxhdpi")
    @Test fun ia_tokens() = shot("18_ia_tokens") { com.desconecta.hp.shared.designsystem.component.ai.AiDesignSheet() }

    // --- Cuenta (paso 6) -----------------------------------------------------------------
    @Config(qualifiers = "w411dp-h1100dp-xxhdpi")
    @Test fun cuenta_consentimiento() = shot("19_cuenta_consentimiento") { ConsentScreen({}, {}) }
    @Test fun cuenta_crear() = shot("20_cuenta_crear") {
        AuthScreen(AccountUiState(), AuthMode.SIGN_UP, googleAvailable = true, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
    }
    @Test fun cuenta_entrar_error() = shot("21_cuenta_entrar_error") {
        AuthScreen(AccountUiState(error = "Correo o contraseña incorrectos."), AuthMode.SIGN_IN, googleAvailable = false, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
    }
    @Test fun cuenta_revisa_correo() = shot("22_cuenta_revisa_correo") {
        AuthScreen(AccountUiState(notice = AccountNotice.CheckEmail("ana@correo.com")), AuthMode.SIGN_UP, false, { _, _ -> }, { _, _ -> }, {}, {}, {}, {})
    }
    @Test fun cuenta_contrasena_nueva() = shot("23_cuenta_contrasena_nueva") { NewPasswordScreen(AccountUiState(), {}, {}) }
    @Config(qualifiers = "w411dp-h1000dp-xxhdpi")
    @Test fun cuenta_borrar() = shot("24_cuenta_borrar") { DeleteAccountScreen(AccountUiState(), "ana@correo.com", {}, {}, {}) }
    @Test fun ajustes_cuenta() = shot("25_ajustes_cuenta") {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.background(com.desconecta.hp.shared.designsystem.theme.Hp.colors.background).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            AccountSection(AuthState.LocalOnly, SyncStatus(), 0, now, {}, {}, {}, {}, {}, {})
            AccountSection(AuthState.SignedIn("u", "ana@correo.com", true), SyncStatus(lastSyncAt = now - 120_000), 0, now, {}, {}, {}, {}, {}, {})
        }
    }

    // --- IA fase 1 -----------------------------------------------------------------------
    @Composable
    private fun aiScreen(settings: com.desconecta.hp.shared.ai.model.AiSettings, learning: com.desconecta.hp.mobile.ai.data.LearningState?, signedIn: Boolean) =
        com.desconecta.hp.mobile.ui.ai.AiSettingsScreen(
            settings, learning, signedIn, hasPin = true,
            onToggle = { _, _ -> }, onCloud = {}, onTone = {}, onCoachEvery = {}, onFamilyOn = {},
            onFamilyOff = { true }, onDeleteAiData = { Result.success(Unit) }, onCreateAccount = {}, onMessage = {},
        )

    @Config(qualifiers = "w411dp-h2200dp-xxhdpi")
    @Test fun ia_ajustes_invitado() = shot("26_ia_ajustes_aprendiendo") {
        aiScreen(com.desconecta.hp.shared.ai.model.AiSettings(), com.desconecta.hp.mobile.ai.data.LearningState(3, 7, false), signedIn = false)
    }
    @Config(qualifiers = "w411dp-h2200dp-xxhdpi")
    @Test fun ia_ajustes_nube() = shot("27_ia_ajustes_nube") {
        aiScreen(com.desconecta.hp.shared.ai.model.AiSettings().withCloud(true, 1), com.desconecta.hp.mobile.ai.data.LearningState(7, 7, true), signedIn = true)
    }
    @Config(qualifiers = "w411dp-h1000dp-xxhdpi")
    @Test fun ia_ajustes_familiar() = shot("28_ia_ajustes_familiar") {
        aiScreen(com.desconecta.hp.shared.ai.model.AiSettings().withFamilyMode(true, 1), null, signedIn = true)
    }

    // --- IA fase 2 -----------------------------------------------------------------------
    private val coachInsight = com.desconecta.hp.shared.ai.model.AiInsight(
        id = "c1", type = com.desconecta.hp.shared.ai.model.InsightType.COACH_MESSAGE,
        content = "Cada minuto fuera de La Medusa del Feed es vida que guardas. ¡Vamos!",
        reason = "La Medusa del Feed te está quitando vida ahora mismo.",
        confidence = com.desconecta.hp.shared.ai.model.Confidence.HIGH,
        origin = com.desconecta.hp.shared.ai.model.AiOrigin.RULES, createdAt = now,
    )
    private val limitInsight = com.desconecta.hp.shared.ai.model.AiInsight(
        id = "l1", type = com.desconecta.hp.shared.ai.model.InsightType.LIMIT_SUGGESTION,
        content = "TikTok: 45 → 35 min al día",
        reason = "Tu media en TikTok fue 38 min al día los últimos 7 días, y tu límite es 45 min.",
        confidence = com.desconecta.hp.shared.ai.model.Confidence.MEDIUM,
        origin = com.desconecta.hp.shared.ai.model.AiOrigin.RULES, createdAt = now,
        payload = """{"packageName":"com.zhiliaoapp.musically","currentMinutes":45,"suggestedMinutes":35}""",
    )

    @Config(qualifiers = "w411dp-h1500dp-xxhdpi")
    @Test fun panel_con_ia() = shot("29_panel_coach") {
        HomeScreen(game, SampleData.history, true, 0, now, {}, {}, {}, {}, {}, coach = coachInsight)
    }
    @Test fun ia_sugerencia_limite() = shot("30_ia_sugerencia_limite") {
        androidx.compose.foundation.layout.Column(
            androidx.compose.ui.Modifier.background(com.desconecta.hp.shared.designsystem.theme.Hp.colors.background).padding(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        ) {
            com.desconecta.hp.mobile.ui.ai.CoachCard(coachInsight.copy(feedback = com.desconecta.hp.shared.ai.model.AiFeedback.POSITIVE), game.player, game.health.status) {}
            com.desconecta.hp.mobile.ui.ai.LimitSuggestionCard(limitInsight, {}, {}, {})
        }
    }

    // --- IA fase 3 -----------------------------------------------------------------------
    @Config(qualifiers = "w411dp-h1100dp-xxhdpi")
    @Test fun panel_oraculo_berserker() = shot("31_panel_oraculo_berserker") {
        HomeScreen(
            game, SampleData.history, true, 0, now, {}, {}, {}, {}, {},
            oracle = com.desconecta.hp.mobile.ui.ai.OracleLine(
                "A este ritmo te quedarías sin vida a las 21:40.",
                "Calculado con tu ritmo de hoy: 36 min de monstruos en 6 h.",
                com.desconecta.hp.shared.ai.model.Confidence.MEDIUM, danger = true,
            ),
            berserker = coachInsight.copy(
                id = "b1", type = com.desconecta.hp.shared.ai.model.InsightType.COMPULSIVE_ALERT,
                content = "Llevas 25 min seguidos en Instagram. ¿Una pausa de un minuto?",
            ),
        )
    }
}
