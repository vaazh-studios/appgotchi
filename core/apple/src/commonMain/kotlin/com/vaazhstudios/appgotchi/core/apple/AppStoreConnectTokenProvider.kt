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
