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
