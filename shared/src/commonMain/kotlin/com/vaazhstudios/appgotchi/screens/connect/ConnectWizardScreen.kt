package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.*
import com.vaazhstudios.appgotchi.core.data.Store
import com.vaazhstudios.appgotchi.data.MAX_KEY_FILE_BYTES
import com.vaazhstudios.appgotchi.data.displayName
import com.vaazhstudios.appgotchi.data.hasExtension
import com.vaazhstudios.appgotchi.data.keyIdFromFileName
import com.vaazhstudios.appgotchi.ui.components.AppCard
import com.vaazhstudios.appgotchi.ui.components.PrimaryButton
import com.vaazhstudios.appgotchi.ui.components.QuietButton
import com.vaazhstudios.appgotchi.ui.components.SecondaryButton
import com.vaazhstudios.appgotchi.ui.components.SegmentedControl
import com.vaazhstudios.appgotchi.ui.components.appTextFieldColors
import com.vaazhstudios.appgotchi.ui.components.focusRing
import com.vaazhstudios.appgotchi.ui.theme.AppgotchiTheme
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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

    // Checks the type and size before reading, hands the text to the view model for the given store
    val onKeyFile: (PlatformFile, Store) -> Unit = { file, store ->
        scope.launch {
            val deliver: (String, String) -> Unit =
                if (store == Store.AppStore) viewModel::onAppleKeyFile else viewModel::onPlayKeyFile
            val name = file.name
            if (!hasExtension(name, if (store == Store.AppStore) "p8" else "json")) {
                // The view model rejects a wrong type before it looks at the text
                deliver(name, "")
                return@launch
            }
            val size = runCatching { file.size() }.getOrNull()
            if (size == null) {
                viewModel.onFileReadFailed()
                return@launch
            }
            if (size > MAX_KEY_FILE_BYTES) {
                viewModel.onFileTooLarge()
                return@launch
            }
            val text = runCatching { file.readString() }.getOrNull()
            if (text == null) viewModel.onFileReadFailed() else deliver(name, text)
        }
    }
    // One launcher per type: Android can't filter unknown extensions like .p8, so the handler re-checks
    val p8Picker = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("p8"))) { file ->
        file?.let { onKeyFile(it, Store.AppStore) }
    }
    val jsonPicker = rememberFilePickerLauncher(type = FileKitType.File(extensions = listOf("json"))) { file ->
        file?.let { onKeyFile(it, Store.GooglePlay) }
    }
    // A drop goes to whichever store is shown when the file lands
    val onDropped: (PlatformFile) -> Unit = { file -> onKeyFile(file, viewModel.state.value.store) }

    Column(
        // safeDrawing covers the system bars, display cutout and keyboard, so Close and Verify stay reachable
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            QuietButton(onClick = onBack) { Text(stringResource(Res.string.wizard_close)) }
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
                Store.AppStore -> AppStoreStep(state, viewModel, onPick = { p8Picker.launch() }, onDropped = onDropped)
                Store.GooglePlay -> PlayStep(state, viewModel, onPick = { jsonPicker.launch() }, onDropped = onDropped)
            }
            state.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            StatusNotice(state)
            Footer(state, viewModel)
            TrustNote(state.store)
        }
    }
}

@Composable
private fun StorePicker(selected: Store, enabled: Boolean, onSelect: (Store) -> Unit) {
    val options = listOf(
        Store.AppStore to stringResource(Res.string.wizard_store_app_store),
        Store.GooglePlay to stringResource(Res.string.wizard_store_google_play),
    )
    SegmentedControl(options = options, selected = selected, onSelect = onSelect, enabled = enabled, modifier = Modifier.fillMaxWidth())
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
                    .background(if (index <= step) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant),
            )
        }
    }
}

@Composable
private fun StepHeading(title: StringResource, body: StringResource) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExternalLinks(vararg links: Pair<StringResource, String>) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { (label, url) ->
            // Opening a browser can fail (no handler, unsupported desktop); never crash over it
            SecondaryButton(onClick = { runCatching { uriHandler.openUri(url) } }) { Text(stringResource(label)) }
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
                val fromFileName = keyIdFromFileName(state.appleKeyFile.name) == state.keyId
                OutlinedTextField(
                    value = state.keyId,
                    onValueChange = viewModel::onKeyIdChange,
                    label = { Text(stringResource(Res.string.apple_key_id)) },
                    supportingText = {
                        Text(stringResource(if (fromFileName) Res.string.apple_key_id_from_file else Res.string.apple_key_id_manual))
                    },
                    singleLine = true,
                    colors = appTextFieldColors(),
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
                colors = appTextFieldColors(),
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
            val interactionSource = remember { MutableInteractionSource() }
            OutlinedCard(
                onClick = onPick,
                modifier = Modifier.fillMaxWidth().focusRing(interactionSource, MaterialTheme.shapes.medium),
                interactionSource = interactionSource,
                border = BorderStroke(
                    width = if (isDragging) 2.dp else 1.dp,
                    color = if (isDragging) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                ),
                colors = CardDefaults.outlinedCardColors(containerColor = AppgotchiTheme.colors.cardFill),
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
        AppCard(contentPadding = PaddingValues(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                val removeDescription = stringResource(Res.string.wizard_remove_file, fileName)
                QuietButton(onClick = onRemove, modifier = Modifier.semantics { contentDescription = removeDescription }) {
                    Text(stringResource(Res.string.wizard_remove))
                }
            }
        }
    }
}

@Composable
private fun EmailRow(email: String) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(2_000)
            copied = false
        }
    }
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            QuietButton(
                onClick = {
                    scope.launch {
                        // The desktop clipboard can be locked by another app; a failed copy just shows no confirmation
                        try {
                            clipboard.setClipEntry(plainTextClipEntry(email))
                            copied = true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            copied = false
                        }
                    }
                },
            ) {
                Text(stringResource(if (copied) Res.string.wizard_copied else Res.string.wizard_copy_email))
            }
        }
    }
}

@Composable
private fun StatusNotice(state: ConnectWizardState) {
    when (state.status) {
        WizardStatus.Verifying -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(Res.string.wizard_checking, state.store.displayName), style = MaterialTheme.typography.bodyMedium)
        }
        WizardStatus.Waiting -> Surface(
            color = AppgotchiTheme.colors.warningContainer,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(1.dp, AppgotchiTheme.colors.pending.copy(alpha = 0.3f)),
        ) {
            Text(
                stringResource(Res.string.play_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = AppgotchiTheme.colors.onWarningContainer,
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
            QuietButton(onClick = viewModel::back, enabled = state.status != WizardStatus.Verifying) {
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
            PrimaryButton(onClick = viewModel::verify, enabled = state.canVerify) { Text(stringResource(label)) }
        } else {
            PrimaryButton(onClick = viewModel::next, enabled = state.canContinue) { Text(stringResource(Res.string.wizard_continue)) }
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
