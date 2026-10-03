package com.vaazhstudios.appgotchi.screens.connect

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun validAppleKeyIsVerifiedThenSaved() = runTest {
        val store = FakeCredentialStore()
        val factory = FakeStoreClientFactory(FakeStoreClient(Store.AppStore) { emptyList() }, FakeStoreClient(Store.GooglePlay) { emptyList() })
        val viewModel = ConnectViewModel(store, factory)

        viewModel.connectAppStore(" issuer ", " KEY ", "-----BEGIN PRIVATE KEY-----")

        assertEquals(AppStoreConnectCredentials("issuer", "KEY", "-----BEGIN PRIVATE KEY-----"), store.apple)
        assertTrue(viewModel.state.value.connected)
        assertNull(viewModel.state.value.appleError)
    }

    @Test
    fun rejectedAppleKeyIsNotSaved() = runTest {
        val store = FakeCredentialStore()
        val factory = FakeStoreClientFactory(
            FakeStoreClient(Store.AppStore) { throw StoreApiException(Store.AppStore, 401, "") },
            FakeStoreClient(Store.GooglePlay) { emptyList() },
        )
        val viewModel = ConnectViewModel(store, factory)

        viewModel.connectAppStore("issuer", "KEY", "pem")

        assertNull(store.apple)
        assertEquals("App Store Connect rejected this key. Check the Issuer ID, Key ID and private key.", viewModel.state.value.appleError)
    }

    @Test
    fun blankAppleFieldsAreRejectedWithoutCallingApple() = runTest {
        val factory = FakeStoreClientFactory(FakeStoreClient(Store.AppStore) { emptyList() }, FakeStoreClient(Store.GooglePlay) { emptyList() })
        val viewModel = ConnectViewModel(FakeCredentialStore(), factory)

        viewModel.connectAppStore("", "KEY", "pem")

        assertEquals("Fill in the Issuer ID, Key ID and private key.", viewModel.state.value.appleError)
        assertTrue(factory.appleCredentialsUsed.isEmpty())
    }

    @Test
    fun validPlayKeyIsVerifiedThenSaved() = runTest {
        val store = FakeCredentialStore()
        val playApp = StoreApp(Store.GooglePlay, "com.example.posepal", "PosePal", "com.example.posepal")
        val factory = FakeStoreClientFactory(FakeStoreClient(Store.AppStore) { emptyList() }, FakeStoreClient(Store.GooglePlay) { listOf(playApp) })
        val viewModel = ConnectViewModel(store, factory)

        viewModel.connectPlay("""{"type":"service_account"}""")

        assertEquals("""{"type":"service_account"}""", store.play?.serviceAccountJson)
        assertTrue(viewModel.state.value.connected)
    }

    @Test
    fun playKeyThatSeesNoAppsIsNotSaved() = runTest {
        // apps:search returns 200 with no apps when the service account isn't invited yet
        val store = FakeCredentialStore()
        val factory = FakeStoreClientFactory(FakeStoreClient(Store.AppStore) { emptyList() }, FakeStoreClient(Store.GooglePlay) { emptyList() })
        val viewModel = ConnectViewModel(store, factory)

        viewModel.connectPlay("""{"type":"service_account"}""")

        assertNull(store.play)
        assertEquals(
            "This key works, but it can't see any apps yet. Invite the service account's email in Play Console → Users and permissions. New permissions can take a few hours to apply.",
            viewModel.state.value.playError,
        )
    }

    @Test
    fun brokenPlayKeyShowsReadableError() = runTest {
        val store = FakeCredentialStore()
        val factory = FakeStoreClientFactory(
            FakeStoreClient(Store.AppStore) { emptyList() },
            FakeStoreClient(Store.GooglePlay) { throw InvalidCredentialsException("This isn't a Google service account key.") },
        )
        val viewModel = ConnectViewModel(store, factory)

        viewModel.connectPlay("nonsense")

        assertNull(store.play)
        assertEquals("This isn't a Google service account key.", viewModel.state.value.playError)
    }
}
