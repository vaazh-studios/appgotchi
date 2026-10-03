# Milestone 1.1 — Connect wizard

## Goal

Replace the paste-fields Connect screen with a guided, one-step-per-screen wizard (the "Focus" direction chosen from the ui.sh prototype in `design/connect-wizard/index.html`). Users add keys by picking or dropping the downloaded file instead of pasting secrets through the clipboard.

## Flows

Store switch (App Store / Google Play) at the top; each store has 3 steps with a progress bar and "Step N of 3".

**App Store Connect**
1. Create a team key — explanation + "Open Team Keys" (opens `https://appstoreconnect.apple.com/access/integrations/api`).
2. Add your AuthKey file — pick (all platforms) or drop (desktop) the `.p8`. Key ID is read from the file name `AuthKey_<KEYID>.p8` and shown in an editable field. Continue needs a file and a Key ID.
3. Paste your Issuer ID — text field (not secret). "Verify and connect".

**Google Play**
1. Create a service account — "Enable the API" + "Service accounts" links.
2. Add the JSON key — pick or drop the `.json`. The file is validated immediately and the service account email is shown with a Copy button. Continue needs a valid key.
3. Invite it in Play Console — email + Copy again, "Open Play Console". "Verify access". If the key works but sees zero apps, show a waiting notice ("permissions can take a few hours") and the button becomes "Check again"; nothing is saved until apps are visible.

## Rules (carried from Milestone 1)

- Verify before save; secrets only to Apple/Google; never crash on user data; key contents are never displayed.
- Wrong file type → readable error ("That's not a .p8 file…"), no crash.
- A leading UTF-8 BOM and surrounding whitespace are stripped from key files.
- Success closes the wizard and Today reloads (existing `credentialChanges` flow).

## Technical decisions

- **FileKit 0.15.0** (`filekit-core`, `filekit-dialogs-compose`) for pickers; one launcher per file type (Android maps unknown extensions to `*/*`, so the view model also checks the extension). Desktop calls `FileKit.init(appId = "com.vaazhstudios.appgotchi")`.
- **Desktop drag-and-drop** via `Modifier.dragAndDropTarget` in a `jvmMain` actual of an `expect` composable `FileDropZone`; Android/iOS actuals just render the content.
- **Links** via `LocalUriHandler`, wrapped in `runCatching`.
- **Clipboard** via `LocalClipboard.setClipEntry(plainTextClipEntry(text))` with an `expect fun plainTextClipEntry`.
- **`ConnectWizardViewModel`** replaces `ConnectViewModel` and holds all wizard logic so it is unit-tested; the UI is a thin renderer.
- Old `ConnectScreen`, `ConnectViewModel` and their tests are deleted.
