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
class NotificationsRepository(private val api: NotificationsApi) {
    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _items = MutableStateFlow<List<AppNotification>>(emptyList())
    val items: StateFlow<List<AppNotification>> = _items.asStateFlow()

    private val _filter = MutableStateFlow(NotificationFilter.All)
    val filter: StateFlow<NotificationFilter> = _filter.asStateFlow()

    suspend fun refreshUnread(): Result<Int> = api.unreadCount().map { it.unread_total }.onSuccess { _unread.value = it }

    suspend fun load(filter: NotificationFilter = _filter.value): Result<List<AppNotification>> {
        _filter.value = filter
        return api.list(type = filter.apiType).map { page ->
            _unread.value = page.unread_total
            _items.value = page.items
            page.items
        }
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
