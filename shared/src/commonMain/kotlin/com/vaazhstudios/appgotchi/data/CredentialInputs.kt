package com.vaazhstudios.appgotchi.data

private val developerAccountId = Regex("""^\d{15,20}$""")
private val issuerId = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")

// Every real ID is 36 characters or fewer, so a run this long is key material pasted without its header lines
private val longBase64Run = Regex("""[A-Za-z0-9+/]{40,}""")

/** True for pasted PEM key text, a bare key body or a whole service account file. */
fun looksLikePrivateKey(text: String): Boolean =
    "PRIVATE KEY" in text || (!text.trimStart().startsWith("http", ignoreCase = true) && longBase64Run.containsMatchIn(text))

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

/** The Play Console page for inviting a user; null unless [developerAccountId] is a valid account ID. */
fun usersAndPermissionsUrl(developerAccountId: String): String? {
    val id = developerAccountId.trim()
    if (developerAccountIdProblem(id) != null || id.isEmpty()) return null
    return "https://play.google.com/console/u/0/developers/$id/users-and-permissions"
}
