package com.desconecta.hp.wear.complication

import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.desconecta.hp.shared.R as SharedR
import com.desconecta.hp.shared.model.GameState
import com.desconecta.hp.wear.data.WatchGameRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

private fun text(s: String) = PlainComplicationText.Builder(s).build()

/**
 * Complicación "HP restante" (arco). La carátula decide cómo dibujar el arco; nosotros
 * damos valor, rango, texto e ícono, y TalkBack recibe una descripción completa.
 */
@AndroidEntryPoint
class HpComplicationService : SuspendingComplicationDataSourceService() {

    @Inject lateinit var repository: WatchGameRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (repository.snapshot.value.game == null) repository.refresh()
        val game = repository.snapshot.value.game ?: return null
        return build(request.complicationType, game)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        build(type, GameState(dayKey = "", maxHp = 120, hp = 84))

    private fun build(type: ComplicationType, game: GameState): ComplicationData? {
        val h = game.health
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, SharedR.drawable.ic_hp_heart)).build()
        val description = text("Vida: ${h.clamped} de ${h.max}")
        return when (type) {
            ComplicationType.RANGED_VALUE ->
                RangedValueComplicationData.Builder(h.clamped.toFloat(), 0f, h.max.toFloat(), description)
                    .setText(text("${h.clamped}"))
                    .setMonochromaticImage(icon)
                    .build()
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text("${h.clamped}"), description)
                    .setTitle(text("HP"))
                    .setMonochromaticImage(icon)
                    .build()
            else -> null
        }
    }
}

/** Complicación "racha": número + llama. */
@AndroidEntryPoint
class StreakComplicationService : SuspendingComplicationDataSourceService() {

    @Inject lateinit var repository: WatchGameRepository

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (repository.snapshot.value.game == null) repository.refresh()
        val days = repository.snapshot.value.game?.player?.streakDays ?: return null
        return build(request.complicationType, days)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = build(type, 12)

    private fun build(type: ComplicationType, days: Int): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(text("$days"), text("Racha de $days días sin Game Over"))
            .setMonochromaticImage(MonochromaticImage.Builder(Icon.createWithResource(this, SharedR.drawable.ic_hp_flame)).build())
            .build()
    }
}
