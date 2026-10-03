# Appgotchi

**Don't let your app die.**

Appgotchi is a free, open-source companion for indie app developers. It puts **App Store Connect** and **Google Play Console** in one place — releases, reviews, revenue, vitals and testing — on **Android, iOS, macOS, Windows and Linux**.

> 🚧 Early development. Milestone 1 works: connect your App Store Connect and Google Play keys and list your apps on Android, iOS and desktop. Dashboards come next — star the repo to follow along.

## Why

- Apple's and Google's consoles are slow, separate, and painful on a phone.
- Existing tools are Apple-only, Mac-only, or paid.
- Appgotchi is **local-first**: your API keys stay in your device's secure storage and are only ever sent to Apple and Google. There is no Appgotchi server.

## Planned features

| Screen | What it shows |
|---|---|
| **Today** | Yesterday's proceeds and downloads on both stores, new reviews, release status, crash alerts |
| **Releases** | App Store phased release and Play staged rollout — pause, resume, halt, increase |
| **Reviews** | One inbox for both stores, with replies |
| **Vitals** | Crash and ANR rates against store thresholds |
| **Revenue** | Trends by country and product |
| **Testing** | TestFlight builds and Play testing tracks |

## Tech

Kotlin Multiplatform + Compose Multiplatform, one codebase for every platform.

```
core/data    shared models and the StoreClient contract
core/apple   App Store Connect API client
core/play    Google Play Developer APIs client
shared       Compose UI and app wiring
androidApp   Android entry point
iosApp       iOS entry point (Xcode project)
desktopApp   macOS / Windows / Linux entry point
```

## Run it

- **Android:** `./gradlew :androidApp:installDebug`
- **Desktop:** `./gradlew :desktopApp:run`
- **iOS:** open `iosApp/iosApp.xcodeproj` in Xcode and run

Requires JDK 17+, Android SDK, and Xcode (for iOS).

## License

[Apache-2.0](LICENSE). Built by [Vaazh Studios](https://github.com/vaazh-studios), bootstrapped from JetBrains' [KMP-App-Template](https://github.com/Kotlin/KMP-App-Template).

Appgotchi is not affiliated with Apple, Google, or Bandai.
