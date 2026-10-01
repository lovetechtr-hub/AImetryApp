package com.djmetry.i18n

/** История поиска во всех поисках приложения. */
internal object SearchHistoryTranslations {

    private val ru = mapOf(
        Strings.SEARCH_POPULAR to "Популярно сейчас",
        Strings.SH_RECENT to "Недавние запросы",
        Strings.SH_OPENED to "Вы открывали",
        Strings.SH_CLEAR to "Очистить",
        Strings.SH_INSERT to "Подставить в поиск",
        Strings.SH_REMOVE to "Удалить из истории",
        Strings.SH_RECENT_PICKS to "Недавние",
    )

    private val en = mapOf(
        Strings.SEARCH_POPULAR to "Popular now",
        Strings.SH_RECENT to "Recent searches",
        Strings.SH_OPENED to "Recently viewed",
        Strings.SH_CLEAR to "Clear",
        Strings.SH_INSERT to "Fill in search",
        Strings.SH_REMOVE to "Remove from history",
        Strings.SH_RECENT_PICKS to "Recent",
    )

    private val es = mapOf(
        Strings.SEARCH_POPULAR to "Popular ahora",
        Strings.SH_RECENT to "Búsquedas recientes",
        Strings.SH_OPENED to "Vistos recientemente",
        Strings.SH_CLEAR to "Borrar",
        Strings.SH_INSERT to "Usar en la búsqueda",
        Strings.SH_REMOVE to "Quitar del historial",
        Strings.SH_RECENT_PICKS to "Recientes",
    )

    private val fr = mapOf(
        Strings.SEARCH_POPULAR to "Populaire en ce moment",
        Strings.SH_RECENT to "Recherches récentes",
        Strings.SH_OPENED to "Consultés récemment",
        Strings.SH_CLEAR to "Effacer",
        Strings.SH_INSERT to "Remplir la recherche",
        Strings.SH_REMOVE to "Retirer de l'historique",
        Strings.SH_RECENT_PICKS to "Récents",
    )

    private val de = mapOf(
        Strings.SEARCH_POPULAR to "Gerade beliebt",
        Strings.SH_RECENT to "Letzte Suchen",
        Strings.SH_OPENED to "Zuletzt angesehen",
        Strings.SH_CLEAR to "Löschen",
        Strings.SH_INSERT to "In Suche übernehmen",
        Strings.SH_REMOVE to "Aus Verlauf entfernen",
        Strings.SH_RECENT_PICKS to "Zuletzt",
    )

    private val uk = mapOf(
        Strings.SEARCH_POPULAR to "Популярне зараз",
        Strings.SH_RECENT to "Нещодавні запити",
        Strings.SH_OPENED to "Ви відкривали",
        Strings.SH_CLEAR to "Очистити",
        Strings.SH_INSERT to "Підставити в пошук",
        Strings.SH_REMOVE to "Видалити з історії",
        Strings.SH_RECENT_PICKS to "Нещодавні",
    )

    private val tr = mapOf(
        Strings.SEARCH_POPULAR to "Şu an popüler",
        Strings.SH_RECENT to "Son aramalar",
        Strings.SH_OPENED to "Son görüntülenenler",
        Strings.SH_CLEAR to "Temizle",
        Strings.SH_INSERT to "Aramaya ekle",
        Strings.SH_REMOVE to "Geçmişten kaldır",
        Strings.SH_RECENT_PICKS to "Son seçilenler",
    )

    private val ja = mapOf(
        Strings.SEARCH_POPULAR to "今人気",
        Strings.SH_RECENT to "最近の検索",
        Strings.SH_OPENED to "最近見た",
        Strings.SH_CLEAR to "消去",
        Strings.SH_INSERT to "検索欄に入れる",
        Strings.SH_REMOVE to "履歴から削除",
        Strings.SH_RECENT_PICKS to "最近",
    )

    private val zhCN = mapOf(
        Strings.SEARCH_POPULAR to "当前热门",
        Strings.SH_RECENT to "最近搜索",
        Strings.SH_OPENED to "最近查看",
        Strings.SH_CLEAR to "清除",
        Strings.SH_INSERT to "填入搜索",
        Strings.SH_REMOVE to "从历史中删除",
        Strings.SH_RECENT_PICKS to "最近",
    )

    private val ptBR = mapOf(
        Strings.SEARCH_POPULAR to "Popular agora",
        Strings.SH_RECENT to "Pesquisas recentes",
        Strings.SH_OPENED to "Vistos recentemente",
        Strings.SH_CLEAR to "Limpar",
        Strings.SH_INSERT to "Preencher a busca",
        Strings.SH_REMOVE to "Remover do histórico",
        Strings.SH_RECENT_PICKS to "Recentes",
    )

    private val it = mapOf(
        Strings.SEARCH_POPULAR to "Popolari ora",
        Strings.SH_RECENT to "Ricerche recenti",
        Strings.SH_OPENED to "Visti di recente",
        Strings.SH_CLEAR to "Cancella",
        Strings.SH_INSERT to "Inserisci nella ricerca",
        Strings.SH_REMOVE to "Rimuovi dalla cronologia",
        Strings.SH_RECENT_PICKS to "Recenti",
    )

    private val ko = mapOf(
        Strings.SEARCH_POPULAR to "지금 인기",
        Strings.SH_RECENT to "최근 검색",
        Strings.SH_OPENED to "최근 본 아티스트",
        Strings.SH_CLEAR to "지우기",
        Strings.SH_INSERT to "검색창에 넣기",
        Strings.SH_REMOVE to "기록에서 삭제",
        Strings.SH_RECENT_PICKS to "최근",
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
