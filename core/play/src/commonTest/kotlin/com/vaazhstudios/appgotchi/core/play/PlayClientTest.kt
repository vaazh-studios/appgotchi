package com.vaazhstudios.appgotchi.core.play

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

class PlayClientTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun listAppsFollowsPageTokens() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer play-token", request.headers[HttpHeaders.Authorization])
            assertEquals("/v1beta1/apps:search", request.url.encodedPath)
            assertEquals("1000", request.url.parameters["pageSize"])
            when (request.url.parameters["pageToken"]) {
                null -> respond(
                    """{"apps":[{"name":"apps/com.example.posepal","packageName":"com.example.posepal","displayName":"PosePal"}],
                       "nextPageToken":"page2"}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                "page2" -> respond(
                    """{"apps":[{"name":"apps/com.example.untitled","packageName":"com.example.untitled"}]}""",
                    HttpStatusCode.OK, jsonHeaders,
                )
                else -> error("unexpected page token")
            }
        }
        val client = PlayClient(HttpClient(engine), { "play-token" })

        val apps = client.listApps()

        assertEquals(
            listOf(
                StoreApp(Store.GooglePlay, "com.example.posepal", "PosePal", "com.example.posepal"),
                StoreApp(Store.GooglePlay, "com.example.untitled", "com.example.untitled", "com.example.untitled"),
            ),
            apps,
        )
    }

    @Test
    fun emptyNextPageTokenEndsPagination() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(
                """{"apps":[{"packageName":"com.example.posepal","displayName":"PosePal"}],"nextPageToken":""}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }

        val apps = PlayClient(HttpClient(engine), { "t" }).listApps()

        assertEquals(1, apps.size)
        assertEquals(1, requests)
    }

    @Test
    fun repeatedPageTokenFailsInsteadOfLooping() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            check(requests <= 5) { "client kept paginating" }
            respond(
                """{"apps":[{"packageName":"com.example.posepal"}],"nextPageToken":"same"}""",
                HttpStatusCode.OK, jsonHeaders,
            )
        }

        assertFailsWith<IllegalStateException> { PlayClient(HttpClient(engine), { "t" }).listApps() }

        assertEquals(2, requests)
    }

    @Test
    fun emptyAccountReturnsNoApps() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.OK, jsonHeaders) }

        assertEquals(emptyList(), PlayClient(HttpClient(engine), { "t" }).listApps())
    }

    @Test
    fun forbiddenThrowsStoreApiException() = runTest {
        val engine = MockEngine { respond("""{"error":{"code":403}}""", HttpStatusCode.Forbidden, jsonHeaders) }

        val error = assertFailsWith<StoreApiException> { PlayClient(HttpClient(engine), { "t" }).listApps() }

        assertEquals(Store.GooglePlay, error.store)
        assertEquals(403, error.status)
    }
}
