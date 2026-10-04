package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard
import kotlin.coroutines.cancellation.CancellationException

actual suspend fun readClipboardText(clipboard: Clipboard): String? = try {
    clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
