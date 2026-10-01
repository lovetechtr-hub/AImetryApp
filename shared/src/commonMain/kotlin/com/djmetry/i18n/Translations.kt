package com.djmetry.i18n

object Translations {
    val en = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "Save",
        Strings.DELETE to "Delete",
        Strings.NEXT to "Next",
        Strings.DISPLAY_NAME to "Display name",
        Strings.ONBOARDING_SKIP to "Skip",
    )
    
    val es = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "Guardar",
        Strings.DELETE to "Eliminar",
        Strings.NEXT to "Siguiente",
        Strings.DISPLAY_NAME to "Nombre",
    )
    
    val fr = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "Enregistrer",
        Strings.DELETE to "Supprimer",
        Strings.NEXT to "Suivant",
        Strings.DISPLAY_NAME to "Nom d'affichage",
    )
    
    val de = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "Speichern",
        Strings.DELETE to "Löschen",
        Strings.NEXT to "Weiter",
        Strings.DISPLAY_NAME to "Anzeigename",
    )
    
    val ru = mapOf(
        Strings.OK to "ОК",
        Strings.SAVE to "Сохранить",
        Strings.DELETE to "Удалить",
        Strings.NEXT to "Далее",
        Strings.DISPLAY_NAME to "Имя",
        Strings.ONBOARDING_SKIP to "Пропустить",
    )
    
    val uk = mapOf(
        Strings.OK to "ОК",
        Strings.SAVE to "Зберегти",
        Strings.DELETE to "Видалити",
        Strings.NEXT to "Далі",
        Strings.DISPLAY_NAME to "Ім'я",
    )
    
    val tr = mapOf(
        Strings.OK to "Tamam",
        Strings.SAVE to "Kaydet",
        Strings.DELETE to "Sil",
        Strings.NEXT to "İleri",
        Strings.DISPLAY_NAME to "Görünen ad",
    )
    
    val ja = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "保存",
        Strings.DELETE to "削除",
        Strings.NEXT to "次へ",
        Strings.DISPLAY_NAME to "表示名",
    )
    
    val zhCN = mapOf(
        Strings.OK to "确定",
        Strings.SAVE to "保存",
        Strings.DELETE to "删除",
        Strings.NEXT to "下一步",
        Strings.DISPLAY_NAME to "显示名称",
    )
    
    val ptBR = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "Salvar",
        Strings.DELETE to "Excluir",
        Strings.NEXT to "Próximo",
        Strings.DISPLAY_NAME to "Nome de exibição",
    )
    
    val it = mapOf(
        Strings.OK to "OK",
        Strings.SAVE to "Salva",
        Strings.DELETE to "Elimina",
        Strings.NEXT to "Avanti",
        Strings.DISPLAY_NAME to "Nome visualizzato",
    )
    
    val ko = mapOf(
        Strings.OK to "확인",
        Strings.SAVE to "저장",
        Strings.DELETE to "삭제",
        Strings.NEXT to "다음",
        Strings.DISPLAY_NAME to "표시 이름",
    )
    
    fun getTranslations(locale: Locale): Map<String, String> {
        val base = when (locale) {
            Locale.ENGLISH -> en
            Locale.SPANISH -> es
            Locale.FRENCH -> fr
            Locale.GERMAN -> de
            Locale.RUSSIAN -> ru
            Locale.UKRAINIAN -> uk
            Locale.TURKISH -> tr
            Locale.JAPANESE -> ja
            Locale.CHINESE_SIMPLIFIED -> zhCN
            Locale.PORTUGUESE_BR -> ptBR
            Locale.ITALIAN -> it
            Locale.KOREAN -> ko
        }
        return base + OnboardingTranslations.forLocale(locale) + LoginTranslations.forLocale(locale) + HomeTranslations.forLocale(locale) + DiscoverTranslations.forLocale(locale) + ProfileTranslations.forLocale(locale) + ArtistTranslations.forLocale(locale) + RatingTranslations.forLocale(locale) + SettingsTranslations.forLocale(locale) + ArtistEditorTranslations.forLocale(locale) + AnalyticsTranslations.forLocale(locale) + DjMapTranslations.forLocale(locale) + RadarTranslations.forLocale(locale) + BookingTranslations.forLocale(locale) + BookingCabinetTranslations.forLocale(locale) + RadarCityTranslations.forLocale(locale) + DeckEndTranslations.forLocale(locale) + TalentsDeckTranslations.forLocale(locale) + AudienceFilterTranslations.forLocale(locale) + BookingContractTranslations.forLocale(locale) + ReviewTranslations.forLocale(locale)
    }
}

