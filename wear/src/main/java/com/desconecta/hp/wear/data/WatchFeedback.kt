package com.desconecta.hp.wear.data

import android.content.Context
import com.desconecta.hp.shared.datalayer.WireEvent
import com.desconecta.hp.shared.designsystem.haptics.HpHapticEvent
import com.desconecta.hp.shared.designsystem.haptics.HpHapticPlayer
import com.desconecta.hp.shared.model.HpWarning
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lo que el jugador SIENTE al llegar un evento: vibración propia de cada evento y, si la
 * app está cerrada, una notificación. Es igual llegue el evento por Bluetooth (Data
 * Layer) o por Wi‑Fi (Supabase).
 */
@Singleton
class WatchFeedback @Inject constructor(
    @param:ApplicationContext context: Context,
    private val notifier: WatchNotifier,
) {
    private val haptics = HpHapticPlayer(context)

    fun play(event: WireEvent, hapticsEnabled: Boolean) {
        haptics.enabled = hapticsEnabled
        haptics.play(
            when (event) {
                is WireEvent.Warning -> when (event.warning) {
                    HpWarning.HALF, HpWarning.LOW -> HpHapticEvent.WARNING
                    HpWarning.VERY_LOW, HpWarning.LAST_FIVE -> HpHapticEvent.WARNING_URGENT
                }
                is WireEvent.MonsterDefeated -> HpHapticEvent.MONSTER_DEFEATED
                WireEvent.GameOver -> HpHapticEvent.GAME_OVER
                is WireEvent.LevelUp, is WireEvent.Achievement -> HpHapticEvent.CELEBRATION
                is WireEvent.Healed -> HpHapticEvent.HEAL
                is WireEvent.Respawn, is WireEvent.OpenPotion -> HpHapticEvent.CONFIRM
            },
        )
        notifier.notifyIfInBackground(event)
    }
}
