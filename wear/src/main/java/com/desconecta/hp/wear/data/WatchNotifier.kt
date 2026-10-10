package com.desconecta.hp.wear.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.desconecta.hp.shared.R as SharedR
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.model.HpWarning
import com.desconecta.hp.wear.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Si la app del reloj está abierta, las alertas se muestran a pantalla completa (la
 * pantalla las recibe por [WatchGameRepository.events]). Si está cerrada, Android no
 * permite abrir una pantalla desde segundo plano, así que se publica una notificación:
 * el jugador nunca se pierde una advertencia.
 */
@Singleton
class WatchNotifier @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Lo actualiza MainActivity en onStart/onStop. */
    @Volatile var appVisible: Boolean = false

    fun notifyIfInBackground(event: WireEvent) {
        if (appVisible) return
        val (title, text, icon) = when (event) {
            is WireEvent.Warning -> Triple(
                "¡Vida al ${percent(event)}!",
                event.attackerName?.let { "$it ataca. Te quedan ${event.hp} HP." } ?: "Te quedan ${event.hp} HP.",
                SharedR.drawable.ic_hp_heart,
            )
            is WireEvent.MonsterDefeated -> Triple("${event.name}, derrotado", "Esa app descansa hasta mañana.", SharedR.drawable.ic_hp_skull)
            WireEvent.GameOver -> Triple("GAME OVER", "Hoy ya jugaste suficiente. Respawn a medianoche.", SharedR.drawable.ic_hp_skull)
            is WireEvent.LevelUp -> Triple("¡LEVEL UP!", "Nivel ${event.level}: ${event.title}", SharedR.drawable.ic_hp_star)
            is WireEvent.Achievement -> Triple("Logro desbloqueado", event.id.title, SharedR.drawable.ic_hp_medal)
            else -> return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return

        ensureChannel()
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(event::class.hashCode(), notification)
    }

    private fun percent(e: WireEvent.Warning) = when (e.warning) {
        HpWarning.HALF -> "50 %"
        HpWarning.LOW -> "20 %"
        HpWarning.VERY_LOW -> "10 %"
        HpWarning.LAST_FIVE -> "5 HP"
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Alertas de vida", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Advertencias, monstruos derrotados, Game Over y logros"
                    enableVibration(false) // el patrón háptico propio ya se reprodujo
                },
            )
        }
    }

    private companion object {
        const val CHANNEL = "hp_alerts"
    }
}
