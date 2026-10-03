package com.vaazhstudios.appgotchi.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.app_name
import appgotchi.shared.generated.resources.today_connect_button
import appgotchi.shared.generated.resources.today_empty_body
import appgotchi.shared.generated.resources.today_empty_title
import appgotchi.shared.generated.resources.today_no_apps
import com.vaazhstudios.appgotchi.data.displayName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun TodayScreen(
    onConnect: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        TodayUiState.Loading -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { CircularProgressIndicator() }

        TodayUiState.NoStores -> Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
            Text(stringResource(Res.string.today_empty_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(Res.string.today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
        }

        is TodayUiState.Loaded -> LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    TextButton(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
                }
            }
            current.sections.forEach { section ->
                item(key = "header-${section.store}") {
                    Text(
                        section.store.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                section.errorMessage?.let { message ->
                    item(key = "error-${section.store}") {
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth())
                    }
                }
                if (section.errorMessage == null && section.apps.isEmpty()) {
                    item(key = "empty-${section.store}") {
                        Text(stringResource(Res.string.today_no_apps), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth())
                    }
                }
                items(section.apps, key = { "${it.store}-${it.id}" }) { app ->
                    ListItem(
                        headlineContent = { Text(app.name) },
                        supportingContent = { Text(app.bundleId) },
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    )
                }
            }
        }
    }
}
