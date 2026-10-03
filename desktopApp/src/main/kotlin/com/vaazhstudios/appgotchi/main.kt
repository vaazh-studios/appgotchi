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
