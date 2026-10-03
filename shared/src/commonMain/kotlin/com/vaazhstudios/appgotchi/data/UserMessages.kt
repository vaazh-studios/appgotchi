package com.vaazhstudios.appgotchi.data

import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import kotlinx.serialization.SerializationException

fun Throwable.userMessage(store: Store): String = when {
    this is InvalidCredentialsException -> message ?: "This key couldn't be read."
    this is StoreApiException && store == Store.GooglePlay && status == 403 && "SERVICE_DISABLED" in body ->
        "Enable the Google Play Developer Reporting API in Google Cloud for this service account's project, then try again."
    this is StoreApiException && (status == 401 || status == 403) -> when (store) {
        Store.AppStore -> "App Store Connect rejected this key. Check the Issuer ID, Key ID and private key."
        Store.GooglePlay -> "Google Play rejected this key. Make sure the service account is invited in Play Console → Users and permissions."
    }
    this is StoreApiException && status == 400 && store == Store.GooglePlay ->
        "Google couldn't sign in with this service account key. Create a new JSON key and try again."
    this is StoreApiException -> "${store.displayName} returned an error (HTTP $status). Try again later."
    this is SerializationException || this is IllegalStateException ->
        "${store.displayName} sent a response Appgotchi doesn't understand yet. Please report this on GitHub."
    else -> "Couldn't reach ${store.displayName}. Check your connection and try again."
}

val Store.displayName: String
    get() = when (this) {
        Store.AppStore -> "App Store Connect"
        Store.GooglePlay -> "Google Play"
    }
