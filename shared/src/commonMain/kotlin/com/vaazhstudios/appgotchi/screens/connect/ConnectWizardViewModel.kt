package com.vaazhstudios.appgotchi.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import com.vaazhstudios.appgotchi.data.CredentialStore
import com.vaazhstudios.appgotchi.data.StoreClientFactory
import com.vaazhstudios.appgotchi.data.cleanKeyFileText
import com.vaazhstudios.appgotchi.data.hasExtension
import com.vaazhstudios.appgotchi.data.keyIdFromFileName
import com.vaazhstudios.appgotchi.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WizardStatus { Idle, Verifying, Waiting, Connected }

data class KeyFile(val name: String, val text: String) {
    override fun toString() = "KeyFile(name=$name, text=***)"
}

data class ConnectWizardState(
    val store: Store = Store.AppStore,
    val step: Int = 0,
    val appleKeyFile: KeyFile? = null,
    val keyId: String = "",
    val issuerId: String = "",
    val playKeyFile: KeyFile? = null,
    val serviceAccountEmail: String? = null,
    val status: WizardStatus = WizardStatus.Idle,
    val error: String? = null,
) {
    val isLastStep: Boolean get() = step == STEP_COUNT - 1

    /** Only the file step (index 1) has a requirement before moving on. */
    val canContinue: Boolean
        get() = when {
            step != 1 -> true
            store == Store.AppStore -> appleKeyFile != null && keyId.isNotBlank()
            else -> serviceAccountEmail != null
        }

    val canVerify: Boolean
        get() = status != WizardStatus.Verifying && when (store) {
            Store.AppStore -> appleKeyFile != null && keyId.isNotBlank() && issuerId.isNotBlank()
            Store.GooglePlay -> playKeyFile != null && serviceAccountEmail != null
        }

    companion object {
        const val STEP_COUNT = 3
    }
}

class ConnectWizardViewModel(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectWizardState())
    val state: StateFlow<ConnectWizardState> = _state.asStateFlow()

    fun selectStore(store: Store) {
        _state.update {
            if (it.status == WizardStatus.Verifying) it
            else it.copy(store = store, step = 0, status = WizardStatus.Idle, error = null)
        }
    }

    fun next() {
        _state.update { if (it.canContinue && !it.isLastStep) it.copy(step = it.step + 1, error = null) else it }
    }

    fun back() {
        _state.update {
            if (it.step > 0 && it.status != WizardStatus.Verifying) {
                it.copy(step = it.step - 1, status = WizardStatus.Idle, error = null)
            } else {
                it
            }
        }
    }

    fun onAppleKeyFile(fileName: String, text: String) {
        if (!hasExtension(fileName, "p8")) {
            showError("That's not a .p8 file. Choose the AuthKey file you downloaded from App Store Connect.")
            return
        }
        _state.update {
            it.copy(
                appleKeyFile = KeyFile(fileName, cleanKeyFileText(text)),
                keyId = keyIdFromFileName(fileName) ?: it.keyId,
                error = null,
            )
        }
    }

    fun removeAppleKeyFile() {
        _state.update { it.copy(appleKeyFile = null, error = null) }
    }

    fun onKeyIdChange(value: String) {
        _state.update { it.copy(keyId = value) }
    }

    fun onIssuerIdChange(value: String) {
        _state.update { it.copy(issuerId = value) }
    }

    fun onPlayKeyFile(fileName: String, text: String) {
        if (!hasExtension(fileName, "json")) {
            showError("That's not a .json file. Choose the service account key you downloaded from Google Cloud.")
            return
        }
        val credentials = PlayCredentials(cleanKeyFileText(text))
        try {
            val email = credentials.serviceAccountKey().clientEmail
            _state.update {
                it.copy(
                    playKeyFile = KeyFile(fileName, credentials.serviceAccountJson),
                    serviceAccountEmail = email,
                    status = WizardStatus.Idle,
                    error = null,
                )
            }
        } catch (e: InvalidCredentialsException) {
            _state.update { it.copy(playKeyFile = null, serviceAccountEmail = null, error = e.message) }
        }
    }

    fun removePlayKeyFile() {
        _state.update { it.copy(playKeyFile = null, serviceAccountEmail = null, status = WizardStatus.Idle, error = null) }
    }

    fun onFileReadFailed() {
        showError("Couldn't read that file. Try choosing it again.")
    }

    fun verify() {
        val current = _state.value
        if (!current.canVerify) return
        _state.update { it.copy(status = WizardStatus.Verifying, error = null) }
        viewModelScope.launch {
            try {
                val status = when (current.store) {
                    Store.AppStore -> verifyAppStore(current)
                    Store.GooglePlay -> verifyPlay(current)
                }
                _state.update { it.copy(status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(status = WizardStatus.Idle, error = e.userMessage(current.store)) }
            }
        }
    }

    private suspend fun verifyAppStore(state: ConnectWizardState): WizardStatus {
        val keyFile = requireNotNull(state.appleKeyFile)
        val credentials = AppStoreConnectCredentials(state.issuerId.trim(), state.keyId.trim(), keyFile.text)
        clientFactory.appStoreConnect(credentials).listApps()
        credentialStore.saveAppStoreConnect(credentials)
        return WizardStatus.Connected
    }

    private suspend fun verifyPlay(state: ConnectWizardState): WizardStatus {
        val credentials = PlayCredentials(requireNotNull(state.playKeyFile).text)
        // apps:search returns no apps (not an error) until the Play Console invite has taken effect
        if (clientFactory.play(credentials).listApps().isEmpty()) return WizardStatus.Waiting
        credentialStore.savePlay(credentials)
        return WizardStatus.Connected
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
