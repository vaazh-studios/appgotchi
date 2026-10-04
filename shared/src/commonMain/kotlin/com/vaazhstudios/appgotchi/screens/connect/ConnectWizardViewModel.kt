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
import com.vaazhstudios.appgotchi.data.developerAccountIdProblem
import com.vaazhstudios.appgotchi.data.hasExtension
import com.vaazhstudios.appgotchi.data.issuerIdProblem
import com.vaazhstudios.appgotchi.data.keyIdFromFileName
import com.vaazhstudios.appgotchi.data.looksLikePrivateKey
import com.vaazhstudios.appgotchi.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val APPLE_KEY_HEADER = "-----BEGIN PRIVATE KEY-----"

enum class WizardStatus { Idle, Verifying, Waiting, Connected }

enum class WizardScreen { Chooser, AppleChoice, AppleQuick, AppleGuided, PlayGuided }

private const val PASTED_KEY_IN_ACCOUNT_ID =
    "That looks like a private key, so Appgotchi didn’t keep it. Paste the Account ID: the long number under your developer name on Play Console’s home page."
private const val PASTED_KEY_IN_KEY_ID =
    "That looks like your private key, so Appgotchi didn’t keep it. The Key ID is the 10-character ID next to the key in App Store Connect."
private const val PASTED_KEY_IN_ISSUER_ID =
    "That looks like your private key, so Appgotchi didn’t keep it. The Issuer ID is the short ID shown above the keys list."

data class KeyFile(val name: String, val text: String) {
    override fun toString() = "KeyFile(name=$name, text=***)"
}

data class ConnectWizardState(
    val screen: WizardScreen = WizardScreen.Chooser,
    val step: Int = 0,
    val appleKeyFile: KeyFile? = null,
    val keyId: String = "",
    val issuerId: String = "",
    val developerAccountId: String = "",
    val playKeyFile: KeyFile? = null,
    val serviceAccountEmail: String? = null,
    val status: WizardStatus = WizardStatus.Idle,
    val error: String? = null,
) {
    val store: Store?
        get() = when (screen) {
            WizardScreen.Chooser -> null
            WizardScreen.PlayGuided -> Store.GooglePlay
            else -> Store.AppStore
        }

    val stepCount: Int
        get() = when (screen) {
            WizardScreen.AppleGuided -> APPLE_STEPS
            WizardScreen.PlayGuided -> PLAY_STEPS
            else -> 1
        }

    val isLastStep: Boolean get() = step == stepCount - 1

    val developerAccountIdError: String? get() = developerAccountIdProblem(developerAccountId)

    val issuerIdError: String? get() = issuerIdProblem(issuerId)

    /** Steps with a requirement: Apple's file step, Play's account ID step and Play's key step. */
    val canContinue: Boolean
        get() = when (screen) {
            WizardScreen.AppleGuided -> step != 1 || (appleKeyFile != null && keyId.isNotBlank())
            WizardScreen.PlayGuided -> when (step) {
                0 -> developerAccountId.isNotBlank() && developerAccountIdError == null
                3 -> serviceAccountEmail != null
                else -> true
            }
            else -> false
        }

    val canVerify: Boolean
        get() = isLastStep && status != WizardStatus.Verifying && status != WizardStatus.Connected && when (screen) {
            WizardScreen.AppleQuick, WizardScreen.AppleGuided ->
                appleKeyFile != null && keyId.isNotBlank() && issuerId.isNotBlank() && issuerIdError == null
            WizardScreen.PlayGuided -> playKeyFile != null && serviceAccountEmail != null
            else -> false
        }

    companion object {
        const val APPLE_STEPS = 3
        const val PLAY_STEPS = 6
    }
}

class ConnectWizardViewModel(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectWizardState())
    val state: StateFlow<ConnectWizardState> = _state.asStateFlow()

    fun chooseStore(store: Store) {
        navigate {
            if (it.screen != WizardScreen.Chooser) it
            else it.copy(screen = if (store == Store.AppStore) WizardScreen.AppleChoice else WizardScreen.PlayGuided, step = 0)
        }
    }

    fun chooseAppleQuick() {
        navigate { if (it.screen == WizardScreen.AppleChoice) it.copy(screen = WizardScreen.AppleQuick, step = 0) else it }
    }

    fun chooseAppleGuided() {
        navigate { if (it.screen == WizardScreen.AppleChoice) it.copy(screen = WizardScreen.AppleGuided, step = 0) else it }
    }

    fun next() {
        navigate { if (it.canContinue && !it.isLastStep) it.copy(step = it.step + 1) else it }
    }

    /** Previous step, or up one screen: Apple paths → key choice → chooser; Play → chooser. */
    fun back() {
        navigate {
            when {
                it.step > 0 -> it.copy(step = it.step - 1)
                it.screen == WizardScreen.AppleQuick || it.screen == WizardScreen.AppleGuided -> it.copy(screen = WizardScreen.AppleChoice)
                it.screen == WizardScreen.AppleChoice || it.screen == WizardScreen.PlayGuided -> it.copy(screen = WizardScreen.Chooser)
                else -> it
            }
        }
    }

    fun onDeveloperAccountIdChange(value: String) {
        editUnlessVerifying {
            if (looksLikePrivateKey(value)) it.copy(developerAccountId = "", error = PASTED_KEY_IN_ACCOUNT_ID)
            else it.copy(developerAccountId = value.trim(), error = null)
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
        editUnlessVerifying {
            if (looksLikePrivateKey(value)) it.copy(keyId = "", error = PASTED_KEY_IN_KEY_ID)
            else it.copy(keyId = value, error = null)
        }
    }

    fun onIssuerIdChange(value: String) {
        editUnlessVerifying {
            if (looksLikePrivateKey(value)) it.copy(issuerId = "", error = PASTED_KEY_IN_ISSUER_ID)
            else it.copy(issuerId = value, error = null)
        }
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
        val store = current.store ?: return
        _state.update { it.copy(status = WizardStatus.Verifying, error = null) }
        viewModelScope.launch {
            try {
                val status = when (store) {
                    Store.AppStore -> verifyAppStore(current)
                    Store.GooglePlay -> verifyPlay(current)
                }
                _state.update { it.copy(status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(status = WizardStatus.Idle, error = e.userMessage(store)) }
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

    /** Moving between steps or screens clears errors and any Waiting state; nothing moves while verifying or once connected. */
    private fun navigate(transform: (ConnectWizardState) -> ConnectWizardState) {
        _state.update {
            if (it.status == WizardStatus.Verifying || it.status == WizardStatus.Connected) it
            else transform(it).copy(status = WizardStatus.Idle, error = null)
        }
    }

    /** The key files and IDs are in use while verifying, so edits made meanwhile are dropped. */
    private fun editUnlessVerifying(transform: (ConnectWizardState) -> ConnectWizardState) {
        _state.update { if (it.status == WizardStatus.Verifying) it else transform(it) }
    }
}
