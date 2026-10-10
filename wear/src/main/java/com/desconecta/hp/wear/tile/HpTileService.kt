package com.desconecta.hp.wear.tile

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.CircularProgressIndicator
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.ProgressIndicatorColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.EdgeContentLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.desconecta.hp.shared.designsystem.color.HpColorSchemes
import com.desconecta.hp.shared.model.GamePhase
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.shared.model.formatMinutes
import com.desconecta.hp.wear.MainActivity
import com.desconecta.hp.wear.data.WatchGameRepository
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.ui.graphics.toArgb
import javax.inject.Inject

/**
 * Tile: la vida de un vistazo sin abrir la app, en el mismo lenguaje visual que el HUD
 * (arco de 240° con el hueco abajo, mismos colores de estado). El botón de poción NO
 * la usa directamente: abre la confirmación de la app, porque los objetos siempre se
 * confirman (prevención de errores).
 */
@AndroidEntryPoint
class HpTileService : TileService() {

    @Inject lateinit var repository: WatchGameRepository

    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        SuspendToFutureAdapter.launchFuture {
            if (repository.snapshot.value.game == null) repository.refresh()
            val game = repository.snapshot.value.game
            val device = requestParams.deviceConfiguration
            TileBuilders.Tile.Builder()
                .setResourcesVersion(RESOURCES_VERSION)
                .setFreshnessIntervalMillis(60_000) // la vida cambia como mucho 1 HP por minuto
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(
                        if (game == null) emptyLayout(device) else layout(game, device),
                    ),
                )
                .build()
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = SuspendToFutureAdapter.launchFuture {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    private fun layout(game: GameState, device: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val colors = HpColorSchemes.WatchDark
        val health = game.health
        val statusColor = colors.forStatus(health.status).toArgb()
        val gameOver = game.phase == GamePhase.GAME_OVER

        val arc = CircularProgressIndicator.Builder()
            .setProgress(health.fraction)
            .setStartAngle(-120f) // 0° = las 12: −120…120 deja el hueco abajo, como el HUD
            .setEndAngle(120f)
            .setStrokeWidth(10f)
            .setCircularProgressIndicatorColors(ProgressIndicatorColors(argb(statusColor), argb(colors.hpTrack.toArgb())))
            .setContentDescription("Vida: ${health.clamped} de ${health.max}")
            .build()

        val number = Text.Builder(this, if (gameOver) "K.O." else "${health.clamped}")
            .setTypography(Typography.TYPOGRAPHY_DISPLAY1)
            .setColor(argb(statusColor))
            .build()

        val label = Text.Builder(this, if (gameOver) "Respawn a medianoche" else "/ ${health.max} HP · ${formatMinutes(health.clamped)}")
            .setTypography(Typography.TYPOGRAPHY_CAPTION1)
            .setColor(argb(colors.textSecondary.toArgb()))
            .build()

        val potions = game.inventory.potions
        val chip = CompactChip.Builder(
            this,
            if (gameOver) "Abrir" else "Poción x$potions",
            ModifiersBuilders.Clickable.Builder()
                .setId("potion")
                .setOnClick(
                    ActionBuilders.LaunchAction.Builder()
                        .setAndroidActivity(
                            ActionBuilders.AndroidActivity.Builder()
                                .setPackageName(packageName)
                                .setClassName(MainActivity::class.java.name)
                                .addKeyToExtraMapping(
                                    MainActivity.EXTRA_OPEN,
                                    ActionBuilders.AndroidStringExtra.Builder()
                                        .setValue(if (gameOver || potions == 0) MainActivity.OPEN_HOME else MainActivity.OPEN_POTION)
                                        .build(),
                                )
                                .build(),
                        )
                        .build(),
                )
                .build(),
            device,
        )
            .setChipColors(ChipColors(argb(colors.regen.toArgb()), argb(colors.onAccent.toArgb())))
            .build()

        return EdgeContentLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setEdgeContent(arc)
            .setPrimaryLabelTextContent(label)
            .setContent(number)
            .setSecondaryLabelTextContent(chip)
            .build()
    }

    private fun emptyLayout(device: DeviceParameters): LayoutElementBuilders.LayoutElement =
        EdgeContentLayout.Builder(device)
            .setContent(
                Text.Builder(this, "Abre HP en tu teléfono")
                    .setTypography(Typography.TYPOGRAPHY_BODY2)
                    .setColor(argb(HpColorSchemes.WatchDark.textPrimary.toArgb()))
                    .setMaxLines(2)
                    .build(),
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
