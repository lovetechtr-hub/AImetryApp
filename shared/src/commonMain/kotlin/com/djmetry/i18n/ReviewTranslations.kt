package com.djmetry.i18n

/** Ревью: подписи кнопок, раньше нарисованных символами. */
internal object ReviewTranslations {

    private val ru = mapOf(
        Strings.ED_MOVE_UP to "Выше",
        Strings.ED_MOVE_DOWN to "Ниже",
        Strings.ED_REMOVE_TRACK to "Убрать трек",
    )

    private val en = mapOf(
        Strings.ED_MOVE_UP to "Move up",
        Strings.ED_MOVE_DOWN to "Move down",
        Strings.ED_REMOVE_TRACK to "Remove track",
    )

    private val es = mapOf(
        Strings.ED_MOVE_UP to "Subir",
        Strings.ED_MOVE_DOWN to "Bajar",
        Strings.ED_REMOVE_TRACK to "Quitar pista",
    )

    private val fr = mapOf(
        Strings.ED_MOVE_UP to "Monter",
        Strings.ED_MOVE_DOWN to "Descendre",
        Strings.ED_REMOVE_TRACK to "Retirer le titre",
    )

    private val de = mapOf(
        Strings.ED_MOVE_UP to "Nach oben",
        Strings.ED_MOVE_DOWN to "Nach unten",
        Strings.ED_REMOVE_TRACK to "Track entfernen",
    )

    private val uk = mapOf(
        Strings.ED_MOVE_UP to "Вище",
        Strings.ED_MOVE_DOWN to "Нижче",
        Strings.ED_REMOVE_TRACK to "Прибрати трек",
    )

    private val tr = mapOf(
        Strings.ED_MOVE_UP to "Yukarı taşı",
        Strings.ED_MOVE_DOWN to "Aşağı taşı",
        Strings.ED_REMOVE_TRACK to "Parçayı kaldır",
    )

    private val ja = mapOf(
        Strings.ED_MOVE_UP to "上へ",
        Strings.ED_MOVE_DOWN to "下へ",
        Strings.ED_REMOVE_TRACK to "トラックを削除",
    )

    private val zhCN = mapOf(
        Strings.ED_MOVE_UP to "上移",
        Strings.ED_MOVE_DOWN to "下移",
        Strings.ED_REMOVE_TRACK to "移除曲目",
    )

    private val ptBR = mapOf(
        Strings.ED_MOVE_UP to "Mover para cima",
        Strings.ED_MOVE_DOWN to "Mover para baixo",
        Strings.ED_REMOVE_TRACK to "Remover faixa",
    )

    private val it = mapOf(
        Strings.ED_MOVE_UP to "Sposta su",
        Strings.ED_MOVE_DOWN to "Sposta giù",
        Strings.ED_REMOVE_TRACK to "Rimuovi traccia",
    )

    private val ko = mapOf(
        Strings.ED_MOVE_UP to "위로",
        Strings.ED_MOVE_DOWN to "아래로",
        Strings.ED_REMOVE_TRACK to "트랙 삭제",
    )

    fun forLocale(locale: Locale): Map<String, String> = when (locale) {
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
}
