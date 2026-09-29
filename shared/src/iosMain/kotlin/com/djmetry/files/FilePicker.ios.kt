package com.djmetry.files

import com.djmetry.api.models.PickedFile
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.darwin.NSObject
import platform.posix.memcpy

/** Делегат выбора: отдаёт первый выбранный URL или null при отмене. Держим ссылку, пока диалог открыт. */
private class PickerDelegate(private val done: CompletableDeferred<NSURL?>) : NSObject(), UIDocumentPickerDelegateProtocol {
    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        done.complete(didPickDocumentsAtURLs.firstOrNull() as? NSURL)
    }
    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) { done.complete(null) }
}

private var activeDelegate: PickerDelegate? = null

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray = ByteArray(length.toInt()).also { bytes ->
    if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
}

/** UIDocumentPicker с фильтром PDF; файл копируется в песочницу приложения (asCopy). */
@OptIn(ExperimentalForeignApi::class)
actual fun platformPdfPicker(): PdfPicker = PdfPicker {
    val done = CompletableDeferred<NSURL?>()
    val delegate = PickerDelegate(done).also { activeDelegate = it }
    val picker = UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypePDF), asCopy = true)
    picker.delegate = delegate
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@PdfPicker null
    var top = root
    while (top.presentedViewController != null) top = top.presentedViewController!!
    top.presentViewController(picker, animated = true, completion = null)
    val url = done.await()
    activeDelegate = null
    url ?: return@PdfPicker null
    val data = NSData.dataWithContentsOfURL(url) ?: return@PdfPicker null
    PickedFile(url.path?.substringAfterLast("/") ?: "file.pdf", "application/pdf", data.toByteArray())
}
