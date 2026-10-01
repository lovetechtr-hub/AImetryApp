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
        handleAppLink(intent)
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
        com.djmetry.push.AndroidPushContext.attach(this)
        DJMetryMessagingService.ensureChannel(this)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        runCatching {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> PushTokens.onNewToken(token, "android") }
        }
    }

    /** Тап по пушу: система (фон) и наш показ (открытое приложение) кладут `url` в extras. */
    /**
     * Тап по пушу: наш (приложение открыто) — с маркером [DJMetryMessagingService.EXTRA_PUSH]; системный (приложение
     * в фоне) — Android кладёт `message.data` прямо в extras (есть `google.message_id`). Отдаём все строковые поля.
     */
    private fun handlePushTap(intent: Intent?) {
        val extras = intent?.extras ?: return
        val ours = extras.getBoolean(DJMetryMessagingService.EXTRA_PUSH, false)
        val fromFcm = extras.containsKey("google.message_id")
        if (!ours && !fromFcm) return
        val data = extras.keySet().filter { !it.startsWith("google.") && !it.startsWith("gcm.") && it != "from" && it != "collapse_key" }
            .mapNotNull { k -> extras.getString(k)?.let { k to it } }.toMap()
        PushTokens.onNotificationOpened(data[DJMetryMessagingService.EXTRA_URL], data)
        extras.keySet().toList().forEach { intent.removeExtra(it) }
    }

    // launchMode=singleTop: deep-link djmetry://oauth приходит сюда
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AndroidOAuthBridge.handleRedirect(intent.data)
        handleAppLink(intent)
        handlePushTap(intent)
    }

    override fun onResume() {
        super.onResume()
        AndroidOAuthBridge.attach(this)
        AndroidOAuthBridge.onAppResumed()
    }

    /** App Link `https://djmetry.com/artist/…` или `/booking/…` — тот же маршрут, что у пуша (карточка артиста, вкладка «Букинг»). */
    private fun handleAppLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "https" || data.host != "djmetry.com") return
        PushTokens.onNotificationOpened(data.toString())
        intent.data = null
    }
}
