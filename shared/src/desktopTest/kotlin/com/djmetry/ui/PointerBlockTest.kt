package com.djmetry.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.dp
import com.djmetry.EdtScene
import com.djmetry.ui.components.blockPointerBelow
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Слой поверх вкладки (карта DJ, аналитика, настройки, карточка): касание не проходит во вкладку под ним,
 * но и не отнимается у содержимого слоя. Раньше слой поглощал события — клики внутри отменялись,
 * а карта получала отмену посреди щипка (зум «глючил», колесо и +/− на десктопе не работали).
 */
class PointerBlockTest {
    @Test
    fun overlayContentGetsClicksAndTabBelowDoesNot() {
        var below = 0
        var inside = 0
        val scene = EdtScene(200, 200) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize().clickable { below++ })
                Box(Modifier.fillMaxSize().blockPointerBelow()) {
                    Box(Modifier.size(50.dp).clickable { inside++ })
                }
            }
        }
        scene.render()
        fun tap(x: Float, y: Float) {
            scene.sendPointerEvent(PointerEventType.Press, Offset(x, y)); scene.render()
            scene.sendPointerEvent(PointerEventType.Release, Offset(x, y)); scene.render()
        }
        tap(20f, 20f)   // кнопка внутри слоя (+/− карты)
        tap(150f, 150f) // пустое место слоя
        scene.close()
        assertEquals(1, inside, "клик внутри слоя срабатывает")
        assertEquals(0, below, "вкладка под слоем касаний не получает")
    }
}
