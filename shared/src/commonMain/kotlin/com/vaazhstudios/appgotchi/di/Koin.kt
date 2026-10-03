package com.vaazhstudios.appgotchi.di

import org.koin.core.context.startKoin
import org.koin.dsl.module

val appModule = module {
}

fun initKoin() {
    startKoin {
        modules(appModule)
    }
}
