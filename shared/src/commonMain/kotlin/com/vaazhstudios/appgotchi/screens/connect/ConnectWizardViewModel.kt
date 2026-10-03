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

private const val APPLE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----"

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
        get() = status != WizardStatus.Verifying && status != WizardStatus.Connected && when (store) {
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
            if (it.status == WizardStatus.Verifying || it.store == store) it
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
        val cleaned = cleanKeyFileText(text)
        if (!cleaned.contains(APPLE_KEY_HEADER)) {
            showError("This .p8 file isn't a valid App Store Connect private key. Download the key again from App Store Connect.")
            return
        }
        editUnlessVerifying {
            it.copy(
                appleKeyFile = KeyFile(fileName, cleaned),
                keyId = keyIdFromFileName(fileName) ?: it.keyId,
                error = null,
            )
        }
    }

    fun removeAppleKeyFile() {
        editUnlessVerifying {
            // A Key ID read from the file's name goes with the file; one the user typed stays
            val readFromFileName = it.appleKeyFile?.let { file -> keyIdFromFileName(file.name) }
            it.copy(
                appleKeyFile = null,
                keyId = if (readFromFileName != null && it.keyId == readFromFileName) "" else it.keyId,
                error = null,
            )
        }
    }

    fun onKeyIdChange(value: String) {
        editUnlessVerifying { it.copy(keyId = value) }
    }

    fun onIssuerIdChange(value: String) {
        editUnlessVerifying { it.copy(issuerId = value) }
    }

    fun onPlayKeyFile(fileName: String, text: String) {
        if (!hasExtension(fileName, "json")) {
            showError("That's not a .json file. Choose the service account key you downloaded from Google Cloud.")
            return
        }
        val credentials = PlayCredentials(cleanKeyFileText(text))
        try {
            val email = credentials.serviceAccountKey().clientEmail
            editUnlessVerifying {
                it.copy(
                    playKeyFile = KeyFile(fileName, credentials.serviceAccountJson),
                    serviceAccountEmail = email,
                    status = WizardStatus.Idle,
                    error = null,
                )
            }
        } catch (e: InvalidCredentialsException) {
            editUnlessVerifying { it.copy(playKeyFile = null, serviceAccountEmail = null, error = e.message) }
        }
    }

    fun removePlayKeyFile() {
        editUnlessVerifying { it.copy(playKeyFile = null, serviceAccountEmail = null, status = WizardStatus.Idle, error = null) }
    }

    fun onFileReadFailed() {
        showError("Couldn't read that file. Try choosing it again.")
    }

    fun onFileTooLarge() {
        showError("That file is too large to be a key file. Choose the file you downloaded.")
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
        editUnlessVerifying { it.copy(error = message) }
    }

    /** The key files and IDs are in use while verifying, so edits made meanwhile are dropped. */
    private fun editUnlessVerifying(transform: (ConnectWizardState) -> ConnectWizardState) {
        _state.update { if (it.status == WizardStatus.Verifying) it else transform(it) }
    }
}
