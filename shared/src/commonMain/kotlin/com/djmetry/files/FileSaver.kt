package com.djmetry.files

/** Сохранение файла пользователем (экспорт CSV). false — отменил или не удалось. */
fun interface FileSaver {
    suspend fun save(name: String, mime: String, bytes: ByteArray): Boolean
}

/** Android — системный диалог «Сохранить как», iOS — меню «Поделиться» (там «Сохранить в Файлы»), десктоп — диалог файла. */
expect fun platformFileSaver(): FileSaver

/**
 * Имя файла для записи на диск: без путей («../», «/»), управляющих символов и с разумной длиной.
 * Имя приходит с сервера (райдер, пресс-кит) — оно не должно выводить запись из временной папки.
 */
fun safeFileName(name: String, fallback: String = "file"): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
        .filter { it >= ' ' && it !in "<>:\"|?*" }
        .trim().trimStart('.')
        .take(120)
    return base.ifBlank { fallback }
}
