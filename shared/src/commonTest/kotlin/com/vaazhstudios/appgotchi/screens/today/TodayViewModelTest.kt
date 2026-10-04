package com.vaazhstudios.appgotchi.screens.today

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.DemoData
import com.vaazhstudios.appgotchi.data.DemoMode
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
import kotlin.test.assertFalse

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
        val viewModel = TodayViewModel(AppsRepository(FakeCredentialStore(), factory), DemoMode())

        assertEquals(TodayUiState.NoStores, viewModel.state.value)
    }

    @Test
    fun connectedStoresAreShownOnCreation() = runTest {
        val store = FakeCredentialStore(apple = AppStoreConnectCredentials("i", "k", "p"))
        val viewModel = TodayViewModel(AppsRepository(store, factory), DemoMode())

        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app), null))), viewModel.state.value)
    }

    @Test
    fun savingAKeyReloadsTheList() = runTest {
        val store = FakeCredentialStore()
        val viewModel = TodayViewModel(AppsRepository(store, factory), DemoMode())
        assertEquals(TodayUiState.NoStores, viewModel.state.value)

        store.saveAppStoreConnect(AppStoreConnectCredentials("i", "k", "p"))

        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app), null))), viewModel.state.value)
    }

    @Test
    fun manualRefreshReadsTheStoresAgain() = runTest {
        var apps = listOf(app)
        val store = FakeCredentialStore(apple = AppStoreConnectCredentials("i", "k", "p"))
        val client = FakeStoreClient(Store.AppStore) { apps }
        val viewModel = TodayViewModel(AppsRepository(store, FakeStoreClientFactory(client, FakeStoreClient(Store.GooglePlay) { emptyList() })), DemoMode())
        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app), null))), viewModel.state.value)

        val newApp = StoreApp(Store.AppStore, "2", "Snaplingo", "com.example.snaplingo")
        apps = listOf(app, newApp)
        viewModel.refresh()

        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app, newApp), null))), viewModel.state.value)
    }

    @Test
    fun demoModeShowsSampleApps() = runTest {
        val demo = DemoMode()
        val viewModel = TodayViewModel(AppsRepository(FakeCredentialStore(), factory), demo)

        demo.enable()

        assertEquals(TodayUiState.Loaded(DemoData.sections, isDemo = true), viewModel.state.value)
    }

    @Test
    fun tryDemoTurnsOnSampleData() = runTest {
        val demo = DemoMode()
        val viewModel = TodayViewModel(AppsRepository(FakeCredentialStore(), factory), demo)

        viewModel.tryDemo()

        assertEquals(TodayUiState.Loaded(DemoData.sections, isDemo = true), viewModel.state.value)
    }

    @Test
    fun exitingDemoReturnsToTheRealState() = runTest {
        val demo = DemoMode().apply { enable() }
        val viewModel = TodayViewModel(AppsRepository(FakeCredentialStore(), factory), demo)

        viewModel.exitDemo()

        assertEquals(TodayUiState.NoStores, viewModel.state.value)
    }

    @Test
    fun savingARealKeyEndsTheDemo() = runTest {
        val store = FakeCredentialStore()
        val demo = DemoMode().apply { enable() }
        val viewModel = TodayViewModel(AppsRepository(store, factory), demo)

        store.saveAppStoreConnect(AppStoreConnectCredentials("i", "k", "p"))

        assertFalse(demo.enabled.value)
        assertEquals(TodayUiState.Loaded(listOf(StoreSection(Store.AppStore, listOf(app), null))), viewModel.state.value)
    }
}
