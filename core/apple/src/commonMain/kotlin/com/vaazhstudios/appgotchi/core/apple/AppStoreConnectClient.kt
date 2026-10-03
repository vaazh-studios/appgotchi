package com.vaazhstudios.appgotchi.core.apple

import com.vaazhstudios.appgotchi.core.data.AccessTokenProvider
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.core.data.StoreJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class AppStoreConnectClient(
    private val httpClient: HttpClient,
    private val tokenProvider: AccessTokenProvider,
    private val baseUrl: String = "https://api.appstoreconnect.apple.com",
) : StoreClient {
    override val store = Store.AppStore

    override suspend fun listApps(): List<StoreApp> {
        val apps = mutableListOf<StoreApp>()
        var url: String? = URLBuilder(baseUrl).apply {
            appendPathSegments("v1", "apps")
            parameters.append("fields[apps]", "name,bundleId")
            parameters.append("limit", "200")
        }.buildString()
        while (url != null) {
            val response = httpClient.get(url) { bearerAuth(tokenProvider.token()) }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw StoreApiException(store, response.status.value, body)
            val page = StoreJson.decodeFromString<AppsResponse>(body)
            page.data.mapTo(apps) { StoreApp(store, it.id, it.attributes.name, it.attributes.bundleId) }
            url = page.links.next?.also { next ->
                // The bearer token must never be sent to another host
                check(next.startsWith("$baseUrl/")) { "Unexpected pagination host in $next" }
            }
        }
        return apps
    }
}

@Serializable
private class AppsResponse(val data: List<AppResource>, val links: PageLinks)

@Serializable
private class AppResource(val id: String, val attributes: AppAttributes)

@Serializable
private class AppAttributes(val name: String, val bundleId: String)

@Serializable
private class PageLinks(val next: String? = null)
