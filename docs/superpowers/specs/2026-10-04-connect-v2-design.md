# Milestone 1.2 — Connect v2

Approved prototype: `design/connect-v2/index.html` (List cards chooser, numbered steps with a button under each linked item). Research behind the Google steps: Thimble's 6-step flow, fastlane/Gradle Play Publisher/Expo/RevenueCat/Codemagic docs, Google's Android Publisher and Reporting API "getting started" pages, Play Console Help 9844686.

## Flow

1. **Chooser** — "Where do you publish?" with two list cards (App Store Connect, Google Play) and "Try the demo with sample apps".
2. **App Store Connect**
   - **Choice** — "I have a key" (quick path) or "Help me create one" (guided).
   - **Quick path** — one screen: AuthKey file (Key ID from file name, editable) + Issuer ID (Paste button + check) → Verify and connect.
   - **Guided (3 steps)** — 1 Create a team key (checklist: open Team Keys ↗; name it "Appgotchi", App Manager role; download the .p8) · 2 Add your AuthKey file · 3 Paste your Issuer ID → Verify.
3. **Google Play (6 steps)**
   1. Developer account ID — checklist (open Play Console ↗; copy the Account ID) + Paste field with checks.
   2. Enable the Google Play APIs — create/choose project ↗; enable Developer Reporting API ↗; enable Android Developer API ↗ (used later for releases and reviews).
   3. Create a service account key — create account named "appgotchi-readonly", no roles ↗; Keys tab; JSON key (offered once). Expandable help: "Create key" greyed out → orgs created since 3 May 2024 block keys; ask an org admin or use a project outside the organization.
   4. Add the service account key — pick/drop JSON; email shown with Copy.
   5. Invite it in Play Console — email + Copy; open *your* Users and permissions ↗ (deep link built from the developer account ID); tick "View app information and download bulk reports (read-only)"; later features ask for more.
   6. Check the connection — Verify access; waiting state ("Google hasn't applied the permissions yet… only apps you've uploaded appear") with Check again.
4. **Demo mode** — Today shows sample apps with a "You're viewing sample data." banner and an "Exit demo" action; reachable from the chooser and Today's empty state; turns off automatically when a real key is saved. In memory only.

## Rules

- Checks on pasted IDs: Account ID must be 15–20 digits; Issuer ID must be a UUID. Text that looks like a private key is never kept in an ID field — the field is cleared and a message explains.
- Key contents never shown; verify-before-save; no crashes on user input (all carried over from M1/M1.1).
- No "link your Cloud project in Play Console" step (no longer required by Google). No official propagation time is claimed.
- Calm theme components only; strings in resources (view-model messages stay in Kotlin, as today).
