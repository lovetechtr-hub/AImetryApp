package com.djmetry.i18n

/** Таблица рейтинга (вариант A «Чистый список» + подиум). */
internal object RatingTranslations {

    private val ru = mapOf(
        Strings.RATING_ARTIST to "Артист",
        Strings.RATING_GENRE to "Жанр",
        Strings.RATING_OPEN_CARD to "Открыть карточку",
        Strings.RT_YEAR to "Итоги года",
        Strings.RT_ALL_COUNTRIES to "Все страны",
        Strings.RT_ALL_GENRES to "Все жанры",
        Strings.RT_YEAR_NOT_FINAL to "Итоги этого года ещё не подведены — загляните позже",
        Strings.RT_EMPTY_FILTER to "Нет артистов для этого фильтра",
        Strings.RT_RESET to "Сбросить",
        Strings.RT_YEAR_SHORT to "Итоги",
    )

    private val en = mapOf(
        Strings.RATING_ARTIST to "Artist",
        Strings.RATING_GENRE to "Genre",
        Strings.RATING_OPEN_CARD to "Open artist page",
        Strings.RT_YEAR to "Year in review",
        Strings.RT_ALL_COUNTRIES to "All countries",
        Strings.RT_ALL_GENRES to "All genres",
        Strings.RT_YEAR_NOT_FINAL to "This year's results aren't final yet — check back later",
        Strings.RT_EMPTY_FILTER to "No artists for this filter",
        Strings.RT_RESET to "Reset",
        Strings.RT_YEAR_SHORT to "Year",
    )

    private val es = mapOf(
        Strings.RATING_ARTIST to "Artista",
        Strings.RATING_GENRE to "Género",
        Strings.RATING_OPEN_CARD to "Abrir ficha",
        Strings.RT_YEAR to "Resumen del año",
        Strings.RT_ALL_COUNTRIES to "Todos los países",
        Strings.RT_ALL_GENRES to "Todos los géneros",
        Strings.RT_YEAR_NOT_FINAL to "Los resultados del año aún no son definitivos — vuelve más tarde",
        Strings.RT_EMPTY_FILTER to "No hay artistas para este filtro",
        Strings.RT_RESET to "Restablecer",
        Strings.RT_YEAR_SHORT to "Año",
    )

    private val fr = mapOf(
        Strings.RATING_ARTIST to "Artiste",
        Strings.RATING_GENRE to "Genre",
        Strings.RATING_OPEN_CARD to "Ouvrir la fiche",
        Strings.RT_YEAR to "Bilan de l'année",
        Strings.RT_ALL_COUNTRIES to "Tous les pays",
        Strings.RT_ALL_GENRES to "Tous les genres",
        Strings.RT_YEAR_NOT_FINAL to "Les résultats de l'année ne sont pas encore définitifs — reviens plus tard",
        Strings.RT_EMPTY_FILTER to "Aucun artiste pour ce filtre",
        Strings.RT_RESET to "Réinitialiser",
        Strings.RT_YEAR_SHORT to "Bilan",
    )

    private val de = mapOf(
        Strings.RATING_ARTIST to "Artist",
        Strings.RATING_GENRE to "Genre",
        Strings.RATING_OPEN_CARD to "Profil öffnen",
        Strings.RT_YEAR to "Jahresrückblick",
        Strings.RT_ALL_COUNTRIES to "Alle Länder",
        Strings.RT_ALL_GENRES to "Alle Genres",
        Strings.RT_YEAR_NOT_FINAL to "Die Jahresergebnisse stehen noch nicht fest — schau später wieder vorbei",
        Strings.RT_EMPTY_FILTER to "Keine Artists für diesen Filter",
        Strings.RT_RESET to "Zurücksetzen",
        Strings.RT_YEAR_SHORT to "Jahr",
    )

    private val uk = mapOf(
        Strings.RATING_ARTIST to "Артист",
        Strings.RATING_GENRE to "Жанр",
        Strings.RATING_OPEN_CARD to "Відкрити картку",
        Strings.RT_YEAR to "Підсумки року",
        Strings.RT_ALL_COUNTRIES to "Усі країни",
        Strings.RT_ALL_GENRES to "Усі жанри",
        Strings.RT_YEAR_NOT_FINAL to "Підсумки цього року ще не підбито — зазирніть пізніше",
        Strings.RT_EMPTY_FILTER to "Немає артистів для цього фільтра",
        Strings.RT_RESET to "Скинути",
        Strings.RT_YEAR_SHORT to "Підсумки",
    )

    private val tr = mapOf(
        Strings.RATING_ARTIST to "Sanatçı",
        Strings.RATING_GENRE to "Tür",
        Strings.RATING_OPEN_CARD to "Sayfayı aç",
        Strings.RT_YEAR to "Yılın özeti",
        Strings.RT_ALL_COUNTRIES to "Tüm ülkeler",
        Strings.RT_ALL_GENRES to "Tüm türler",
        Strings.RT_YEAR_NOT_FINAL to "Bu yılın sonuçları henüz kesinleşmedi — sonra tekrar bak",
        Strings.RT_EMPTY_FILTER to "Bu filtre için sanatçı yok",
        Strings.RT_RESET to "Sıfırla",
        Strings.RT_YEAR_SHORT to "Yıl",
    )

    private val ja = mapOf(
        Strings.RATING_ARTIST to "アーティスト",
        Strings.RATING_GENRE to "ジャンル",
        Strings.RATING_OPEN_CARD to "ページを開く",
        Strings.RT_YEAR to "年間総括",
        Strings.RT_ALL_COUNTRIES to "すべての国",
        Strings.RT_ALL_GENRES to "すべてのジャンル",
        Strings.RT_YEAR_NOT_FINAL to "今年の結果はまだ確定していません。後でご確認ください",
        Strings.RT_EMPTY_FILTER to "このフィルターに該当するアーティストはいません",
        Strings.RT_RESET to "リセット",
        Strings.RT_YEAR_SHORT to "年間",
    )

    private val zhCN = mapOf(
        Strings.RATING_ARTIST to "艺人",
        Strings.RATING_GENRE to "风格",
        Strings.RATING_OPEN_CARD to "打开艺人页",
        Strings.RT_YEAR to "年度总结",
        Strings.RT_ALL_COUNTRIES to "所有国家",
        Strings.RT_ALL_GENRES to "所有风格",
        Strings.RT_YEAR_NOT_FINAL to "今年的结果尚未最终确定，请稍后再来",
        Strings.RT_EMPTY_FILTER to "没有符合此筛选的艺人",
        Strings.RT_RESET to "重置",
        Strings.RT_YEAR_SHORT to "年度",
    )

    private val ptBR = mapOf(
        Strings.RATING_ARTIST to "Artista",
        Strings.RATING_GENRE to "Gênero",
        Strings.RATING_OPEN_CARD to "Abrir perfil",
        Strings.RT_YEAR to "Retrospectiva do ano",
        Strings.RT_ALL_COUNTRIES to "Todos os países",
        Strings.RT_ALL_GENRES to "Todos os gêneros",
        Strings.RT_YEAR_NOT_FINAL to "Os resultados do ano ainda não são finais — volte mais tarde",
        Strings.RT_EMPTY_FILTER to "Nenhum artista para este filtro",
        Strings.RT_RESET to "Redefinir",
        Strings.RT_YEAR_SHORT to "Ano",
    )

    private val it = mapOf(
        Strings.RATING_ARTIST to "Artista",
        Strings.RATING_GENRE to "Genere",
        Strings.RATING_OPEN_CARD to "Apri scheda",
        Strings.RT_YEAR to "Il meglio dell'anno",
        Strings.RT_ALL_COUNTRIES to "Tutti i paesi",
        Strings.RT_ALL_GENRES to "Tutti i generi",
        Strings.RT_YEAR_NOT_FINAL to "I risultati dell'anno non sono ancora definitivi — torna più tardi",
        Strings.RT_EMPTY_FILTER to "Nessun artista per questo filtro",
        Strings.RT_RESET to "Reimposta",
        Strings.RT_YEAR_SHORT to "Anno",
    )

    private val ko = mapOf(
        Strings.RATING_ARTIST to "아티스트",
        Strings.RATING_GENRE to "장르",
        Strings.RATING_OPEN_CARD to "아티스트 페이지 열기",
        Strings.RT_YEAR to "올해 결산",
        Strings.RT_ALL_COUNTRIES to "모든 국가",
        Strings.RT_ALL_GENRES to "모든 장르",
        Strings.RT_YEAR_NOT_FINAL to "올해 결과는 아직 확정되지 않았습니다. 나중에 다시 확인하세요",
        Strings.RT_EMPTY_FILTER to "이 필터에 맞는 아티스트가 없습니다",
        Strings.RT_RESET to "초기화",
        Strings.RT_YEAR_SHORT to "연간",
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
