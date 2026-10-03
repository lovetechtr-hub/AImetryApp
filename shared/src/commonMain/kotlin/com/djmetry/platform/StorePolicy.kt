package com.djmetry.platform

import com.djmetry.config.AppConfig

/**
 * Правила магазинов о подписке (модель Spotify/Netflix, App Store 3.1.3(b), Google Play Payments):
 * в приложениях из App Store и Google Play подписка не продаётся и не рекламируется — ни цен, ни «перейдите
 * на тариф», ни ссылок на оплату. Оформленная на сайте подписка в приложении работает. Десктоп не из магазина —
 * там можно.
 */
object StorePolicy {
    /** Можно ли звать к покупке тарифа (тексты «перейдите на …», ссылки на тарифы). */
    val purchaseCallsAllowed: Boolean get() = isDesktopPlatform

    /**
     * Страница кабинета сайта из приложения: метка `from=app` — сайт по ней прячет кнопки оплаты и тарифы
     * (docs/BACKEND_API.md → «Подписка и магазины»). На десктопе метка не нужна.
     */
    fun siteUrl(path: String, desktop: Boolean = isDesktopPlatform): String {
        val url = AppConfig.BASE_URL + path
        if (desktop) return url
        return url + (if ('?' in path) "&" else "?") + "from=app"
    }
}
