package com.vaazhstudios.appgotchi.di

import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.CredentialStore
import com.vaazhstudios.appgotchi.data.DefaultStoreClientFactory
import com.vaazhstudios.appgotchi.data.KSafeCredentialStore
import com.vaazhstudios.appgotchi.data.StoreClientFactory
import com.vaazhstudios.appgotchi.screens.connect.ConnectWizardViewModel
import com.vaazhstudios.appgotchi.screens.today.TodayViewModel
import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
            }
        }
    }
    single<StoreClientFactory> { DefaultStoreClientFactory(get()) }
    single<CredentialStore> { KSafeCredentialStore(lazy { get<KSafe>() }) }
    single { AppsRepository(get(), get()) }
    viewModelOf(::ConnectWizardViewModel)
    viewModelOf(::TodayViewModel)
}

fun initKoin() = initKoin {}

fun initKoin(config: KoinAppDeclaration) {
    startKoin {
        config()
        modules(appModule, platformModule)
    }
}
