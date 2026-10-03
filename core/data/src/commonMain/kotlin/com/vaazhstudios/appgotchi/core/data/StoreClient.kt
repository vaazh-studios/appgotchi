package com.vaazhstudios.appgotchi.core.data

enum class Store { AppStore, GooglePlay }

data class StoreApp(
    val store: Store,
    val id: String,
    val name: String,
    val bundleId: String,
)

interface StoreClient {
    val store: Store
    suspend fun listApps(): List<StoreApp>
}
