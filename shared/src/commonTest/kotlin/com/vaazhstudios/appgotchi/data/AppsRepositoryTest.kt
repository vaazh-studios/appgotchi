package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppsRepositoryTest {
    private val appleApp = StoreApp(Store.AppStore, "1", "PosePal", "com.example.posepal")
    private val playApp = StoreApp(Store.GooglePlay, "com.example.posepal", "PosePal", "com.example.posepal")
    private val bothStores = FakeCredentialStore(AppStoreConnectCredentials("i", "k", "p"), PlayCredentials("{}"))

    private fun factory(
        apple: () -> List<StoreApp> = { listOf(appleApp) },
        play: () -> List<StoreApp> = { listOf(playApp) },
    ) = FakeStoreClientFactory(FakeStoreClient(Store.AppStore, apple), FakeStoreClient(Store.GooglePlay, play))

    @Test
    fun noCredentialsMeansNoSections() = runTest {
        assertEquals(emptyList(), AppsRepository(FakeCredentialStore(), factory()).loadSections())
    }

    @Test
    fun connectedStoresAreListedInOrder() = runTest {
        assertEquals(
            listOf(
                StoreSection(Store.AppStore, listOf(appleApp), errorMessage = null),
                StoreSection(Store.GooglePlay, listOf(playApp), errorMessage = null),
            ),
            AppsRepository(bothStores, factory()).loadSections(),
        )
    }

    @Test
    fun duplicateAppIdsWithinAStoreAreListedOnce() = runTest {
        val repository = AppsRepository(bothStores, factory(apple = { listOf(appleApp, appleApp) }))

        assertEquals(listOf(appleApp), repository.loadSections()[0].apps)
    }

    @Test
    fun aFailingStoreGetsAnErrorWithoutHidingTheOther() = runTest {
        val repository = AppsRepository(bothStores, factory(apple = { throw StoreApiException(Store.AppStore, 401, "") }))

        val sections = repository.loadSections()

        assertEquals(
            StoreSection(Store.AppStore, emptyList(), "App Store Connect rejected this key. Check the Issuer ID, Key ID and private key."),
            sections[0],
        )
        assertEquals(StoreSection(Store.GooglePlay, listOf(playApp), null), sections[1])
    }

    @Test
    fun disabledReportingApiGetsASpecificMessage() = runTest {
        val body = """{"error":{"code":403,"status":"PERMISSION_DENIED","details":[{"reason":"SERVICE_DISABLED"}]}}"""
        val repository = AppsRepository(bothStores, factory(play = { throw StoreApiException(Store.GooglePlay, 403, body) }))

        assertEquals(
            "Enable the Google Play Developer Reporting API in Google Cloud for this service account's project, then try again.",
            repository.loadSections()[1].errorMessage,
        )
    }

    @Test
    fun unreadableStorageBecomesAnErrorInsteadOfACrash() = runTest {
        val store = FakeCredentialStore(AppStoreConnectCredentials("i", "k", "p"), failReads = true)

        val sections = AppsRepository(store, factory()).loadSections()

        assertEquals(
            listOf(
                StoreSection(Store.AppStore, emptyList(), "Couldn't read the saved App Store Connect key from this device's secure storage. Unlock your device and try again."),
                StoreSection(Store.GooglePlay, emptyList(), "Couldn't read the saved Google Play key from this device's secure storage. Unlock your device and try again."),
            ),
            sections,
        )
    }
}
