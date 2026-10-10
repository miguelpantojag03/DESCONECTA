package com.desconecta.hp.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.desconecta.hp.mobile.ui.theme.MobileHpTheme
import com.desconecta.hp.shared.designsystem.catalog.DesignSystemCatalog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MobileHpTheme {
                // Fase 1: muestra el catálogo del sistema de diseño. En la fase 3 se
                // reemplaza por la navegación real (onboarding → panel principal).
                DesignSystemCatalog(Modifier.fillMaxSize().safeDrawingPadding())
            }
        }
    }
}
