package com.djmetry.files

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import com.djmetry.api.models.PickedFile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/**
 * Мост к системному выбору документа. MainActivity регистрирует `GetContent` и передаёт запуск в [launcher],
 * а результат — в [onResult] (как мост для входа — `AndroidOAuthBridge`).
 */
object AndroidFilePickerBridge {
    private var activityRef: WeakReference<Activity>? = null
    private var pending: CompletableDeferred<Uri?>? = null
    var launcher: ((String) -> Unit)? = null

    fun attach(activity: Activity) { activityRef = WeakReference(activity) }

    fun onResult(uri: Uri?) { pending?.complete(uri); pending = null }

    suspend fun pick(mime: String): PickedFile? {
        val launch = launcher ?: return null
        val activity = activityRef?.get() ?: return null
        val deferred = CompletableDeferred<Uri?>().also { pending?.complete(null); pending = it }
        launch(mime)
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = activity.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "file.pdf"
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@withContext null
            PickedFile(name, resolver.getType(uri), bytes)
        }
    }
}

actual fun platformPdfPicker(): PdfPicker = PdfPicker { AndroidFilePickerBridge.pick("application/pdf") }
