package com.vaazhstudios.appgotchi.core.apple

/**
 * An App Store Connect API key (Users and Access → Integrations).
 * Kept in the platform keychain on the user's device; never sent anywhere except Apple.
 */
data class AppStoreConnectCredentials(
    val issuerId: String,
    val keyId: String,
    val privateKeyPem: String,
) {
    override fun toString() = "AppStoreConnectCredentials(issuerId=$issuerId, keyId=$keyId, privateKeyPem=***)"
}
