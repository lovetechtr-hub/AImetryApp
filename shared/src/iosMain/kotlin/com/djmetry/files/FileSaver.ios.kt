package com.djmetry.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CompletableDeferred
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.popoverPresentationController

/** Файл кладём во временную папку и открываем меню «Поделиться» — там «Сохранить в Файлы», почта, мессенджеры. */
@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
actual fun platformFileSaver(): FileSaver = FileSaver { name, _, bytes ->
    val url = NSURL.fileURLWithPath(NSTemporaryDirectory() + name)
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    if (!data.writeToURL(url, atomically = true)) return@FileSaver false
    val root = UIApplication.sharedApplication.keyWindow?.rootViewController ?: return@FileSaver false
    var top = root
    while (top.presentedViewController != null) top = top.presentedViewController!!
    val done = CompletableDeferred<Boolean>()
    val sheet = UIActivityViewController(activityItems = listOf(url), applicationActivities = null)
    sheet.completionWithItemsHandler = { _, completed, _, _ -> done.complete(completed) }
    // iPad: меню — поповер, ему нужен источник
    sheet.popoverPresentationController?.sourceView = top.view
    top.presentViewController(sheet, animated = true, completion = null)
    done.await()
}
