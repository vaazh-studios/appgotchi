package com.vaazhstudios.appgotchi

import android.app.Application
import com.vaazhstudios.appgotchi.di.initKoin

class AppgotchiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin()
    }
}
