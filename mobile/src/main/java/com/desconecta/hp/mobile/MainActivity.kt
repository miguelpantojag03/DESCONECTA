package com.desconecta.hp.mobile

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.desconecta.hp.mobile.ui.MainViewModel
import com.desconecta.hp.mobile.ui.PhoneApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refreshPermissions() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent { PhoneApp(viewModel) }
    }

    /** Al volver de Ajustes (conceder permisos), refresca su estado y arranca el monitoreo. */
    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissions()
        viewModel.refreshWatch()
        viewModel.ensureServices()
    }
}
