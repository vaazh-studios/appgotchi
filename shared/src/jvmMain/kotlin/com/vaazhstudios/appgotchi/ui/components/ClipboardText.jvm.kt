package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import kotlin.coroutines.cancellation.CancellationException

actual suspend fun readClipboardText(clipboard: Clipboard): String? = try {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
