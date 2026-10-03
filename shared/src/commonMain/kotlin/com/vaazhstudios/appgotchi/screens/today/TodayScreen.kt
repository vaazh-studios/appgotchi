package com.vaazhstudios.appgotchi.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import appgotchi.shared.generated.resources.Res
import appgotchi.shared.generated.resources.app_name
import appgotchi.shared.generated.resources.today_empty_body
import appgotchi.shared.generated.resources.today_empty_title
import org.jetbrains.compose.resources.stringResource

@Composable
fun TodayScreen() {
    Column(
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
    }
}
