package com.vaazhstudios.appgotchi.screens.connect

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.data.FakeCredentialStore
import com.vaazhstudios.appgotchi.data.FakeStoreClient
import com.vaazhstudios.appgotchi.data.FakeStoreClientFactory
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectWizardViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val playApp = StoreApp(Store.GooglePlay, "com.example.posepal", "PosePal", "com.example.posepal")
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

        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "﻿-----BEGIN PRIVATE KEY-----\n")

        assertEquals("7XK2M9Q4TB", vm.state.value.keyId)
        assertEquals("-----BEGIN PRIVATE KEY-----", vm.state.value.appleKeyFile?.text)
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
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "pem")
        vm.onIssuerIdChange("  issuer-1  ")

        vm.verify()

        assertEquals(WizardStatus.Connected, vm.state.value.status)
        assertEquals(AppStoreConnectCredentials("issuer-1", "7XK2M9Q4TB", "pem"), store.apple)
    }

    @Test
    fun rejectedAppleKeyShowsAnErrorAndIsNotSaved() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store, apple = { throw StoreApiException(Store.AppStore, 401, "") })
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "pem")
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
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "pem")
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
}
