package com.djmetry.i18n

/** «Открытия»: подборка «Новые таланты» (рейтинг Talents). */
internal object TalentsDeckTranslations {

    private val ru = mapOf(
        Strings.CHIP_TALENTS to "Новые таланты",
        Strings.DE_DESC_TALENTS to "Восходящие артисты из рейтинга Talents",
    )

    private val en = mapOf(
        Strings.CHIP_TALENTS to "New talents",
        Strings.DE_DESC_TALENTS to "Rising artists from the Talents chart",
    )

    private val es = mapOf(
        Strings.CHIP_TALENTS to "Nuevos talentos",
        Strings.DE_DESC_TALENTS to "Artistas en ascenso del ranking Talents",
    )

    private val fr = mapOf(
        Strings.CHIP_TALENTS to "Nouveaux talents",
        Strings.DE_DESC_TALENTS to "Artistes montants du classement Talents",
    )

    private val de = mapOf(
        Strings.CHIP_TALENTS to "Neue Talente",
        Strings.DE_DESC_TALENTS to "Aufstrebende Künstler aus dem Talents-Ranking",
    )

    private val uk = mapOf(
        Strings.CHIP_TALENTS to "Нові таланти",
        Strings.DE_DESC_TALENTS to "Висхідні артисти з рейтингу Talents",
    )

    private val tr = mapOf(
        Strings.CHIP_TALENTS to "Yeni yetenekler",
        Strings.DE_DESC_TALENTS to "Talents sıralamasından yükselen sanatçılar",
    )

    private val ja = mapOf(
        Strings.CHIP_TALENTS to "新しい才能",
        Strings.DE_DESC_TALENTS to "Talentsランキングの注目アーティスト",
    )

    private val zhCN = mapOf(
        Strings.CHIP_TALENTS to "新晋人才",
        Strings.DE_DESC_TALENTS to "Talents 榜单上的新星",
    )

    private val ptBR = mapOf(
        Strings.CHIP_TALENTS to "Novos talentos",
        Strings.DE_DESC_TALENTS to "Artistas em ascensão do ranking Talents",
    )

    private val it = mapOf(
        Strings.CHIP_TALENTS to "Nuovi talenti",
        Strings.DE_DESC_TALENTS to "Artisti emergenti dalla classifica Talents",
    )

    private val ko = mapOf(
        Strings.CHIP_TALENTS to "새로운 재능",
        Strings.DE_DESC_TALENTS to "Talents 순위의 떠오르는 아티스트",
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
