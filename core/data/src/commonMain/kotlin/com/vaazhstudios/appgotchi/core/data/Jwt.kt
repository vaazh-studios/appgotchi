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
