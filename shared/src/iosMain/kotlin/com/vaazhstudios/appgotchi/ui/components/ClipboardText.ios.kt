package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard
import platform.UIKit.UIPasteboard

actual suspend fun readClipboardText(clipboard: Clipboard): String? = runCatching {
    UIPasteboard.generalPasteboard.string
}.getOrNull()
