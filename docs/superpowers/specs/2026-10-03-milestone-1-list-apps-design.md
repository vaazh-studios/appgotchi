# Milestone 1 — "List my apps from both stores"

## Goal

A user can paste an App Store Connect API key and/or a Google Play service account key, Appgotchi verifies it against the real store, stores it securely on the device, and the Today screen lists that account's apps from each connected store. Works on Android, iOS and desktop (macOS/Windows/Linux).

## Scope

In:
- App Store Connect client: ES256 JWT (15-minute lifetime, cached), `GET /v1/apps` with pagination via `links.next`.
- Google Play client: service-account RS256 JWT exchanged for an OAuth access token (cached), `GET https://playdeveloperreporting.googleapis.com/v1beta1/apps:search` with pagination via `nextPageToken`.
- Secure credential storage with KSafe (Android Keystore, iOS Keychain, macOS Keychain / Windows DPAPI / Linux Secret Service on desktop).
- Connect screen (paste fields; key is verified with a real API call before it is saved).
- Today screen listing apps grouped by store, with a per-store error message if a store call fails.

Out (later milestones): disconnecting stores, file pickers, individual (non-team) App Store Connect keys, any sales/review/release data, app icons.

## Decisions

- **Crypto:** `dev.whyoleg.cryptography` 0.6.0 with `cryptography-provider-optimal` (JDK on Android/JVM; CryptoKit + Apple Security on iOS). ECDSA P-256 / SHA-256 / RAW signature for Apple; RSA PKCS#1 v1.5 / SHA-256 for Google. Keys are PKCS#8 PEM.
- **Storage:** `eu.anifantakis:ksafe` 3.3.0 behind a `CredentialStore` interface.
- **HTTP:** one shared Ktor `HttpClient`; clients parse JSON themselves with a lenient `Json` so they don't depend on client configuration. Non-2xx responses throw `StoreApiException`.
- **Auth abstraction:** both clients take an `AccessTokenProvider` (`suspend fun token(): String`) so HTTP behaviour is tested without real keys.
- **Verification before save:** a key is only stored after `listApps()` succeeds with it. A Play key that succeeds but sees zero apps is refused, because `apps:search` returns an empty list (not an error) for a service account that hasn't been invited yet.
- **Secrets go to fixed hosts only:** the Google token URL is hard-coded (the `token_uri` in the pasted JSON is ignored) and App Store Connect pagination links must stay on Apple's host.
- **Never crash on user data:** unreadable keys throw `InvalidCredentialsException` with a readable message; storage and network failures become per-store error messages on the Today screen.
- **Storage hygiene:** KSafe file `appgotchi` on all platforms, namespaced `com.vaazhstudios.appgotchi` on desktop; the Apple key is stored as one value; Android backup is off.
- **Refresh policy:** Today loads once and reloads only when a key is saved; one client per key is reused so cached tokens survive refreshes.
- **No Appgotchi server, no logging of secrets.** Credential types redact themselves in `toString()`.

## Module ownership

- `core/data` — `Store`, `StoreApp`, `StoreClient`, `AccessTokenProvider`, `StoreApiException`, `Jwt`, `StoreJson`, `epochSecondsNow()`.
- `core/apple` — `AppStoreConnectCredentials`, `AppStoreConnectTokenProvider`, `AppStoreConnectClient`.
- `core/play` — `PlayCredentials`, `ServiceAccountKey`, `PlayTokenProvider`, `PlayClient`.
- `shared` — `CredentialStore`/`KSafeCredentialStore`, `StoreClientFactory`, `AppsRepository`, view models, Compose screens, Koin wiring.

## Testing

- Unit tests run on JVM (`jvmTest`): JWT encoding, token signing (verified with a generated key pair), HTTP paging and errors (Ktor `MockEngine`), repository and view models (fakes).
- iOS crypto path verified with `iosSimulatorArm64Test` on the token-provider tests.
- End-to-end check: the user connects their own keys in the desktop app.
