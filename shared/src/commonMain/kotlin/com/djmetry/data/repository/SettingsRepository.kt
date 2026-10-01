package com.djmetry.data.repository

import com.djmetry.api.endpoints.EmailToggle
import com.djmetry.api.endpoints.SettingsApi
import com.djmetry.api.models.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Типы push в порядке показа (как на бэке, `PUSH_NOTIFICATION_TYPES`). */
val PUSH_TYPES = listOf(
    "release_radar", "pre_save", "concert", "smart_link", "booking", "track_support",
    "integration_health", "verification", "digest_top_clubs", "digest_top_djs", "digest_ranking", "digest_talents",
)

/** Типы колокольчика в приложении. */
val IN_APP_TYPES = listOf("release_radar", "pre_save", "booking", "concert")

const val MAX_GENRES = 5

/** Жанр как на сайте: trim + схлопывание пробелов. */
fun normalizeGenre(g: String): String = g.trim().replace(Regex("""\s+"""), " ")

/**
 * Добавить жанр по правилам спеки: из списка, без дублей (без учёта регистра), не больше [MAX_GENRES],
 * новый — в конец (порядок = приоритет). Нельзя добавить — список без изменений.
 */
fun addGenre(current: List<String>, genre: String): List<String> {
    val g = normalizeGenre(genre)
    if (g.isEmpty() || current.size >= MAX_GENRES || current.any { it.equals(g, ignoreCase = true) }) return current
    return current + g
}

/** Проверенный артист: влияет на гейтинг (дайджесты рейтинга и Talents — только артистам, жанры — только фанатам). */
fun isVerifiedArtist(me: MeResponse): Boolean = me.artistVerification?.isVerified == true

/** Какие тумблеры писем показать этому пользователю. */
fun visibleToggles(verified: Boolean): List<EmailToggle> = EmailToggle.values().filter { !it.artistsOnly || verified }

/** Выбор жанров — только не-артистам. */
fun showsGenres(verified: Boolean): Boolean = !verified

/** Сколько типов включено: нет типа в карте — включён (так считает бэкенд). */
fun enabledCount(types: Map<String, Boolean>, all: List<String>): Int = all.count { types[it] != false }

/** Дата рождения: YYYY-MM-DD, год 1900…[maxYear]. Пустая строка — допустимо (очистить). */
fun isValidBirthDate(value: String, maxYear: Int): Boolean {
    if (value.isEmpty()) return true
    val m = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(value) ?: return false
    val (y, mo, d) = m.destructured.toList().map { it.toInt() }
    if (y !in 1900..maxYear || mo !in 1..12) return false
    val days = when (mo) { 2 -> if (y % 4 == 0 && (y % 100 != 0 || y % 400 == 0)) 29 else 28; 4, 6, 9, 11 -> 30; else -> 31 }
    return d in 1..days
}

/**
 * Состояние экрана «Настройки». Каждая секция может не загрузиться (null) — экран показывает остальное.
 */
data class SettingsState(
    val me: MeResponse,
    val push: PushPreferences? = null,
    val inApp: NotificationsSettings? = null,
    val toggles: Map<EmailToggle, Boolean> = emptyMap(),
    val releaseRadar: ReleaseRadarSettings? = null,
    val concert: ConcertAlertsSettings? = null,
) {
    val verified: Boolean get() = isVerifiedArtist(me)
    val profile: UserProfile? get() = me.user
}

/**
 * Настройки пользователя и артиста. Загрузка — все секции параллельно, каждая fail-safe.
 * Тумблеры сохраняются сразу (оптимистично): состояние меняется мгновенно, при ошибке — откат и Result.failure.
 * Формы (регион, дата, Concert Radar) — явное «Сохранить».
 */
class SettingsRepository(private val api: SettingsApi) : UserScoped {
    private val _state = MutableStateFlow<SettingsState?>(null)
    val state: StateFlow<SettingsState?> = _state.asStateFlow()

    override suspend fun clearUserData() { _state.value = null }

    suspend fun load(me: MeResponse): SettingsState = coroutineScope {
        val verified = isVerifiedArtist(me)
        val push = async { api.push().getOrNull() }
        val inApp = async { api.inApp().getOrNull() }
        val rr = async { api.releaseRadar().getOrNull() }
        val concert = async { api.concertAlerts().getOrNull() }
        val toggles = visibleToggles(verified).map { t -> async { t to api.toggle(t).getOrNull() } }
        SettingsState(
            me = me,
            push = push.await(),
            inApp = inApp.await(),
            toggles = toggles.awaitAll().mapNotNull { (t, v) -> v?.let { t to it } }.toMap(),
            releaseRadar = rr.await(),
            concert = concert.await(),
        ).also { _state.value = it }
    }

    /** Оптимистичное изменение: [apply] сразу, [call] в сеть; ошибка — вернуть как было. */
    private suspend fun <T> optimistic(apply: (SettingsState) -> SettingsState, call: suspend () -> Result<T>): Result<T> {
        val before = _state.value ?: return Result.failure(IllegalStateException("settings not loaded"))
        _state.value = apply(before)
        return call().onFailure { _state.value = before }
    }

    suspend fun setToggle(t: EmailToggle, value: Boolean): Result<Boolean> =
        optimistic({ it.copy(toggles = it.toggles + (t to value)) }) { api.setToggle(t, value) }

    suspend fun setPushEnabled(value: Boolean): Result<PushPreferences> =
        optimistic({ s -> s.copy(push = (s.push ?: PushPreferences()).copy(enabled = value)) }) {
            api.setPush(PushPreferencesPatch(enabled = value)).onSuccess { p -> _state.update { it?.copy(push = p) } }
        }

    suspend fun setPushType(type: String, value: Boolean): Result<PushPreferences> =
        optimistic({ s -> s.copy(push = (s.push ?: PushPreferences()).let { it.copy(types = it.types + (type to value)) }) }) {
            api.setPush(PushPreferencesPatch(types = mapOf(type to value))).onSuccess { p -> _state.update { it?.copy(push = p) } }
        }

    suspend fun setInAppEnabled(value: Boolean): Result<NotificationsSettings> =
        optimistic({ s -> s.copy(inApp = (s.inApp ?: NotificationsSettings()).copy(inAppEnabled = value)) }) {
            api.setInApp(NotificationsSettingsPatch(inAppEnabled = value)).onSuccess { n -> _state.update { it?.copy(inApp = n) } }
        }

    suspend fun setInAppType(type: String, value: Boolean): Result<NotificationsSettings> =
        optimistic({ s -> s.copy(inApp = (s.inApp ?: NotificationsSettings()).let { it.copy(types = it.types + (type to value)) }) }) {
            api.setInApp(NotificationsSettingsPatch(types = mapOf(type to value))).onSuccess { n -> _state.update { it?.copy(inApp = n) } }
        }

    suspend fun setReleaseRadar(enabled: Boolean? = null, frequency: String? = null): Result<ReleaseRadarSettings> =
        optimistic({ s ->
            val cur = s.releaseRadar ?: ReleaseRadarSettings()
            s.copy(releaseRadar = cur.copy(releaseRadarEnabled = enabled ?: cur.releaseRadarEnabled, releaseRadarFrequency = frequency ?: cur.releaseRadarFrequency))
        }) { api.setReleaseRadar(ReleaseRadarPatch(enabled, frequency)).onSuccess { r -> _state.update { it?.copy(releaseRadar = r) } } }

    /** Concert Radar — форма с кнопкой «Сохранить». Ответ бэкенда содержит итоговую локацию. */
    suspend fun saveConcert(enabled: Boolean, frequency: String, country: String, city: String): Result<ConcertAlertsSettings> =
        api.setConcertAlerts(ConcertAlertsPatch(enabled, frequency, country.trim(), city.trim()))
            .onSuccess { c -> _state.update { it?.copy(concert = c) } }

    /** Регион и дата рождения — форма с кнопкой «Сохранить»; регион — локация по умолчанию для Concert Radar. */
    suspend fun saveProfile(country: String, city: String, region: String, birthDate: String): Result<Unit> =
        api.saveProfile(ProfileSettingsPatch(country.trim(), city.trim(), region.trim(), birthDate.trim())).map {
            _state.update { s ->
                s?.copy(me = s.me.copy(user = (s.me.user ?: UserProfile()).copy(
                    country = country.trim().ifEmpty { null }, city = city.trim().ifEmpty { null },
                    region = region.trim().ifEmpty { null }, birthDate = birthDate.trim().ifEmpty { null },
                )))
            }
            // Concert Radar берёт локацию из профиля — перечитываем итоговую
            api.concertAlerts().onSuccess { c -> _state.update { it?.copy(concert = c) } }
            Unit
        }

    suspend fun saveLanguage(code: String): Result<Unit> = api.saveLanguage(code).map {
        _state.update { s -> s?.copy(me = s.me.copy(user = (s.me.user ?: UserProfile()).copy(preferredLanguage = code))) }
        Unit
    }

    /** Жанры фаната: не больше [MAX_GENRES] — лишние отбрасываем до запроса. */
    suspend fun saveGenres(genres: List<String>): Result<Unit> {
        val list = genres.fold(emptyList<String>()) { acc, g -> addGenre(acc, g) }
        return api.saveGenres(list).map {
            _state.update { s -> s?.copy(me = s.me.copy(user = (s.me.user ?: UserProfile()).copy(music_genre_preferences = list))) }
            Unit
        }
    }

    suspend fun allGenres(): Result<List<String>> = api.genres()
    suspend fun countries(): Result<List<Country>> = api.countries().map { it.countries }
    suspend fun cities(country: String, query: String?): Result<List<City>> = api.cities(country, query).map { it.cities }
}
