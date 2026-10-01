package com.djmetry.data

import com.djmetry.FakeBackend
import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.data.repository.NotificationFilter
import com.djmetry.data.repository.NotificationsRepository
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import io.ktor.client.engine.mock.respond
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
    fun loadUsesBackendFilterButBellKeepsTotalUnread() = runTest {
        val b = backend(unreadAfter = 7)
        val repo = NotificationsRepository(NotificationsApi(b.client()))

        repo.load(NotificationFilter.Booking).getOrThrow()

        val req = b.request("GET", "/api/me/notifications")!!
        assertEquals("booking", req.url.parameters["type"], "фильтрует бэкенд")
        assertEquals("15", req.url.parameters["limit"])
        assertEquals(7, repo.unread.value, "с фильтром unread_total страницы — только по типу; колокольчику нужен общий из unread-count")
        assertEquals(listOf("n1", "n2"), repo.items.value.map { it.id })
        assertEquals(NotificationFilter.Booking, repo.filter.value)
    }

    @Test
    fun allFilterTakesUnreadFromPageWithoutExtraRequest() = runTest {
        val b = backend(unreadAfter = 7)
        val repo = NotificationsRepository(NotificationsApi(b.client()))
        repo.load(NotificationFilter.All).getOrThrow()
        assertEquals(3, repo.unread.value)
        assertNull(b.request("GET", "/api/me/notifications/unread-count"), "без фильтра счёт уже в странице")
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

    @Test
    fun lateAnswerOfPreviousChipDoesNotOverwriteList() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val engine = io.ktor.client.engine.mock.MockEngine { req ->
            val slow = req.url.parameters["type"] == null && req.url.encodedPath.endsWith("/notifications")
            if (slow) gate.await()
            val body = if (slow) page else """{"unread_total":1,"items":[{"id":"b1","type":"booking","read":false,"title":"B"}],"next_cursor":null}"""
            respond(if (req.url.encodedPath.endsWith("unread-count")) """{"unread_total":1}""" else body, HttpStatusCode.OK,
                io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        }
        val r = NotificationsRepository(com.djmetry.api.endpoints.NotificationsApi(com.djmetry.api.createApiClient(tokenProvider = { "t" }, engine = engine)))
        val all = async { r.load(NotificationFilter.All) }
        kotlinx.coroutines.yield()
        r.load(NotificationFilter.entries.first { it.apiType == "booking" })
        gate.complete(Unit); all.await()
        assertEquals(listOf("b1"), r.items.value.map { it.id }, "под чипом «Букинг» — только его ответ")
    }

    @Test
    fun cancelledLoadMoreDoesNotBlockPaging() = runTest {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val first = """{"unread_total":1,"items":[{"id":"n1","type":"booking","read":false,"title":"A"}],"next_cursor":"c2"}"""
        val second = """{"unread_total":1,"items":[{"id":"n2","type":"booking","read":true,"title":"B"}],"next_cursor":null}"""
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val engine = io.ktor.client.engine.mock.MockEngine { req ->
            val cursor = req.url.parameters["cursor"]
            if (cursor != null && !started.isCompleted) { started.complete(Unit); gate.await() }
            respond(if (cursor == null) first else second, HttpStatusCode.OK, io.ktor.http.headersOf(io.ktor.http.HttpHeaders.ContentType, "application/json"))
        }
        val r = NotificationsRepository(com.djmetry.api.endpoints.NotificationsApi(com.djmetry.api.createApiClient(tokenProvider = { "t" }, engine = engine)))
        r.load()
        val job = launch { r.loadMore() }
        started.await() // запрос догрузки ушёл и висит
        job.cancel(); job.join()
        r.loadMore().getOrThrow()
        assertEquals(listOf("n1", "n2"), r.items.value.map { it.id }, "раньше флаг загрузки оставался true навсегда")
    }

    @Test
    fun chipOnlySelectsFilter() {
        val r = NotificationsRepository(com.djmetry.api.endpoints.NotificationsApi(backend().client()))
        r.select(NotificationFilter.entries.last())
        assertEquals(NotificationFilter.entries.last(), r.filter.value)
    }
}
