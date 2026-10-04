package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor

actual suspend fun readClipboardText(clipboard: Clipboard): String? = runCatching {
    Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as? String
}.getOrNull()
