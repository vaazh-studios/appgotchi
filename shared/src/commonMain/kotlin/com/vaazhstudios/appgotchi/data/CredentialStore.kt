package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.StoreJson
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

interface CredentialStore {
    val changes: Flow<Unit>
    suspend fun appStoreConnect(): AppStoreConnectCredentials?
    suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials)
    suspend fun play(): PlayCredentials?
    suspend fun savePlay(credentials: PlayCredentials)
}

// Lazy so a KSafe construction failure surfaces inside the guarded reads and saves, not at launch
class KSafeCredentialStore(private val ksafe: Lazy<KSafe>) : CredentialStore {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun appStoreConnect(): AppStoreConnectCredentials? =
        ksafe.value.get<String?>(APP_STORE_CONNECT_KEY, null)
            ?.let { StoreJson.decodeFromString<StoredAppStoreConnectKey>(it) }
            ?.let { AppStoreConnectCredentials(it.issuerId, it.keyId, it.privateKeyPem) }

    override suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials) {
        // One value, one write: a partial save can never mix fields from two keys
        val stored = StoredAppStoreConnectKey(credentials.issuerId, credentials.keyId, credentials.privateKeyPem)
        ksafe.value.put(APP_STORE_CONNECT_KEY, StoreJson.encodeToString(StoredAppStoreConnectKey.serializer(), stored))
        _changes.tryEmit(Unit)
    }

    override suspend fun play(): PlayCredentials? =
        ksafe.value.get<String?>(PLAY_SERVICE_ACCOUNT, null)?.let(::PlayCredentials)

    override suspend fun savePlay(credentials: PlayCredentials) {
        ksafe.value.put(PLAY_SERVICE_ACCOUNT, credentials.serviceAccountJson)
        _changes.tryEmit(Unit)
    }

    private companion object {
        const val APP_STORE_CONNECT_KEY = "appStoreConnect.key"
        const val PLAY_SERVICE_ACCOUNT = "play.serviceAccount"
    }
}

@Serializable
private class StoredAppStoreConnectKey(val issuerId: String, val keyId: String, val privateKeyPem: String)
