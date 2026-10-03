package com.vaazhstudios.appgotchi.screens.today

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.FakeCredentialStore
import com.vaazhstudios.appgotchi.data.FakeStoreClient
import com.vaazhstudios.appgotchi.data.FakeStoreClientFactory
import com.vaazhstudios.appgotchi.data.StoreSection
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

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val app = StoreApp(Store.AppStore, "1", "PosePal", "com.example.posepal")
    private val factory = FakeStoreClientFactory(FakeStoreClient(Store.AppStore) { listOf(app) }, FakeStoreClient(Store.GooglePlay) { emptyList() })

    @Test
    fun withoutCredentialsTheScreenAsksToConnect() = runTest {
        val viewModel = TodayViewModel(AppsRepository(FakeCredentialStore(), factory))

        assertEquals(TodayUiState.NoStores, viewModel.state.value)
    }

    @Test
    fun connectedStoresAreShownOnCreation() = runTest {
        val store = FakeCredentialStore(apple = AppStoreConnectCredentials("i", "k", "p"))
        val viewModel = TodayViewModel(AppsRepository(store, factory))

        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app), null))), viewModel.state.value)
    }

    @Test
    fun savingAKeyReloadsTheList() = runTest {
        val store = FakeCredentialStore()
        val viewModel = TodayViewModel(AppsRepository(store, factory))
        assertEquals(TodayUiState.NoStores, viewModel.state.value)

        store.saveAppStoreConnect(AppStoreConnectCredentials("i", "k", "p"))

        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app), null))), viewModel.state.value)
    }
}
