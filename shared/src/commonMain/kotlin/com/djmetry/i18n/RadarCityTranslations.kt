package com.djmetry.i18n

/** Радар: карточка «укажите страну и город» для «Рядом со мной». */
internal object RadarCityTranslations {

    private val ru = mapOf(
        Strings.RADAR_CITY_MSG to "Укажите страну и город — покажем концерты рядом с вами",
        Strings.RADAR_CITY_BTN to "Указать страну и город",
    )

    private val en = mapOf(
        Strings.RADAR_CITY_MSG to "Add your country and city to see concerts near you",
        Strings.RADAR_CITY_BTN to "Set country and city",
    )

    private val es = mapOf(
        Strings.RADAR_CITY_MSG to "Indica tu país y ciudad para ver conciertos cerca de ti",
        Strings.RADAR_CITY_BTN to "Indicar país y ciudad",
    )

    private val fr = mapOf(
        Strings.RADAR_CITY_MSG to "Indiquez votre pays et votre ville pour voir les concerts près de chez vous",
        Strings.RADAR_CITY_BTN to "Choisir pays et ville",
    )

    private val de = mapOf(
        Strings.RADAR_CITY_MSG to "Gib Land und Stadt an, um Konzerte in deiner Nähe zu sehen",
        Strings.RADAR_CITY_BTN to "Land und Stadt angeben",
    )

    private val uk = mapOf(
        Strings.RADAR_CITY_MSG to "Вкажіть країну та місто — покажемо концерти поруч із вами",
        Strings.RADAR_CITY_BTN to "Вказати країну та місто",
    )

    private val tr = mapOf(
        Strings.RADAR_CITY_MSG to "Yakınındaki konserleri görmek için ülke ve şehrini ekle",
        Strings.RADAR_CITY_BTN to "Ülke ve şehir seç",
    )

    private val ja = mapOf(
        Strings.RADAR_CITY_MSG to "国と都市を設定すると、近くのコンサートを表示します",
        Strings.RADAR_CITY_BTN to "国と都市を設定",
    )

    private val zhCN = mapOf(
        Strings.RADAR_CITY_MSG to "设置国家和城市，查看你附近的演出",
        Strings.RADAR_CITY_BTN to "设置国家和城市",
    )

    private val ptBR = mapOf(
        Strings.RADAR_CITY_MSG to "Informe seu país e cidade para ver shows perto de você",
        Strings.RADAR_CITY_BTN to "Definir país e cidade",
    )

    private val it = mapOf(
        Strings.RADAR_CITY_MSG to "Indica paese e città per vedere i concerti vicino a te",
        Strings.RADAR_CITY_BTN to "Imposta paese e città",
    )

    private val ko = mapOf(
        Strings.RADAR_CITY_MSG to "국가와 도시를 설정하면 근처 공연을 보여 드려요",
        Strings.RADAR_CITY_BTN to "국가 및 도시 설정",
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
