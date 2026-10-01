package com.djmetry.android

import android.app.Application
import com.djmetry.AppContainer
import com.djmetry.data.local.SessionStorageImpl

/**
 * Зависимости — на весь процесс, а не на Activity: поворот планшета пересоздаёт Activity, но не HTTP-клиент,
 * кэши и сессию (раньше старый клиент оставался висеть, а состояние входа терялось).
 */
class DJMetryApplication : Application() {
    val container: AppContainer by lazy { AppContainer(SessionStorageImpl().apply { initialize(this@DJMetryApplication) }) }
}
