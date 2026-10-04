package com.vaazhstudios.appgotchi.ui.components

import androidx.compose.ui.platform.Clipboard

/** Plain text currently on the clipboard, or null. Never throws. */
expect suspend fun readClipboardText(clipboard: Clipboard): String?
