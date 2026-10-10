package com.desconecta.hp.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp

/**
 * Componentes del teléfono sobre Material 3. Los íconos de interfaz (atrás, ajustes)
 * son Material Symbols Rounded; los de juego, pixel art. Así se distingue de un vistazo
 * lo que es "sistema" de lo que es "juego".
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HpTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = Hp.type.title, modifier = Modifier.semantics { heading() }) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Hp.colors.background,
            titleContentColor = Hp.colors.textPrimary,
            navigationIconContentColor = Hp.colors.textPrimary,
            actionIconContentColor = Hp.colors.textPrimary,
        ),
    )
}

/** Acción principal: una por pantalla, ancho completo, 56 dp (fácil de alcanzar). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Hp.colors.hpHealthy,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = Hp.shapes.button,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = Hp.colors.onAccent),
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(Hp.colors.onAccent))
            Spacer(Modifier.width(HpSpacing.sm))
        }
        Text(text, style = Hp.type.label)
    }
}

/** Acción secundaria: contorno, para que nunca compita con la principal. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    iconTint: Color = Hp.colors.regen,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = Hp.shapes.button,
        contentPadding = PaddingValues(horizontal = HpSpacing.lg),
    ) {
        if (icon != null) {
            Image(icon, null, Modifier.size(20.dp), colorFilter = ColorFilter.tint(if (enabled) iconTint else Hp.colors.textSecondary))
            Spacer(Modifier.width(HpSpacing.sm))
        }
        Text(text, style = Hp.type.label, color = if (enabled) Hp.colors.textPrimary else Hp.colors.textSecondary)
    }
}

@Composable
fun HpCard(
    modifier: Modifier = Modifier,
    container: Color = Hp.colors.surface,
    border: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Hp.shapes.card)
            .background(container)
            .then(if (border != null) Modifier.border(1.dp, border, Hp.shapes.card) else Modifier)
            .padding(HpSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
        content = content,
    )
}

/** Título de sección en pixel: separa grupos (Gestalt: región común + proximidad). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = HpSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = Hp.type.gameLabel,
            color = Hp.colors.textSecondary,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

/** Fila con ícono pixel + título + subtítulo, para listas y tarjetas informativas. */
@Composable
fun InfoRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HpSpacing.md),
    ) {
        Box(
            Modifier.size(40.dp).clip(Hp.shapes.slot).background(Hp.colors.surfaceElevated),
            contentAlignment = Alignment.Center,
        ) {
            Image(icon, null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(tint))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Hp.type.bodyStrong, color = Hp.colors.textPrimary)
            if (subtitle != null) Text(subtitle, style = Hp.type.caption, color = Hp.colors.textSecondary)
        }
        trailing?.invoke()
    }
}
