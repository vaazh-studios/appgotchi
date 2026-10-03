package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.vinceglb.filekit.PlatformFile

/** True where files can be dragged onto the window (desktop). */
expect val fileDropSupported: Boolean

/** Accepts a dropped file on desktop; elsewhere it just renders [content]. */
@Composable
expect fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
)
