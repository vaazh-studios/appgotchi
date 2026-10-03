package com.vaazhstudios.appgotchi.core.play

import com.vaazhstudios.appgotchi.core.data.AccessTokenProvider
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.core.data.StoreJson
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

class PlayClient(
    private val httpClient: HttpClient,
    private val tokenProvider: AccessTokenProvider,
    private val baseUrl: String = "https://playdeveloperreporting.googleapis.com",
) : StoreClient {
    override val store = Store.GooglePlay

    override suspend fun listApps(): List<StoreApp> {
        val apps = mutableListOf<StoreApp>()
        var pageToken: String? = null
        do {
            val response = httpClient.get("$baseUrl/v1beta1/apps:search") {
                bearerAuth(tokenProvider.token())
                parameter("pageSize", 1000)
                pageToken?.let { parameter("pageToken", it) }
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) throw StoreApiException(store, response.status.value, body)
            val page = StoreJson.decodeFromString<SearchAppsResponse>(body)
            page.apps.mapTo(apps) { StoreApp(store, it.packageName, it.displayName ?: it.packageName, it.packageName) }
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return apps
    }
}

@Serializable
private class SearchAppsResponse(
    val apps: List<PlayApp> = emptyList(),
    val nextPageToken: String? = null,
)

@Serializable
private class PlayApp(val packageName: String, val displayName: String? = null)
