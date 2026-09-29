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

class MainActivity : ComponentActivity() {

    // Выбор PDF (райдер, пресс-кит) — системный выбор документа; результат уходит в общий код через мост
    private val saveDocument = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> AndroidFileSaverBridge.onResult(uri) }
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
        setContent { DJMetryApp(container) }
    }

    // launchMode=singleTop: deep-link djmetry://oauth приходит сюда
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        AndroidOAuthBridge.handleRedirect(intent.data)
    }

    override fun onResume() {
        super.onResume()
        AndroidOAuthBridge.attach(this)
        AndroidOAuthBridge.onAppResumed()
    }
}
