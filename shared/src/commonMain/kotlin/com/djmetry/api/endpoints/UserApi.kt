package com.djmetry.api.endpoints

import com.djmetry.api.apiCall
import com.djmetry.api.models.*
import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.serialization.json.JsonObject

/** Эндпоинты текущего пользователя (/api/me/…). Все, кроме getMe, требуют Bearer-токен. */
class UserApi(private val http: HttpClient) {

    /** GET /api/me — без сессии возвращает isAuthed=false (не 401). */
    suspend fun getMe(): Result<MeResponse> = apiCall { http.get("me") }

    // ───── Артист аккаунта, docs §2 ─────

    suspend fun selectArtist(spotifyArtistId: String): Result<SelectArtistResponse> =
        apiCall { http.post("me/select-artist") { setBody(SpotifyArtistIdRequest(spotifyArtistId)) } }

    /** Ошибки: artist_limit_reached, artist_not_catalog_verified, artist_not_dj, artist_not_found. */
    suspend fun claimArtist(spotifyArtistId: String): Result<ClaimArtistResponse> =
        apiCall { http.post("me/claim-artist") { setBody(SpotifyArtistIdRequest(spotifyArtistId)) } }

    suspend fun searchArtist(query: String): Result<ArtistSearchResult> =
        apiCall { http.get("me/search-artist") { parameter("q", query) } }

    suspend fun getArtistData(): Result<ArtistDataResponse> = apiCall { http.get("me/artist") }

    suspend fun getLatestSnapshot(): Result<Snapshot> = apiCall { http.get("me/snapshot/latest") }

    suspend fun getSnapshots(days: Int = 30): Result<SnapshotsResponse> =
        apiCall { http.get("me/snapshots") { parameter("days", days) } }

    /** Привязка Spotify-артиста (замена удалённого verify-as-artist). Для наших DJ вернёт is_our_dj → claim. */
    suspend fun linkSpotifyArtist(spotifyUrlOrId: String): Result<LinkSpotifyArtistResponse> =
        apiCall { http.post("me/music-page/link-spotify-artist") { setBody(LinkSpotifyArtistRequest(spotifyUrlOrId)) } }

    suspend fun unverifyAsArtist(): Result<SuccessResponse> = apiCall { http.post("me/unverify-as-artist") }

    suspend fun reportVerificationFixed(requestId: String? = null): Result<SuccessResponse> =
        apiCall { http.patch("me/verification-request") { setBody(VerificationFixedRequest(requestId = requestId)) } }

    suspend fun updateArtistLocation(city: String? = null, country: String? = null, region: String? = null): Result<UpdateLocationResponse> =
        apiCall { http.post("me/artist/update-location") { setBody(UpdateLocationRequest(city, country, region)) } }

    suspend fun updateYouTubeHandle(youtubeHandle: String, spotifyArtistId: String? = null): Result<UpdateYouTubeHandleResponse> =
        apiCall { http.post("me/artist/youtube-handle") { setBody(UpdateYouTubeHandleRequest(youtubeHandle, spotifyArtistId)) } }

    // ───── Подписки и голоса (эндпоинты сайта) ─────

    suspend fun follow(spotifyArtistId: String): Result<SuccessResponse> = apiCall { http.post("artists/$spotifyArtistId/follow") }

    suspend fun unfollow(spotifyArtistId: String): Result<SuccessResponse> = apiCall { http.delete("artists/$spotifyArtistId/follow") }

    suspend fun getFollows(): Result<FollowsResponse> = apiCall { http.get("me/follows") }

    suspend fun getVotes(): Result<VotesPayload> = apiCall { http.get("vote/status") }

    /** Заменяет весь набор голосов пользователя. */
    suspend fun setVotes(spotifyArtistIds: List<String>): Result<VotesPayload> =
        apiCall { http.post("vote") { setBody(VotesPayload(spotifyArtistIds)) } }

    // ───── Пуши, docs §3 (доставка на бэке пока выключена, регистрация работает) ─────

    /** Требует привязанного Spotify: иначе 400 "Spotify account not linked". */
    suspend fun registerDeviceToken(deviceToken: String, platform: String, spotifyArtistId: String): Result<SuccessResponse> =
        apiCall { http.post("me/register-device-token") { setBody(DeviceTokenRequest(deviceToken, platform, spotifyArtistId)) } }

    suspend fun unregisterDeviceToken(deviceToken: String, spotifyArtistId: String): Result<SuccessResponse> =
        apiCall { http.delete("me/unregister-device-token") { setBody(DeviceTokenRequest(deviceToken, null, spotifyArtistId)) } }

    // ───── Настройки уведомлений, docs §4 ─────

    suspend fun getPushPreferences(): Result<PushPreferences> = apiCall { http.get("me/push-preferences") }

    suspend fun updatePushPreferences(prefs: PushPreferences): Result<PushPreferences> =
        apiCall { http.put("me/push-preferences") { setBody(prefs) } }

    suspend fun getReleaseRadar(): Result<ReleaseRadarSettings> = apiCall { http.get("me/release-radar") }

    suspend fun updateReleaseRadar(settings: ReleaseRadarSettings): Result<ReleaseRadarSettings> =
        apiCall { http.put("me/release-radar") { setBody(settings) } }

    suspend fun getConcertAlerts(): Result<ConcertAlertsSettings> = apiCall { http.get("me/concert-alerts") }

    suspend fun updateConcertAlerts(settings: ConcertAlertsSettings): Result<ConcertAlertsSettings> =
        apiCall { http.put("me/concert-alerts") { setBody(settings) } }

    suspend fun getUnreadNotificationsCount(): Result<JsonObject> = apiCall { http.get("me/notifications/unread-count") }
}
