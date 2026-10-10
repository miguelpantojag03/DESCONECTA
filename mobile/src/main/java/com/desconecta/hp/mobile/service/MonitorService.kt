package com.desconecta.hp.mobile.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.desconecta.hp.mobile.MainActivity
import com.desconecta.hp.mobile.data.GameRepository
import com.desconecta.hp.mobile.system.AppCatalog
import com.desconecta.hp.shared.R as SharedR
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.formatMinutes
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Detecta qué app está en primer plano con UsageStatsManager (eventos RESUMED/PAUSED).
 * Se consulta solo el intervalo nuevo desde la última vez: barato y preciso.
 */
class UsageTracker(context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)
    private var lastQuery = System.currentTimeMillis() - 60_000
    var foreground: String? = null
        private set

    fun poll(now: Long): String? {
        val events = usm.queryEvents(lastQuery, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> foreground = e.packageName
                UsageEvents.Event.ACTIVITY_PAUSED -> if (e.packageName == foreground) foreground = null
            }
        }
        lastQuery = now
        return foreground
    }
}

/**
 * Servicio en primer plano que nunca se detiene: cada 5 s (pantalla encendida) suma el
 * tiempo de la app visible; cada minuto completo de una app aplica 1 HP de daño. Con la
 * pantalla apagada mide la pausa para la regeneración (+5 HP cada 30 min, máx. 20/día).
 *
 * Funciona aunque el reloj esté desconectado: aplica las reglas con el estado local y el
 * reloj se pone al día al reconectar (DataClient).
 */
@AndroidEntryPoint
class MonitorService : LifecycleService() {

    @Inject lateinit var repository: GameRepository
    @Inject lateinit var appCatalog: AppCatalog

    private val accumulatedMs = mutableMapOf<String, Long>()
    private var idleSince: Long? = null
    private var regenBlocksGranted = 0

    override fun onCreate() {
        super.onCreate()
        startAsForeground(null)
        val tracker = UsageTracker(this)
        val power = getSystemService(PowerManager::class.java)
        val allowed = appCatalog.alwaysAllowed()

        lifecycleScope.launch {
            var last = System.currentTimeMillis()
            while (true) {
                val now = System.currentTimeMillis()
                val elapsed = (now - last).coerceIn(0, 60_000)
                last = now
                repository.ensureToday()

                if (power.isInteractive) {
                    onScreenOn()
                    val pkg = tracker.poll(now)
                    if (pkg == null || pkg in allowed) {
                        repository.onIdle()
                    } else {
                        val total = (accumulatedMs[pkg] ?: 0L) + elapsed
                        val minutes = (total / 60_000).toInt()
                        accumulatedMs[pkg] = total - minutes * 60_000L
                        if (minutes > 0) repository.onUsage(pkg, minutes)
                    }
                    delay(TICK_ACTIVE_MS)
                } else {
                    onScreenOff(now)
                    delay(TICK_IDLE_MS)
                }
            }
        }

        // La notificación persistente muestra la vida actual: el monitoreo es visible y honesto.
        lifecycleScope.launch {
            repository.state.collectLatest { startAsForeground(it) }
        }
    }

    private suspend fun onScreenOn() {
        if (idleSince != null) {
            idleSince = null
            regenBlocksGranted = 0
            repository.setRegenerating(false)
        }
    }

    private suspend fun onScreenOff(now: Long) {
        val since = idleSince ?: now.also {
            idleSince = it
            repository.onIdle()
            repository.setRegenerating(true)
        }
        val blocks = ((now - since) / (GameEngine.REGEN_IDLE_MINUTES * 60_000L)).toInt()
        while (regenBlocksGranted < blocks) {
            repository.regenerate()
            regenBlocksGranted++
        }
    }

    private fun startAsForeground(state: GameState?) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Barra de vida", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Muestra tu vida restante mientras HP vigila a los monstruos"
                    setShowBadge(false)
                },
            )
        }
        val text = when {
            state == null -> "Preparando tu partida…"
            state.phase == GamePhase.GAME_OVER -> "Game Over · respawn a medianoche"
            else -> "${state.currentHp} / ${state.maxHp} HP · quedan ${formatMinutes(state.currentHp)}" +
                (state.attacker?.let { " · ${it.name} ataca" } ?: "")
        }
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(SharedR.drawable.ic_hp_heart)
            .setContentTitle("HP: Health Points")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(state?.maxHp ?: 0, state?.currentHp ?: 0, state == null)
            .setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
    }

    companion object {
        private const val CHANNEL = "hp_monitor"
        private const val NOTIFICATION_ID = 1
        private const val TICK_ACTIVE_MS = 5_000L
        private const val TICK_IDLE_MS = 60_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }
    }
}
