package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard
import kotlin.coroutines.cancellation.CancellationException
import platform.UIKit.UIPasteboard

actual suspend fun readClipboardText(clipboard: Clipboard): String? = try {
    UIPasteboard.generalPasteboard.string
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
