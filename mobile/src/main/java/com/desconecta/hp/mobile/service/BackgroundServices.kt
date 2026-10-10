package com.desconecta.hp.mobile.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.system.AppCatalog
import com.desconecta.hp.mobile.ui.block.BlockActivity
import com.desconecta.hp.shared.datalayer.DataLayerPaths
import com.desconecta.hp.shared.datalayer.WireCodec
import com.desconecta.hp.shared.model.GamePhase
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject

// =====================================================================================
// Bloqueo de apps (AccessibilityService)
// =====================================================================================

/**
 * Detecta la app que pasa a primer plano y, si su monstruo está derrotado (o es Game
 * Over, o está activo el modo estudio), muestra encima la pantalla de bloqueo.
 *
 * Siempre permitidas: HP, launcher, sistema, marcador y emergencias; en Game Over
 * también las aliadas (Maps, banco…) por seguridad.
 */
@AndroidEntryPoint
class BlockerService : AccessibilityService() {

    @Inject lateinit var repository: GameRepository
    @Inject lateinit var appCatalog: AppCatalog

    private val allowed by lazy { appCatalog.alwaysAllowed() }
    private var lastBlocked: Pair<String, Long>? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in allowed) return
        val state = repository.state.value ?: return
        val now = System.currentTimeMillis()

        val reason = when {
            state.phase == GamePhase.GAME_OVER && !state.isAlly(pkg) -> BlockActivity.Reason.GAME_OVER
            state.monster(pkg)?.isDefeated == true -> BlockActivity.Reason.DEFEATED
            state.studyModeActive(now) && state.monster(pkg) != null -> BlockActivity.Reason.STUDY
            else -> return
        }
        // Evita relanzar en bucle por eventos repetidos de la misma ventana.
        lastBlocked?.let { (p, t) -> if (p == pkg && now - t < 1_500) return }
        lastBlocked = pkg to now
        startActivity(BlockActivity.intent(this, pkg, reason))
    }

    override fun onInterrupt() = Unit
}

// =====================================================================================
// Comandos del reloj
// =====================================================================================

@AndroidEntryPoint
class PhoneListenerService : WearableListenerService() {

    @Inject lateinit var repository: GameRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onMessageReceived(message: MessageEvent) {
        if (message.path != DataLayerPaths.COMMAND) return
        val command = runCatching { WireCodec.decodeCommand(message.data) }.getOrNull() ?: return
        scope.launch { repository.handleCommand(command, message.sourceNodeId) }
    }

    override fun onCapabilityChanged(info: CapabilityInfo) {
        scope.launch { repository.refreshWatchConnection() }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

// =====================================================================================
// Respawn de medianoche (WorkManager)
// =====================================================================================

class RespawnWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun repository(): GameRepository
    }

    override suspend fun doWork(): Result {
        val repo = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).repository()
        repo.respawn()
        schedule(applicationContext) // la siguiente medianoche
        return Result.success()
    }

    companion object {
        private const val NAME = "hp_respawn"

        /** Programa el trabajo para 1 minuto después de la próxima medianoche local. */
        fun schedule(context: Context) {
            val zone = ZoneId.systemDefault()
            val next = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1)
            val delay = Duration.between(java.time.ZonedDateTime.now(zone), next).toMillis().coerceAtLeast(0)
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<RespawnWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build(),
            )
        }
    }
}

/** Tras reiniciar el teléfono: vuelve a arrancar el monitoreo y el respawn. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            RespawnWorker.schedule(context)
            MonitorService.start(context)
        }
    }
}

/** Para que la UI pueda arrancar todo tras el onboarding. */
class GameServices @Inject constructor() {
    fun startAll(context: Context) {
        MonitorService.start(context)
        RespawnWorker.schedule(context)
    }
}
