package com.aimetry.ui.navigation

sealed class Screen {
    object Splash : Screen()
    object Onboarding : Screen()
    object LoginType : Screen()
    data class Login(val loginType: com.aimetry.domain.model.LoginType) : Screen()
    object Main : Screen()
    object ArtistDashboard : Screen()
    object SearchArtists : Screen()
    data class ArtistDetails(val spotifyArtistId: String) : Screen()
    object ArtistVerification : Screen()
    object Profile : Screen()
    object Trends : Screen()
    object TopArtists : Screen()
    object DJMag : Screen()
}

