package com.djmetry.files

import com.djmetry.api.models.PickedFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Системный диалог выбора файла (AWT — на macOS это нативный Finder-диалог), только .pdf. */
actual fun platformPdfPicker(): PdfPicker = PdfPicker {
    val path = withContext(Dispatchers.Main) {
        val dialog = FileDialog(null as Frame?, "PDF", FileDialog.LOAD).apply {
            setFilenameFilter { _, name -> name.endsWith(".pdf", ignoreCase = true) }
            file = "*.pdf"
            isVisible = true
        }
        val dir = dialog.directory; val name = dialog.file
        if (dir != null && name != null) File(dir, name) else null
    } ?: return@PdfPicker null
    withContext(Dispatchers.IO) { PickedFile(path.name, if (path.extension.equals("pdf", true)) "application/pdf" else null, path.readBytes()) }
}
