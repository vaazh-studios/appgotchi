package com.vaazhstudios.appgotchi.data

private val developerAccountId = Regex("""^\d{15,20}$""")
private val issuerId = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")

/** True for pasted PEM key text or a whole service account file. */
fun looksLikePrivateKey(text: String): Boolean = "PRIVATE KEY" in text

fun developerAccountIdProblem(text: String): String? {
    val value = text.trim()
    return when {
        value.isEmpty() || developerAccountId.matches(value) -> null
        else -> "That isn’t a developer account ID. It’s the long number shown as Account ID on Play Console’s home page."
    }
}

fun issuerIdProblem(text: String): String? {
    val value = text.trim()
    return when {
        value.isEmpty() || issuerId.matches(value) -> null
        else -> "An Issuer ID looks like 1a2b3c4d-1a2b-1a2b-1a2b-1a2b3c4d5e6f."
    }
}
