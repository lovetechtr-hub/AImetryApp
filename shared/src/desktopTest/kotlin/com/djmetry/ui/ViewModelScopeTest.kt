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
}
