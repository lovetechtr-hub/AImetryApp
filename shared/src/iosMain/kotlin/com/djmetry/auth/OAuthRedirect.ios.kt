package com.djmetry.auth

internal actual fun platformOAuthRedirect(): OAuthRedirect = CustomSchemeRedirect(open = ::authenticateInBrowser)
