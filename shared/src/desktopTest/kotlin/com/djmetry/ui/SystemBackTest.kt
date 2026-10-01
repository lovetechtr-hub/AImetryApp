package com.djmetry.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import com.djmetry.AppContainer
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.api.models.MeResponse
import com.djmetry.data.local.NavMemory
import com.djmetry.ui.i18n.I18nProvider
import com.djmetry.ui.screens.MainShell
import com.djmetry.ui.screens.MainTab
import com.djmetry.ui.theme.DJMetryTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Системное «Назад» (Android — кнопка/жест, iOS — свайп от края): закрывает верхний слой, а не приложение.
 * Вместо окна — свой диспетчер событий навигации; где мы — по [NavMemory] (вкладка + карточка артиста).
 */
class SystemBackTest {
    /** Сцену трогаем только с EDT, как в приложении: часть эффектов возобновляется там и иначе меряет макет параллельно с тестом. */
    private fun <T> edt(block: () -> T): T { var r: Result<T>? = null; javax.swing.SwingUtilities.invokeAndWait { r = runCatching(block) }; return r!!.getOrThrow() }

    private fun shell(tab: MainTab, artistId: String?, steps: Int): List<String?> {
        val storage = FakeSessionStorage("t").apply { saveLocale("ru") }
        NavMemory.attach(storage)
        val container = AppContainer(storage, FakeBackend(emptyMap()).engine)
        val dispatcher = NavigationEventDispatcher()
        val input = DirectNavigationEventInput().also { dispatcher.addInput(it) }
        val owner = object : NavigationEventDispatcherOwner { override val navigationEventDispatcher = dispatcher }
        val scene = edt { ImageComposeScene(390, 844, Density(1f)) {
            CompositionLocalProvider(LocalAppContainer provides container, LocalInspectionMode provides true, LocalNavigationEventDispatcherOwner provides owner) {
                DJMetryTheme { I18nProvider(localizationManager = container.localization) { MainShell(MeResponse(isAuthed = true), onLoggedOut = {}, initialTab = tab, initialArtistId = artistId) } }
            }
        } }
        var t = 0L
        fun frames() = repeat(10) { edt { scene.render(t) }; t += 50_000_000L; Thread.sleep(20) }
        frames()
        val seen = mutableListOf(storage.nav?.substringAfter('|'))
        repeat(steps) {
            edt { input.backCompleted() }
            frames()
            seen += storage.nav?.substringAfter('|')
        }
        edt { scene.close() }
        return seen
    }

    @Test
    fun backClosesArtistCardThenReturnsToDiscover() {
        // Карточка поверх «Рейтинга» → «Назад» → «Рейтинг» → «Назад» → «Открытия»
        assertEquals(listOf("Rating|a1", "Rating|", "Discover|"), shell(MainTab.Rating, "a1", steps = 2))
    }

    @Test
    fun backOnDiscoverRootIsLeftToSystem() {
        // На корне «Открытий» обработчиков нет — система сворачивает приложение, состояние не меняется
        assertEquals(listOf("Discover|", "Discover|"), shell(MainTab.Discover, null, steps = 1))
    }
}
