package com.djmetry.i18n

/** Таблица рейтинга (вариант A «Чистый список» + подиум). */
internal object RatingTranslations {

    private val ru = mapOf(
        Strings.RATING_ARTIST to "Артист",
        Strings.RATING_GENRE to "Жанр",
        Strings.RATING_OPEN_CARD to "Открыть карточку",
    )

    private val en = mapOf(
        Strings.RATING_ARTIST to "Artist",
        Strings.RATING_GENRE to "Genre",
        Strings.RATING_OPEN_CARD to "Open artist page",
    )

    private val es = mapOf(
        Strings.RATING_ARTIST to "Artista",
        Strings.RATING_GENRE to "Género",
        Strings.RATING_OPEN_CARD to "Abrir ficha",
    )

    private val fr = mapOf(
        Strings.RATING_ARTIST to "Artiste",
        Strings.RATING_GENRE to "Genre",
        Strings.RATING_OPEN_CARD to "Ouvrir la fiche",
    )

    private val de = mapOf(
        Strings.RATING_ARTIST to "Artist",
        Strings.RATING_GENRE to "Genre",
        Strings.RATING_OPEN_CARD to "Profil öffnen",
    )

    private val uk = mapOf(
        Strings.RATING_ARTIST to "Артист",
        Strings.RATING_GENRE to "Жанр",
        Strings.RATING_OPEN_CARD to "Відкрити картку",
    )

    private val tr = mapOf(
        Strings.RATING_ARTIST to "Sanatçı",
        Strings.RATING_GENRE to "Tür",
        Strings.RATING_OPEN_CARD to "Sayfayı aç",
    )

    private val ja = mapOf(
        Strings.RATING_ARTIST to "アーティスト",
        Strings.RATING_GENRE to "ジャンル",
        Strings.RATING_OPEN_CARD to "ページを開く",
    )

    private val zhCN = mapOf(
        Strings.RATING_ARTIST to "艺人",
        Strings.RATING_GENRE to "风格",
        Strings.RATING_OPEN_CARD to "打开艺人页",
    )

    private val ptBR = mapOf(
        Strings.RATING_ARTIST to "Artista",
        Strings.RATING_GENRE to "Gênero",
        Strings.RATING_OPEN_CARD to "Abrir perfil",
    )

    private val it = mapOf(
        Strings.RATING_ARTIST to "Artista",
        Strings.RATING_GENRE to "Genere",
        Strings.RATING_OPEN_CARD to "Apri scheda",
    )

    private val ko = mapOf(
        Strings.RATING_ARTIST to "아티스트",
        Strings.RATING_GENRE to "장르",
        Strings.RATING_OPEN_CARD to "아티스트 페이지 열기",
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
