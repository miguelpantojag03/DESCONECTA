package com.desconecta.hp.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.healthContentDescription
import com.desconecta.hp.shared.designsystem.theme.rememberStatusVisual
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.formatMinutes
import com.desconecta.hp.wear.ui.theme.WearHpTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WearHpTheme {
                // Fase 1: prueba de humo de los tokens. En la fase 3 se reemplaza por el HUD.
                TokenSmokeTest(Health(current = 84, max = 120))
            }
        }
    }
}

@Composable
private fun TokenSmokeTest(health: Health) {
    val visual = rememberStatusVisual(health.status)
    val description = healthContentDescription(health)
    Box(
        Modifier
            .fillMaxSize()
            .background(Hp.colors.background)
            .semantics(mergeDescendants = true) { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HpSpacing.xs),
        ) {
            Image(visual.icon, null, Modifier.size(32.dp), colorFilter = ColorFilter.tint(visual.color))
            Text("${health.clamped}", style = Hp.type.hpNumber, color = visual.color)
            Text("/ ${health.max} HP", style = Hp.type.hpMax, color = Hp.colors.textSecondary)
            Text(formatMinutes(health.clamped), style = Hp.type.caption, color = Hp.colors.textPrimary)
            Text(visual.label, style = Hp.type.gameLabel, color = visual.color)
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun TokenSmokeTestPreview() {
    WearHpTheme { TokenSmokeTest(Health(current = 84, max = 120)) }
}

@WearPreviewDevices
@Composable
private fun TokenSmokeTestCriticalPreview() {
    WearHpTheme { TokenSmokeTest(Health(current = 12, max = 120)) }
}

@WearPreviewDevices
@Composable
private fun TokenSmokeTestAmbientPreview() {
    WearHpTheme(ambient = true) { TokenSmokeTest(Health(current = 47, max = 120)) }
}
