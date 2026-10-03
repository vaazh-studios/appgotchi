package com.vaazhstudios.appgotchi.core.play

/**
 * A Google Cloud service account key (JSON) that has been invited to the Play Console.
 * Kept in the platform keychain on the user's device; never sent anywhere except Google.
 */
data class PlayCredentials(
    val serviceAccountJson: String,
) {
    override fun toString() = "PlayCredentials(serviceAccountJson=***)"
}
