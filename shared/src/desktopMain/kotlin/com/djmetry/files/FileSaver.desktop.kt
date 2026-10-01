package com.djmetry.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Системный диалог сохранения (AWT — на macOS нативный Finder-диалог). */
actual fun platformFileSaver(): FileSaver = FileSaver { name, _, bytes ->
    val target = withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, name, FileDialog.SAVE).apply { file = safeFileName(name); isVisible = true }
        val dir = dialog.directory; val file = dialog.file
        if (dir != null && file != null) File(dir, file) else null
    } ?: return@FileSaver false
    withContext(Dispatchers.IO) { runCatching { target.writeBytes(bytes) }.isSuccess }
}
