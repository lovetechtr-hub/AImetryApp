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
        // Нет приложения для выбора файла — не оставляем «висящий» запрос
        if (runCatching { launch(mime) }.isFailure) { pending = null; return null }
        val uri = deferred.await() ?: return null
        return withContext(Dispatchers.IO) {
            val resolver = activity.contentResolver
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "file.pdf"
            // Не больше лимита + 1 байт: огромный файл не читаем целиком в память (OOM), проверка размера всё равно его отклонит
            val bytes = resolver.openInputStream(uri)?.use { readAtMost(it, com.djmetry.data.repository.MAX_PDF_BYTES + 1) } ?: return@withContext null
            PickedFile(name, resolver.getType(uri), bytes)
        }
    }
}

actual fun platformPdfPicker(): PdfPicker = PdfPicker { AndroidFilePickerBridge.pick("application/pdf") }

/** Прочитать поток, но не больше [limit] байт. */
internal fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (out.size() < limit) {
        val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
