package com.djmetry.ui

import com.djmetry.config.AppConfig
import com.djmetry.i18n.Locale
import com.djmetry.i18n.Strings
import com.djmetry.i18n.Translations
import com.djmetry.platform.StorePolicy
import kotlin.test.*

/** Модель Spotify/Netflix: в приложениях из магазинов — без призыва к оплате и с меткой для сайта. */
class StorePolicyTest {
    @Test
    fun siteLinksFromStoreAppsAreMarked() {
        assertEquals("${AppConfig.BASE_URL}/dashboard/music/page?from=app", StorePolicy.siteUrl("/dashboard/music/page", desktop = false))
        assertEquals("${AppConfig.BASE_URL}/x?a=1&from=app", StorePolicy.siteUrl("/x?a=1", desktop = false))
        assertEquals("${AppConfig.BASE_URL}/dashboard/music/page", StorePolicy.siteUrl("/dashboard/music/page", desktop = true))
    }

    @Test
    fun storeTextHasNoCallToUpgrade() {
        for (l in Locale.entries) {
            val t = Translations.getTranslations(l)[Strings.AUD_EMAILS_HIDDEN_STORE]
            assertNotNull(t, "перевод есть: $l")
            assertFalse(t.contains("Release"), "без названия платного тарифа: $l")
        }
    }
}
