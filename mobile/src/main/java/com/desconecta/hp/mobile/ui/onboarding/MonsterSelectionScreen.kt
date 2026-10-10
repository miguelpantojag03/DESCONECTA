package com.desconecta.hp.mobile.ui.onboarding

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.desconecta.hp.mobile.system.InstalledApp
import com.desconecta.hp.mobile.ui.AppsState
import com.desconecta.hp.mobile.ui.components.HpTopBar
import com.desconecta.hp.mobile.ui.components.PrimaryButton
import com.desconecta.hp.mobile.ui.components.SectionLabel
import com.desconecta.hp.shared.designsystem.component.HpSkeletonCard
import com.desconecta.hp.shared.designsystem.component.HpStateMessage
import com.desconecta.hp.shared.designsystem.component.ScreenStateKind
import com.desconecta.hp.shared.designsystem.icon.MonsterSprites
import com.desconecta.hp.shared.designsystem.spacing.HpSpacing
import com.desconecta.hp.shared.designsystem.theme.Hp
import com.desconecta.hp.shared.domain.GameEngine
import com.desconecta.hp.shared.domain.MonsterCatalog
import com.desconecta.hp.shared.model.Ally
import com.desconecta.hp.shared.model.Monster
import com.desconecta.hp.shared.model.formatMinutes

enum class AppRole { NONE, MONSTER, ALLY }

/**
 * Elegir qué apps son monstruos (con límite) y cuáles aliadas.
 *
 * @param initialMonsters / [initialAllies] para editar una selección existente.
 * @param onlyMonsters modo "añadir monstruo" desde el bestiario (sin aliadas).
 */
@Composable
fun MonsterSelectionScreen(
    apps: AppsState,
    onLoad: () -> Unit,
    title: String = "Elige tus monstruos",
    confirmLabel: String = "¡Empezar!",
    initialMonsters: List<Monster> = emptyList(),
    initialAllies: List<Ally> = emptyList(),
    onlyMonsters: Boolean = false,
    onBack: (() -> Unit)? = null,
    onConfirm: (monsters: List<Monster>, allies: List<Ally>) -> Unit,
) {
    val c = Hp.colors
    LaunchedEffect(Unit) { if (apps !is AppsState.Ready) onLoad() }

    val roles = remember {
        mutableStateMapOf<String, AppRole>().apply {
            initialMonsters.forEach { put(it.packageName, AppRole.MONSTER) }
            initialAllies.forEach { put(it.packageName, AppRole.ALLY) }
        }
    }
    val limits = remember { mutableStateMapOf<String, Int>().apply { initialMonsters.forEach { put(it.packageName, it.limitMinutes) } } }
    var query by remember { mutableStateOf("") }
    var preselected by remember { mutableStateOf(initialMonsters.isNotEmpty() || initialAllies.isNotEmpty()) }

    // Las apps adictivas conocidas llegan ya marcadas como monstruo (valor por defecto sensato).
    LaunchedEffect(apps) {
        if (apps is AppsState.Ready && !preselected) {
            apps.apps.filter { MonsterCatalog.isKnownAddictive(it.packageName) }.forEach { roles[it.packageName] = AppRole.MONSTER }
            preselected = true
        }
    }

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().navigationBarsPadding()) {
        HpTopBar(title, onBack = onBack)
        when (apps) {
            AppsState.Loading -> Column(Modifier.padding(HpSpacing.lg), verticalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
                repeat(6) { HpSkeletonCard() }
            }
            AppsState.Error -> HpStateMessage(
                ScreenStateKind.ERROR,
                title = "No pude leer tus apps",
                body = "Revisa que HP tenga permiso para ver las apps instaladas e inténtalo de nuevo.",
                action = { PrimaryButton("Reintentar", onClick = onLoad) },
            )
            is AppsState.Ready -> {
                val filtered = apps.apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                val suggested = filtered.filter { MonsterCatalog.isKnownAddictive(it.packageName) }
                val others = filtered - suggested.toSet()
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    placeholder = { Text("Buscar app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = HpSpacing.lg),
                    colors = fieldColors(),
                )
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(HpSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
                ) {
                    if (filtered.isEmpty()) {
                        item {
                            HpStateMessage(ScreenStateKind.EMPTY, "Ninguna app coincide", "Prueba con otro nombre.")
                        }
                    }
                    if (suggested.isNotEmpty()) {
                        item { SectionLabel("Sugeridos · las más adictivas") }
                        items(suggested, key = { "s" + it.packageName }) { app ->
                            AppRow(app, roles[app.packageName] ?: AppRole.NONE, limits[app.packageName], onlyMonsters,
                                onRole = { roles[app.packageName] = it }, onLimit = { limits[app.packageName] = it })
                        }
                    }
                    if (others.isNotEmpty()) {
                        item { SectionLabel("Todas las apps") }
                        items(others, key = { it.packageName }) { app ->
                            AppRow(app, roles[app.packageName] ?: AppRole.NONE, limits[app.packageName], onlyMonsters,
                                onRole = { roles[app.packageName] = it }, onLimit = { limits[app.packageName] = it })
                        }
                    }
                }
                val monsterCount = roles.count { it.value == AppRole.MONSTER }
                val allyCount = roles.count { it.value == AppRole.ALLY }
                Column(Modifier.background(c.surface).padding(HpSpacing.lg), verticalArrangement = Arrangement.spacedBy(HpSpacing.sm)) {
                    Text(
                        "$monsterCount monstruo${if (monsterCount == 1) "" else "s"}" +
                            if (onlyMonsters) "" else " · $allyCount aliada${if (allyCount == 1) "" else "s"}",
                        style = Hp.type.caption,
                        color = c.textSecondary,
                    )
                    PrimaryButton(confirmLabel, onClick = {
                        val byPkg = apps.apps.associateBy { it.packageName }
                        val monsters = roles.filter { it.value == AppRole.MONSTER }.keys.mapNotNull { pkg ->
                            val app = byPkg[pkg] ?: return@mapNotNull initialMonsters.firstOrNull { it.packageName == pkg }
                            initialMonsters.firstOrNull { it.packageName == pkg }?.copy(limitMinutes = limits[pkg] ?: 30)
                                ?: MonsterCatalog.monsterFor(pkg, app.label, limits[pkg])
                        }
                        val allies = roles.filter { it.value == AppRole.ALLY }.keys.mapNotNull { pkg -> byPkg[pkg]?.let { Ally(pkg, it.label) } }
                        onConfirm(monsters, allies)
                    })
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    role: AppRole,
    limit: Int?,
    onlyMonsters: Boolean,
    onRole: (AppRole) -> Unit,
    onLimit: (Int) -> Unit,
) {
    val c = Hp.colors
    val suggestion = MonsterCatalog.suggestionFor(app.packageName)
    val currentLimit = limit ?: suggestion?.limitMinutes ?: 30
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (role == AppRole.MONSTER) c.surfaceElevated else c.surface, Hp.shapes.card)
            .padding(HpSpacing.md),
        verticalArrangement = Arrangement.spacedBy(HpSpacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HpSpacing.md)) {
            AppIcon(app.icon)
            Column(Modifier.weight(1f)) {
                Text(app.label, style = Hp.type.bodyStrong, color = c.textPrimary)
                if (role == AppRole.MONSTER) {
                    Text(suggestion?.name ?: "Fantasma de ${app.label}", style = Hp.type.caption, color = c.hpCritical)
                } else if (role == AppRole.ALLY) {
                    Text("Aliada · no hace daño", style = Hp.type.caption, color = c.hpHealthy)
                }
            }
            if (role == AppRole.MONSTER) {
                Image(MonsterSprites.of(suggestion?.art ?: com.desconecta.hp.shared.model.MonsterArt.FANTASMA), null, Modifier.size(32.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(HpSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = role == AppRole.MONSTER,
                onClick = { onRole(if (role == AppRole.MONSTER) AppRole.NONE else AppRole.MONSTER) },
                label = { Text("Monstruo") },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.criticalContainer, selectedLabelColor = c.hpCritical),
            )
            if (!onlyMonsters) {
                FilterChip(
                    selected = role == AppRole.ALLY,
                    onClick = { onRole(if (role == AppRole.ALLY) AppRole.NONE else AppRole.ALLY) },
                    label = { Text("Aliada") },
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = c.healthyContainer, selectedLabelColor = c.hpHealthy),
                )
            }
            Spacer(Modifier.weight(1f))
            if (role == AppRole.MONSTER) {
                LimitStepper(currentLimit, onLimit)
            }
        }
    }
}

@Composable
fun LimitStepper(value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(
            onClick = { onChange((value - GameEngine.LIMIT_STEP).coerceAtLeast(GameEngine.LIMIT_MIN)) },
            modifier = Modifier.semantics { contentDescription = "Restar 5 minutos" },
        ) { Text("−") }
        Text(
            formatMinutes(value),
            style = Hp.type.bodyStrong,
            color = Hp.colors.textPrimary,
            modifier = Modifier.padding(horizontal = HpSpacing.xs).semantics { contentDescription = "Límite ${formatMinutes(value)}" },
        )
        FilledTonalIconButton(
            onClick = { onChange((value + GameEngine.LIMIT_STEP).coerceAtMost(GameEngine.LIMIT_MAX)) },
            modifier = Modifier.semantics { contentDescription = "Sumar 5 minutos" },
        ) { Text("+") }
    }
}

@Composable
fun AppIcon(drawable: Drawable?, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val bitmap = remember(drawable) { drawable?.toBitmap(96, 96)?.asImageBitmap() }
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Spacer(Modifier.size(size).background(Hp.colors.surfaceElevated, Hp.shapes.md))
    }
}

