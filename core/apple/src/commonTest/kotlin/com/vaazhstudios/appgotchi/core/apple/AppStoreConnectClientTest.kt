package com.vaazhstudios.appgotchi.core.apple

import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppStoreConnectClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun listAppsFollowsPaginationAndSendsBearerToken() = runTest {
        val requested = mutableListOf<String>()
        val engine = MockEngine { request ->
            requested += request.url.toString()
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            if (request.url.parameters["cursor"] == null) {
                respond(
                    """{"data":[{"type":"apps","id":"1","attributes":{"name":"PosePal","bundleId":"com.example.posepal","sku":"x"}}],
                       "links":{"self":"s","next":"https://api.example.com/v1/apps?cursor=abc"}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            } else {
                respond(
                    """{"data":[{"type":"apps","id":"2","attributes":{"name":"Snaplingo","bundleId":"com.example.snaplingo"}}],
                       "links":{"self":"s"}}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
            }
        }
        val client = AppStoreConnectClient(HttpClient(engine), { "test-token" }, baseUrl = "https://api.example.com")

        val apps = client.listApps()

        assertEquals(
            listOf(
                StoreApp(Store.AppStore, "1", "PosePal", "com.example.posepal"),
                StoreApp(Store.AppStore, "2", "Snaplingo", "com.example.snaplingo"),
            ),
            apps,
        )
        assertEquals("https://api.example.com/v1/apps?fields%5Bapps%5D=name%2CbundleId&limit=200", requested[0])
        assertEquals("https://api.example.com/v1/apps?cursor=abc", requested[1])
    }

    @Test
    fun nonSuccessStatusThrowsStoreApiException() = runTest {
        val engine = MockEngine { respond("""{"errors":[{"status":"401"}]}""", HttpStatusCode.Unauthorized, jsonHeaders) }
        val client = AppStoreConnectClient(HttpClient(engine), { "bad" })

        val error = assertFailsWith<StoreApiException> { client.listApps() }

        assertEquals(Store.AppStore, error.store)
        assertEquals(401, error.status)
    }
}
