package com.desconecta.hp.mobile

import android.app.Application
import com.desconecta.hp.mobile.ai.AiCoordinator
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class HpMobileApp : Application() {

    @Inject lateinit var ai: AiCoordinator

    override fun onCreate() {
        super.onCreate()
        // La IA corre en el teléfono aunque la app no esté abierta (el monitoreo sí lo está).
        ai.start()
    }
}
