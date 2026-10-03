package com.vaazhstudios.appgotchi.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import com.vaazhstudios.appgotchi.data.CredentialStore
import com.vaazhstudios.appgotchi.data.StoreClientFactory
import com.vaazhstudios.appgotchi.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val busy: Boolean = false,
    val appleError: String? = null,
    val playError: String? = null,
    val connected: Boolean = false,
)

class ConnectViewModel(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun connectAppStore(issuerId: String, keyId: String, privateKey: String) {
        val credentials = AppStoreConnectCredentials(issuerId.trim(), keyId.trim(), privateKey.trim())
        if (credentials.issuerId.isEmpty() || credentials.keyId.isEmpty() || credentials.privateKeyPem.isEmpty()) {
            _state.update { it.copy(appleError = "Fill in the Issuer ID, Key ID and private key.") }
            return
        }
        verifyAndSave(Store.AppStore) {
            clientFactory.appStoreConnect(credentials).listApps()
            credentialStore.saveAppStoreConnect(credentials)
        }
    }

    fun connectPlay(serviceAccountJson: String) {
        val credentials = PlayCredentials(serviceAccountJson.trim())
        if (credentials.serviceAccountJson.isEmpty()) {
            _state.update { it.copy(playError = "Paste the contents of your service account JSON key.") }
            return
        }
        verifyAndSave(Store.GooglePlay) {
            if (clientFactory.play(credentials).listApps().isEmpty()) {
                throw InvalidCredentialsException(
                    "This key works, but it can't see any apps yet. Invite the service account's email in Play Console → Users and permissions. New permissions can take a few hours to apply."
                )
            }
            credentialStore.savePlay(credentials)
        }
    }

    private fun verifyAndSave(store: Store, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, appleError = null, playError = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false, connected = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.userMessage(store)
                _state.update {
                    when (store) {
                        Store.AppStore -> it.copy(busy = false, appleError = message)
                        Store.GooglePlay -> it.copy(busy = false, playError = message)
                    }
                }
            }
        }
    }
}
