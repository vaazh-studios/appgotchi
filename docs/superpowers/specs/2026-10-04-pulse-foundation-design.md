# Milestone 2.1 — Pulse foundation

Milestone 2 is **Pulse**: after-release observation for every app (downloads, proceeds, rating, reviews, crash rate). It ships in three parts:

- **2.1 (this spec):** the foundation, using only data reachable with the keys people already connect.
- **2.2:** Apple downloads and proceeds (vendor number, plus a Sales and Reports key when needed).
- **2.3:** Google Play downloads, rating and reviews (Play reports folder in Cloud Storage).

Background and decisions:
- Data sources and permissions: `docs/research/2026-10-04-pulse-data-sources.md`.
- Approved prototype: `design/pulse/index.html`. It uses rows with a metrics column, one unlock card per app, a single-column detail screen, and the 1.09% crash threshold line.
- Rejected directions: a Releases milestone (status already comes by email and in the consoles) and History first (it builds on Pulse's numbers and storage).

## What people get

1. **Today** shows one row per app, or per linked iOS + Android pair.
   - **Left side:** icon, name and store marks.
   - **Metrics column:** rating ("4.7 ★ · 3 new") and the Android crash rate with a 30-day mini chart.
   - **Unlock card:** a quiet, locked "Downloads & proceeds" card ("Add your vendor number" / "Add your reports folder"). It goes nowhere in 2.1 and gets wired up in 2.2 and 2.3.
   - **Sorting:** rows needing attention come first (over the crash threshold, or new 1–2★ reviews); the rest are alphabetical.
2. **App detail** is a single column:
   - **Header:** app name, store marks, the link state ("Linked: iOS + Android") and a **Change** link.
   - **Rating per store:** App Store rating for the device's country, e.g. "4.7 ★ (US) · 1,204 ratings". The Google Play rating says it needs the reports folder (2.3).
   - **Crash chart:** a large 30-day Android crash-rate chart with Google's 1.09% bad-behaviour line and a tooltip per day.
   - **Reviews:** App Store reviews, newest first, with stars, title, body, territory, date and version.
     - Filters: All / 1–2★ / Unread.
     - Opening the detail marks its reviews as read.
     - Read-only; replying comes later.
   - **Locked placeholders** for downloads and proceeds.
3. **Link apps sheet:** lists the automatic pairs and lets people change or unlink a pair. Manual choices are kept on the device and always win over automatic matching.
4. **Demo mode** gains sample ratings, reviews and crash series for Sproutly, Tidepool and Inkwell, including one app over the threshold.

## Data

| Metric | Source | Access |
|---|---|---|
| App Store reviews | App Store Connect `GET /v1/apps/{id}/customerReviews`, newest first | Existing key. If refused (401/403), the section says the key's role can't read reviews instead of showing an error. |
| App Store rating | Public iTunes Lookup (`averageUserRating`, `userRatingCount`) for the device's country, falling back to US when the device has none | No key. Never send credentials here. |
| Android crash rate | Play Developer Reporting `crashRateMetricSet:query`, daily, last 30 days | Existing scope and permission. |

- **Crash rate:**
  - The chart shows daily user-perceived crash rate.
  - The threshold warning uses Google's own measure: the 28-day user-weighted user-perceived crash rate compared with 1.09%.
  - Exact metric names are confirmed during planning.
  - Days are in Google's `America/Los_Angeles` time zone.
  - The latest available day comes from `freshnessInfo`.
- **Reviews sync:** on first sync, fetch at most the 200 newest reviews and mark them all read, so only reviews arriving later count as "new". After that, fetch pages until reaching a review already stored. "N new" on Today is the number of unread reviews.
- **Refresh:** runs when Today opens and on Refresh.
  - It fetches only what's missing; stored past days are never fetched again.
  - Each source fails on its own: a failing source shows "—" with a one-line hint and doesn't block the others.
  - App listing keeps working as today.
- **Pairing:** automatic when an iOS bundle ID equals an Android package name, otherwise when names match exactly (case-insensitive, trimmed). A manual link or unlink overrides both.

## Storage (new)

- **Engine:** an SQLDelight database on each platform: Android driver, native driver on iOS, JDBC SQLite file in the app data directory on desktop.
- **Tables:**
  - `app_link`: manual pairs and unlinks.
  - `daily_metric`: app, store, date, metric, value. Holds crash rate now; downloads and proceeds later.
  - `review`: id, app, store, rating, title, body, territory, created, version, read flag.
- **Not secret:** the database holds only public reviews and numbers. Keys stay in secure storage (KSafe) as today. SECURITY.md says the database is not encrypted.
- **Cleanup:** when a store's key is replaced or removed, that store's rows are deleted. Demo mode never writes to the database.

## Safety

- **Central allow-list:** one list of hosts that may receive an `Authorization` header, enforced in one place for every client:
  - `api.appstoreconnect.apple.com`
  - `playdeveloperreporting.googleapis.com`
  - `oauth2.googleapis.com`

  Any other host gets no credentials. That includes pagination links that point elsewhere and the public iTunes Lookup.
- No new permissions or scopes in 2.1.

## Hardening carried in

- Android system Back steps back through the Connect wizard instead of leaving it.
- User-facing error messages (`UserMessages.kt`, view-model messages, `AppsRepository`) move to a typed error mapped to string resources.
- The Apple pagination guard keeps a seen-URL set, as Play's already does.
- `StoreClientFactory` becomes safe for concurrent refreshes, because sources now load in parallel.
- Connect fixes: accept renamed downloads like `AuthKey_X (1).p8`, and fix the vacuous test `renamedAppleFileKeepsTheTypedKeyId`.

## Rules

- Calm theme components only. Strings live in resources and use typographic apostrophes (’).
- Read-only: nothing in 2.1 changes anything in either store.
- No crashes on unexpected or missing data. Parsers tolerate absent fields.
- Tests:
  - fake sources and an in-memory database for refresh, pairing and sorting;
  - recorded sample responses for each parser;
  - a test that credentials never reach a host outside the allow-list;
  - demo data tests.
