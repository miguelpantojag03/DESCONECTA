package com.desconecta.hp.shared.designsystem.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.color.HpColors
import com.desconecta.hp.shared.designsystem.icon.PixelIcons
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.designsystem.theme.HpTheme
import com.desconecta.hp.shared.designsystem.theme.rememberStatusVisual
import com.desconecta.hp.shared.model.HealthStatus

/**
 * Hoja de estilo viva del sistema de diseño: paleta con su contraste real, escala
 * tipográfica, íconos, formas y estados de vida. Sirve para la presentación de la
 * materia y para detectar regresiones visuales.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignSystemCatalog(modifier: Modifier = Modifier) {
    val c = Hp.colors
    Column(
        modifier
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.xl),
    ) {
        Section("Color") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm), verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                colorRoles(c).forEach { (name, color) -> Swatch(name, color, c.background) }
            }
        }

        Section("Estados de vida") {
            Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                HealthStatus.entries.forEach { StatusRow(it) }
            }
        }

        Section("Tipografía") {
            val t = Hp.type
            Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                Specimen("84", t.hpNumber, c.hpHealthy, "hpNumber · Press Start 2P")
                Specimen("de 120 HP", t.hpMax, c.textSecondary, "hpMax · Silkscreen")
                Specimen("GAME OVER", t.gameTitle, c.textPrimary, "gameTitle · Press Start 2P")
                Specimen("NV 12  x3", t.gameLabel, c.xp, "gameLabel · Silkscreen")
                Specimen("Scrollzilla ataca", t.headline, c.textPrimary, "headline · Inter SemiBold")
                Specimen("Monstruos activos", t.title, c.textPrimary, "title · Inter SemiBold")
                Specimen("Respawn a medianoche", t.body, c.textPrimary, "body · Inter Regular")
                Specimen("Usar poción", t.label, c.textPrimary, "label · Inter SemiBold")
                Specimen("1 h 24 min", t.caption, c.textSecondary, "caption · Inter Medium, tnum")
            }
        }

        Section("Íconos pixel 16×16") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md), verticalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                val tints = listOf(c.hpHealthy, c.hpHurt, c.hpCritical, c.hpCritical, c.regen, c.regen, c.regen, c.hpHurt, c.gold, c.textSecondary, c.textSecondary, c.xp)
                PixelIcons.all.forEachIndexed { i, (name, icon) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(icon, contentDescription = name, Modifier.size(32.dp), colorFilter = ColorFilter.tint(tints.getOrElse(i) { c.textSecondary }))
                        BasicText(name, style = Hp.type.caption.copy(color = c.textSecondary))
                    }
                }
            }
        }

        Section("Formas") {
            Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                ShapeSample("card", Modifier.background(c.surfaceElevated, Hp.shapes.card))
                ShapeSample("bar", Modifier.background(c.hpHealthy, Hp.shapes.bar))
                ShapeSample("slot", Modifier.border(2.dp, c.outlineStrong, Hp.shapes.slot))
                ShapeSample("badge", Modifier.background(c.gold, Hp.shapes.badge))
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
        BasicText(title.uppercase(), style = Hp.type.gameLabel.copy(color = Hp.colors.textSecondary))
        content()
    }
}

@Composable
private fun Swatch(name: String, color: Color, background: Color) {
    val ratio = contrast(color, background)
    Column(Modifier.width(88.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(color, Hp.shapes.sm)
                .border(1.dp, Hp.colors.outline, Hp.shapes.sm),
        )
        BasicText(name, style = Hp.type.caption.copy(color = Hp.colors.textPrimary))
        BasicText("%.1f:1".format(ratio), style = Hp.type.caption.copy(color = Hp.colors.textSecondary))
    }
}

@Composable
private fun StatusRow(status: HealthStatus) {
    val v = rememberStatusVisual(status)
    Row(
        Modifier
            .fillMaxWidth()
            .background(v.container, Hp.shapes.md)
            .padding(HpSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        Image(v.icon, contentDescription = null, Modifier.size(32.dp), colorFilter = ColorFilter.tint(v.color))
        BasicText(v.label, style = Hp.type.bodyStrong.copy(color = v.color))
        BasicText(v.haptic?.name ?: "sin vibración", style = Hp.type.caption.copy(color = Hp.colors.textSecondary))
    }
}

@Composable
private fun Specimen(sample: String, style: TextStyle, color: Color, caption: String) {
    Column {
        BasicText(sample, style = style.copy(color = color))
        BasicText(caption, style = Hp.type.caption.copy(color = Hp.colors.textSecondary))
    }
}

@Composable
private fun ShapeSample(name: String, shapeModifier: Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp, 32.dp).then(shapeModifier))
        BasicText(name, style = Hp.type.caption.copy(color = Hp.colors.textSecondary))
    }
}

private fun colorRoles(c: HpColors) = listOf(
    "surface" to c.surface, "elevated" to c.surfaceElevated, "text" to c.textPrimary,
    "text 2" to c.textSecondary, "saludable" to c.hpHealthy, "herido" to c.hpHurt,
    "crítico" to c.hpCritical, "regen" to c.regen, "xp" to c.xp, "oro" to c.gold,
)

/** Contraste WCAG 2.1 entre dos colores opacos. */
fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

@Preview(name = "Catálogo · teléfono", widthDp = 400, heightDp = 1400, showBackground = true, backgroundColor = 0xFF0B0B10)
@Composable
private fun CatalogPhonePreview() {
    HpTheme(formFactor = HpFormFactor.PHONE) { DesignSystemCatalog() }
}

@Preview(name = "Catálogo · alto contraste", widthDp = 400, heightDp = 1400, showBackground = true, backgroundColor = 0xFF0B0B10)
@Composable
private fun CatalogHighContrastPreview() {
    HpTheme(formFactor = HpFormFactor.PHONE, highContrast = true) { DesignSystemCatalog() }
}
