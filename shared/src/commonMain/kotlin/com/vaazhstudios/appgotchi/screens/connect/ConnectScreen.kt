package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.connect_apple_help
import appgotchi.shared.generated.resources.connect_apple_issuer
import appgotchi.shared.generated.resources.connect_apple_key_id
import appgotchi.shared.generated.resources.connect_apple_private_key
import appgotchi.shared.generated.resources.connect_apple_title
import appgotchi.shared.generated.resources.connect_back
import appgotchi.shared.generated.resources.connect_button
import appgotchi.shared.generated.resources.connect_play_help
import appgotchi.shared.generated.resources.connect_play_json
import appgotchi.shared.generated.resources.connect_play_title
import appgotchi.shared.generated.resources.connect_privacy_note
import appgotchi.shared.generated.resources.connect_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ConnectScreen(
    onConnected: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConnectViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.connected) {
        if (state.connected) onConnected()
    }

    var issuerId by remember { mutableStateOf("") }
    var keyId by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var serviceAccountJson by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text(stringResource(Res.string.connect_back)) }
            Text(stringResource(Res.string.connect_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(Res.string.connect_privacy_note), style = MaterialTheme.typography.bodyMedium)

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.connect_apple_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.connect_apple_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(issuerId, { issuerId = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.connect_apple_issuer)) }, singleLine = true)
                    OutlinedTextField(keyId, { keyId = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(Res.string.connect_apple_key_id)) }, singleLine = true)
                    OutlinedTextField(
                        privateKey, { privateKey = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.connect_apple_private_key)) },
                        visualTransformation = PasswordVisualTransformation(),
                        minLines = 3,
                    )
                    state.appleError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { viewModel.connectAppStore(issuerId, keyId, privateKey) }, enabled = !state.busy) {
                        Text(stringResource(Res.string.connect_button))
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(Res.string.connect_play_title), style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(Res.string.connect_play_help), style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        serviceAccountJson, { serviceAccountJson = it }, Modifier.fillMaxWidth(),
                        label = { Text(stringResource(Res.string.connect_play_json)) },
                        visualTransformation = PasswordVisualTransformation(),
                        minLines = 3,
                    )
                    state.playError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(onClick = { viewModel.connectPlay(serviceAccountJson) }, enabled = !state.busy) {
                        Text(stringResource(Res.string.connect_button))
                    }
                }
            }
        }
    }
}
