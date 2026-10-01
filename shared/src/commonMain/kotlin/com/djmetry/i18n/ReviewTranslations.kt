package com.djmetry.i18n

/** Ревью: подписи кнопок, раньше нарисованных символами. */
internal object ReviewTranslations {

    private val ru = mapOf(
        Strings.BR_USE_CITY to "Указать «%s»",
        Strings.BK_ERR_NOT_IN_COMPANY to "Артист не подтверждён в этом агентстве — выберите другого",
        Strings.BK_ERR_EVENT_TYPE to "Выберите тип события",
        Strings.RT_TALENT_SCORE to "Оценка таланта",
        Strings.AUD_FAN_SCORE to "оценка фаната",
        Strings.ED_MOVE_UP to "Выше",
        Strings.ED_MOVE_DOWN to "Ниже",
        Strings.ED_REMOVE_TRACK to "Убрать трек",
    )

    private val en = mapOf(
        Strings.BR_USE_CITY to "Use “%s”",
        Strings.BK_ERR_NOT_IN_COMPANY to "This artist isn't confirmed with this agency — pick another",
        Strings.BK_ERR_EVENT_TYPE to "Choose the event type",
        Strings.RT_TALENT_SCORE to "Talent score",
        Strings.AUD_FAN_SCORE to "fan score",
        Strings.ED_MOVE_UP to "Move up",
        Strings.ED_MOVE_DOWN to "Move down",
        Strings.ED_REMOVE_TRACK to "Remove track",
    )

    private val es = mapOf(
        Strings.BR_USE_CITY to "Usar «%s»",
        Strings.BK_ERR_NOT_IN_COMPANY to "Este artista no está confirmado en esta agencia: elige otro",
        Strings.BK_ERR_EVENT_TYPE to "Elige el tipo de evento",
        Strings.RT_TALENT_SCORE to "Puntuación de talento",
        Strings.AUD_FAN_SCORE to "puntuación de fan",
        Strings.ED_MOVE_UP to "Subir",
        Strings.ED_MOVE_DOWN to "Bajar",
        Strings.ED_REMOVE_TRACK to "Quitar pista",
    )

    private val fr = mapOf(
        Strings.BR_USE_CITY to "Utiliser « %s »",
        Strings.BK_ERR_NOT_IN_COMPANY to "Cet artiste n'est pas confirmé dans cette agence — choisissez-en un autre",
        Strings.BK_ERR_EVENT_TYPE to "Choisissez le type d'événement",
        Strings.RT_TALENT_SCORE to "Score talent",
        Strings.AUD_FAN_SCORE to "score fan",
        Strings.ED_MOVE_UP to "Monter",
        Strings.ED_MOVE_DOWN to "Descendre",
        Strings.ED_REMOVE_TRACK to "Retirer le titre",
    )

    private val de = mapOf(
        Strings.BR_USE_CITY to "„%s“ verwenden",
        Strings.BK_ERR_NOT_IN_COMPANY to "Dieser Artist ist bei dieser Agentur nicht bestätigt — wähle einen anderen",
        Strings.BK_ERR_EVENT_TYPE to "Wähle die Art der Veranstaltung",
        Strings.RT_TALENT_SCORE to "Talent-Score",
        Strings.AUD_FAN_SCORE to "Fan-Score",
        Strings.ED_MOVE_UP to "Nach oben",
        Strings.ED_MOVE_DOWN to "Nach unten",
        Strings.ED_REMOVE_TRACK to "Track entfernen",
    )

    private val uk = mapOf(
        Strings.BR_USE_CITY to "Вказати «%s»",
        Strings.BK_ERR_NOT_IN_COMPANY to "Артист не підтверджений у цій агенції — оберіть іншого",
        Strings.BK_ERR_EVENT_TYPE to "Оберіть тип події",
        Strings.RT_TALENT_SCORE to "Оцінка таланту",
        Strings.AUD_FAN_SCORE to "оцінка фаната",
        Strings.ED_MOVE_UP to "Вище",
        Strings.ED_MOVE_DOWN to "Нижче",
        Strings.ED_REMOVE_TRACK to "Прибрати трек",
    )

    private val tr = mapOf(
        Strings.BR_USE_CITY to "“%s” kullan",
        Strings.BK_ERR_NOT_IN_COMPANY to "Bu sanatçı bu ajansta onaylı değil — başka birini seçin",
        Strings.BK_ERR_EVENT_TYPE to "Etkinlik türünü seçin",
        Strings.RT_TALENT_SCORE to "Yetenek puanı",
        Strings.AUD_FAN_SCORE to "hayran puanı",
        Strings.ED_MOVE_UP to "Yukarı taşı",
        Strings.ED_MOVE_DOWN to "Aşağı taşı",
        Strings.ED_REMOVE_TRACK to "Parçayı kaldır",
    )

    private val ja = mapOf(
        Strings.BR_USE_CITY to "「%s」を使う",
        Strings.BK_ERR_NOT_IN_COMPANY to "このアーティストはこのエージェンシーで未承認です。別のアーティストを選んでください",
        Strings.BK_ERR_EVENT_TYPE to "イベントの種類を選んでください",
        Strings.RT_TALENT_SCORE to "タレントスコア",
        Strings.AUD_FAN_SCORE to "ファンスコア",
        Strings.ED_MOVE_UP to "上へ",
        Strings.ED_MOVE_DOWN to "下へ",
        Strings.ED_REMOVE_TRACK to "トラックを削除",
    )

    private val zhCN = mapOf(
        Strings.BR_USE_CITY to "使用“%s”",
        Strings.BK_ERR_NOT_IN_COMPANY to "该艺人未在此经纪公司确认，请选择其他艺人",
        Strings.BK_ERR_EVENT_TYPE to "请选择活动类型",
        Strings.RT_TALENT_SCORE to "天赋分",
        Strings.AUD_FAN_SCORE to "粉丝分",
        Strings.ED_MOVE_UP to "上移",
        Strings.ED_MOVE_DOWN to "下移",
        Strings.ED_REMOVE_TRACK to "移除曲目",
    )

    private val ptBR = mapOf(
        Strings.BR_USE_CITY to "Usar “%s”",
        Strings.BK_ERR_NOT_IN_COMPANY to "Este artista não está confirmado nesta agência — escolha outro",
        Strings.BK_ERR_EVENT_TYPE to "Escolha o tipo de evento",
        Strings.RT_TALENT_SCORE to "Pontuação de talento",
        Strings.AUD_FAN_SCORE to "pontuação de fã",
        Strings.ED_MOVE_UP to "Mover para cima",
        Strings.ED_MOVE_DOWN to "Mover para baixo",
        Strings.ED_REMOVE_TRACK to "Remover faixa",
    )

    private val it = mapOf(
        Strings.BR_USE_CITY to "Usa «%s»",
        Strings.BK_ERR_NOT_IN_COMPANY to "Questo artista non è confermato in questa agenzia: scegline un altro",
        Strings.BK_ERR_EVENT_TYPE to "Scegli il tipo di evento",
        Strings.RT_TALENT_SCORE to "Punteggio talento",
        Strings.AUD_FAN_SCORE to "punteggio fan",
        Strings.ED_MOVE_UP to "Sposta su",
        Strings.ED_MOVE_DOWN to "Sposta giù",
        Strings.ED_REMOVE_TRACK to "Rimuovi traccia",
    )

    private val ko = mapOf(
        Strings.BR_USE_CITY to "“%s” 사용",
        Strings.BK_ERR_NOT_IN_COMPANY to "이 아티스트는 이 에이전시에서 승인되지 않았어요. 다른 아티스트를 선택하세요",
        Strings.BK_ERR_EVENT_TYPE to "행사 유형을 선택하세요",
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
