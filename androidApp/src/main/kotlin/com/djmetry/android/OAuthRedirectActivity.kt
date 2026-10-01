package com.djmetry.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.djmetry.auth.AndroidOAuthBridge

/**
 * Приёмник `djmetry://oauth` (как RedirectUriReceiverActivity в AppAuth). Custom Tab открыт в той же задаче
 * поверх MainActivity; без этого экрана редирект создавал вторую MainActivity сверху — человек вошёл, а видел
 * экран входа. Здесь: отдаём код входу и возвращаемся в существующую MainActivity, Custom Tab закрывается.
 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidOAuthBridge.handleRedirect(intent?.data)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
