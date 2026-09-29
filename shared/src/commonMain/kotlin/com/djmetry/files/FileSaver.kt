package com.djmetry.files

/** Сохранение файла пользователем (экспорт CSV). false — отменил или не удалось. */
fun interface FileSaver {
    suspend fun save(name: String, mime: String, bytes: ByteArray): Boolean
}

/** Android — системный диалог «Сохранить как», iOS — меню «Поделиться» (там «Сохранить в Файлы»), десктоп — диалог файла. */
expect fun platformFileSaver(): FileSaver
