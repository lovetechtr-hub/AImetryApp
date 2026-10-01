package com.djmetry.files

import android.app.Activity
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

/** Мост к системному «Сохранить как» (CreateDocument): MainActivity регистрирует контракт и передаёт запуск в [launcher]. */
object AndroidFileSaverBridge {
    private var activityRef: WeakReference<Activity>? = null
    private var pending: CompletableDeferred<Uri?>? = null
    /** Системный «Сохранить как»: имя и тип файла (PDF — как PDF, CSV — как CSV). */
    var launcher: ((name: String, mime: String) -> Unit)? = null

    fun attach(activity: Activity) { activityRef = WeakReference(activity) }

    fun onResult(uri: Uri?) { pending?.complete(uri); pending = null }

    suspend fun save(name: String, mime: String, bytes: ByteArray): Boolean {
        val launch = launcher ?: return false
        val activity = activityRef?.get() ?: return false
        val deferred = CompletableDeferred<Uri?>().also { pending?.complete(null); pending = it }
        if (runCatching { launch(name, mime) }.isFailure) { pending = null; return false }
        val uri = deferred.await() ?: return false
        return withContext(Dispatchers.IO) {
            runCatching { activity.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } != null }.getOrDefault(false)
        }
    }
}

actual fun platformFileSaver(): FileSaver = FileSaver { name, mime, bytes -> AndroidFileSaverBridge.save(name, mime, bytes) }
