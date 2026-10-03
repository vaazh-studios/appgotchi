# Milestone 1: List My Apps From Both Stores — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paste an App Store Connect key and/or a Google Play service account key, verify it, store it securely, and list the account's apps from both stores on the Today screen — on Android, iOS and desktop.

**Architecture:** Store-specific clients live in `core/apple` and `core/play` and implement `StoreClient` from `core/data`. Each client gets an `AccessTokenProvider` (JWT signing for Apple, JWT→OAuth exchange for Google), so HTTP code is tested with Ktor `MockEngine` and fake tokens, and signing code is tested with freshly generated key pairs. The `shared` module stores credentials with KSafe, builds clients through a `StoreClientFactory`, and exposes two screens (Today, Connect) driven by view models.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1, Ktor 3.5.1, Koin 4.2.2, kotlinx.serialization, `dev.whyoleg.cryptography` 0.6.0, `eu.anifantakis:ksafe` 3.3.0.

**Spec:** `docs/superpowers/specs/2026-10-03-milestone-1-list-apps-design.md`

## Global Constraints

- Package root: `com.vaazhstudios.appgotchi` (core modules: `com.vaazhstudios.appgotchi.core.{data,apple,play}`).
- Targets for every module: `androidLibrary`, `iosArm64`, `iosSimulatorArm64`, `jvm`. No web targets.
- Never log or `toString()` secret material; credential classes redact themselves.
- Keys are only persisted after a successful `listApps()` call with them.
- Secrets are only ever sent to Apple (`https://api.appstoreconnect.apple.com`) and Google (`https://oauth2.googleapis.com/token`, `https://playdeveloperreporting.googleapis.com`). Never to a URL taken from user input or a response body on another host.
- Nothing the user does may crash the app: bad keys, storage failures and network errors become readable messages.
- Apple JWT: header `{"alg":"ES256","kid":<keyId>,"typ":"JWT"}`, claims `iss`=issuerId, `iat`, `exp`=iat+900, `aud`="appstoreconnect-v1". Signature format RAW (64 bytes).
- Google JWT: header `{"alg":"RS256","typ":"JWT"}`, claims `iss`=client_email, `scope`="https://www.googleapis.com/auth/playdeveloperreporting", `aud`=token_uri, `iat`, `exp`=iat+3600.
- Run unit tests with `./gradlew :<module>:jvmTest` from the repo root (`~/tap and touch studios/appgotchi`).
- Commit after every task with a message ending in `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: Dependencies, shared store contract and JWT helper (`core/data`)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `core/data/build.gradle.kts`
- Create: `core/data/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/data/StoreClient.kt` (replace contents)
- Create: `core/data/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/data/Jwt.kt`
- Test: `core/data/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/data/JwtTest.kt`

**Interfaces:**
- Produces:
  - `enum class Store { AppStore, GooglePlay }`
  - `data class StoreApp(val store: Store, val id: String, val name: String, val bundleId: String)`
  - `interface StoreClient { val store: Store; suspend fun listApps(): List<StoreApp> }`
  - `fun interface AccessTokenProvider { suspend fun token(): String }`
  - `class StoreApiException(val store: Store, val status: Int, val body: String) : Exception`
  - `class InvalidCredentialsException(message: String, cause: Throwable? = null) : Exception` — thrown when a pasted key can't be parsed; its message is shown to the user verbatim.
  - `val StoreJson: Json` (ignoreUnknownKeys)
  - `fun epochSecondsNow(): Long`
  - `object Jwt { fun signingInput(header: JsonObject, claims: JsonObject): String; fun assemble(signingInput: String, signature: ByteArray): String; fun encode(bytes: ByteArray): String; fun decode(part: String): ByteArray }`

- [ ] **Step 1: Add catalog entries**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
cryptography = "0.6.0"
ksafe = "3.3.0"
```

Add under `[libraries]`:

```toml
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
cryptography-core = { module = "dev.whyoleg.cryptography:cryptography-core", version.ref = "cryptography" }
cryptography-provider-optimal = { module = "dev.whyoleg.cryptography:cryptography-provider-optimal", version.ref = "cryptography" }
ksafe = { module = "eu.anifantakis:ksafe", version.ref = "ksafe" }
koin-android = { module = "io.insert-koin:koin-android", version.ref = "koin" }
```

- [ ] **Step 2: Give `core/data` its dependencies**

Inside the `kotlin { … }` block of `core/data/build.gradle.kts`, after the `androidLibrary { … }` block, add:

```kotlin
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.ktor.client.core)
            api(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
```

- [ ] **Step 3: Write the failing test**

`core/data/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/data/JwtTest.kt`:

```kotlin
package com.vaazhstudios.appgotchi.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class JwtTest {
    @Test
    fun signingInputIsTwoUnpaddedBase64UrlJsonParts() {
        val header = buildJsonObject { put("alg", "ES256") }
        val claims = buildJsonObject { put("sub", "ü?>") }

        val input = Jwt.signingInput(header, claims)
        val parts = input.split(".")

        assertEquals(2, parts.size)
        assertFalse(input.contains('='))
        assertFalse(input.contains('+'))
        assertFalse(input.contains('/'))
        assertEquals("ES256", Json.parseToJsonElement(Jwt.decode(parts[0]).decodeToString()).jsonObject["alg"]!!.jsonPrimitive.content)
        assertEquals("ü?>", Json.parseToJsonElement(Jwt.decode(parts[1]).decodeToString()).jsonObject["sub"]!!.jsonPrimitive.content)
    }

    @Test
    fun assembleAppendsEncodedSignature() {
        val signature = byteArrayOf(-1, -2, -3)

        val token = Jwt.assemble("a.b", signature)

        assertEquals("a.b.__79", token)
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :core:data:jvmTest`
Expected: FAIL — compilation error `Unresolved reference 'Jwt'`.

- [ ] **Step 5: Implement**

Replace `core/data/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/data/StoreClient.kt` with:

```kotlin
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
```

Create `core/data/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/data/Jwt.kt`:

```kotlin
package com.vaazhstudios.appgotchi.core.data

import kotlinx.serialization.json.JsonObject
import kotlin.io.encoding.Base64

object Jwt {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    fun signingInput(header: JsonObject, claims: JsonObject): String =
        encode(header.toString().encodeToByteArray()) + "." + encode(claims.toString().encodeToByteArray())

    fun assemble(signingInput: String, signature: ByteArray): String = signingInput + "." + encode(signature)

    fun encode(bytes: ByteArray): String = base64Url.encode(bytes)

    fun decode(part: String): ByteArray = base64Url.decode(part)
}
```

If the compiler reports that `Base64` or `Clock` requires a different opt-in, add the opt-in it names; do not change behaviour.

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:data:jvmTest`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml core/data docs
git commit -m "Add store contract and JWT helper to core/data

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: App Store Connect token provider (`core/apple`)

**Files:**
- Modify: `core/apple/build.gradle.kts`
- Create: `core/apple/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectTokenProvider.kt`
- Test: `core/apple/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectTokenProviderTest.kt`

**Interfaces:**
- Consumes: `AccessTokenProvider`, `Jwt`, `epochSecondsNow()` (Task 1); existing `AppStoreConnectCredentials(issuerId, keyId, privateKeyPem)`.
- Produces: `class AppStoreConnectTokenProvider(credentials: AppStoreConnectCredentials, now: () -> Long = ::epochSecondsNow) : AccessTokenProvider` with `companion object { const val LIFETIME_SECONDS = 900L }`.

- [ ] **Step 1: Add dependencies**

In `core/apple/build.gradle.kts`, replace the `sourceSets { … }` block with:

```kotlin
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
```

- [ ] **Step 2: Write the failing test**

`core/apple/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectTokenProviderTest.kt`:

```kotlin
package com.vaazhstudios.appgotchi.core.apple

import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Jwt
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppStoreConnectTokenProviderTest {
    private val ecdsa = CryptographyProvider.Default.get(ECDSA)

    private fun decodePart(part: String): JsonObject =
        Json.parseToJsonElement(Jwt.decode(part).decodeToString()).jsonObject

    @Test
    fun tokenIsAValidEs256JwtForAppStoreConnect() = runTest {
        val keyPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
        val pem = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.PEM).decodeToString()
        val provider = AppStoreConnectTokenProvider(
            AppStoreConnectCredentials(issuerId = "issuer-123", keyId = "KEY123", privateKeyPem = pem),
            now = { 1_000L },
        )

        val parts = provider.token().split(".")

        val header = decodePart(parts[0])
        assertEquals("ES256", header["alg"]!!.jsonPrimitive.content)
        assertEquals("KEY123", header["kid"]!!.jsonPrimitive.content)
        assertEquals("JWT", header["typ"]!!.jsonPrimitive.content)
        val claims = decodePart(parts[1])
        assertEquals("issuer-123", claims["iss"]!!.jsonPrimitive.content)
        assertEquals("appstoreconnect-v1", claims["aud"]!!.jsonPrimitive.content)
        assertEquals(1_000L, claims["iat"]!!.jsonPrimitive.long)
        assertEquals(1_900L, claims["exp"]!!.jsonPrimitive.long)
        val signature = Jwt.decode(parts[2])
        assertEquals(64, signature.size)
        val verified = keyPair.publicKey
            .signatureVerifier(SHA256, ECDSA.SignatureFormat.RAW)
            .tryVerifySignature("${parts[0]}.${parts[1]}".encodeToByteArray(), signature)
        assertTrue(verified)
    }

    @Test
    fun tokenIsReusedUntilAMinuteBeforeExpiry() = runTest {
        val keyPair = ecdsa.keyPairGenerator(EC.Curve.P256).generateKey()
        val pem = keyPair.privateKey.encodeToByteArray(EC.PrivateKey.Format.PEM).decodeToString()
        var now = 1_000L
        val provider = AppStoreConnectTokenProvider(
            AppStoreConnectCredentials("issuer", "KEY", pem),
            now = { now },
        )

        val first = provider.token()
        now = 1_000L + AppStoreConnectTokenProvider.LIFETIME_SECONDS - 61
        assertEquals(first, provider.token())
        now = 1_000L + AppStoreConnectTokenProvider.LIFETIME_SECONDS - 60
        assertNotEquals(first, provider.token())
    }

    @Test
    fun malformedPrivateKeyGivesReadableError() = runTest {
        val provider = AppStoreConnectTokenProvider(AppStoreConnectCredentials("issuer", "KEY", "not a key"))

        val error = assertFailsWith<InvalidCredentialsException> { provider.token() }

        assertEquals(
            "This isn't a valid App Store Connect private key. Paste the whole .p8 file, including the BEGIN and END lines.",
            error.message,
        )
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:apple:jvmTest`
Expected: FAIL — `Unresolved reference 'AppStoreConnectTokenProvider'` (plus follow-on `Cannot infer type` errors).

- [ ] **Step 4: Implement**

`core/apple/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectTokenProvider.kt`:

```kotlin
package com.vaazhstudios.appgotchi.core.apple

import com.vaazhstudios.appgotchi.core.data.AccessTokenProvider
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Jwt
import com.vaazhstudios.appgotchi.core.data.epochSecondsNow
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.EC
import dev.whyoleg.cryptography.algorithms.ECDSA
import dev.whyoleg.cryptography.algorithms.SHA256
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AppStoreConnectTokenProvider(
    private val credentials: AppStoreConnectCredentials,
    private val now: () -> Long = ::epochSecondsNow,
) : AccessTokenProvider {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAt = 0L

    override suspend fun token(): String = mutex.withLock {
        val issuedAt = now()
        cachedToken?.takeIf { issuedAt < expiresAt - 60 }
            ?: createToken(issuedAt).also {
                cachedToken = it
                expiresAt = issuedAt + LIFETIME_SECONDS
            }
    }

    private suspend fun createToken(issuedAt: Long): String {
        val header = buildJsonObject {
            put("alg", "ES256")
            put("kid", credentials.keyId)
            put("typ", "JWT")
        }
        val claims = buildJsonObject {
            put("iss", credentials.issuerId)
            put("iat", issuedAt)
            put("exp", issuedAt + LIFETIME_SECONDS)
            put("aud", "appstoreconnect-v1")
        }
        val signingInput = Jwt.signingInput(header, claims)
        val privateKey = try {
            CryptographyProvider.Default.get(ECDSA)
                .privateKeyDecoder(EC.Curve.P256)
                .decodeFromByteArray(EC.PrivateKey.Format.PEM, credentials.privateKeyPem.trim().encodeToByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // JVM throws IllegalArgumentException, iOS (CryptoKit) throws IllegalStateException
            throw InvalidCredentialsException(
                "This isn't a valid App Store Connect private key. Paste the whole .p8 file, including the BEGIN and END lines.",
                e,
            )
        }
        val signature = privateKey
            .signatureGenerator(SHA256, ECDSA.SignatureFormat.RAW)
            .generateSignature(signingInput.encodeToByteArray())
        return Jwt.assemble(signingInput, signature)
    }

    companion object {
        const val LIFETIME_SECONDS = 900L
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:apple:jvmTest`
Expected: PASS (3 tests).

- [ ] **Step 6: Commit**

```bash
git add core/apple
git commit -m "Sign App Store Connect API tokens with ES256

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: App Store Connect client — list apps (`core/apple`)

**Files:**
- Create: `core/apple/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectClient.kt`
- Test: `core/apple/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectClientTest.kt`

**Interfaces:**
- Consumes: `StoreClient`, `StoreApp`, `Store`, `AccessTokenProvider`, `StoreApiException`, `StoreJson` (Task 1).
- Produces: `class AppStoreConnectClient(httpClient: HttpClient, tokenProvider: AccessTokenProvider, baseUrl: String = "https://api.appstoreconnect.apple.com") : StoreClient`.

- [ ] **Step 1: Write the failing test**

`core/apple/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectClientTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:apple:jvmTest`
Expected: FAIL — `Unresolved reference 'AppStoreConnectClient'`.

- [ ] **Step 3: Implement**

`core/apple/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/apple/AppStoreConnectClient.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:apple:jvmTest`
Expected: PASS (5 tests total in module).

- [ ] **Step 5: Commit**

```bash
git add core/apple
git commit -m "List apps from App Store Connect with pagination

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Google Play credentials and OAuth token provider (`core/play`)

**Files:**
- Modify: `core/play/build.gradle.kts`
- Modify: `core/play/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/play/PlayCredentials.kt`
- Create: `core/play/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/play/PlayTokenProvider.kt`
- Test: `core/play/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/play/PlayTokenProviderTest.kt`

**Interfaces:**
- Consumes: `AccessTokenProvider`, `Jwt`, `StoreApiException`, `InvalidCredentialsException`, `StoreJson`, `epochSecondsNow()`, `Store` (Task 1).
- Produces:
  - `data class PlayCredentials(val serviceAccountJson: String)` with `fun serviceAccountKey(): ServiceAccountKey` (throws `InvalidCredentialsException` with a user-readable message for anything that isn't a service account key).
  - `class ServiceAccountKey(val clientEmail: String, val privateKey: String)`
  - `class PlayTokenProvider(credentials: PlayCredentials, httpClient: HttpClient, now: () -> Long = ::epochSecondsNow) : AccessTokenProvider` with `companion object { const val SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"; const val TOKEN_URL = "https://oauth2.googleapis.com/token" }`. The key is parsed lazily on first `token()` call, so constructing the provider never throws.
- The `token_uri` field of the pasted JSON is deliberately ignored: the signed assertion only ever goes to Google's fixed token URL.

- [ ] **Step 1: Add dependencies**

In `core/play/build.gradle.kts`, replace the `sourceSets { … }` block with:

```kotlin
    sourceSets {
        commonMain.dependencies {
            api(projects.core.data)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
```

- [ ] **Step 2: Write the failing test**

`core/play/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/play/PlayTokenProviderTest.kt`:

```kotlin
package com.vaazhstudios.appgotchi.core.play

import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Jwt
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.forms.FormDataContent
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PlayTokenProviderTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val rsa = CryptographyProvider.Default.get(RSA.PKCS1)

    private suspend fun generateKey() = rsa.keyPairGenerator(keySize = 2048.bits, digest = SHA256).generateKey()

    private fun serviceAccountJson(privateKeyPem: String) = buildJsonObject {
        put("type", "service_account")
        put("client_email", "appgotchi@example.iam.gserviceaccount.com")
        put("private_key", privateKeyPem)
        put("token_uri", "https://attacker.example.com/token")
    }.toString()

    @Test
    fun exchangesASignedJwtForAnAccessTokenAndCachesIt() = runTest {
        val keyPair = generateKey()
        val pem = keyPair.privateKey.encodeToByteArray(RSA.PrivateKey.Format.PEM).decodeToString()
        var tokenRequests = 0
        val engine = MockEngine { request ->
            tokenRequests++
            assertEquals(PlayTokenProvider.TOKEN_URL, request.url.toString())
            val form = (request.body as FormDataContent).formData
            assertEquals("urn:ietf:params:oauth:grant-type:jwt-bearer", form["grant_type"])
            val parts = form["assertion"]!!.split(".")
            val claims = Json.parseToJsonElement(Jwt.decode(parts[1]).decodeToString()).jsonObject
            assertEquals("appgotchi@example.iam.gserviceaccount.com", claims["iss"]!!.jsonPrimitive.content)
            assertEquals(PlayTokenProvider.SCOPE, claims["scope"]!!.jsonPrimitive.content)
            assertEquals(PlayTokenProvider.TOKEN_URL, claims["aud"]!!.jsonPrimitive.content)
            val issuedAt = claims["iat"]!!.jsonPrimitive.long
            assertEquals(issuedAt + 3600, claims["exp"]!!.jsonPrimitive.long)
            val verified = keyPair.publicKey.signatureVerifier()
                .tryVerifySignature("${parts[0]}.${parts[1]}".encodeToByteArray(), Jwt.decode(parts[2]))
            assertTrue(verified)
            respond("""{"access_token":"ya29.token$tokenRequests","expires_in":3599,"token_type":"Bearer"}""", HttpStatusCode.OK, jsonHeaders)
        }
        var now = 5_000L
        val provider = PlayTokenProvider(PlayCredentials(serviceAccountJson(pem)), HttpClient(engine), now = { now })

        assertEquals("ya29.token1", provider.token())
        now = 5_000L + 3599 - 61
        assertEquals("ya29.token1", provider.token())
        assertEquals(1, tokenRequests)

        now = 5_000L + 3599 - 60
        assertEquals("ya29.token2", provider.token())
        assertEquals(2, tokenRequests)
    }

    @Test
    fun rejectedTokenRequestThrowsStoreApiException() = runTest {
        val pem = generateKey().privateKey.encodeToByteArray(RSA.PrivateKey.Format.PEM).decodeToString()
        val engine = MockEngine { respond("""{"error":"invalid_grant"}""", HttpStatusCode.BadRequest, jsonHeaders) }
        val provider = PlayTokenProvider(PlayCredentials(serviceAccountJson(pem)), HttpClient(engine))

        val error = assertFailsWith<StoreApiException> { provider.token() }

        assertEquals(400, error.status)
    }

    @Test
    fun invalidServiceAccountJsonGivesReadableErrorOnFirstUse() = runTest {
        val engine = MockEngine { error("must not be called") }
        // Constructing must not throw; the error surfaces when the token is requested
        val provider = PlayTokenProvider(PlayCredentials("""{"type":"authorized_user"}"""), HttpClient(engine))

        val error = assertFailsWith<InvalidCredentialsException> { provider.token() }

        assertEquals("This isn't a Google service account key. Download a JSON key for a service account from Google Cloud.", error.message)
    }

    @Test
    fun malformedPrivateKeyGivesReadableError() = runTest {
        val engine = MockEngine { error("must not be called") }
        val provider = PlayTokenProvider(PlayCredentials(serviceAccountJson("not a key")), HttpClient(engine))

        val error = assertFailsWith<InvalidCredentialsException> { provider.token() }

        assertEquals("The private key in this service account file is damaged. Download a new JSON key from Google Cloud.", error.message)
    }

    @Test
    fun credentialsRedactTheKeyInToString() {
        assertEquals("PlayCredentials(serviceAccountJson=***)", PlayCredentials("secret").toString())
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :core:play:jvmTest`
Expected: FAIL — compilation error `Unresolved reference 'PlayTokenProvider'`.

- [ ] **Step 4: Implement credentials parsing**

Replace `core/play/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/play/PlayCredentials.kt` with:

```kotlin
package com.vaazhstudios.appgotchi.core.play

import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.StoreJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A Google Cloud service account key (JSON) that has been invited to the Play Console.
 * Kept in the platform keychain on the user's device; never sent anywhere except Google.
 */
data class PlayCredentials(
    val serviceAccountJson: String,
) {
    fun serviceAccountKey(): ServiceAccountKey {
        val file = runCatching { StoreJson.decodeFromString<ServiceAccountFile>(serviceAccountJson) }.getOrNull()
        if (file == null || file.type != "service_account" || file.clientEmail == null || file.privateKey == null) {
            throw InvalidCredentialsException(
                "This isn't a Google service account key. Download a JSON key for a service account from Google Cloud."
            )
        }
        return ServiceAccountKey(file.clientEmail, file.privateKey)
    }

    override fun toString() = "PlayCredentials(serviceAccountJson=***)"
}

class ServiceAccountKey(
    val clientEmail: String,
    val privateKey: String,
) {
    override fun toString() = "ServiceAccountKey(clientEmail=$clientEmail, privateKey=***)"
}

@Serializable
private class ServiceAccountFile(
    val type: String? = null,
    @SerialName("client_email") val clientEmail: String? = null,
    @SerialName("private_key") val privateKey: String? = null,
)
```

- [ ] **Step 5: Implement the token provider**

`core/play/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/play/PlayTokenProvider.kt`:

```kotlin
package com.vaazhstudios.appgotchi.core.play

import com.vaazhstudios.appgotchi.core.data.AccessTokenProvider
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Jwt
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreJson
import com.vaazhstudios.appgotchi.core.data.epochSecondsNow
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.RSA
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class PlayTokenProvider(
    private val credentials: PlayCredentials,
    private val httpClient: HttpClient,
    private val now: () -> Long = ::epochSecondsNow,
) : AccessTokenProvider {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var expiresAt = 0L

    override suspend fun token(): String = mutex.withLock {
        val requestedAt = now()
        cachedToken?.takeIf { requestedAt < expiresAt - 60 }
            ?: requestToken(requestedAt).let { response ->
                cachedToken = response.accessToken
                expiresAt = requestedAt + response.expiresIn
                response.accessToken
            }
    }

    private suspend fun requestToken(issuedAt: Long): TokenResponse {
        // Built before the form: the parameters {} builder isn't inline, so it can't call suspend functions
        val assertion = signedAssertion(issuedAt)
        val response = httpClient.submitForm(
            url = TOKEN_URL,
            formParameters = parameters {
                append("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                append("assertion", assertion)
            },
        )
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw StoreApiException(Store.GooglePlay, response.status.value, body)
        return StoreJson.decodeFromString<TokenResponse>(body)
    }

    private suspend fun signedAssertion(issuedAt: Long): String {
        val key = credentials.serviceAccountKey()
        val header = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
        }
        val claims = buildJsonObject {
            put("iss", key.clientEmail)
            put("scope", SCOPE)
            put("aud", TOKEN_URL)
            put("iat", issuedAt)
            put("exp", issuedAt + 3600)
        }
        val signingInput = Jwt.signingInput(header, claims)
        val privateKey = try {
            CryptographyProvider.Default.get(RSA.PKCS1)
                .privateKeyDecoder(SHA256)
                .decodeFromByteArray(RSA.PrivateKey.Format.PEM, key.privateKey.trim().encodeToByteArray())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw InvalidCredentialsException(
                "The private key in this service account file is damaged. Download a new JSON key from Google Cloud.",
                e,
            )
        }
        val signature = privateKey.signatureGenerator().generateSignature(signingInput.encodeToByteArray())
        return Jwt.assemble(signingInput, signature)
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/playdeveloperreporting"
        const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    }
}

@Serializable
private class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)
```

- [ ] **Step 6: Run test to verify it passes**

Run: `./gradlew :core:play:jvmTest`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add core/play
git commit -m "Exchange Play service account keys for OAuth tokens

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Google Play client — list apps (`core/play`)

**Files:**
- Create: `core/play/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/play/PlayClient.kt`
- Test: `core/play/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/play/PlayClientTest.kt`

**Interfaces:**
- Consumes: `StoreClient`, `StoreApp`, `Store`, `AccessTokenProvider`, `StoreApiException`, `StoreJson` (Task 1).
- Produces: `class PlayClient(httpClient: HttpClient, tokenProvider: AccessTokenProvider, baseUrl: String = "https://playdeveloperreporting.googleapis.com") : StoreClient`.

- [ ] **Step 1: Write the failing test**

`core/play/src/commonTest/kotlin/com/vaazhstudios/appgotchi/core/play/PlayClientTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:play:jvmTest`
Expected: FAIL — `Unresolved reference 'PlayClient'` (plus follow-on `Cannot infer type` errors).

- [ ] **Step 3: Implement**

`core/play/src/commonMain/kotlin/com/vaazhstudios/appgotchi/core/play/PlayClient.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:play:jvmTest`
Expected: PASS (8 tests total in module).

- [ ] **Step 5: Commit**

```bash
git add core/play
git commit -m "List apps from Google Play via the Reporting API

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Secure credential storage, client factory, apps repository and DI (`shared`)

**Files:**
- Modify: `shared/build.gradle.kts`
- Modify: `androidApp/build.gradle.kts`
- Modify: `androidApp/src/main/AndroidManifest.xml`
- Create: `androidApp/src/main/res/xml/data_extraction_rules.xml`
- Modify: `desktopApp/build.gradle.kts`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/CredentialStore.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/StoreClientFactory.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/AppsRepository.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/UserMessages.kt`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.kt`
- Create: `shared/src/androidMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.android.kt`
- Create: `shared/src/iosMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.ios.kt`
- Create: `shared/src/jvmMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.jvm.kt`
- Modify: `androidApp/src/main/kotlin/com/vaazhstudios/appgotchi/AppgotchiApplication.kt`
- Create: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/data/Fakes.kt`
- Test: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/data/AppsRepositoryTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 1–5.
- Produces:
  - `interface CredentialStore { val changes: Flow<Unit>; suspend fun appStoreConnect(): AppStoreConnectCredentials?; suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials); suspend fun play(): PlayCredentials?; suspend fun savePlay(credentials: PlayCredentials) }` — `changes` emits after every successful save.
  - `class KSafeCredentialStore(ksafe: KSafe) : CredentialStore`
  - `interface StoreClientFactory { fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient; fun play(credentials: PlayCredentials): StoreClient }` and `class DefaultStoreClientFactory(httpClient: HttpClient) : StoreClientFactory` (keeps only the most recent client per store, so cached tokens survive refreshes while rejected keys don't pile up in memory).
  - `data class StoreSection(val store: Store, val apps: List<StoreApp>, val errorMessage: String?)`
  - `class AppsRepository(credentialStore: CredentialStore, clientFactory: StoreClientFactory) { val credentialChanges: Flow<Unit>; suspend fun loadSections(): List<StoreSection> }` — `loadSections()` never throws (except cancellation); failures become a section with `errorMessage`.
  - `fun Throwable.userMessage(store: Store): String` and `val Store.displayName: String`
  - Test fakes: `FakeCredentialStore(apple, play, failReads)`, `FakeStoreClient(store, result)`, `FakeStoreClientFactory(apple, play)`

- [ ] **Step 1: Add dependencies and platform settings**

In `shared/build.gradle.kts`, inside `sourceSets { … }`:
- add to `androidMain.dependencies`: `implementation(libs.koin.android)`
- add to `commonMain.dependencies`: `implementation(libs.ksafe)`
- add a new block:

```kotlin
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
```

In `androidApp/build.gradle.kts` `dependencies { … }`, add `implementation(libs.koin.android)`.

In `androidApp/src/main/AndroidManifest.xml`, change `android:allowBackup="true"` to `android:allowBackup="false"` and add `android:dataExtractionRules="@xml/data_extraction_rules"` to the `<application>` element. The Keystore key never leaves the device, so backed-up or transferred secrets would be silently unreadable; `allowBackup="false"` alone doesn't stop device-to-device transfer on Android 12+.

Create `androidApp/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
        <exclude domain="file" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
    </device-transfer>
</data-extraction-rules>
```

In `desktopApp/build.gradle.kts`, inside `nativeDistributions { … }`, add (KSafe needs it to reach the OS key store in packaged builds):

```kotlin
            modules("jdk.unsupported")
```

- [ ] **Step 2: Write test fakes**

`shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/data/Fakes.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeCredentialStore(
    var apple: AppStoreConnectCredentials? = null,
    var play: PlayCredentials? = null,
    var failReads: Boolean = false,
) : CredentialStore {
    override val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override suspend fun appStoreConnect(): AppStoreConnectCredentials? {
        if (failReads) error("keychain locked")
        return apple
    }

    override suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials) {
        apple = credentials
        changes.tryEmit(Unit)
    }

    override suspend fun play(): PlayCredentials? {
        if (failReads) error("keychain locked")
        return play
    }

    override suspend fun savePlay(credentials: PlayCredentials) {
        play = credentials
        changes.tryEmit(Unit)
    }
}

class FakeStoreClient(
    override val store: Store,
    private val result: () -> List<StoreApp>,
) : StoreClient {
    override suspend fun listApps() = result()
}

class FakeStoreClientFactory(
    private val apple: StoreClient,
    private val play: StoreClient,
) : StoreClientFactory {
    val appleCredentialsUsed = mutableListOf<AppStoreConnectCredentials>()
    val playCredentialsUsed = mutableListOf<PlayCredentials>()

    override fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient {
        appleCredentialsUsed += credentials
        return apple
    }

    override fun play(credentials: PlayCredentials): StoreClient {
        playCredentialsUsed += credentials
        return play
    }
}
```

- [ ] **Step 3: Write the failing test**

`shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/data/AppsRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest`
Expected: FAIL — `Unresolved reference 'CredentialStore'` / `'AppsRepository'` (plus follow-on `Cannot infer type` errors).

- [ ] **Step 5: Implement storage**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/CredentialStore.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.StoreJson
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable

interface CredentialStore {
    val changes: Flow<Unit>
    suspend fun appStoreConnect(): AppStoreConnectCredentials?
    suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials)
    suspend fun play(): PlayCredentials?
    suspend fun savePlay(credentials: PlayCredentials)
}

class KSafeCredentialStore(private val ksafe: KSafe) : CredentialStore {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val changes: Flow<Unit> = _changes.asSharedFlow()

    override suspend fun appStoreConnect(): AppStoreConnectCredentials? =
        ksafe.get<String?>(APP_STORE_CONNECT_KEY, null)
            ?.let { StoreJson.decodeFromString<StoredAppStoreConnectKey>(it) }
            ?.let { AppStoreConnectCredentials(it.issuerId, it.keyId, it.privateKeyPem) }

    override suspend fun saveAppStoreConnect(credentials: AppStoreConnectCredentials) {
        // One value, one write: a partial save can never mix fields from two keys
        val stored = StoredAppStoreConnectKey(credentials.issuerId, credentials.keyId, credentials.privateKeyPem)
        ksafe.put(APP_STORE_CONNECT_KEY, StoreJson.encodeToString(StoredAppStoreConnectKey.serializer(), stored))
        _changes.tryEmit(Unit)
    }

    override suspend fun play(): PlayCredentials? =
        ksafe.get<String?>(PLAY_SERVICE_ACCOUNT, null)?.let(::PlayCredentials)

    override suspend fun savePlay(credentials: PlayCredentials) {
        ksafe.put(PLAY_SERVICE_ACCOUNT, credentials.serviceAccountJson)
        _changes.tryEmit(Unit)
    }

    private companion object {
        const val APP_STORE_CONNECT_KEY = "appStoreConnect.key"
        const val PLAY_SERVICE_ACCOUNT = "play.serviceAccount"
    }
}

@Serializable
private class StoredAppStoreConnectKey(val issuerId: String, val keyId: String, val privateKeyPem: String)
```

KSafe 3.3.0 API (verified): package `eu.anifantakis.lib.ksafe`, `suspend inline fun <reified T> get(key, defaultValue)` and `suspend fun put(key, value)`.

- [ ] **Step 6: Implement client factory, repository and user messages**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/StoreClientFactory.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectClient
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectTokenProvider
import com.vaazhstudios.appgotchi.core.data.StoreClient
import com.vaazhstudios.appgotchi.core.play.PlayClient
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import com.vaazhstudios.appgotchi.core.play.PlayTokenProvider
import io.ktor.client.HttpClient

interface StoreClientFactory {
    fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient
    fun play(credentials: PlayCredentials): StoreClient
}

/**
 * Keeps the most recent client per store so cached access tokens survive refreshes.
 * A different key replaces the old client, so rejected keys aren't held in memory. Called from the main thread only.
 */
class DefaultStoreClientFactory(private val httpClient: HttpClient) : StoreClientFactory {
    private var appStoreConnectClient: Pair<AppStoreConnectCredentials, StoreClient>? = null
    private var playClient: Pair<PlayCredentials, StoreClient>? = null

    override fun appStoreConnect(credentials: AppStoreConnectCredentials): StoreClient {
        appStoreConnectClient?.let { (cachedFor, client) -> if (cachedFor == credentials) return client }
        return AppStoreConnectClient(httpClient, AppStoreConnectTokenProvider(credentials))
            .also { appStoreConnectClient = credentials to it }
    }

    override fun play(credentials: PlayCredentials): StoreClient {
        playClient?.let { (cachedFor, client) -> if (cachedFor == credentials) return client }
        return PlayClient(httpClient, PlayTokenProvider(credentials, httpClient))
            .also { playClient = credentials to it }
    }
}
```

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/AppsRepository.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.core.data.StoreClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow

data class StoreSection(
    val store: Store,
    val apps: List<StoreApp>,
    val errorMessage: String?,
)

class AppsRepository(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) {
    val credentialChanges: Flow<Unit> get() = credentialStore.changes

    suspend fun loadSections(): List<StoreSection> = coroutineScope {
        listOf(
            async { section(Store.AppStore, credentialStore::appStoreConnect, clientFactory::appStoreConnect) },
            async { section(Store.GooglePlay, credentialStore::play, clientFactory::play) },
        ).awaitAll().filterNotNull()
    }

    private suspend fun <C : Any> section(
        store: Store,
        readCredentials: suspend () -> C?,
        createClient: (C) -> StoreClient,
    ): StoreSection? {
        val credentials = try {
            readCredentials() ?: return null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return StoreSection(
                store,
                emptyList(),
                "Couldn't read the saved ${store.displayName} key from this device's secure storage. Unlock your device and try again.",
            )
        }
        return try {
            StoreSection(store, createClient(credentials).listApps(), errorMessage = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            StoreSection(store, emptyList(), e.userMessage(store))
        }
    }
}
```

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/UserMessages.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import kotlinx.serialization.SerializationException

fun Throwable.userMessage(store: Store): String = when {
    this is InvalidCredentialsException -> message ?: "This key couldn't be read."
    this is StoreApiException && store == Store.GooglePlay && status == 403 && "SERVICE_DISABLED" in body ->
        "Enable the Google Play Developer Reporting API in Google Cloud for this service account's project, then try again."
    this is StoreApiException && (status == 401 || status == 403) -> when (store) {
        Store.AppStore -> "App Store Connect rejected this key. Check the Issuer ID, Key ID and private key."
        Store.GooglePlay -> "Google Play rejected this key. Make sure the service account is invited in Play Console → Users and permissions."
    }
    this is StoreApiException && status == 400 && store == Store.GooglePlay ->
        "Google couldn't sign in with this service account key. Create a new JSON key and try again."
    this is StoreApiException -> "${store.displayName} returned an error (HTTP $status). Try again later."
    this is SerializationException || this is IllegalStateException ->
        "${store.displayName} sent a response Appgotchi doesn't understand yet. Please report this on GitHub."
    else -> "Couldn't reach ${store.displayName}. Check your connection and try again."
}

val Store.displayName: String
    get() = when (this) {
        Store.AppStore -> "App Store Connect"
        Store.GooglePlay -> "Google Play"
    }
```

(`SerializationException` extends `IllegalArgumentException`, which is why there is no generic `IllegalArgumentException` branch: raw parser messages must never reach the user. `IllegalStateException` comes from the pagination host check in `AppStoreConnectClient`.)

- [ ] **Step 7: Wire Koin with a per-platform module**

Replace `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt` with:

```kotlin
package com.vaazhstudios.appgotchi.di

import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.CredentialStore
import com.vaazhstudios.appgotchi.data.DefaultStoreClientFactory
import com.vaazhstudios.appgotchi.data.KSafeCredentialStore
import com.vaazhstudios.appgotchi.data.StoreClientFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                requestTimeoutMillis = 30_000
            }
        }
    }
    single<StoreClientFactory> { DefaultStoreClientFactory(get()) }
    single<CredentialStore> { KSafeCredentialStore(get()) }
    single { AppsRepository(get(), get()) }
}

fun initKoin() = initKoin {}

fun initKoin(config: KoinAppDeclaration) {
    startKoin {
        config()
        modules(appModule, platformModule)
    }
}
```

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.kt`:

```kotlin
package com.vaazhstudios.appgotchi.di

import org.koin.core.module.Module

expect val platformModule: Module
```

`shared/src/androidMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.android.kt`:

```kotlin
package com.vaazhstudios.appgotchi.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { KSafe(androidContext(), fileName = "appgotchi") }
}
```

`shared/src/iosMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.ios.kt`:

```kotlin
package com.vaazhstudios.appgotchi.di

import eu.anifantakis.lib.ksafe.KSafe
import org.koin.dsl.module

actual val platformModule = module {
    single { KSafe(fileName = "appgotchi") }
}
```

`shared/src/jvmMain/kotlin/com/vaazhstudios/appgotchi/di/PlatformModule.jvm.kt` — on desktop KSafe's files and OS-keystore entries are shared with every other KSafe app unless namespaced:

```kotlin
package com.vaazhstudios.appgotchi.di

import eu.anifantakis.lib.ksafe.KSafe
import eu.anifantakis.lib.ksafe.KSafeConfig
import org.koin.dsl.module

actual val platformModule = module {
    single { KSafe(fileName = "appgotchi", config = KSafeConfig(appNamespace = "com.vaazhstudios.appgotchi")) }
}
```

If a KSafe constructor parameter name differs from the above, look it up in the KSafe 3.3.0 sources for that platform (`KSafe.android.kt`, `KSafe.ios.kt`/`KSafe.apple.kt`, `KSafe.jvm.kt`) and keep the same file name and namespace.

Replace `androidApp/src/main/kotlin/com/vaazhstudios/appgotchi/AppgotchiApplication.kt` with:

```kotlin
package com.vaazhstudios.appgotchi

import android.app.Application
import com.vaazhstudios.appgotchi.di.initKoin
import org.koin.android.ext.koin.androidContext

class AppgotchiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@AppgotchiApplication) }
    }
}
```

`desktopApp/.../main.kt` and `iosApp/iosApp/iOSApp.swift` keep calling the no-argument `initKoin()` / `KoinKt.doInitKoin()`.

- [ ] **Step 8: Run tests and build every platform**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (5 tests).

Run: `./gradlew :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add shared androidApp desktopApp
git commit -m "Store credentials securely and load apps from connected stores

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Connect screen (`shared`)

**Files:**
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`
- Test: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModelTest.kt`

**Interfaces:**
- Consumes: `CredentialStore`, `StoreClientFactory`, `userMessage`, fakes (Task 6).
- Produces:
  - `data class ConnectUiState(val busy: Boolean = false, val appleError: String? = null, val playError: String? = null, val connected: Boolean = false)`
  - `class ConnectViewModel(credentialStore: CredentialStore, clientFactory: StoreClientFactory) : ViewModel` with `val state: StateFlow<ConnectUiState>`, `fun connectAppStore(issuerId: String, keyId: String, privateKey: String)`, `fun connectPlay(serviceAccountJson: String)`.
  - `@Composable fun ConnectScreen(onConnected: () -> Unit, onBack: () -> Unit, viewModel: ConnectViewModel = koinViewModel())`

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModelTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest`
Expected: FAIL — `Unresolved reference 'ConnectViewModel'`.

- [ ] **Step 3: Implement the view model**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModel.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import com.vaazhstudios.appgotchi.data.CredentialStore
import com.vaazhstudios.appgotchi.data.StoreClientFactory
import com.vaazhstudios.appgotchi.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConnectUiState(
    val busy: Boolean = false,
    val appleError: String? = null,
    val playError: String? = null,
    val connected: Boolean = false,
)

class ConnectViewModel(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    fun connectAppStore(issuerId: String, keyId: String, privateKey: String) {
        val credentials = AppStoreConnectCredentials(issuerId.trim(), keyId.trim(), privateKey.trim())
        if (credentials.issuerId.isEmpty() || credentials.keyId.isEmpty() || credentials.privateKeyPem.isEmpty()) {
            _state.update { it.copy(appleError = "Fill in the Issuer ID, Key ID and private key.") }
            return
        }
        verifyAndSave(Store.AppStore) {
            clientFactory.appStoreConnect(credentials).listApps()
            credentialStore.saveAppStoreConnect(credentials)
        }
    }

    fun connectPlay(serviceAccountJson: String) {
        val credentials = PlayCredentials(serviceAccountJson.trim())
        if (credentials.serviceAccountJson.isEmpty()) {
            _state.update { it.copy(playError = "Paste the contents of your service account JSON key.") }
            return
        }
        verifyAndSave(Store.GooglePlay) {
            if (clientFactory.play(credentials).listApps().isEmpty()) {
                throw InvalidCredentialsException(
                    "This key works, but it can't see any apps yet. Invite the service account's email in Play Console → Users and permissions. New permissions can take a few hours to apply."
                )
            }
            credentialStore.savePlay(credentials)
        }
    }

    private fun verifyAndSave(store: Store, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, appleError = null, playError = null) }
        viewModelScope.launch {
            try {
                block()
                _state.update { it.copy(busy = false, connected = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.userMessage(store)
                _state.update {
                    when (store) {
                        Store.AppStore -> it.copy(busy = false, appleError = message)
                        Store.GooglePlay -> it.copy(busy = false, playError = message)
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (11 tests total in module).

- [ ] **Step 5: Add strings**

Replace `shared/src/commonMain/composeResources/values/strings.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<resources>
    <string name="app_name">Appgotchi</string>
    <string name="today_empty_title">No stores connected yet</string>
    <string name="today_empty_body">Connect App Store Connect or Google Play to start keeping your apps alive.</string>
    <string name="today_connect_button">Connect a store</string>
    <string name="today_no_apps">No apps in this account yet.</string>
    <string name="connect_title">Connect stores</string>
    <string name="connect_back">Back</string>
    <string name="connect_privacy_note">Keys are stored in this device\'s secure storage and are only ever sent to Apple and Google.</string>
    <string name="connect_apple_title">App Store Connect</string>
    <string name="connect_apple_help">Create a key in App Store Connect → Users and Access → Integrations. The App Manager role is enough.</string>
    <string name="connect_apple_issuer">Issuer ID</string>
    <string name="connect_apple_key_id">Key ID</string>
    <string name="connect_apple_private_key">Private key (.p8 file contents)</string>
    <string name="connect_play_title">Google Play</string>
    <string name="connect_play_help">Create a service account key (JSON) in Google Cloud, enable the Google Play Developer Reporting API, and invite the service account email in Play Console → Users and permissions.</string>
    <string name="connect_play_json">Service account JSON</string>
    <string name="connect_button">Verify and connect</string>
</resources>
```

- [ ] **Step 6: Implement the screen and register the view model**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectScreen.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.connect_apple_help
import appgotchi.shared.generated.resources.connect_apple_issuer
import appgotchi.shared.generated.resources.connect_apple_key_id
import appgotchi.shared.generated.resources.connect_apple_private_key
import appgotchi.shared.generated.resources.connect_apple_title
import appgotchi.shared.generated.resources.connect_back
import appgotchi.shared.generated.resources.connect_button
import appgotchi.shared.generated.resources.connect_play_help
import appgotchi.shared.generated.resources.connect_play_json
import appgotchi.shared.generated.resources.connect_play_title
import appgotchi.shared.generated.resources.connect_privacy_note
import appgotchi.shared.generated.resources.connect_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connected) {
        if (state.connected) onConnected()
    }

    var issuerId by remember { mutableStateOf("") }
    var keyId by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var serviceAccountJson by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.connect_back)) }
            Text(stringResource(Res.string.connect_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.connect_privacy_note), style = MaterialTheme.typography.bodyMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.connect_apple_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.connect_apple_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(issuerId, { issuerId = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.connect_apple_issuer)) }, singleLine = true)
                    OutlinedTextField(keyId, { keyId = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.connect_apple_key_id)) }, singleLine = true)
                    OutlinedTextField(
                        privateKey, { privateKey = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.connect_apple_private_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        minLines = 3,
                    )
                    state.appleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { viewModel.connectAppStore(issuerId, keyId, privateKey) }, enabled = !state.busy) {
                        Text(stringResource(Res.string.connect_button))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.connect_play_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.connect_play_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        serviceAccountJson, { serviceAccountJson = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.connect_play_json)) },
                        visualTransformation = PasswordVisualTransformation(),
                        minLines = 3,
                    )
                    state.playError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { viewModel.connectPlay(serviceAccountJson) }, enabled = !state.busy) {
                        Text(stringResource(Res.string.connect_button))
                    }
                }
            }
        }
    }
}
```

In `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`, add the import `import com.vaazhstudios.appgotchi.screens.connect.ConnectViewModel` and `import org.koin.core.module.dsl.viewModelOf`, and add this line at the end of `appModule`:

```kotlin
    viewModelOf(::ConnectViewModel)
```

- [ ] **Step 7: Build**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: PASS and BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add shared
git commit -m "Add Connect screen that verifies keys before saving them

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Today screen with apps from both stores, navigation, end-to-end check

**Files:**
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayScreen.kt` (replace)
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/App.kt` (replace)
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`
- Test: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayViewModelTest.kt`

**Interfaces:**
- Consumes: `AppsRepository`, `StoreSection`, `displayName`, fakes (Task 6); `ConnectScreen` (Task 7).
- Produces:
  - `sealed interface TodayUiState { data object Loading; data object NoStores; data class Loaded(val sections: List<StoreSection>) }`
  - `class TodayViewModel(repository: AppsRepository) : ViewModel` with `val state: StateFlow<TodayUiState>` and `fun refresh()`. It loads once on creation, reloads whenever `repository.credentialChanges` emits, and a new `refresh()` cancels any load still running.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayViewModelTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest`
Expected: FAIL — `Unresolved reference 'TodayViewModel'` (plus follow-on `Cannot infer type` errors).

- [ ] **Step 3: Implement the view model**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayViewModel.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.data.AppsRepository
import com.vaazhstudios.appgotchi.data.StoreSection
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface TodayUiState {
    data object Loading : TodayUiState
    data object NoStores : TodayUiState
    data class Loaded(val sections: List<StoreSection>) : TodayUiState
}

class TodayViewModel(private val repository: AppsRepository) : ViewModel() {
    private val _state = MutableStateFlow<TodayUiState>(TodayUiState.Loading)
    val state: StateFlow<TodayUiState> = _state.asStateFlow()
    private var loadJob: Job? = null

    init {
        refresh()
        viewModelScope.launch {
            repository.credentialChanges.collect { refresh() }
        }
    }

    fun refresh() {
        loadJob?.cancel()
        _state.value = TodayUiState.Loading
        loadJob = viewModelScope.launch {
            val sections = repository.loadSections()
            _state.value = if (sections.isEmpty()) TodayUiState.NoStores else TodayUiState.Loaded(sections)
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (14 tests total in module).

- [ ] **Step 5: Implement the screen**

Replace `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/today/TodayScreen.kt` with:

```kotlin
package com.vaazhstudios.appgotchi.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.app_name
import appgotchi.shared.generated.resources.today_connect_button
import appgotchi.shared.generated.resources.today_empty_body
import appgotchi.shared.generated.resources.today_empty_title
import appgotchi.shared.generated.resources.today_no_apps
import com.vaazhstudios.appgotchi.data.displayName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayScreen(
    onConnect: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        TodayUiState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        TodayUiState.NoStores -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(Res.string.today_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
        }

        is TodayUiState.Loaded -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    TextButton(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
                }
            }
            current.sections.forEach { section ->
                item(key = "header-${section.store}") {
                    Text(
                        section.store.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                section.errorMessage?.let { message ->
                    item(key = "error-${section.store}") {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth())
                    }
                }
                if (section.errorMessage == null && section.apps.isEmpty()) {
                    item(key = "empty-${section.store}") {
                        Text(stringResource(Res.string.today_no_apps), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth())
                    }
                }
                items(section.apps, key = { "${it.store}-${it.id}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.bundleId) },
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 6: Wire navigation and DI**

Replace `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/App.kt` with:

```kotlin
package com.vaazhstudios.appgotchi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaazhstudios.appgotchi.screens.connect.ConnectScreen
import com.vaazhstudios.appgotchi.screens.today.TodayScreen
import kotlinx.serialization.Serializable

@Serializable
object TodayDestination

@Serializable
object ConnectDestination

@Composable
fun App() {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = TodayDestination) {
                composable<TodayDestination> {
                    TodayScreen(onConnect = { navController.navigate(ConnectDestination) })
                }
                composable<ConnectDestination> {
                    // Typed pop is a no-op if Connect is already gone, so "Back" racing a
                    // finished verification can't pop the Today screen too
                    ConnectScreen(
                        onConnected = { navController.popBackStack<ConnectDestination>(inclusive = true) },
                        onBack = { navController.popBackStack<ConnectDestination>(inclusive = true) },
                    )
                }
            }
        }
    }
}
```

In `Koin.kt`, add `import com.vaazhstudios.appgotchi.screens.today.TodayViewModel` and the line `viewModelOf(::TodayViewModel)` at the end of `appModule`.

- [ ] **Step 7: Full verification**

Run: `./gradlew :core:data:jvmTest :core:apple:jvmTest :core:play:jvmTest :shared:jvmTest`
Expected: all PASS.

Run: `./gradlew :core:apple:iosSimulatorArm64Test :core:play:iosSimulatorArm64Test`
Expected: PASS — proves ES256 (CryptoKit) and RS256 (Apple Security) signing work on iOS.

Run: `./gradlew :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

Run: `./gradlew :desktopApp:run` and check: the empty state shows "Connect a store"; the Connect screen opens; pressing "Verify and connect" with empty fields shows "Fill in the Issuer ID, Key ID and private key.".

- [ ] **Step 8: Commit**

```bash
git add shared
git commit -m "Show apps from connected stores on the Today screen

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 9: Hand off the real-key check to the user**

The user (not the agent) pastes their own App Store Connect key and Play service account JSON into the desktop app's Connect screen and confirms their apps appear. Agents must not read `.p8`, `.p12` or service-account files from the user's disk.
