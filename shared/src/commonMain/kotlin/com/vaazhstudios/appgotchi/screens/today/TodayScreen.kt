package com.vaazhstudios.appgotchi.screens.today

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.today_apps
import appgotchi.shared.generated.resources.today_connect_button
import appgotchi.shared.generated.resources.today_empty_body
import appgotchi.shared.generated.resources.today_empty_title
import appgotchi.shared.generated.resources.today_no_apps
import appgotchi.shared.generated.resources.today_refresh
import appgotchi.shared.generated.resources.today_stores
import appgotchi.shared.generated.resources.today_summary
import appgotchi.shared.generated.resources.today_title
import com.vaazhstudios.appgotchi.core.data.StoreApp
import com.vaazhstudios.appgotchi.data.StoreSection
import com.vaazhstudios.appgotchi.data.displayName
import com.vaazhstudios.appgotchi.ui.components.AppCard
import com.vaazhstudios.appgotchi.ui.components.BrandMark
import com.vaazhstudios.appgotchi.ui.components.PrimaryButton
import com.vaazhstudios.appgotchi.ui.components.SecondaryButton
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private val ContentWidth = 720.dp

@Composable
fun TodayScreen(
    onConnect: () -> Unit,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (val current = state) {
        TodayUiState.Loading -> Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TodayUiState.NoStores -> EmptyState(onConnect)
        is TodayUiState.Loaded -> LoadedState(current.sections, onConnect, onRefresh = viewModel::refresh)
    }
}

@Composable
private fun EmptyState(onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.today_empty_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(Res.string.today_empty_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        PrimaryButton(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
    }
}

@Composable
private fun LoadedState(sections: List<StoreSection>, onConnect: () -> Unit, onRefresh: () -> Unit) {
    val appCount = sections.sumOf { it.apps.size }
    LazyColumn(
        modifier = Modifier.fillMaxSize().safeDrawingPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(Modifier.widthIn(max = ContentWidth).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                BrandMark()
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).widthIn(min = 200.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            stringResource(Res.string.today_title),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.semantics { heading() },
                        )
                        Text(
                            stringResource(
                                Res.string.today_summary,
                                pluralStringResource(Res.plurals.today_apps, appCount, appCount),
                                pluralStringResource(Res.plurals.today_stores, sections.size, sections.size),
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(onClick = onRefresh) { Text(stringResource(Res.string.today_refresh)) }
                        PrimaryButton(onClick = onConnect) { Text(stringResource(Res.string.today_connect_button)) }
                    }
                }
            }
        }
        sections.forEach { section ->
            item(key = "header-${section.store}") {
                Row(
                    modifier = Modifier.widthIn(max = ContentWidth).fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        section.store.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                    Text("${section.apps.size}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            section.errorMessage?.let { message ->
                item(key = "error-${section.store}") {
                    AppCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            if (section.errorMessage == null && section.apps.isEmpty()) {
                item(key = "empty-${section.store}") {
                    AppCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) {
                        Text(stringResource(Res.string.today_no_apps), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(section.apps, key = { "${it.store}-${it.id}" }) { app ->
                AppCard(Modifier.widthIn(max = ContentWidth).fillMaxWidth()) { AppRow(app) }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AppRow(app: StoreApp) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(36.dp).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(app.name.take(1).uppercase(), style = MaterialTheme.typography.titleSmall)
        }
        Column(Modifier.weight(1f)) {
            Text(app.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.bundleId,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
