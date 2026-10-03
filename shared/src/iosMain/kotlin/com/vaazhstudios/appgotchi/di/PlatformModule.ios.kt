package com.vaazhstudios.appgotchi.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.dsl.module

actual val platformModule = module {
    single { KSafe(fileName = "appgotchi") }
}
