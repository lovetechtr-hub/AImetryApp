package com.djmetry.domain.model

/** Провайдеры входа в приложении. Spotify на бэкенде тоже есть, но в мобильном входе не используется. */
enum class OAuthProvider(val value: String) {
    APPLE("apple"),
    GOOGLE("google"),
    FACEBOOK("facebook"),
}
