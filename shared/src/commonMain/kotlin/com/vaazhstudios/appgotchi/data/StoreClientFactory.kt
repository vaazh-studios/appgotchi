package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectClient
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectTokenProvider
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.core.play.PlayClient
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import com.vaazhstudios.appgotchi.core.play.PlayTokenProvider
import io.ktor.client.HttpClient

interface StoreClientFactory {
    fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient
    fun play(credentials: PlayCredentials): StoreClient
}

/**
 * Keeps the most recent client per store so cached access tokens survive refreshes.
 * A different key replaces the old client, so rejected keys aren't held in memory. Called from the main thread only.
 */
class DefaultStoreClientFactory(private val httpClient: HttpClient) : StoreClientFactory {
    private var appStoreConnectClient: Pair<AppStoreConnectCredentials, StoreClient>? = null
    private var playClient: Pair<PlayCredentials, StoreClient>? = null

    override fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient {
        appStoreConnectClient?.let { (cachedFor, client) -> if (cachedFor == credentials) return client }
        return AppStoreConnectClient(httpClient, AppStoreConnectTokenProvider(credentials))
            .also { appStoreConnectClient = credentials to it }
    }

    override fun play(credentials: PlayCredentials): StoreClient {
        playClient?.let { (cachedFor, client) -> if (cachedFor == credentials) return client }
        return PlayClient(httpClient, PlayTokenProvider(credentials, httpClient))
            .also { playClient = credentials to it }
    }
}
