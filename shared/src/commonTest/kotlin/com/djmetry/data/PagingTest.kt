package com.djmetry.data

import com.djmetry.api.createApiClient
import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.data.repository.NotificationsRepository
import com.djmetry.ui.components.PagedList
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.*

/** Длинные списки: уведомления листаются по курсору, «все люди/лиды» — по страницам, без дублей и с ошибкой отдельно от «пусто». */
class PagingTest {
    @Test
    fun notificationsLoadMoreByCursor() = runTest {
        var calls = 0
        val engine = MockEngine { req ->
            calls++
            val body = when (req.url.parameters["cursor"]) {
                null -> """{"unread_total":3,"items":[{"id":"1","type":"booking","title":"a"},{"id":"2","type":"booking","title":"b"}],"next_cursor":"c2"}"""
                "c2" -> """{"unread_total":3,"items":[{"id":"2","type":"booking","title":"b"},{"id":"3","type":"concert","title":"c"}],"next_cursor":null}"""
                else -> error("unexpected")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val repo = NotificationsRepository(NotificationsApi(createApiClient(tokenProvider = { null }, languageProvider = { null }, engine = engine)))
        repo.load().getOrThrow()
        assertEquals("c2", repo.hasMore.value)
        repo.loadMore().getOrThrow()
        assertEquals(listOf("1", "2", "3"), repo.items.value.map { it.id }, "без дубля «2»")
        assertNull(repo.hasMore.value)
        val before = calls
        repo.loadMore().getOrThrow()
        assertEquals(before, calls, "дальше нет — запроса нет")
    }

    @Test
    fun pagedListLoadsNextOnceAndKeepsErrorApart() = runTest {
        var fail = true
        val pages = mutableListOf<Int>()
        val list = PagedList(listOf("a", "b"), total = 4) { p -> pages += p; if (fail) Result.failure(RuntimeException()) else Result.success(listOf("c", "d")) }
        list.next()
        assertTrue(list.failed); assertEquals(2, list.items.size)
        fail = false
        list.next()
        assertFalse(list.failed)
        assertEquals(listOf("a", "b", "c", "d"), list.items.toList())
        assertEquals(listOf(2, 2), pages, "после ошибки — та же страница")
        list.next()
        assertEquals(listOf(2, 2), pages, "всё загружено — больше не просим")
    }
}
