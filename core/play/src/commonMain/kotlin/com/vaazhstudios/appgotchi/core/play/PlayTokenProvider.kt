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
