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
            "This .p8 file isn't a valid App Store Connect private key. Download the key again from App Store Connect.",
            error.message,
        )
    }
}
