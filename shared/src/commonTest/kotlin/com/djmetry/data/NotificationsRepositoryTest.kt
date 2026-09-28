package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.data.repository.NotificationFilter
import com.djmetry.data.repository.NotificationsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class NotificationsRepositoryTest {

    private val page = """{"unread_total":3,"items":[
        {"id":"n1","type":"booking","read":false,"created_at":"2026-09-28 10:00:00","title":"Заявка №2","body":"Booking Machine","url":"/dashboard#booking-artist"},
        {"id":"n2","type":"release_radar","read":true,"title":"Happier","body":"Marshmello","image_url":"https://i.scdn.co/image/x"}],"next_cursor":null}"""

    private fun backend(unreadAfter: Int = 2) = FakeBackend(
        mapOf(
            "GET /api/me/notifications" to (HttpStatusCode.OK to page),
            "GET /api/me/notifications/unread-count" to (HttpStatusCode.OK to """{"unread_total":$unreadAfter}"""),
            "POST /api/me/notifications/read" to (HttpStatusCode.OK to """{"success":true}"""),
        )
    )

    @Test
    fun loadUsesBackendFilterAndUnreadTotal() = runTest {
        val b = backend()
        val repo = NotificationsRepository(NotificationsApi(b.client()))

        repo.load(NotificationFilter.Booking).getOrThrow()

        val req = b.request("GET", "/api/me/notifications")!!
        assertEquals("booking", req.url.parameters["type"], "фильтрует бэкенд")
        assertEquals("15", req.url.parameters["limit"])
        assertEquals(3, repo.unread.value)
        assertEquals(listOf("n1", "n2"), repo.items.value.map { it.id })
        assertEquals(NotificationFilter.Booking, repo.filter.value)
    }

    @Test
    fun allFilterSendsNoType() = runTest {
        val b = backend()
        NotificationsRepository(NotificationsApi(b.client())).load(NotificationFilter.All)
        assertNull(b.request("GET", "/api/me/notifications")!!.url.parameters["type"])
    }

    @Test
    fun markReadSendsIdAndTakesCountFromBackend() = runTest {
        val b = backend(unreadAfter = 2)
        val repo = NotificationsRepository(NotificationsApi(b.client()))
        repo.load()

        repo.markRead(repo.items.value.first()).getOrThrow()

        assertEquals("""{"ids":["n1"]}""", (b.request("POST", "/api/me/notifications/read")!!.body as TextContent).text)
        assertTrue(repo.items.value.first().read)
        assertEquals(2, repo.unread.value, "число непрочитанных — с бэкенда, не вычисляем сами")
    }

    @Test
    fun alreadyReadIsNotSentAgain() = runTest {
        val b = backend()
        val repo = NotificationsRepository(NotificationsApi(b.client()))
        repo.load()
        repo.markRead(repo.items.value[1])
        assertNull(b.request("POST", "/api/me/notifications/read"))
    }

    @Test
    fun markAllRespectsCurrentFilter() = runTest {
        val b = backend(unreadAfter = 0)
        val repo = NotificationsRepository(NotificationsApi(b.client()))
        repo.load(NotificationFilter.Releases)

        repo.markAllRead().getOrThrow()

        assertEquals("""{"all":true,"type":"release_radar"}""", (b.request("POST", "/api/me/notifications/read")!!.body as TextContent).text)
        assertTrue(repo.items.value.all { it.read })
        assertEquals(0, repo.unread.value)
    }
}
