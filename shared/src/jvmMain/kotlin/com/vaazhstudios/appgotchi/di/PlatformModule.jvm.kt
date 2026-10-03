package com.vaazhstudios.appgotchi.di

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.dsl.module

actual val platformModule = module {
    single { KSafe(fileName = "appgotchi", config = KSafeConfig(appNamespace = "com.vaazhstudios.appgotchi")) }
}
