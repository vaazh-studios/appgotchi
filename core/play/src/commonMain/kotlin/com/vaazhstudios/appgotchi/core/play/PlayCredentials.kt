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
