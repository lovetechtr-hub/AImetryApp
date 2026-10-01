package com.djmetry.i18n

/** Букинг: строки нового контракта бэкенда. */
internal object BookingContractTranslations {

    private val ru = mapOf(
        Strings.BC_VS_PREV to "%s к прошлому месяцу",
        Strings.BC_REVOKE to "Отозвать",
        Strings.BC_REVOKE_Q to "Отозвать приглашение для %s?",
        Strings.BK_ERR_BEFORE_DATE to "«Выступил» можно отметить не раньше дня события",
    )

    private val en = mapOf(
        Strings.BC_VS_PREV to "%s vs last month",
        Strings.BC_REVOKE to "Revoke",
        Strings.BC_REVOKE_Q to "Revoke the invitation for %s?",
        Strings.BK_ERR_BEFORE_DATE to "You can mark «performed» only on or after the event day",
    )

    private val es = mapOf(
        Strings.BC_VS_PREV to "%s vs. el mes pasado",
        Strings.BC_REVOKE to "Revocar",
        Strings.BC_REVOKE_Q to "¿Revocar la invitación de %s?",
        Strings.BK_ERR_BEFORE_DATE to "Solo puedes marcar «actuó» desde el día del evento",
    )

    private val fr = mapOf(
        Strings.BC_VS_PREV to "%s vs le mois dernier",
        Strings.BC_REVOKE to "Révoquer",
        Strings.BC_REVOKE_Q to "Révoquer l'invitation de %s ?",
        Strings.BK_ERR_BEFORE_DATE to "«A joué» se marque à partir du jour de l'événement",
    )

    private val de = mapOf(
        Strings.BC_VS_PREV to "%s ggü. Vormonat",
        Strings.BC_REVOKE to "Zurückziehen",
        Strings.BC_REVOKE_Q to "Einladung für %s zurückziehen?",
        Strings.BK_ERR_BEFORE_DATE to "«Aufgetreten» erst ab dem Veranstaltungstag",
    )

    private val uk = mapOf(
        Strings.BC_VS_PREV to "%s до минулого місяця",
        Strings.BC_REVOKE to "Відкликати",
        Strings.BC_REVOKE_Q to "Відкликати запрошення для %s?",
        Strings.BK_ERR_BEFORE_DATE to "«Виступив» можна позначити не раніше дня події",
    )

    private val tr = mapOf(
        Strings.BC_VS_PREV to "geçen aya göre %s",
        Strings.BC_REVOKE to "Geri al",
        Strings.BC_REVOKE_Q to "%s için davet geri alınsın mı?",
        Strings.BK_ERR_BEFORE_DATE to "«Sahneye çıktı» ancak etkinlik gününden itibaren işaretlenebilir",
    )

    private val ja = mapOf(
        Strings.BC_VS_PREV to "先月比 %s",
        Strings.BC_REVOKE to "取り消す",
        Strings.BC_REVOKE_Q to "%s への招待を取り消しますか？",
        Strings.BK_ERR_BEFORE_DATE to "「出演済み」はイベント当日以降に設定できます",
    )

    private val zhCN = mapOf(
        Strings.BC_VS_PREV to "较上月 %s",
        Strings.BC_REVOKE to "撤回",
        Strings.BC_REVOKE_Q to "撤回对 %s 的邀请？",
        Strings.BK_ERR_BEFORE_DATE to "「已演出」只能在活动当天或之后标记",
    )

    private val ptBR = mapOf(
        Strings.BC_VS_PREV to "%s vs. mês passado",
        Strings.BC_REVOKE to "Revogar",
        Strings.BC_REVOKE_Q to "Revogar o convite de %s?",
        Strings.BK_ERR_BEFORE_DATE to "«Se apresentou» só a partir do dia do evento",
    )

    private val it = mapOf(
        Strings.BC_VS_PREV to "%s rispetto al mese scorso",
        Strings.BC_REVOKE to "Revoca",
        Strings.BC_REVOKE_Q to "Revocare l'invito per %s?",
        Strings.BK_ERR_BEFORE_DATE to "«Esibito» si segna dal giorno dell'evento",
    )

    private val ko = mapOf(
        Strings.BC_VS_PREV to "지난달 대비 %s",
        Strings.BC_REVOKE to "취소",
        Strings.BC_REVOKE_Q to "%s 초대를 취소할까요?",
        Strings.BK_ERR_BEFORE_DATE to "「공연 완료」는 행사 당일부터 표시할 수 있어요",
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
