package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

actual val fileDropSupported: Boolean = false

@Composable
actual fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    Box(modifier) { content(false) }
}
