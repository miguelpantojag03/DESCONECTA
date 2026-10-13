package com.desconecta.hp.shared.designsystem.component.ai

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.ai.engine.AiResult
import com.desconecta.hp.shared.ai.model.AiFeature
import com.desconecta.hp.shared.ai.model.AiFeedback
import com.desconecta.hp.shared.ai.model.AiOrigin
import com.desconecta.hp.shared.ai.model.Confidence
import com.desconecta.hp.shared.designsystem.catalog.contrast
import com.desconecta.hp.shared.designsystem.component.Avatar
import com.desconecta.hp.shared.designsystem.icon.AvatarMood
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.designsystem.theme.HpTheme
import com.desconecta.hp.shared.model.AvatarStyle

/** Hoja de estilo de la IA en el teléfono: tokens + componentes + los estados sin resultado. */
@Composable
fun AiDesignSheet(modifier: Modifier = Modifier) {
    val c = Hp.colors
    var feedback by remember { mutableStateOf<AiFeedback?>(AiFeedback.POSITIVE) }
    Column(
        modifier
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.lg),
    ) {
        Label("Identificación")
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            AiBadge()
            Swatch("IA", c.ai)
            Swatch("Regen", c.regen)
            Swatch("Vida", c.hpHealthy)
        }

        Label("Burbuja del coach")
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            Avatar(AvatarStyle.INDIGO, AvatarMood.HAPPY, size = 48.dp)
            AiSpeechBubble(
                "Vas mejor que ayer a esta hora 💪",
                reason = "Ayer a las 6 p. m. llevabas 15 HP menos",
                confidence = Confidence.HIGH,
                origin = AiOrigin.DEVICE,
                feedback = feedback,
                onFeedback = { feedback = it },
                tail = BubbleTail.START,
                modifier = Modifier.weight(1f),
            )
        }

        Label("Predicción con poca certeza")
        AiSpeechBubble(
            "Al ritmo actual: Game Over a las 9:40 p. m.",
            reason = "Solo hay 2 martes con datos",
            confidence = Confidence.LOW,
            origin = AiOrigin.RULES,
            tail = BubbleTail.NONE,
        )

        Label("Pensando")
        AiThinking()

        Label("Aprendiendo")
        AiLearningProgress(day = 3, of = 7)

        Label("Estados sin resultado")
        AiStateView(AiResult.Offline) {}
        AiStateView(AiResult.CloudNotAllowed) {}
        AiStateView(AiResult.Disabled(AiFeature.COACH)) {}
        AiStateView(AiResult.MissingPermission("calendario")) {}
        AiStateView(AiResult.Failed<String>("timeout")) {}
    }
}

/** Muestra para el reloj: el HUD solo deja sitio para una línea de burbuja. */
@Composable
fun AiWatchSample(modifier: Modifier = Modifier) {
    val c = Hp.colors
    Box(modifier.fillMaxSize().background(c.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(AvatarStyle.INDIGO, AvatarMood.WORRIED, size = 32.dp)
            AiSpeechBubble("Si aguantas 30 min, tu racha llega a 8 días 🔥")
            AiThinking()
            AiLearningProgress(day = 3, of = 7)
        }
    }
}

@Composable
private fun Label(text: String) {
    BasicText(text.uppercase(), style = Hp.type.gameLabel.copy(color = Hp.colors.textSecondary))
}

@Composable
private fun Swatch(name: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(32.dp).background(color, Hp.shapes.sm))
        BasicText(name, style = Hp.type.caption.copy(color = Hp.colors.textSecondary))
        BasicText("%.1f:1".format(contrast(color, Hp.colors.background)), style = Hp.type.caption.copy(color = Hp.colors.textSecondary))
    }
}

@Preview(name = "IA · teléfono", widthDp = 400, heightDp = 1500, showBackground = true, backgroundColor = 0xFF0B0B10)
@Composable
private fun AiSheetPhonePreview() = HpTheme(HpFormFactor.PHONE) { AiDesignSheet() }

@Preview(name = "IA · movimiento reducido", widthDp = 400, heightDp = 1500, showBackground = true, backgroundColor = 0xFF0B0B10)
@Composable
private fun AiSheetReducedPreview() = HpTheme(HpFormFactor.PHONE, reducedMotion = true) { AiDesignSheet() }

@Preview(name = "IA · reloj redondo", device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun AiWatchRoundPreview() = HpTheme(HpFormFactor.WATCH) { AiWatchSample() }

@Preview(name = "IA · reloj cuadrado", device = "id:wearos_square", showSystemUi = true)
@Composable
private fun AiWatchSquarePreview() = HpTheme(HpFormFactor.WATCH) { AiWatchSample() }

@Preview(name = "IA · ambient", device = "id:wearos_small_round", showSystemUi = true)
@Composable
private fun AiWatchAmbientPreview() = HpTheme(HpFormFactor.WATCH, ambient = true) { AiWatchSample() }
