package com.vaazhstudios.appgotchi.screens.connect

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.awtTransferable
import androidx.compose.ui.draganddrop.dragData
import io.github.vinceglb.filekit.PlatformFile
import java.awt.datatransfer.DataFlavor
import java.io.File

actual val fileDropSupported: Boolean = true

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun FileDropZone(
    onFileDropped: (PlatformFile) -> Unit,
    modifier: Modifier,
    content: @Composable (isDragging: Boolean) -> Unit,
) {
    var isDragging by remember { mutableStateOf(false) }
    val currentOnFileDropped by rememberUpdatedState(onFileDropped)
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                isDragging = true
            }

            override fun onExited(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                isDragging = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                isDragging = false
                // The transfer can fail (InvalidDnDOperationException, IOException, UnsupportedFlavorException); a bad drop is ignored
                val file = runCatching {
                    val transferable = event.awtTransferable
                    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return@runCatching null
                    (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
                        ?.filterIsInstance<File>()
                        ?.firstOrNull()
                }.getOrNull() ?: return false
                currentOnFileDropped(PlatformFile(file))
                return true
            }
        }
    }
    Box(
        modifier = modifier.dragAndDropTarget(
            shouldStartDragAndDrop = { event -> event.dragData() is DragData.FilesList },
            target = target,
        ),
    ) {
        content(isDragging)
    }
}
