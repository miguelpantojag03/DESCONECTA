package com.desconecta.hp.mobile.system

import android.app.AppOpsManager
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.desconecta.hp.mobile.service.BlockerService
import com.desconecta.hp.shared.domain.GameEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sign
import kotlin.math.sin

// =====================================================================================
// Bloqueo total (Device Admin)
// =====================================================================================

/** Receptor de administrador de dispositivo: solo se usa para `lockNow()`. */
class HpDeviceAdminReceiver : DeviceAdminReceiver()

@Singleton
class DeviceLocker @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    val admin = ComponentName(context, HpDeviceAdminReceiver::class.java)

    fun isAdminActive(): Boolean = dpm.isAdminActive(admin)

    /** Game Over: apaga la pantalla. Al desbloquear, el AccessibilityService muestra el Game Over. */
    fun lockNow() {
        if (isAdminActive()) runCatching { dpm.lockNow() }
    }
}

// =====================================================================================
// Permisos
// =====================================================================================

enum class HpPermission { USAGE, ACCESSIBILITY, OVERLAY, NOTIFICATIONS, DEVICE_ADMIN }

@Singleton
class PermissionChecker @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val locker: DeviceLocker,
) {
    fun isGranted(p: HpPermission): Boolean = when (p) {
        HpPermission.USAGE -> hasUsageAccess()
        HpPermission.ACCESSIBILITY -> isAccessibilityEnabled()
        HpPermission.OVERLAY -> Settings.canDrawOverlays(context)
        HpPermission.NOTIFICATIONS -> Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        HpPermission.DEVICE_ADMIN -> locker.isAdminActive()
    }

    fun missingRequired(): List<HpPermission> =
        listOf(HpPermission.USAGE, HpPermission.ACCESSIBILITY, HpPermission.OVERLAY).filterNot(::isGranted)

    private fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(context, BlockerService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return TextUtils.SimpleStringSplitter(':').apply { setString(enabled) }.any { it.equals(expected, ignoreCase = true) }
    }

    /** Intent que abre la pantalla de ajustes donde se concede [p]. */
    fun settingsIntent(p: HpPermission): Intent = when (p) {
        HpPermission.USAGE -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        HpPermission.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        HpPermission.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:${context.packageName}"))
        HpPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        HpPermission.DEVICE_ADMIN -> Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, locker.admin)
            .putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "HP solo usa este permiso para apagar la pantalla cuando llegas a Game Over.",
            )
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

// =====================================================================================
// Apps instaladas
// =====================================================================================

data class InstalledApp(val packageName: String, val label: String, val icon: Drawable?)

@Singleton
class AppCatalog @Inject constructor(@param:ApplicationContext private val context: Context) {

    /** Apps con ícono en el launcher (sin la propia HP). */
    suspend fun launchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                runCatching {
                    val info = pm.getApplicationInfo(pkg, 0)
                    InstalledApp(pkg, pm.getApplicationLabel(info).toString(), pm.getApplicationIcon(info))
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }

    fun iconOf(packageName: String): Drawable? = runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    /**
     * Paquetes que NUNCA cuentan ni se bloquean: la propia HP, el launcher, la interfaz
     * del sistema y el marcador/llamadas (seguridad: emergencias siempre accesibles).
     */
    fun alwaysAllowed(): Set<String> {
        val pm = context.packageManager
        val home = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            .map { it.activityInfo.packageName }
        val dialer = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        return buildSet {
            add(context.packageName)
            add("com.android.systemui")
            add("com.android.settings")
            add("com.android.phone")
            add("com.android.emergency")
            add("com.google.android.dialer")
            addAll(home)
            dialer?.let(::add)
        }
    }
}

// =====================================================================================
// Sonido chiptune (opcional, desactivado por defecto)
// =====================================================================================

/**
 * Efectos de 8 bits generados con ondas cuadradas (sin archivos de audio). Son cortos
 * (< 600 ms) y solo suenan en recompensas: nunca para castigar.
 */
@Singleton
class ChipTune @Inject constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun play(event: GameEvent) {
        val notes: List<Pair<Double, Int>> = when (event) {
            is GameEvent.LevelUp -> listOf(523.25 to 90, 659.25 to 90, 783.99 to 90, 1046.5 to 220)          // Do-Mi-Sol-Do
            is GameEvent.AchievementUnlocked -> listOf(783.99 to 80, 1046.5 to 80, 1318.5 to 200)          // Sol-Do-Mi
            is GameEvent.Healed -> listOf(659.25 to 60, 880.0 to 60, 1174.7 to 120)                          // subida rápida
            else -> return
        }
        scope.launch { runCatching { render(notes) } }
    }

    private fun render(notes: List<Pair<Double, Int>>) {
        val rate = 22_050
        val samples = notes.flatMap { (freq, ms) ->
            val n = rate * ms / 1000
            List(n) { i ->
                // Onda cuadrada con envolvente de caída para que no "clickee".
                val env = 1.0 - i.toDouble() / n
                (sign(sin(2 * PI * freq * i / rate)) * 0.25 * env * Short.MAX_VALUE).toInt().toShort()
            }
        }.toShortArray()
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
            .setAudioFormat(
                AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.play()
        Thread.sleep(notes.sumOf { it.second }.toLong() + 50)
        track.release()
    }
}
