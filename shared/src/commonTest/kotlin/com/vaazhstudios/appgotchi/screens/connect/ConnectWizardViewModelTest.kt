package com.vaazhstudios.appgotchi.screens.connect

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.data.FakeCredentialStore
import com.vaazhstudios.appgotchi.data.FakeStoreClient
import com.vaazhstudios.appgotchi.data.FakeStoreClientFactory
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectWizardViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val playApp = StoreApp(Store.GooglePlay, "com.example.posepal", "PosePal", "com.example.posepal")
    private val pem = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----"
    private val serviceAccountJson =
        """{"type":"service_account","client_email":"appgotchi@project.iam.gserviceaccount.com","private_key":"x"}"""

    private fun viewModel(
        store: FakeCredentialStore = FakeCredentialStore(),
        apple: () -> List<StoreApp> = { emptyList() },
        play: () -> List<StoreApp> = { listOf(playApp) },
    ) = ConnectWizardViewModel(
        store,
        FakeStoreClientFactory(FakeStoreClient(Store.AppStore, apple), FakeStoreClient(Store.GooglePlay, play)),
    )

    @Test
    fun startsOnTheFirstAppStoreStep() {
        val state = viewModel().state.value

        assertEquals(Store.AppStore, state.store)
        assertEquals(0, state.step)
        assertEquals(WizardStatus.Idle, state.status)
    }

    @Test
    fun appleKeyIdIsReadFromTheFileNameAndTextIsCleaned() {
        val vm = viewModel()

        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "\uFEFF$pem\n")

        assertEquals("7XK2M9Q4TB", vm.state.value.keyId)
        assertEquals(pem, vm.state.value.appleKeyFile?.text)
    }

    @Test
    fun renamedAppleFileKeepsTheTypedKeyId() {
        val vm = viewModel()
        vm.onKeyIdChange("ABC123")

        vm.onAppleKeyFile("my-key.p8", "key")

        assertEquals("ABC123", vm.state.value.keyId)
    }

    @Test
    fun wrongAppleFileTypeShowsAnError() {
        val vm = viewModel()

        vm.onAppleKeyFile("notes.txt", "hello")

        assertNull(vm.state.value.appleKeyFile)
        assertEquals("That's not a .p8 file. Choose the AuthKey file you downloaded from App Store Connect.", vm.state.value.error)
    }

    @Test
    fun cannotContinuePastTheFileStepWithoutAFile() {
        val vm = viewModel()

        vm.next()
        vm.next()

        assertEquals(1, vm.state.value.step)
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun appleKeyIsVerifiedThenSaved() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store)
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.onIssuerIdChange("  issuer-1  ")

        vm.verify()

        assertEquals(WizardStatus.Connected, vm.state.value.status)
        assertEquals(AppStoreConnectCredentials("issuer-1", "7XK2M9Q4TB", pem), store.apple)
    }

    @Test
    fun rejectedAppleKeyShowsAnErrorAndIsNotSaved() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store, apple = { throw StoreApiException(Store.AppStore, 401, "") })
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.onIssuerIdChange("issuer-1")

        vm.verify()

        assertEquals(WizardStatus.Idle, vm.state.value.status)
        assertEquals("App Store Connect rejected this key. Check the Issuer ID, Key ID and private key.", vm.state.value.error)
        assertNull(store.apple)
    }

    @Test
    fun playKeyFileRevealsTheServiceAccountEmail() {
        val vm = viewModel()
        vm.selectStore(Store.GooglePlay)

        vm.onPlayKeyFile("appgotchi-key.json", serviceAccountJson)

        assertEquals("appgotchi@project.iam.gserviceaccount.com", vm.state.value.serviceAccountEmail)
        assertTrue(vm.state.value.canVerify)
    }

    @Test
    fun invalidPlayKeyShowsAReadableError() {
        val vm = viewModel()
        vm.selectStore(Store.GooglePlay)

        vm.onPlayKeyFile("other.json", """{"type":"authorized_user"}""")

        assertNull(vm.state.value.serviceAccountEmail)
        assertEquals(
            "This isn't a Google service account key. Download a JSON key for a service account from Google Cloud.",
            vm.state.value.error,
        )
    }

    @Test
    fun playKeyThatSeesNoAppsWaitsWithoutSaving() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store, play = { emptyList() })
        vm.selectStore(Store.GooglePlay)
        vm.onPlayKeyFile("appgotchi-key.json", serviceAccountJson)

        vm.verify()

        assertEquals(WizardStatus.Waiting, vm.state.value.status)
        assertNull(store.play)
    }

    @Test
    fun checkingAgainConnectsOnceAppsAppear() = runTest {
        val store = FakeCredentialStore()
        var apps = emptyList<StoreApp>()
        val vm = viewModel(store, play = { apps })
        vm.selectStore(Store.GooglePlay)
        vm.onPlayKeyFile("appgotchi-key.json", serviceAccountJson)
        vm.verify()

        apps = listOf(playApp)
        vm.verify()

        assertEquals(WizardStatus.Connected, vm.state.value.status)
        assertEquals(serviceAccountJson, store.play?.serviceAccountJson)
    }

    @Test
    fun switchingStoreReturnsToTheFirstStep() {
        val vm = viewModel()
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.next()
        vm.next()

        vm.selectStore(Store.GooglePlay)

        assertEquals(Store.GooglePlay, vm.state.value.store)
        assertEquals(0, vm.state.value.step)
    }

    @Test
    fun unreadableFileShowsAnError() {
        val vm = viewModel()

        vm.onFileReadFailed()

        assertEquals("Couldn't read that file. Try choosing it again.", vm.state.value.error)
    }

    @Test
    fun appleFileWithoutAPrivateKeyIsRejectedAndNotStored() {
        val vm = viewModel()

        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "just some text")

        assertNull(vm.state.value.appleKeyFile)
        assertEquals(
            "This .p8 file isn't a valid App Store Connect private key. Download the key again from App Store Connect.",
            vm.state.value.error,
        )
    }

    @Test
    fun wrongAppleFileTypeIsRejectedBeforeTheTextIsLookedAt() {
        val vm = viewModel()

        vm.onAppleKeyFile("notes.txt", "")

        assertEquals("That's not a .p8 file. Choose the AuthKey file you downloaded from App Store Connect.", vm.state.value.error)
    }

    @Test
    fun malformedPlayTextShowsAReadableError() {
        val vm = viewModel()
        vm.selectStore(Store.GooglePlay)

        vm.onPlayKeyFile("key.json", "not json")

        assertNull(vm.state.value.playKeyFile)
        assertEquals(
            "This isn't a Google service account key. Download a JSON key for a service account from Google Cloud.",
            vm.state.value.error,
        )
    }

    @Test
    fun oversizedFileShowsAReadableError() {
        val vm = viewModel()

        vm.onFileTooLarge()

        assertEquals("That file is too large to be a key file. Choose the file you downloaded.", vm.state.value.error)
    }

    @Test
    fun keyFileToStringRedactsTheText() {
        val keyFile = KeyFile("AuthKey_7XK2M9Q4TB.p8", "super-secret")

        assertEquals("KeyFile(name=AuthKey_7XK2M9Q4TB.p8, text=***)", keyFile.toString())
    }

    @Test
    fun cannotVerifyOnceConnected() = runTest {
        val vm = viewModel()
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.onIssuerIdChange("issuer-1")
        vm.verify()

        assertEquals(WizardStatus.Connected, vm.state.value.status)
        assertFalse(vm.state.value.canVerify)
    }

    @Test
    fun selectingTheCurrentStoreKeepsTheStep() {
        val vm = viewModel()
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.next()

        vm.selectStore(Store.AppStore)

        assertEquals(1, vm.state.value.step)
    }

    @Test
    fun removingTheAppleFileClearsAKeyIdReadFromItsName() {
        val vm = viewModel()
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)

        vm.removeAppleKeyFile()

        assertNull(vm.state.value.appleKeyFile)
        assertEquals("", vm.state.value.keyId)
    }

    @Test
    fun removingTheAppleFileKeepsAKeyIdTheUserTyped() {
        val vm = viewModel()
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.onKeyIdChange("ABC123")

        vm.removeAppleKeyFile()

        assertEquals("ABC123", vm.state.value.keyId)
    }

    @Test
    fun editsAreIgnoredWhileVerifying() {
        val gate = CompletableDeferred<List<StoreApp>>()
        val vm = ConnectWizardViewModel(
            FakeCredentialStore(),
            FakeStoreClientFactory(GatedStoreClient(Store.AppStore, gate), GatedStoreClient(Store.GooglePlay, gate)),
        )
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", pem)
        vm.onIssuerIdChange("issuer-1")
        vm.verify()
        val verifying = vm.state.value
        assertEquals(WizardStatus.Verifying, verifying.status)

        vm.onAppleKeyFile("AuthKey_OTHER.p8", pem)
        vm.removeAppleKeyFile()
        vm.onKeyIdChange("CHANGED")
        vm.onIssuerIdChange("changed")
        vm.onPlayKeyFile("key.json", serviceAccountJson)
        vm.removePlayKeyFile()
        vm.onFileReadFailed()
        vm.onFileTooLarge()

        assertEquals(verifying, vm.state.value)
        gate.complete(emptyList())
        assertNotEquals(WizardStatus.Verifying, vm.state.value.status)
    }
}

private class GatedStoreClient(
    override val store: Store,
    private val gate: CompletableDeferred<List<StoreApp>>,
) : StoreClient {
    override suspend fun listApps() = gate.await()
}
