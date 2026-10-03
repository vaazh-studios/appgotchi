package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeCredentialStore(
    var apple: AppStoreConnectCredentials? = null,
    var play: PlayCredentials? = null,
    var failReads: Boolean = false,
) : CredentialStore {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun appStoreConnect(): AppStoreConnectCredentials? {
        if (failReads) error("keychain locked")
        return apple
    }

    override suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials) {
        apple = credentials
        changes.tryEmit(Unit)
    }

    override suspend fun play(): PlayCredentials? {
        if (failReads) error("keychain locked")
        return play
    }

    override suspend fun savePlay(credentials: PlayCredentials) {
        play = credentials
        changes.tryEmit(Unit)
    }
}

class FakeStoreClient(
    override val store: Store,
    private val result: () -> List<StoreApp>,
) : StoreClient {
    override suspend fun listApps() = result()
}

class FakeStoreClientFactory(
    private val apple: StoreClient,
    private val play: StoreClient,
) : StoreClientFactory {
    val appleCredentialsUsed = mutableListOf<AppStoreConnectCredentials>()
    val playCredentialsUsed = mutableListOf<PlayCredentials>()

    override fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient {
        appleCredentialsUsed += credentials
        return apple
    }

    override fun play(credentials: PlayCredentials): StoreClient {
        playCredentialsUsed += credentials
        return play
    }
}
