package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard

actual suspend fun readClipboardText(clipboard: Clipboard): String? = runCatching {
    clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
}.getOrNull()
