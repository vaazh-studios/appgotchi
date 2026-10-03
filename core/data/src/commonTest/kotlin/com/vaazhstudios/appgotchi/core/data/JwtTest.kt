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
