package com.desconecta.hp.mobile.ui.block

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.ui.theme.MobileHpTheme
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.formatCountdown
import com.desconecta.hp.shared.model.formatDuration
import com.desconecta.hp.shared.model.millisUntilRespawn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockViewModel @Inject constructor(private val repository: GameRepository) : ViewModel() {
    val state = repository.state
    val watchConnected = repository.watchConnected

    private val _potionSent = MutableStateFlow(false)
    val potionSent: StateFlow<Boolean> = _potionSent.asStateFlow()

    init {
        viewModelScope.launch { repository.refreshWatchConnection() }
    }

    fun askPotion(pkg: String) = viewModelScope.launch {
        _potionSent.value = repository.askWatchForPotion(pkg)
    }
}

/**
 * Se muestra encima de una app bloqueada (lanzada por el AccessibilityService). "Atrás"
 * lleva al inicio, nunca de vuelta a la app bloqueada. Se cierra sola si el bloqueo deja
 * de aplicar (p. ej. una poción despertó al monstruo o llegó el respawn).
 */
@AndroidEntryPoint
class BlockActivity : ComponentActivity() {

    enum class Reason { DEFEATED, GAME_OVER, STUDY }

    private val viewModel: BlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // La app es siempre oscura: iconos de la barra de estado claros aunque el sistema esté en modo claro.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })

        setContent {
            val pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
            val reason = runCatching { Reason.valueOf(intent.getStringExtra(EXTRA_REASON).orEmpty()) }.getOrDefault(Reason.DEFEATED)
            val game by viewModel.state.collectAsStateWithLifecycle()
            val connected by viewModel.watchConnected.collectAsStateWithLifecycle()
            val sent by viewModel.potionSent.collectAsStateWithLifecycle()
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
            ) {
                val g = game
                val monster = g?.monster(pkg)
                val untilRespawn = millisUntilRespawn(now)
                when {
                    g == null -> LaunchedEffect(Unit) { finish() }
                    reason == Reason.GAME_OVER || g.phase == GamePhase.GAME_OVER -> {
                        if (g.phase != GamePhase.GAME_OVER) LaunchedEffect(Unit) { finish() }
                        PhoneGameOverScreen(
                            game = g,
                            untilRespawn = formatDuration(untilRespawn),
                            countdown = formatCountdown(untilRespawn),
                            reviveText = if (g.inventory.reviveReady(now)) {
                                "¿Emergencia de tiempo? Usa Revivir desde tu reloj (1 vez por semana, con PIN)."
                            } else {
                                "Revivir vuelve a estar disponible en ${formatDuration(g.inventory.reviveAvailableAt - now)}."
                            },
                            onEmergency = { dial("tel:$EMERGENCY_NUMBER") },
                            onDialer = { dial(null) },
                        )
                    }
                    reason == Reason.STUDY -> {
                        if (!g.studyModeActive(now)) LaunchedEffect(Unit) { finish() }
                        StudyModeScreen(
                            appLabel = monster?.appLabel ?: "Esta app",
                            remaining = formatDuration(g.studyModeUntil - now),
                            onHome = ::goHome,
                        )
                    }
                    monster == null || !monster.isDefeated -> LaunchedEffect(Unit) { finish() }
                    else -> MonsterDefeatedScreen(
                        monster = monster,
                        untilRespawn = formatDuration(untilRespawn),
                        potionState = when {
                            sent -> PotionButtonState.SENT
                            g.inventory.potions == 0 -> PotionButtonState.NO_POTIONS
                            !connected -> PotionButtonState.WATCH_OFFLINE
                            else -> PotionButtonState.READY
                        },
                        onHome = ::goHome,
                        onPotion = { viewModel.askPotion(pkg) },
                    )
                }
            }
        }
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    private fun dial(uri: String?) {
        val intent = Intent(Intent.ACTION_DIAL).apply { uri?.let { data = Uri.parse(it) } }
        runCatching { startActivity(intent) }
    }

    companion object {
        private const val EXTRA_PKG = "pkg"
        private const val EXTRA_REASON = "reason"

        /** Número de emergencias por defecto (911 en América; cámbialo a 112 en Europa). */
        const val EMERGENCY_NUMBER = "911"

        fun intent(context: Context, pkg: String, reason: Reason): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_PKG, pkg)
                .putExtra(EXTRA_REASON, reason.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
