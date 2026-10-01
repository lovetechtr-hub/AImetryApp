package com.djmetry.ui

import androidx.compose.runtime.*
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModel
import com.djmetry.AppContainer
import com.djmetry.EdtScene
import com.djmetry.FakeBackend
import com.djmetry.FakeSessionStorage
import com.djmetry.LocalAppContainer
import com.djmetry.ui.search.appViewModel
import kotlin.test.*

/**
 * ViewModel экрана живёт дольше самого экрана: вкладку убрали из композиции и вернули (смена вкладки,
 * поворот на Android, ресайз окна) — тот же экземпляр с тем же состоянием. Выход из аккаунта — новый.
 */
class ViewModelScopeTest {
    private inline fun <reified VM : ViewModel> survives(): Pair<VM, VM> {
        val c = AppContainer(FakeSessionStorage(), FakeBackend(emptyMap()).engine)
        val seen = mutableListOf<VM>()
        var show by mutableStateOf(true)
        val scene = EdtScene(100, 100, Density(1f)) {
            CompositionLocalProvider(LocalAppContainer provides c) {
                if (show) { val vm = appViewModel<VM>(); SideEffect { if (seen.lastOrNull() !== vm) seen += vm } }
            }
        }
        scene.render(0)
        com.djmetry.onEdt { show = false }; scene.render(16_000_000)
        com.djmetry.onEdt { show = true }; scene.render(32_000_000)
        val first = seen.first(); val back = seen.last()
        kotlinx.coroutines.runBlocking { c.userScoped().forEach { it.clearUserData() } }
        com.djmetry.onEdt { show = false }; scene.render(48_000_000)
        com.djmetry.onEdt { show = true }; scene.render(64_000_000)
        val afterLogout = seen.last()
        scene.close()
        assertSame(first, back, "${VM::class.simpleName}: вернулись на экран — тот же ViewModel")
        assertNotSame(first, afterLogout, "${VM::class.simpleName}: после выхода из аккаунта — новый")
        return first to back
    }

    @Test fun search() { survives<com.djmetry.ui.search.SearchViewModel>() }
    @Test fun discover() { survives<com.djmetry.ui.screens.DiscoverViewModel>() }
    @Test fun radar() { survives<com.djmetry.ui.radar.RadarViewModel>() }
    @Test fun booking() { survives<com.djmetry.ui.booking.BookingViewModel>() }

    @Test
    fun radarKeepsFiltersAcrossTabSwitch() {
        val (vm, back) = survives<com.djmetry.ui.radar.RadarViewModel>()
        vm.query = "fisher"; vm.showConcerts = true
        assertEquals("fisher", back.query)
        assertTrue(back.showConcerts)
    }

    @Test fun rating() { survives<com.djmetry.ui.rating.RatingViewModel>() }
    @Test fun artist() { survives<com.djmetry.ui.artist.ArtistViewModel>() }
    @Test fun releases() { survives<com.djmetry.ui.radar.ArtistReleasesViewModel>() }
    @Test fun profile() { survives<com.djmetry.ui.profile.ProfileViewModel>() }
    @Test fun djMap() { survives<com.djmetry.ui.djmap.DjMapViewModel>() }
    @Test fun analytics() { survives<com.djmetry.ui.analytics.AnalyticsViewModel>() }
    @Test fun audience() { survives<com.djmetry.ui.analytics.AudienceViewModel>() }
    @Test fun screenState() { survives<com.djmetry.ui.search.ScreenStateViewModel>() }

    @Test
    fun anotherArtistStartsClean() {
        // Раньше форма заявки артиста A оставалась поверх карточки B
        val vm = com.djmetry.ui.artist.ArtistViewModel()
        vm.bind("A"); vm.booking = "A"; vm.toast = "x"
        vm.bind("A")
        assertEquals("A", vm.booking, "тот же артист — состояние на месте")
        vm.bind("B")
        assertNull(vm.booking); assertNull(vm.toast); assertNull(vm.card)
    }

    @Test
    fun screenStateResetsWhenSavedValueChanges() {
        val vm = com.djmetry.ui.search.ScreenStateViewModel()
        val draft = vm.state("city", listOf("Berlin")) { "Berlin" }
        draft.value = "Berl"
        assertEquals("Berl", vm.state("city", listOf("Berlin")) { "Berlin" }.value, "несохранённая правка живёт")
        assertEquals("Munich", vm.state("city", listOf("Munich")) { "Munich" }.value, "сервер сохранил новое — черновик заново")
    }
}
