package com.djmetry.i18n

import kotlin.test.*

/** Все тексты онбординга и входа переведены на все 12 языков, плейсхолдеры на месте. */
class TranslationsTest {

    private val requiredKeys = listOf(
        Strings.ONBOARDING_SKIP, Strings.NEXT, Strings.OB_START,
        Strings.OB1_TITLE, Strings.OB1_DESC, Strings.OB2_TITLE, Strings.OB2_DESC,
        Strings.OB3_TITLE, Strings.OB3_DESC, Strings.OB4_TITLE, Strings.OB4_DESC,
        Strings.OB_RISING, Strings.OB_NOW, Strings.OB_HOURS_AGO, Strings.OB_FIRST_TO_KNOW, Strings.OB_CONCERT_WHEN,
        Strings.OB_CLICKS, Strings.OB_PRESAVE, Strings.OB_TRACK_PLAYED,
        Strings.OB_ROLE_ARTIST, Strings.OB_ROLE_MANAGER, Strings.OB_ROLE_CUSTOMER, Strings.OB_PAID,
        Strings.OB_STATUS_ACCEPTED, Strings.OB_STATUS_ON_THE_WAY, Strings.OB_STATUS_ON_STAGE, Strings.OB_STATUS_DONE,
        Strings.OB_TOKEN_LINK,
        Strings.LOGIN_HEADLINE, Strings.LOGIN_APPLE, Strings.LOGIN_GOOGLE, Strings.LOGIN_FACEBOOK,
        Strings.LOGIN_LEGAL, Strings.LOGIN_TERMS, Strings.LOGIN_PRIVACY,
        Strings.LOGIN_ERROR_FAILED, Strings.LOGIN_ERROR_BLOCKED, Strings.LOGIN_ERROR_NETWORK,
        Strings.HOME_HELLO, Strings.HOME_TOP, Strings.HOME_LOGOUT,
        Strings.HOME_GREETING_MORNING,
        Strings.HOME_GREETING_DAY,
        Strings.HOME_GREETING_EVENING,
        Strings.HOME_GREETING_NIGHT,
        Strings.HOME_RISING,
        Strings.HOME_ALL,
        Strings.HOME_HITS,
        Strings.HOME_CONCERTS,
        Strings.HOME_OPEN,
        Strings.HOME_WEEK,
        Strings.HOME_ERROR,
        Strings.HOME_RETRY,
        Strings.HOME_TICKETS,
        Strings.TAB_HOME,
        Strings.TAB_RATING,
        Strings.TAB_SEARCH,
        Strings.TAB_RADARS,
        Strings.TAB_PROFILE,
        Strings.SEARCH_HINT,
        Strings.SEARCH_EMPTY,
        Strings.RADARS_SOON_TITLE,
        Strings.RADARS_SOON_TEXT,
        Strings.PROFILE_FOLLOWS,
        Strings.PROFILE_VOTES,
        Strings.MONTHS_SHORT,
        Strings.TAB_DISCOVER,
        Strings.TAB_BOOKING,
        Strings.SEG_DISCOVER,
        Strings.SEG_FOLLOWING,
        Strings.CHIP_RISING,
        Strings.CHIP_BREAKTHROUGH,
        Strings.CHIP_STABLE,
        Strings.STAMP_FOLLOW,
        Strings.STAMP_SKIP,
        Strings.STAMP_VOTE,
        Strings.DECK_HINT,
        Strings.DECK_EMPTY_TITLE,
        Strings.DECK_EMPTY_TEXT,
        Strings.DECK_RELOAD,
        Strings.TOAST_FOLLOWED,
        Strings.TOAST_VOTED,
        Strings.TOAST_VOTE_LIMIT,
        Strings.TOAST_FAILED,
        Strings.TOAST_NEED_LOGIN,
        Strings.FOLLOWING_EMPTY,
        Strings.UNFOLLOW,
        Strings.YOUR_VOTE,
        Strings.SCORE_CAPTION,
        Strings.BOOKING_SOON_TITLE,
        Strings.BOOKING_SOON_TEXT,
        Strings.ACTION_SKIP,
        Strings.ACTION_VOTE,
        Strings.ACTION_FOLLOW,
        Strings.YOUR_VOTES,
        Strings.NEXT_IN_DECK,
        Strings.PROFILE_TITLE,
        Strings.NOTIF_TITLE,
        Strings.NOTIF_READ_ALL,
        Strings.NOTIF_EMPTY,
        Strings.NOTIF_F_ALL,
        Strings.NOTIF_F_BOOKING,
        Strings.NOTIF_F_RELEASES,
        Strings.NOTIF_F_PRESAVE,
        Strings.NOTIF_F_CONCERTS,
        Strings.NOTIF_TYPE_BOOKING,
        Strings.TIME_NOW,
        Strings.TIME_MIN,
        Strings.TIME_HOURS,
        Strings.TIME_DAYS,
        Strings.PROFILE_PLACE,
        Strings.PROFILE_FOLLOWERS,
        Strings.PROFILE_PAGE,
        Strings.TILE_BIO,
        Strings.TILE_BIO_SUB,
        Strings.TILE_LINKS,
        Strings.TILE_LINKS_SUB,
        Strings.TILE_ANALYTICS,
        Strings.TILE_ANALYTICS_SUB,
        Strings.TILE_RADARS,
        Strings.TILE_RADARS_SUB,
        Strings.SCORE_30D,
        Strings.TRACKS,
        Strings.BOOKING_REQUESTS,
        Strings.BOOKING_ALL,
        Strings.RADAR_OPEN,
        Strings.BS_NEW,
        Strings.BS_IN_PROGRESS,
        Strings.BS_ACCEPTED,
        Strings.BS_DECLINED,
        Strings.BS_PAID,
        Strings.BS_ON_THE_WAY,
        Strings.BS_AT_HOTEL,
        Strings.BS_AT_VENUE,
        Strings.BS_FINISHED,
        Strings.BS_COMPLETED,
    )

    @Test
    fun allTwelveLocalesAreSupported() {
        assertEquals(12, Locale.values().size)
    }

    @Test
    fun everyLocaleHasEveryKey() {
        val missing = Locale.values().flatMap { locale ->
            val map = Translations.getTranslations(locale)
            requiredKeys.filter { map[it].isNullOrBlank() }.map { "${locale.code}: $it" }
        }
        assertTrue(missing.isEmpty(), "Нет перевода: $missing")
    }

    @Test
    fun placeholdersArePreserved() {
        Locale.values().forEach { locale ->
            val map = Translations.getTranslations(locale)
            val legal = map.getValue(Strings.LOGIN_LEGAL)
            assertTrue("{terms}" in legal && "{privacy}" in legal, "${locale.code}: LOGIN_LEGAL без плейсхолдеров")
            assertTrue("%s" in map.getValue(Strings.OB_TRACK_PLAYED), "${locale.code}: OB_TRACK_PLAYED без %s")
            assertTrue("%s" in map.getValue(Strings.HOME_HELLO), "${locale.code}: HOME_HELLO без %s")
            assertTrue("%s" in map.getValue(Strings.HOME_WEEK), "${locale.code}: HOME_WEEK без %s")
            assertTrue("%s" in map.getValue(Strings.TOAST_FOLLOWED), "${locale.code}: TOAST_FOLLOWED без %s")
            assertTrue("%s" in map.getValue(Strings.TOAST_VOTED), "${locale.code}: TOAST_VOTED без %s")
            listOf(Strings.TIME_MIN, Strings.TIME_HOURS, Strings.TIME_DAYS).forEach { key ->
                assertTrue("%s" in map.getValue(key), "${locale.code}: $key без %s")
            }
            assertEquals(12, map.getValue(Strings.MONTHS_SHORT).split(' ').size, "${locale.code}: MONTHS_SHORT должно быть 12 месяцев")
        }
    }

    @Test
    fun noOldBrandNameInTexts() {
        Locale.values().forEach { locale ->
            Translations.getTranslations(locale).forEach { (key, value) ->
                assertFalse(value.contains("aimetry", ignoreCase = true), "${locale.code}/$key содержит старое название")
            }
        }
    }
}
