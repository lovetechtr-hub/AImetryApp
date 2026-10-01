package com.djmetry.i18n

/** Понятные ошибки действий (лимит подписок, легенды, устаревший статус заявки) и подтверждение закрытия заявки. */
internal object ActionErrorTranslations {

    private val ru = mapOf(
        Strings.BR_DISCARD_ASK to "Закрыть заявку? Введённое не сохранится",
        Strings.BR_DISCARD to "Закрыть",
        Strings.TOAST_FOLLOW_LIMIT to "Подписок уже %1\$s — это максимум. Отпишитесь от кого-нибудь в «Подписках»",
        Strings.TOAST_FOLLOW_LEGEND to "На легенд подписаться нельзя — они вне рейтинга",
        Strings.BK_ERR_STALE to "Заявка уже изменилась — показываем актуальную",
    )
    private val en = mapOf(
        Strings.BR_DISCARD_ASK to "Close the request? What you entered will be lost",
        Strings.BR_DISCARD to "Close",
        Strings.TOAST_FOLLOW_LIMIT to "You already follow %1\$s artists — that's the maximum. Unfollow someone in Following",
        Strings.TOAST_FOLLOW_LEGEND to "Legends can't be followed — they're outside the ranking",
        Strings.BK_ERR_STALE to "This request has already changed — showing the latest version",
    )
    private val es = mapOf(
        Strings.BR_DISCARD_ASK to "¿Cerrar la solicitud? Se perderá lo que escribiste",
        Strings.BR_DISCARD to "Cerrar",
        Strings.TOAST_FOLLOW_LIMIT to "Ya sigues a %1\$s artistas, es el máximo. Deja de seguir a alguien en Siguiendo",
        Strings.TOAST_FOLLOW_LEGEND to "No se puede seguir a las leyendas: están fuera del ranking",
        Strings.BK_ERR_STALE to "La solicitud ya cambió: mostramos la versión actual",
    )
    private val fr = mapOf(
        Strings.BR_DISCARD_ASK to "Fermer la demande ? Votre saisie sera perdue",
        Strings.BR_DISCARD to "Fermer",
        Strings.TOAST_FOLLOW_LIMIT to "Vous suivez déjà %1\$s artistes, c'est le maximum. Ne suivez plus quelqu'un dans Abonnements",
        Strings.TOAST_FOLLOW_LEGEND to "Impossible de suivre les légendes : elles sont hors classement",
        Strings.BK_ERR_STALE to "La demande a déjà changé : voici la version à jour",
    )
    private val de = mapOf(
        Strings.BR_DISCARD_ASK to "Anfrage schließen? Deine Eingaben gehen verloren",
        Strings.BR_DISCARD to "Schließen",
        Strings.TOAST_FOLLOW_LIMIT to "Du folgst bereits %1\$s Artists – das ist das Maximum. Entfolge jemandem unter Gefolgt",
        Strings.TOAST_FOLLOW_LEGEND to "Legenden kann man nicht folgen – sie sind außerhalb des Rankings",
        Strings.BK_ERR_STALE to "Die Anfrage hat sich bereits geändert – aktuelle Version wird angezeigt",
    )
    private val uk = mapOf(
        Strings.BR_DISCARD_ASK to "Закрити заявку? Введене не збережеться",
        Strings.BR_DISCARD to "Закрити",
        Strings.TOAST_FOLLOW_LIMIT to "Підписок уже %1\$s — це максимум. Відпишіться від когось у «Підписках»",
        Strings.TOAST_FOLLOW_LEGEND to "На легенд підписатися не можна — вони поза рейтингом",
        Strings.BK_ERR_STALE to "Заявка вже змінилася — показуємо актуальну",
    )
    private val tr = mapOf(
        Strings.BR_DISCARD_ASK to "Talep kapatılsın mı? Girdiklerin kaybolacak",
        Strings.BR_DISCARD to "Kapat",
        Strings.TOAST_FOLLOW_LIMIT to "Zaten %1\$s sanatçıyı takip ediyorsun, bu en fazlası. Takip Edilenler'den birini bırak",
        Strings.TOAST_FOLLOW_LEGEND to "Efsaneler takip edilemez, sıralamanın dışındalar",
        Strings.BK_ERR_STALE to "Talep zaten değişti, güncel hali gösteriliyor",
    )
    private val ja = mapOf(
        Strings.BR_DISCARD_ASK to "リクエストを閉じますか？入力内容は保存されません",
        Strings.BR_DISCARD to "閉じる",
        Strings.TOAST_FOLLOW_LIMIT to "フォローは既に%1\$s組で上限です。フォロー中から誰かを外してください",
        Strings.TOAST_FOLLOW_LEGEND to "レジェンドはランキング対象外のためフォローできません",
        Strings.BK_ERR_STALE to "リクエストは既に更新されています。最新の内容を表示します",
    )
    private val zhCN = mapOf(
        Strings.BR_DISCARD_ASK to "关闭请求？已填写的内容将不会保存",
        Strings.BR_DISCARD to "关闭",
        Strings.TOAST_FOLLOW_LIMIT to "你已关注 %1\$s 位艺人，已达上限。请在“关注”中取消关注一位",
        Strings.TOAST_FOLLOW_LEGEND to "传奇艺人不在排名内，无法关注",
        Strings.BK_ERR_STALE to "该请求已更新，正在显示最新内容",
    )
    private val ptBR = mapOf(
        Strings.BR_DISCARD_ASK to "Fechar o pedido? O que você preencheu será perdido",
        Strings.BR_DISCARD to "Fechar",
        Strings.TOAST_FOLLOW_LIMIT to "Você já segue %1\$s artistas, é o máximo. Deixe de seguir alguém em Seguindo",
        Strings.TOAST_FOLLOW_LEGEND to "Não é possível seguir lendas: elas ficam fora do ranking",
        Strings.BK_ERR_STALE to "O pedido já mudou: mostrando a versão atual",
    )
    private val it = mapOf(
        Strings.BR_DISCARD_ASK to "Chiudere la richiesta? Quanto inserito andrà perso",
        Strings.BR_DISCARD to "Chiudi",
        Strings.TOAST_FOLLOW_LIMIT to "Segui già %1\$s artisti, è il massimo. Smetti di seguire qualcuno in Seguiti",
        Strings.TOAST_FOLLOW_LEGEND to "Le leggende non si possono seguire: sono fuori classifica",
        Strings.BK_ERR_STALE to "La richiesta è già cambiata: mostriamo la versione aggiornata",
    )
    private val ko = mapOf(
        Strings.BR_DISCARD_ASK to "요청을 닫을까요? 입력한 내용은 저장되지 않습니다",
        Strings.BR_DISCARD to "닫기",
        Strings.TOAST_FOLLOW_LIMIT to "이미 %1\$s명을 팔로우하고 있어 최대치입니다. 팔로잉에서 한 명을 해제하세요",
        Strings.TOAST_FOLLOW_LEGEND to "레전드는 랭킹 밖이라 팔로우할 수 없습니다",
        Strings.BK_ERR_STALE to "요청이 이미 변경되었습니다. 최신 내용을 표시합니다",
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
