# Milestone 1.1: Connect Wizard — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the paste-fields Connect screen with a one-step-per-screen wizard where users pick or drop their downloaded key file instead of pasting secrets.

**Architecture:** All wizard behaviour lives in `ConnectWizardViewModel` (unit-tested with the existing fakes). The Compose screen only renders state and forwards events. FileKit provides file pickers on every platform; desktop adds drag-and-drop through an `expect`/`actual` `FileDropZone`. A small pure helper file parses the Apple Key ID from the file name and cleans key text.

**Tech Stack:** Kotlin 2.4.10, Compose Multiplatform 1.11.1 (Material 3), Koin 4.2.2, FileKit 0.15.0.

**Spec:** `docs/superpowers/specs/2026-10-04-connect-wizard-design.md` (visual reference: `design/connect-wizard/index.html`)

## Global Constraints

- Package root `com.vaazhstudios.appgotchi`; new UI code under `shared/src/*/kotlin/com/vaazhstudios/appgotchi/screens/connect/`.
- FileKit version `0.15.0` exactly (0.16 needs Kotlin 2.4.20 / CMP 1.12).
- Key file contents are never displayed, logged or put in `toString()`; `KeyFile.toString()` redacts.
- Keys are saved only after a successful `listApps()`; a Play key that sees zero apps is not saved (wizard shows the waiting notice).
- Nothing the user does may crash the app: wrong file types, unreadable files, failed link opening all become readable messages or no-ops.
- All user-visible strings live in `shared/src/commonMain/composeResources/values/strings.xml`; use the typographic apostrophe `’` (Compose resources don't unescape `\'`).
- Run tests with `./gradlew :shared:jvmTest` from the repo root (`~/tap and touch studios/appgotchi`).
- Never run `./gradlew --stop` (it kills the user's running app).
- Commit after every task; every message ends with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

### Task 1: FileKit dependency and key-file helpers

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`
- Modify: `desktopApp/build.gradle.kts`
- Modify: `desktopApp/src/main/kotlin/com/vaazhstudios/appgotchi/main.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/KeyFiles.kt`
- Test: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/data/KeyFilesTest.kt`

**Interfaces:**
- Produces (package `com.vaazhstudios.appgotchi.data`):
  - `fun keyIdFromFileName(fileName: String): String?`
  - `fun cleanKeyFileText(text: String): String`
  - `fun hasExtension(fileName: String, extension: String): Boolean`
  - Catalog aliases `libs.filekit.core`, `libs.filekit.dialogs.compose`

- [ ] **Step 1: Add FileKit to the catalog**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
filekit = "0.15.0"
```

and under `[libraries]`:

```toml
filekit-core = { module = "io.github.vinceglb:filekit-core", version.ref = "filekit" }
filekit-dialogs-compose = { module = "io.github.vinceglb:filekit-dialogs-compose", version.ref = "filekit" }
```

- [ ] **Step 2: Add the dependencies**

In `shared/build.gradle.kts`, inside `commonMain.dependencies { … }`, add after `implementation(libs.ksafe)`:

```kotlin
            implementation(libs.filekit.dialogs.compose)
```

In `desktopApp/build.gradle.kts`, inside `dependencies { … }`, add:

```kotlin
    implementation(libs.filekit.core)
```

- [ ] **Step 3: Initialise FileKit on desktop**

Replace `desktopApp/src/main/kotlin/com/vaazhstudios/appgotchi/main.kt` with:

```kotlin
package com.vaazhstudios.appgotchi

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.vaazhstudios.appgotchi.di.initKoin
import io.github.vinceglb.filekit.FileKit

fun main() {
    initKoin()
    FileKit.init(appId = "com.vaazhstudios.appgotchi")

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Appgotchi",
        ) {
            App()
        }
    }
}
```

If `FileKit.init` is an extension function that needs its own import, add `import io.github.vinceglb.filekit.init` (check the FileKit 0.15.0 JVM sources); keep the call identical.

- [ ] **Step 4: Write the failing test**

`shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/data/KeyFilesTest.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyFilesTest {
    @Test
    fun keyIdIsReadFromApplesFileName() {
        assertEquals("7XK2M9Q4TB", keyIdFromFileName("AuthKey_7XK2M9Q4TB.p8"))
    }

    @Test
    fun renamedFilesHaveNoKeyId() {
        assertNull(keyIdFromFileName("my-apple-key.p8"))
        assertNull(keyIdFromFileName("AuthKey_7XK2M9Q4TB.p8.txt"))
        assertNull(keyIdFromFileName("AuthKey_.p8"))
    }

    @Test
    fun cleaningStripsByteOrderMarkAndWhitespace() {
        assertEquals("-----BEGIN PRIVATE KEY-----", cleanKeyFileText("﻿  -----BEGIN PRIVATE KEY-----\n\n"))
    }

    @Test
    fun extensionCheckIgnoresCase() {
        assertTrue(hasExtension("AuthKey_ABC.P8", "p8"))
        assertTrue(hasExtension("key.json", "json"))
        assertFalse(hasExtension("key.json.txt", "json"))
        assertFalse(hasExtension("p8", "p8"))
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest`
Expected: FAIL — compilation error `Unresolved reference 'keyIdFromFileName'`.

- [ ] **Step 6: Implement**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/data/KeyFiles.kt`:

```kotlin
package com.vaazhstudios.appgotchi.data

private val appleKeyFileName = Regex("""^AuthKey_([A-Z0-9]+)\.p8$""")

/** Apple names downloaded keys `AuthKey_<KEYID>.p8`; returns null once the file has been renamed. */
fun keyIdFromFileName(fileName: String): String? = appleKeyFileName.find(fileName)?.groupValues?.get(1)

/** Key files are small text files: drop a UTF-8 byte-order mark and surrounding whitespace. */
fun cleanKeyFileText(text: String): String = text.removePrefix("﻿").trim()

fun hasExtension(fileName: String, extension: String): Boolean =
    fileName.length > extension.length + 1 && fileName.endsWith(".$extension", ignoreCase = true)
```

- [ ] **Step 7: Run tests and build desktop**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin`
Expected: PASS (20 tests in `shared`), BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add gradle/libs.versions.toml shared desktopApp docs/superpowers/specs/2026-10-04-connect-wizard-design.md docs/superpowers/plans/2026-10-04-connect-wizard.md design/connect-wizard/index.html
git commit -m "Add FileKit and helpers for reading downloaded key files

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: ConnectWizardViewModel

**Files:**
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`
- Test: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardViewModelTest.kt`

**Interfaces:**
- Consumes: `CredentialStore`, `StoreClientFactory`, `userMessage`, fakes (`FakeCredentialStore`, `FakeStoreClient`, `FakeStoreClientFactory`), `keyIdFromFileName`, `cleanKeyFileText`, `hasExtension` (Task 1); `PlayCredentials.serviceAccountKey()` throws `InvalidCredentialsException`.
- Produces (package `com.vaazhstudios.appgotchi.screens.connect`):
  - `enum class WizardStatus { Idle, Verifying, Waiting, Connected }`
  - `data class KeyFile(val name: String, val text: String)` (redacted `toString`)
  - `data class ConnectWizardState(store, step, appleKeyFile, keyId, issuerId, playKeyFile, serviceAccountEmail, status, error)` with `isLastStep`, `canContinue`, `canVerify`, `companion object { const val STEP_COUNT = 3 }`
  - `class ConnectWizardViewModel(credentialStore, clientFactory) : ViewModel` with `state: StateFlow<ConnectWizardState>` and `selectStore(Store)`, `next()`, `back()`, `onAppleKeyFile(fileName, text)`, `removeAppleKeyFile()`, `onKeyIdChange(String)`, `onIssuerIdChange(String)`, `onPlayKeyFile(fileName, text)`, `removePlayKeyFile()`, `onFileReadFailed()`, `verify()`
- The old `ConnectViewModel` stays registered until Task 4 deletes it.

- [ ] **Step 1: Write the failing test**

`shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardViewModelTest.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.data.StoreApiException
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.data.FakeCredentialStore
import com.vaazhstudios.appgotchi.data.FakeStoreClient
import com.vaazhstudios.appgotchi.data.FakeStoreClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectWizardViewModelTest {
    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val playApp = StoreApp(Store.GooglePlay, "com.example.posepal", "PosePal", "com.example.posepal")
    private val serviceAccountJson =
        """{"type":"service_account","client_email":"appgotchi@project.iam.gserviceaccount.com","private_key":"x"}"""

    private fun viewModel(
        store: FakeCredentialStore = FakeCredentialStore(),
        apple: () -> List<StoreApp> = { emptyList() },
        play: () -> List<StoreApp> = { listOf(playApp) },
    ) = ConnectWizardViewModel(
        store,
        FakeStoreClientFactory(FakeStoreClient(Store.AppStore, apple), FakeStoreClient(Store.GooglePlay, play)),
    )

    @Test
    fun startsOnTheFirstAppStoreStep() {
        val state = viewModel().state.value

        assertEquals(Store.AppStore, state.store)
        assertEquals(0, state.step)
        assertEquals(WizardStatus.Idle, state.status)
    }

    @Test
    fun appleKeyIdIsReadFromTheFileNameAndTextIsCleaned() {
        val vm = viewModel()

        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "﻿-----BEGIN PRIVATE KEY-----\n")

        assertEquals("7XK2M9Q4TB", vm.state.value.keyId)
        assertEquals("-----BEGIN PRIVATE KEY-----", vm.state.value.appleKeyFile?.text)
    }

    @Test
    fun renamedAppleFileKeepsTheTypedKeyId() {
        val vm = viewModel()
        vm.onKeyIdChange("ABC123")

        vm.onAppleKeyFile("my-key.p8", "key")

        assertEquals("ABC123", vm.state.value.keyId)
    }

    @Test
    fun wrongAppleFileTypeShowsAnError() {
        val vm = viewModel()

        vm.onAppleKeyFile("notes.txt", "hello")

        assertNull(vm.state.value.appleKeyFile)
        assertEquals("That’s not a .p8 file. Choose the AuthKey file you downloaded from App Store Connect.", vm.state.value.error)
    }

    @Test
    fun cannotContinuePastTheFileStepWithoutAFile() {
        val vm = viewModel()

        vm.next()
        vm.next()

        assertEquals(1, vm.state.value.step)
        assertFalse(vm.state.value.canContinue)
    }

    @Test
    fun appleKeyIsVerifiedThenSaved() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store)
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "pem")
        vm.onIssuerIdChange("  issuer-1  ")

        vm.verify()

        assertEquals(WizardStatus.Connected, vm.state.value.status)
        assertEquals(AppStoreConnectCredentials("issuer-1", "7XK2M9Q4TB", "pem"), store.apple)
    }

    @Test
    fun rejectedAppleKeyShowsAnErrorAndIsNotSaved() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store, apple = { throw StoreApiException(Store.AppStore, 401, "") })
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "pem")
        vm.onIssuerIdChange("issuer-1")

        vm.verify()

        assertEquals(WizardStatus.Idle, vm.state.value.status)
        assertEquals("App Store Connect rejected this key. Check the Issuer ID, Key ID and private key.", vm.state.value.error)
        assertNull(store.apple)
    }

    @Test
    fun playKeyFileRevealsTheServiceAccountEmail() {
        val vm = viewModel()
        vm.selectStore(Store.GooglePlay)

        vm.onPlayKeyFile("appgotchi-key.json", serviceAccountJson)

        assertEquals("appgotchi@project.iam.gserviceaccount.com", vm.state.value.serviceAccountEmail)
        assertTrue(vm.state.value.canVerify)
    }

    @Test
    fun invalidPlayKeyShowsAReadableError() {
        val vm = viewModel()
        vm.selectStore(Store.GooglePlay)

        vm.onPlayKeyFile("other.json", """{"type":"authorized_user"}""")

        assertNull(vm.state.value.serviceAccountEmail)
        assertEquals(
            "This isn't a Google service account key. Download a JSON key for a service account from Google Cloud.",
            vm.state.value.error,
        )
    }

    @Test
    fun playKeyThatSeesNoAppsWaitsWithoutSaving() = runTest {
        val store = FakeCredentialStore()
        val vm = viewModel(store, play = { emptyList() })
        vm.selectStore(Store.GooglePlay)
        vm.onPlayKeyFile("appgotchi-key.json", serviceAccountJson)

        vm.verify()

        assertEquals(WizardStatus.Waiting, vm.state.value.status)
        assertNull(store.play)
    }

    @Test
    fun checkingAgainConnectsOnceAppsAppear() = runTest {
        val store = FakeCredentialStore()
        var apps = emptyList<StoreApp>()
        val vm = viewModel(store, play = { apps })
        vm.selectStore(Store.GooglePlay)
        vm.onPlayKeyFile("appgotchi-key.json", serviceAccountJson)
        vm.verify()

        apps = listOf(playApp)
        vm.verify()

        assertEquals(WizardStatus.Connected, vm.state.value.status)
        assertEquals(serviceAccountJson, store.play?.serviceAccountJson)
    }

    @Test
    fun switchingStoreReturnsToTheFirstStep() {
        val vm = viewModel()
        vm.onAppleKeyFile("AuthKey_7XK2M9Q4TB.p8", "pem")
        vm.next()
        vm.next()

        vm.selectStore(Store.GooglePlay)

        assertEquals(Store.GooglePlay, vm.state.value.store)
        assertEquals(0, vm.state.value.step)
    }

    @Test
    fun unreadableFileShowsAnError() {
        val vm = viewModel()

        vm.onFileReadFailed()

        assertEquals("Couldn’t read that file. Try choosing it again.", vm.state.value.error)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :shared:jvmTest`
Expected: FAIL — `Unresolved reference 'ConnectWizardViewModel'` (plus follow-on errors).

- [ ] **Step 3: Implement**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardViewModel.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaazhstudios.appgotchi.core.apple.AppStoreConnectCredentials
import com.vaazhstudios.appgotchi.core.data.InvalidCredentialsException
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.core.play.PlayCredentials
import com.vaazhstudios.appgotchi.data.CredentialStore
import com.vaazhstudios.appgotchi.data.StoreClientFactory
import com.vaazhstudios.appgotchi.data.cleanKeyFileText
import com.vaazhstudios.appgotchi.data.hasExtension
import com.vaazhstudios.appgotchi.data.keyIdFromFileName
import com.vaazhstudios.appgotchi.data.userMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WizardStatus { Idle, Verifying, Waiting, Connected }

data class KeyFile(val name: String, val text: String) {
    override fun toString() = "KeyFile(name=$name, text=***)"
}

data class ConnectWizardState(
    val store: Store = Store.AppStore,
    val step: Int = 0,
    val appleKeyFile: KeyFile? = null,
    val keyId: String = "",
    val issuerId: String = "",
    val playKeyFile: KeyFile? = null,
    val serviceAccountEmail: String? = null,
    val status: WizardStatus = WizardStatus.Idle,
    val error: String? = null,
) {
    val isLastStep: Boolean get() = step == STEP_COUNT - 1

    /** Only the file step (index 1) has a requirement before moving on. */
    val canContinue: Boolean
        get() = when {
            step != 1 -> true
            store == Store.AppStore -> appleKeyFile != null && keyId.isNotBlank()
            else -> serviceAccountEmail != null
        }

    val canVerify: Boolean
        get() = status != WizardStatus.Verifying && when (store) {
            Store.AppStore -> appleKeyFile != null && keyId.isNotBlank() && issuerId.isNotBlank()
            Store.GooglePlay -> playKeyFile != null && serviceAccountEmail != null
        }

    companion object {
        const val STEP_COUNT = 3
    }
}

class ConnectWizardViewModel(
    private val credentialStore: CredentialStore,
    private val clientFactory: StoreClientFactory,
) : ViewModel() {
    private val _state = MutableStateFlow(ConnectWizardState())
    val state: StateFlow<ConnectWizardState> = _state.asStateFlow()

    fun selectStore(store: Store) {
        _state.update {
            if (it.status == WizardStatus.Verifying) it
            else it.copy(store = store, step = 0, status = WizardStatus.Idle, error = null)
        }
    }

    fun next() {
        _state.update { if (it.canContinue && !it.isLastStep) it.copy(step = it.step + 1, error = null) else it }
    }

    fun back() {
        _state.update {
            if (it.step > 0 && it.status != WizardStatus.Verifying) {
                it.copy(step = it.step - 1, status = WizardStatus.Idle, error = null)
            } else {
                it
            }
        }
    }

    fun onAppleKeyFile(fileName: String, text: String) {
        if (!hasExtension(fileName, "p8")) {
            showError("That’s not a .p8 file. Choose the AuthKey file you downloaded from App Store Connect.")
            return
        }
        _state.update {
            it.copy(
                appleKeyFile = KeyFile(fileName, cleanKeyFileText(text)),
                keyId = keyIdFromFileName(fileName) ?: it.keyId,
                error = null,
            )
        }
    }

    fun removeAppleKeyFile() {
        _state.update { it.copy(appleKeyFile = null, error = null) }
    }

    fun onKeyIdChange(value: String) {
        _state.update { it.copy(keyId = value) }
    }

    fun onIssuerIdChange(value: String) {
        _state.update { it.copy(issuerId = value) }
    }

    fun onPlayKeyFile(fileName: String, text: String) {
        if (!hasExtension(fileName, "json")) {
            showError("That’s not a .json file. Choose the service account key you downloaded from Google Cloud.")
            return
        }
        val credentials = PlayCredentials(cleanKeyFileText(text))
        try {
            val email = credentials.serviceAccountKey().clientEmail
            _state.update {
                it.copy(
                    playKeyFile = KeyFile(fileName, credentials.serviceAccountJson),
                    serviceAccountEmail = email,
                    status = WizardStatus.Idle,
                    error = null,
                )
            }
        } catch (e: InvalidCredentialsException) {
            _state.update { it.copy(playKeyFile = null, serviceAccountEmail = null, error = e.message) }
        }
    }

    fun removePlayKeyFile() {
        _state.update { it.copy(playKeyFile = null, serviceAccountEmail = null, status = WizardStatus.Idle, error = null) }
    }

    fun onFileReadFailed() {
        showError("Couldn’t read that file. Try choosing it again.")
    }

    fun verify() {
        val current = _state.value
        if (!current.canVerify) return
        _state.update { it.copy(status = WizardStatus.Verifying, error = null) }
        viewModelScope.launch {
            try {
                val status = when (current.store) {
                    Store.AppStore -> verifyAppStore(current)
                    Store.GooglePlay -> verifyPlay(current)
                }
                _state.update { it.copy(status = status) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(status = WizardStatus.Idle, error = e.userMessage(current.store)) }
            }
        }
    }

    private suspend fun verifyAppStore(state: ConnectWizardState): WizardStatus {
        val keyFile = requireNotNull(state.appleKeyFile)
        val credentials = AppStoreConnectCredentials(state.issuerId.trim(), state.keyId.trim(), keyFile.text)
        clientFactory.appStoreConnect(credentials).listApps()
        credentialStore.saveAppStoreConnect(credentials)
        return WizardStatus.Connected
    }

    private suspend fun verifyPlay(state: ConnectWizardState): WizardStatus {
        val credentials = PlayCredentials(requireNotNull(state.playKeyFile).text)
        // apps:search returns no apps (not an error) until the Play Console invite has taken effect
        if (clientFactory.play(credentials).listApps().isEmpty()) return WizardStatus.Waiting
        credentialStore.savePlay(credentials)
        return WizardStatus.Connected
    }

    private fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
```

- [ ] **Step 4: Register in Koin**

In `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`, add the import `import com.vaazhstudios.appgotchi.screens.connect.ConnectWizardViewModel` and the line `viewModelOf(::ConnectWizardViewModel)` directly below `viewModelOf(::ConnectViewModel)`.

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :shared:jvmTest`
Expected: PASS (33 tests in `shared`).

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "Add the Connect wizard's view model

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Platform pieces — file drop zone and clipboard

**Files:**
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.kt`
- Create: `shared/src/jvmMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.jvm.kt`
- Create: `shared/src/androidMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.android.kt`
- Create: `shared/src/iosMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.ios.kt`
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.kt`
- Create: `shared/src/jvmMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.jvm.kt`
- Create: `shared/src/androidMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.android.kt`
- Create: `shared/src/iosMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.ios.kt`

**Interfaces:**
- Consumes: FileKit `PlatformFile` (Task 1).
- Produces (package `com.vaazhstudios.appgotchi.screens.connect`):
  - `expect val fileDropSupported: Boolean` (true only on desktop)
  - `@Composable expect fun FileDropZone(onFileDropped: (PlatformFile) -> Unit, modifier: Modifier = Modifier, content: @Composable (isDragging: Boolean) -> Unit)`
  - `expect fun plainTextClipEntry(text: String): ClipEntry`

These are thin platform adapters with no testable logic; verification is compiling every target.

- [ ] **Step 1: Common declarations**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

/** True where files can be dragged onto the window (desktop). */
expect val fileDropSupported: Boolean

/** Accepts a dropped file on desktop; elsewhere it just renders [content]. */
@Composable
expect fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
)
```

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.ui.platform.ClipEntry

expect fun plainTextClipEntry(text: String): ClipEntry
```

- [ ] **Step 2: Desktop actuals**

`shared/src/jvmMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.jvm.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor
import java.io.File

actual val fileDropSupported: Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentOnFileDropped by rememberUpdatedState(onFileDropped)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                val transferable = event.awtTransferable
                if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return false
                val file = (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                    ?.filterIsInstance<File>()
                    ?.firstOrNull()
                    ?: return false
                currentOnFileDropped(PlatformFile(file))
                return true
            }
        }
    }
    Box(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
            target = target,
        ),
    ) {
        content(isDragging)
    }
}
```

`shared/src/jvmMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.jvm.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry
import java.awt.datatransfer.StringSelection

@OptIn(ExperimentalComposeUiApi::class)
actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(StringSelection(text))
```

- [ ] **Step 3: Android actuals**

`shared/src/androidMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.android.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

actual val fileDropSupported: Boolean = false

@Composable
actual fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    Box(modifier) { content(false) }
}
```

`shared/src/androidMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.android.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import android.content.ClipData
import androidx.compose.ui.platform.ClipEntry

actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry(ClipData.newPlainText("text", text))
```

- [ ] **Step 4: iOS actuals**

`shared/src/iosMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/FileDropZone.ios.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

actual val fileDropSupported: Boolean = false

@Composable
actual fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    Box(modifier) { content(false) }
}
```

`shared/src/iosMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/PlainTextClipEntry.ios.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.ClipEntry

@OptIn(ExperimentalComposeUiApi::class)
actual fun plainTextClipEntry(text: String): ClipEntry = ClipEntry.withPlainText(text)
```

- [ ] **Step 5: Compile every target**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL; `shared` still 33 tests passing. If an import path differs in CMP 1.11.1 (e.g. `awtTransferable`/`dragData` live in another package), fix only the import.

- [ ] **Step 6: Commit**

```bash
git add shared
git commit -m "Add desktop file drop and clipboard helpers for the Connect wizard

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Wizard screen, navigation, and removal of the old Connect screen

**Files:**
- Create: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardScreen.kt`
- Modify: `shared/src/commonMain/composeResources/values/strings.xml`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/di/Koin.kt`
- Delete: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectScreen.kt`
- Delete: `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModel.kt`
- Delete: `shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModelTest.kt`

**Interfaces:**
- Consumes: `ConnectWizardViewModel`, `ConnectWizardState`, `WizardStatus` (Task 2); `FileDropZone`, `fileDropSupported`, `plainTextClipEntry` (Task 3); `Store.displayName`.
- Produces: `@Composable fun ConnectWizardScreen(onConnected: () -> Unit, onBack: () -> Unit, viewModel: ConnectWizardViewModel = koinViewModel())`.

- [ ] **Step 1: Replace the strings**

Replace `shared/src/commonMain/composeResources/values/strings.xml` with:

```xml
<?xml version="1.0" encoding="UTF-8" ?>
<resources>
    <string name="app_name">Appgotchi</string>
    <string name="today_empty_title">No stores connected yet</string>
    <string name="today_empty_body">Connect App Store Connect or Google Play to start keeping your apps alive.</string>
    <string name="today_connect_button">Connect a store</string>
    <string name="today_refresh">Refresh</string>
    <string name="today_no_apps">No apps in this account yet.</string>

    <string name="wizard_close">Close</string>
    <string name="wizard_store_app_store">App Store</string>
    <string name="wizard_store_google_play">Google Play</string>
    <string name="wizard_step_counter">%1$s · Step %2$d of %3$d</string>
    <string name="wizard_continue">Continue</string>
    <string name="wizard_back">Back</string>
    <string name="wizard_remove">Remove</string>
    <string name="wizard_copy">Copy</string>
    <string name="wizard_check_again">Check again</string>
    <string name="wizard_checking">Checking with %1$s…</string>
    <string name="wizard_drop_hint">Drop it here or click to choose it</string>
    <string name="wizard_pick_hint">Tap to choose it from your files</string>
    <string name="wizard_trust_apple">Stored in this device’s secure storage. Only ever sent to Apple.</string>
    <string name="wizard_trust_play">Stored in this device’s secure storage. Only ever sent to Google.</string>

    <string name="apple_step1_title">Create a team key</string>
    <string name="apple_step1_body">In App Store Connect, open Users and Access, then Integrations. Generate a team key with the App Manager role. Apple lets you download it only once.</string>
    <string name="apple_open_team_keys">Open Team Keys</string>
    <string name="apple_step2_title">Add your AuthKey file</string>
    <string name="apple_step2_body">Add the .p8 file you downloaded. Keep its original name so Appgotchi can read the Key ID.</string>
    <string name="apple_file_label">Add the .p8 file</string>
    <string name="apple_key_id">Key ID</string>
    <string name="apple_key_id_from_file">Read from the file name.</string>
    <string name="apple_key_id_manual">The file was renamed. Enter the Key ID shown next to the key in App Store Connect.</string>
    <string name="apple_step3_title">Paste your Issuer ID</string>
    <string name="apple_step3_body">It’s shown above the keys list on the Team Keys page. The Issuer ID isn’t secret.</string>
    <string name="apple_issuer_id">Issuer ID</string>
    <string name="apple_verify">Verify and connect</string>

    <string name="play_step1_title">Create a service account</string>
    <string name="play_step1_body">In Google Cloud, enable the Play Developer Reporting API. Then create a service account and download a JSON key for it.</string>
    <string name="play_enable_api">Enable the API</string>
    <string name="play_service_accounts">Service accounts</string>
    <string name="play_step2_title">Add the JSON key</string>
    <string name="play_step2_body">Add the JSON key file. Appgotchi shows the account email so you can invite it next.</string>
    <string name="play_file_label">Add the .json key file</string>
    <string name="play_file_added">Service account key added.</string>
    <string name="play_step3_title">Invite it in Play Console</string>
    <string name="play_step3_body">Invite this email under Users and permissions with View app information (read-only). New permissions can take a few hours to apply.</string>
    <string name="play_open_console">Open Play Console</string>
    <string name="play_verify">Verify access</string>
    <string name="play_waiting">The key works, but Google hasn’t applied the Play Console permissions yet. This can take a few hours, so check again later.</string>
</resources>
```

- [ ] **Step 2: Write the screen**

`shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectWizardScreen.kt`:

```kotlin
package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.*
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.data.displayName
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val TEAM_KEYS_URL = "https://appstoreconnect.apple.com/access/integrations/api"
private const val ENABLE_REPORTING_API_URL =
    "https://console.cloud.google.com/apis/library/playdeveloperreporting.googleapis.com"
private const val SERVICE_ACCOUNTS_URL = "https://console.cloud.google.com/iam-admin/serviceaccounts"
private const val PLAY_CONSOLE_URL = "https://play.google.com/console/u/0/developers"

@Composable
fun ConnectWizardScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectWizardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.status) {
        if (state.status == WizardStatus.Connected) onConnected()
    }

    // Reads the picked/dropped file and hands it to the view model for the store currently shown
    val onKeyFile: (PlatformFile) -> Unit = { file ->
        scope.launch {
            val text = runCatching { file.readString() }.getOrNull()
            when {
                text == null -> viewModel.onFileReadFailed()
                viewModel.state.value.store == Store.AppStore -> viewModel.onAppleKeyFile(file.name, text)
                else -> viewModel.onPlayKeyFile(file.name, text)
            }
        }
    }
    // One launcher per type: Android can't filter unknown extensions like .p8, so the view model re-checks
    val p8Picker = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("p8"))) { file ->
        file?.let(onKeyFile)
    }
    val jsonPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("json"))) { file ->
        file?.let(onKeyFile)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.wizard_close)) }
            StorePicker(state.store, enabled = state.status != WizardStatus.Verifying, onSelect = viewModel::selectStore)
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                StepProgress(state.step)
                Text(
                    stringResource(Res.string.wizard_step_counter, state.store.displayName, state.step + 1, ConnectWizardState.STEP_COUNT),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (state.store) {
                Store.AppStore -> AppStoreStep(state, viewModel, onPick = { p8Picker.launch() }, onDropped = onKeyFile)
                Store.GooglePlay -> PlayStep(state, viewModel, onPick = { jsonPicker.launch() }, onDropped = onKeyFile)
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            StatusNotice(state)
            Footer(state, viewModel)
            TrustNote(state.store)
        }
    }
}

@Composable
private fun StorePicker(selected: Store, enabled: Boolean, onSelect: (Store) -> Unit) {
    val labels = mapOf(
        Store.AppStore to Res.string.wizard_store_app_store,
        Store.GooglePlay to Res.string.wizard_store_google_play,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        Store.entries.forEachIndexed { index, store ->
            SegmentedButton(
                selected = store == selected,
                onClick = { onSelect(store) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index = index, count = Store.entries.size),
            ) {
                Text(stringResource(labels.getValue(store)))
            }
        }
    }
}

@Composable
private fun StepProgress(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(ConnectWizardState.STEP_COUNT) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (index <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
            )
        }
    }
}

@Composable
private fun StepHeading(title: StringResource, body: StringResource) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExternalLinks(vararg links: Pair<StringResource, String>) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { (label, url) ->
            // Opening a browser can fail (no handler, unsupported desktop); never crash over it
            OutlinedButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(stringResource(label)) }
        }
    }
}

@Composable
private fun AppStoreStep(
    state: ConnectWizardState,
    viewModel: ConnectWizardViewModel,
    onPick: () -> Unit,
    onDropped: (PlatformFile) -> Unit,
) {
    when (state.step) {
        0 -> {
            StepHeading(Res.string.apple_step1_title, Res.string.apple_step1_body)
            ExternalLinks(Res.string.apple_open_team_keys to TEAM_KEYS_URL)
        }
        1 -> {
            StepHeading(Res.string.apple_step2_title, Res.string.apple_step2_body)
            KeyFileField(
                fileName = state.appleKeyFile?.name,
                detail = null,
                label = Res.string.apple_file_label,
                onPick = onPick,
                onDropped = onDropped,
                onRemove = viewModel::removeAppleKeyFile,
            )
            if (state.appleKeyFile != null) {
                val fromFileName = state.keyId.isNotBlank() && state.appleKeyFile.name.contains(state.keyId)
                OutlinedTextField(
                    value = state.keyId,
                    onValueChange = viewModel::onKeyIdChange,
                    label = { Text(stringResource(Res.string.apple_key_id)) },
                    supportingText = {
                        Text(stringResource(if (fromFileName) Res.string.apple_key_id_from_file else Res.string.apple_key_id_manual))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        else -> {
            StepHeading(Res.string.apple_step3_title, Res.string.apple_step3_body)
            OutlinedTextField(
                value = state.issuerId,
                onValueChange = viewModel::onIssuerIdChange,
                label = { Text(stringResource(Res.string.apple_issuer_id)) },
                singleLine = true,
                enabled = state.status != WizardStatus.Verifying,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlayStep(
    state: ConnectWizardState,
    viewModel: ConnectWizardViewModel,
    onPick: () -> Unit,
    onDropped: (PlatformFile) -> Unit,
) {
    when (state.step) {
        0 -> {
            StepHeading(Res.string.play_step1_title, Res.string.play_step1_body)
            ExternalLinks(
                Res.string.play_enable_api to ENABLE_REPORTING_API_URL,
                Res.string.play_service_accounts to SERVICE_ACCOUNTS_URL,
            )
        }
        1 -> {
            StepHeading(Res.string.play_step2_title, Res.string.play_step2_body)
            KeyFileField(
                fileName = state.playKeyFile?.name,
                detail = if (state.playKeyFile != null) stringResource(Res.string.play_file_added) else null,
                label = Res.string.play_file_label,
                onPick = onPick,
                onDropped = onDropped,
                onRemove = viewModel::removePlayKeyFile,
            )
            state.serviceAccountEmail?.let { EmailRow(it) }
        }
        else -> {
            StepHeading(Res.string.play_step3_title, Res.string.play_step3_body)
            state.serviceAccountEmail?.let { EmailRow(it) }
            ExternalLinks(Res.string.play_open_console to PLAY_CONSOLE_URL)
        }
    }
}

@Composable
private fun KeyFileField(
    fileName: String?,
    detail: String?,
    label: StringResource,
    onPick: () -> Unit,
    onDropped: (PlatformFile) -> Unit,
    onRemove: () -> Unit,
) {
    if (fileName == null) {
        FileDropZone(onFileDropped = onDropped, modifier = Modifier.fillMaxWidth()) { isDragging ->
            OutlinedCard(
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(
                    width = if (isDragging) 2.dp else 1.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (fileDropSupported) Res.string.wizard_drop_hint else Res.string.wizard_pick_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    } else {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                TextButton(onClick = onRemove) { Text(stringResource(Res.string.wizard_remove)) }
            }
        }
    }
}

@Composable
private fun EmailRow(email: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                email,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { scope.launch { clipboard.setClipEntry(plainTextClipEntry(email)) } }) {
                Text(stringResource(Res.string.wizard_copy))
            }
        }
    }
}

@Composable
private fun StatusNotice(state: ConnectWizardState) {
    when (state.status) {
        WizardStatus.Verifying -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Text(stringResource(Res.string.wizard_checking, state.store.displayName), style = MaterialTheme.typography.bodyMedium)
        }
        WizardStatus.Waiting -> Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
            Text(
                stringResource(Res.string.play_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(12.dp),
            )
        }
        else -> Unit
    }
}

@Composable
private fun Footer(state: ConnectWizardState, viewModel: ConnectWizardViewModel) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (state.step > 0) {
            TextButton(onClick = viewModel::back, enabled = state.status != WizardStatus.Verifying) {
                Text(stringResource(Res.string.wizard_back))
            }
        }
        Spacer(Modifier.weight(1f))
        if (state.isLastStep) {
            val label = when {
                state.status == WizardStatus.Waiting -> Res.string.wizard_check_again
                state.store == Store.AppStore -> Res.string.apple_verify
                else -> Res.string.play_verify
            }
            Button(onClick = viewModel::verify, enabled = state.canVerify) { Text(stringResource(label)) }
        } else {
            Button(onClick = viewModel::next, enabled = state.canContinue) { Text(stringResource(Res.string.wizard_continue)) }
        }
    }
}

@Composable
private fun TrustNote(store: Store) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            Icons.Default.Lock,
            contentDescription = null,
            modifier = Modifier.size(16.dp).padding(top = 2.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(if (store == Store.AppStore) Res.string.wizard_trust_apple else Res.string.wizard_trust_play),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
```

If the compiler rejects the wildcard resource import, replace `import appgotchi.shared.generated.resources.*` with one explicit import per string used (as the other screens do). If `FlowRow` requires an opt-in in this Compose version, add `@OptIn(ExperimentalLayoutApi::class)` to `ExternalLinks`. If `autoCorrectEnabled` doesn't exist on `KeyboardOptions`, use `autoCorrect = false`.

- [ ] **Step 3: Switch navigation to the wizard**

In `shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/App.kt`, replace `import com.vaazhstudios.appgotchi.screens.connect.ConnectScreen` with `import com.vaazhstudios.appgotchi.screens.connect.ConnectWizardScreen`, and replace `ConnectScreen(` with `ConnectWizardScreen(` (the two callback arguments stay the same).

- [ ] **Step 4: Delete the old screen and view model**

```bash
git rm shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectScreen.kt \
       shared/src/commonMain/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModel.kt \
       shared/src/commonTest/kotlin/com/vaazhstudios/appgotchi/screens/connect/ConnectViewModelTest.kt
```

In `Koin.kt`, remove `import com.vaazhstudios.appgotchi.screens.connect.ConnectViewModel` and the line `viewModelOf(::ConnectViewModel)`.

- [ ] **Step 5: Build and test everything**

Run: `./gradlew :shared:jvmTest :desktopApp:compileKotlin :androidApp:assembleDebug :shared:linkDebugFrameworkIosSimulatorArm64`
Expected: BUILD SUCCESSFUL; `shared` 27 tests passing (33 − 6 deleted).

Run: `./gradlew :shared:iosSimulatorArm64Test`
Expected: PASS (27 tests).

- [ ] **Step 6: Desktop smoke check**

Launch in the background for ~60 s and check the log for startup exceptions (do not use `--stop`):
`( ./gradlew :desktopApp:run --console=plain > /private/tmp/claude-501/appgotchi-wizard-run.log 2>&1 & PID=$!; sleep 60; kill $PID; sleep 3; pkill -f 'com.vaazhstudios.appgotchi.MainKt' || true )`
Expected: no exceptions in the log. Interactive checks (pick a file, drop a file, copy email) are for the user.

- [ ] **Step 7: Commit**

```bash
git add -A shared
git commit -m "Replace the Connect screen with a step-by-step wizard

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
