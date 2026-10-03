package com.vaazhstudios.appgotchi

import android.app.Application
import com.vaazhstudios.appgotchi.di.initKoin
import org.koin.android.ext.koin.androidContext

class AppgotchiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@AppgotchiApplication) }
    }
}
