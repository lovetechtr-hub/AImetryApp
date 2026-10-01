package com.djmetry.data.repository

import com.djmetry.api.endpoints.NotificationsApi
import com.djmetry.api.models.AppNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Чипы фильтра колокольчика → тип бэкенда (фильтрует бэкенд, клиент только передаёт `type`). */
enum class NotificationFilter(val apiType: String?) {
    All(null),
    Booking("booking"),
    Releases("release_radar"),
    PreSave("pre_save"),
    Concerts("concert"),
}

/**
 * Колокольчик: число непрочитанных, список по фильтру, «прочитано».
 * Число непрочитанных всегда берём с бэкенда; флаг `read` у элемента меняем сразу, чтобы UI не ждал сеть.
 */
class NotificationsRepository(private val api: NotificationsApi) : UserScoped {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    /** Курсор следующей страницы (`next_cursor`); null — дальше нет. */
    private val _next = MutableStateFlow<String?>(null)
    val hasMore: StateFlow<String?> = _next.asStateFlow()
    private var loadingMore = false

    private val _filter = MutableStateFlow(NotificationFilter.All)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    override suspend fun clearUserData() {
        _unread.value = 0
        _items.value = emptyList()
        _next.value = null
        _filter.value = NotificationFilter.All
    }

    suspend fun refreshUnread(): Result<Int> = api.unreadCount().map { it.unread_total }.onSuccess { _unread.value = it }

    suspend fun load(filter: NotificationFilter = _filter.value): Result<List<AppNotification>> {
        _filter.value = filter
        val result = api.list(type = filter.apiType).map { page ->
            // С фильтром бэкенд считает непрочитанные только этого типа — колокольчику нужен общий счёт
            if (filter.apiType == null) _unread.value = page.unread_total
            _items.value = page.items
            _next.value = page.next_cursor
            page.items
        }
        if (filter.apiType != null) refreshUnread()
        return result
    }

    /** Следующая страница (раньше было видно только первые 15). Одна загрузка за раз. */
    suspend fun loadMore(): Result<Unit> {
        val cursor = _next.value ?: return Result.success(Unit)
        if (loadingMore) return Result.success(Unit)
        loadingMore = true
        val type = _filter.value.apiType
        return api.list(type = type, cursor = cursor).map { page ->
            if (_filter.value.apiType == type && _next.value == cursor) {
                _items.update { old -> old + page.items.filter { n -> old.none { it.id == n.id } } }
                _next.value = page.next_cursor
            }
            Unit
        }.also { loadingMore = false }
    }

    suspend fun markRead(notification: AppNotification): Result<Unit> {
        if (notification.read) return Result.success(Unit)
        _items.update { list -> list.map { if (it.id == notification.id) it.copy(read = true) else it } }
        return api.markRead(listOf(notification.id)).map { refreshUnread(); Unit }
    }

    suspend fun markAllRead(): Result<Unit> {
        val type = _filter.value.apiType
        _items.update { list -> list.map { it.copy(read = true) } }
        return api.markAllRead(type).map { refreshUnread(); Unit }
    }
}
