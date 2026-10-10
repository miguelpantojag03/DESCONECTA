package com.desconecta.hp.shared.designsystem.component.healthbar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.designsystem.theme.HpFormFactor
import com.desconecta.hp.shared.designsystem.theme.HpTheme
import com.desconecta.hp.shared.model.Health
import com.desconecta.hp.shared.model.MonsterArt

// Dispositivos de Wear OS (mismos ids que WearDevices, sin depender de wear-tooling en shared).
internal const val SMALL_ROUND = "id:wearos_small_round"
internal const val LARGE_ROUND = "id:wearos_large_round"
internal const val SQUARE = "id:wearos_square"

// Datos de prueba realistas: 120 HP = 2 h diarias.
private val Healthy = Health(84, 120)   // 1 h 24 min
private val Hurt = Health(47, 120)      // 47 min
private val Critical = Health(12, 120)  // 12 min
private val GameOver = Health(0, 120)
private val Regen = Health(66, 120)

private val Scrollzilla = HealthAttacker.of(MonsterArt.SCROLLZILLA)
private val Medusa = HealthAttacker.of(MonsterArt.MEDUSA)
private val ReyAutoplay = HealthAttacker.of(MonsterArt.REY_AUTOPLAY)

// -------------------------------------------------------------------------------------
// Contenedores de preview
// -------------------------------------------------------------------------------------

@Composable
private fun Watch(ambient: Boolean = false, highContrast: Boolean = false, content: @Composable () -> Unit) {
    HpTheme(HpFormFactor.WATCH, ambient = ambient, highContrast = highContrast) {
        Box(Modifier.fillMaxSize().background(Hp.colors.background), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun Phone(content: @Composable () -> Unit) {
    HpTheme(HpFormFactor.PHONE) {
        Box(Modifier.background(Hp.colors.background).padding(HpSpacing.phoneGutter)) { content() }
    }
}

/** Frame estático con el color real del estado (los previews no ejecutan animaciones). */
@Composable
private fun still(health: Health) = HealthBarFrame.still(health, Hp.colors.forStatus(health.status))

// -------------------------------------------------------------------------------------
// Reloj redondo
// -------------------------------------------------------------------------------------

@Preview(name = "Redondo · Saludable", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundHealthy() = Watch {
    HealthBar(Healthy, variant = HealthBarVariant.ARC, attacker = Medusa)
}

@Preview(name = "Redondo · Herido", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundHurt() = Watch {
    HealthBar(Hurt, variant = HealthBarVariant.ARC, attacker = ReyAutoplay)
}

@Preview(name = "Redondo · Crítico", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundCritical() = Watch {
    HealthBar(Critical, variant = HealthBarVariant.ARC, attacker = Scrollzilla)
}

@Preview(name = "Redondo · Game Over", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundGameOver() = Watch {
    HealthBar(GameOver, variant = HealthBarVariant.ARC)
}

@Preview(name = "Redondo · Regenerando", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundRegenerating() = Watch {
    // Momento congelado: avance cian por delante de la barra + partículas a mitad de vuelo.
    val frame = still(Regen).copy(
        fill = 61f / 120, trail = 66f / 120, trailKind = TrailKind.HEAL,
        particles = 0.45f, particlesFrom = 61f / 120, particlesTo = 66f / 120,
    )
    HealthBarLayout(Regen, frame, variant = HealthBarVariant.ARC)
}

@Preview(name = "Redondo · Golpe (fantasma + destello)", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundHit() = Watch {
    // 50 ms después de un golpe de Scrollzilla: la barra ya bajó, la fantasma sigue arriba.
    val frame = still(Hurt).copy(trail = 55f / 120, trailKind = TrailKind.DAMAGE, flash = 0.6f)
    HealthBarLayout(Hurt, frame, variant = HealthBarVariant.ARC, attacker = Scrollzilla)
}

@Preview(name = "Redondo · Ambient", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundAmbient() = Watch(ambient = true) {
    HealthBar(Hurt, variant = HealthBarVariant.ARC, attacker = Medusa)
}

@Preview(name = "Redondo · Alto contraste", device = SMALL_ROUND, showSystemUi = true)
@Composable
private fun RoundHighContrast() = Watch(highContrast = true) {
    HealthBar(Critical, variant = HealthBarVariant.ARC)
}

@Preview(name = "Redondo grande · 180 HP", device = LARGE_ROUND, showSystemUi = true)
@Composable
private fun RoundLarge() = Watch {
    HealthBar(Health(133, 180), variant = HealthBarVariant.ARC, attacker = Medusa)
}

@Preview(name = "Redondo · Texto grande", device = SMALL_ROUND, showSystemUi = true, fontScale = 1.24f)
@Composable
private fun RoundLargeText() = Watch {
    HealthBar(Healthy, variant = HealthBarVariant.ARC)
}

// -------------------------------------------------------------------------------------
// Reloj cuadrado
// -------------------------------------------------------------------------------------

@Preview(name = "Cuadrado · Saludable", device = SQUARE, showSystemUi = true)
@Composable
private fun SquareHealthy() = Watch {
    HealthBar(Healthy, Modifier.padding(HpSpacing.lg), variant = HealthBarVariant.LINEAR, attacker = Medusa)
}

@Preview(name = "Cuadrado · Crítico", device = SQUARE, showSystemUi = true)
@Composable
private fun SquareCritical() = Watch {
    HealthBar(Critical, Modifier.padding(HpSpacing.lg), variant = HealthBarVariant.LINEAR, attacker = Scrollzilla)
}

@Preview(name = "Cuadrado · Ambient", device = SQUARE, showSystemUi = true)
@Composable
private fun SquareAmbient() = Watch(ambient = true) {
    HealthBar(Hurt, Modifier.padding(HpSpacing.lg), variant = HealthBarVariant.LINEAR)
}

// -------------------------------------------------------------------------------------
// Teléfono
// -------------------------------------------------------------------------------------

@Preview(name = "Teléfono · todos los estados", widthDp = 400, heightDp = 760, backgroundColor = 0xFF0B0B10, showBackground = true)
@Composable
private fun PhoneAllStates() = Phone {
    Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.xl)) {
        HealthBar(Healthy, attacker = Medusa)
        HealthBar(Hurt, attacker = ReyAutoplay)
        HealthBar(Critical, attacker = Scrollzilla)
        HealthBar(GameOver)
        HealthBarLayout(
            Regen,
            still(Regen).copy(
                fill = 61f / 120, trail = 66f / 120, trailKind = TrailKind.HEAL,
                particles = 0.4f, particlesFrom = 61f / 120, particlesTo = 66f / 120,
            ),
        )
    }
}

@Preview(name = "Teléfono · mini barras de monstruo", widthDp = 360, backgroundColor = 0xFF0B0B10, showBackground = true)
@Composable
private fun PhoneMiniBars() = Phone {
    Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
        // Scrollzilla: 18 de 30 min · Medusa: 4 de 20 · Rey Autoplay: derrotado
        listOf(Health(18, 30), Health(4, 20), Health(0, 45)).forEach { h ->
            SegmentedBar(still(h), h.max, height = 6.dp)
        }
    }
}

// -------------------------------------------------------------------------------------
// Playground interactivo (modo "Interactive" del panel de previews o en un dispositivo)
// -------------------------------------------------------------------------------------

@Preview(name = "Playground", widthDp = 400, heightDp = 520, backgroundColor = 0xFF0B0B10, showBackground = true)
@Composable
fun HealthBarPlayground() = Phone {
    var hp by remember { mutableIntStateOf(84) }
    var regenerating by remember { mutableStateOf(false) }
    var attacker by remember { mutableStateOf(MonsterArt.SCROLLZILLA) }
    val health = Health(hp, 120)

    Column(verticalArrangement = Arrangement.spacedBy(HpSpacing.lg)) {
        HealthBar(health, attacker = HealthAttacker.of(attacker), regenerating = regenerating)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            DemoButton("-1 HP") { attacker = MonsterArt.FANTASMA; hp = (hp - 1).coerceAtLeast(0) }
            DemoButton("Feroz -1") { attacker = MonsterArt.SCROLLZILLA; hp = (hp - 1).coerceAtLeast(0) }
            DemoButton("-10 HP") { hp = (hp - 10).coerceAtLeast(0) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
            DemoButton("Poción +10") { hp = (hp + 10).coerceAtMost(120) }
            DemoButton(if (regenerating) "Regen: sí" else "Regen: no") { regenerating = !regenerating }
            DemoButton("Reset") { hp = 120 }
        }
    }
}

@Composable
private fun DemoButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .sizeIn(minWidth = 96.dp, minHeight = 48.dp)
            .clip(Hp.shapes.button)
            .background(Hp.colors.surfaceElevated)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = HpSpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = Hp.type.label.copy(color = Hp.colors.textPrimary))
    }
}
