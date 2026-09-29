package com.djmetry.files

import com.djmetry.api.models.PickedFile

/** Выбор PDF-файла системным диалогом платформы (райдер, пресс-кит). null — пользователь отменил. */
fun interface PdfPicker {
    suspend fun pick(): PickedFile?
}

/** Реализация для платформы: Android — выбор документа, iOS — UIDocumentPicker, десктоп — диалог файла. */
expect fun platformPdfPicker(): PdfPicker
