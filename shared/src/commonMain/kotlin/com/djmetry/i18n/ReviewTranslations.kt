package com.djmetry.i18n

/** Ревью: подписи кнопок, раньше нарисованных символами. */
internal object ReviewTranslations {

    private val ru = mapOf(
        Strings.RT_TALENT_SCORE to "Оценка таланта",
        Strings.AUD_FAN_SCORE to "оценка фаната",
        Strings.ED_MOVE_UP to "Выше",
        Strings.ED_MOVE_DOWN to "Ниже",
        Strings.ED_REMOVE_TRACK to "Убрать трек",
    )

    private val en = mapOf(
        Strings.RT_TALENT_SCORE to "Talent score",
        Strings.AUD_FAN_SCORE to "fan score",
        Strings.ED_MOVE_UP to "Move up",
        Strings.ED_MOVE_DOWN to "Move down",
        Strings.ED_REMOVE_TRACK to "Remove track",
    )

    private val es = mapOf(
        Strings.RT_TALENT_SCORE to "Puntuación de talento",
        Strings.AUD_FAN_SCORE to "puntuación de fan",
        Strings.ED_MOVE_UP to "Subir",
        Strings.ED_MOVE_DOWN to "Bajar",
        Strings.ED_REMOVE_TRACK to "Quitar pista",
    )

    private val fr = mapOf(
        Strings.RT_TALENT_SCORE to "Score talent",
        Strings.AUD_FAN_SCORE to "score fan",
        Strings.ED_MOVE_UP to "Monter",
        Strings.ED_MOVE_DOWN to "Descendre",
        Strings.ED_REMOVE_TRACK to "Retirer le titre",
    )

    private val de = mapOf(
        Strings.RT_TALENT_SCORE to "Talent-Score",
        Strings.AUD_FAN_SCORE to "Fan-Score",
        Strings.ED_MOVE_UP to "Nach oben",
        Strings.ED_MOVE_DOWN to "Nach unten",
        Strings.ED_REMOVE_TRACK to "Track entfernen",
    )

    private val uk = mapOf(
        Strings.RT_TALENT_SCORE to "Оцінка таланту",
        Strings.AUD_FAN_SCORE to "оцінка фаната",
        Strings.ED_MOVE_UP to "Вище",
        Strings.ED_MOVE_DOWN to "Нижче",
        Strings.ED_REMOVE_TRACK to "Прибрати трек",
    )

    private val tr = mapOf(
        Strings.RT_TALENT_SCORE to "Yetenek puanı",
        Strings.AUD_FAN_SCORE to "hayran puanı",
        Strings.ED_MOVE_UP to "Yukarı taşı",
        Strings.ED_MOVE_DOWN to "Aşağı taşı",
        Strings.ED_REMOVE_TRACK to "Parçayı kaldır",
    )

    private val ja = mapOf(
        Strings.RT_TALENT_SCORE to "タレントスコア",
        Strings.AUD_FAN_SCORE to "ファンスコア",
        Strings.ED_MOVE_UP to "上へ",
        Strings.ED_MOVE_DOWN to "下へ",
        Strings.ED_REMOVE_TRACK to "トラックを削除",
    )

    private val zhCN = mapOf(
        Strings.RT_TALENT_SCORE to "天赋分",
        Strings.AUD_FAN_SCORE to "粉丝分",
        Strings.ED_MOVE_UP to "上移",
        Strings.ED_MOVE_DOWN to "下移",
        Strings.ED_REMOVE_TRACK to "移除曲目",
    )

    private val ptBR = mapOf(
        Strings.RT_TALENT_SCORE to "Pontuação de talento",
        Strings.AUD_FAN_SCORE to "pontuação de fã",
        Strings.ED_MOVE_UP to "Mover para cima",
        Strings.ED_MOVE_DOWN to "Mover para baixo",
        Strings.ED_REMOVE_TRACK to "Remover faixa",
    )

    private val it = mapOf(
        Strings.RT_TALENT_SCORE to "Punteggio talento",
        Strings.AUD_FAN_SCORE to "punteggio fan",
        Strings.ED_MOVE_UP to "Sposta su",
        Strings.ED_MOVE_DOWN to "Sposta giù",
        Strings.ED_REMOVE_TRACK to "Rimuovi traccia",
    )

    private val ko = mapOf(
        Strings.RT_TALENT_SCORE to "탤런트 점수",
        Strings.AUD_FAN_SCORE to "팬 점수",
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
