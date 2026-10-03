package com.vaazhstudios.appgotchi

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vaazhstudios.appgotchi.screens.connect.ConnectWizardScreen
import com.vaazhstudios.appgotchi.screens.today.TodayScreen
import kotlinx.serialization.Serializable

@Serializable
object TodayDestination

@Serializable
object ConnectDestination

@Composable
fun App() {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            NavHost(navController = navController, startDestination = TodayDestination) {
                composable<TodayDestination> {
                    TodayScreen(onConnect = { navController.navigate(ConnectDestination) })
                }
                composable<ConnectDestination> {
                    // Typed pop is a no-op if Connect is already gone, so "Back" racing a
                    // finished verification can't pop the Today screen too
                    ConnectWizardScreen(
                        onConnected = { navController.popBackStack<ConnectDestination>(inclusive = true) },
                        onBack = { navController.popBackStack<ConnectDestination>(inclusive = true) },
                    )
                }
            }
        }
    }
}
