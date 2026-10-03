package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

data class StoreSection(
    val store: Store,
    val apps: List<StoreApp>,
    val errorMessage: String?,
)

class AppsRepository(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) {
    val credentialChanges: Flow<Unit> get() = credentialStore.changes

    suspend fun loadSections(): List<StoreSection> = coroutineScope {
        listOf(
            async { section(Store.AppStore, credentialStore::appStoreConnect, clientFactory::appStoreConnect) },
            async { section(Store.GooglePlay, credentialStore::play, clientFactory::play) },
        ).awaitAll().filterNotNull()
    }

    private suspend fun <C : Any> section(
        store: Store,
        readCredentials: suspend () -> C?,
        createClient: (C) -> StoreClient,
    ): StoreSection? {
        val credentials = try {
            readCredentials() ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return StoreSection(
                store,
                emptyList(),
                "Couldn't read the saved ${store.displayName} key from this device's secure storage. Unlock your device and try again.",
            )
        }
        return try {
            StoreSection(store, createClient(credentials).listApps(), errorMessage = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StoreSection(store, emptyList(), e.userMessage(store))
        }
    }
}
