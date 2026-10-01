package com.djmetry.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.djmetry.LocalAppContainer

/**
 * Фото артиста для ростеров букинга: своё из ответа, а если его нет — из карточки артиста (кэш на сессию).
 * Бэкенд отдаёт фото из аккаунта артиста, и у небольших артистов оно пустое — без запасного пути вместо
 * лица значок-заглушка.
 */
@Composable
fun rememberArtistPhoto(spotifyArtistId: String, url: String?): String? {
    if (!url.isNullOrBlank()) return url
    val repo = LocalAppContainer.current.artists
    val photo by produceState(repo.cachedPhoto(spotifyArtistId), spotifyArtistId) { value = repo.photo(spotifyArtistId) }
    return photo
}
