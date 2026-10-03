package com.vaazhstudios.appgotchi

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.vaazhstudios.appgotchi.di.initKoin

fun main() {
    initKoin()

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Appgotchi",
        ) {
            App()
        }
    }
}
