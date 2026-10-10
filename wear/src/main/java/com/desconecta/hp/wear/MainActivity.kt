package com.desconecta.hp.wear

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import androidx.wear.ambient.AmbientLifecycleObserver
import com.desconecta.hp.wear.data.WatchNotifier
import com.desconecta.hp.wear.ui.WatchViewModel
import com.desconecta.hp.wear.ui.WearApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var notifier: WatchNotifier

    private val viewModel: WatchViewModel by viewModels()

    /** Ambient Mode (siempre activo): el HUD pasa a contornos, sin animaciones. */
    private val ambient = mutableStateOf(false)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            ambient.value = true
        }

        override fun onExitAmbient() {
            ambient.value = false
        }
    }

    /** Pantalla pedida desde el Tile ("potion") o null. */
    private val openRequest = mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycle.addObserver(AmbientLifecycleObserver(this, ambientCallback))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        openRequest.value = intent?.getStringExtra(EXTRA_OPEN)
        setContent {
            WearApp(
                viewModel,
                ambient = ambient.value,
                openRequest = openRequest.value,
                onOpenHandled = { openRequest.value = null },
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        openRequest.value = intent.getStringExtra(EXTRA_OPEN)
    }

    override fun onStart() {
        super.onStart()
        notifier.appVisible = true
    }

    override fun onStop() {
        notifier.appVisible = false
        super.onStop()
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_POTION = "potion"
        const val OPEN_HOME = "home"
    }
}
