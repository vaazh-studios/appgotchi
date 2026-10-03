package com.vaazhstudios.appgotchi.core.data

import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

enum class Store { AppStore, GooglePlay }

data class StoreApp(
    val store: Store,
    val id: String,
    val name: String,
    val bundleId: String,
)

interface StoreClient {
    val store: Store
    suspend fun listApps(): List<StoreApp>
}

fun interface AccessTokenProvider {
    suspend fun token(): String
}

class StoreApiException(
    val store: Store,
    val status: Int,
    val body: String,
) : Exception("${store.name} API returned HTTP $status")

class InvalidCredentialsException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

val StoreJson: Json = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalTime::class)
fun epochSecondsNow(): Long = Clock.System.now().epochSeconds
