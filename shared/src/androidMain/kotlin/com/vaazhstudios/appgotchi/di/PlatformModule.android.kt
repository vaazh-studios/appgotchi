package com.vaazhstudios.appgotchi.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { KSafe(androidContext(), fileName = "appgotchi") }
}
