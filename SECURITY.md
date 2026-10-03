# Security

Appgotchi handles App Store Connect API keys and Google Play service account keys. Our rules:

- **Keys never leave your device** except in requests to Apple (`api.appstoreconnect.apple.com`) and Google (`*.googleapis.com`).
- **Keys are stored in the platform keychain**: Keychain on iOS/macOS, Android Keystore, Windows Credential Manager, Secret Service on Linux.
- **No Appgotchi server, no analytics, no telemetry.**
- **Keys are never logged.** Credential types redact themselves in `toString()`.

Prefer an App Store Connect key with the narrowest role you need, and a Play service account with access only to the apps you want to see.

## Reporting a vulnerability

Please **do not open a public issue**. Use GitHub's [private vulnerability reporting](https://github.com/vaazh-studios/appgotchi/security/advisories/new) instead. We'll respond as quickly as we can.
