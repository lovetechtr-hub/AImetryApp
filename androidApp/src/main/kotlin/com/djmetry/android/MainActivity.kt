package com.djmetry.android

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.djmetry.AppContainer
import com.djmetry.DJMetryApp
import com.djmetry.auth.AndroidOAuthBridge
import com.djmetry.files.AndroidFilePickerBridge
import com.djmetry.files.AndroidFileSaverBridge
import androidx.activity.result.contract.ActivityResultContracts
import com.djmetry.data.local.SessionStorageImpl
import com.djmetry.push.PushTokens
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    // Выбор PDF (райдер, пресс-кит) — системный выбор документа; результат уходит в общий код через мост
    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> AndroidFileSaverBridge.onResult(uri) }
    // Android 13+: разрешение на уведомления; ответ не важен — токен регистрируем в любом случае
    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val pickDocument = registerForActivityResult(ActivityResultContracts.GetContent()) { uri -> AndroidFilePickerBridge.onResult(uri) }

    private val container by lazy {
        AppContainer(SessionStorageImpl().apply { initialize(this@MainActivity) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Телефоны — только портрет; планшеты (sw ≥ 600dp) — любая ориентация (docs/RULES.md)
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        AndroidOAuthBridge.attach(this)
        AndroidOAuthBridge.handleRedirect(intent?.data)
        AndroidFilePickerBridge.attach(this)
        AndroidFilePickerBridge.launcher = { mime -> pickDocument.launch(mime) }
        AndroidFileSaverBridge.attach(this)
        AndroidFileSaverBridge.launcher = { name -> saveDocument.launch(name) }
        setupPush()
        handlePushTap(intent)
        setContent { DJMetryApp(container) }
    }

    /** FCM: канал, разрешение (Android 13+) и текущий токен — в общий код, он зарегистрирует его на бэкенде. */
    private fun setupPush() {
        DJMetryMessagingService.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> PushTokens.onNewToken(token, "android") }
        }
    }

    /** Тап по пушу: система (фон) и наш показ (открытое приложение) кладут `url` в extras. */
    private fun handlePushTap(intent: Intent?) {
        intent?.getStringExtra(DJMetryMessagingService.EXTRA_URL)?.let {
            PushTokens.onNotificationOpened(it)
            intent.removeExtra(DJMetryMessagingService.EXTRA_URL)
        }
    }

    // launchMode=singleTop: deep-link djmetry://oauth приходит сюда
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AndroidOAuthBridge.handleRedirect(intent.data)
        handlePushTap(intent)
    }

    override fun onResume() {
        super.onResume()
        AndroidOAuthBridge.attach(this)
        AndroidOAuthBridge.onAppResumed()
    }
}
