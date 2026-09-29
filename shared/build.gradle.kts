import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // JDK 25 для всех JVM-компиляций: MapLibre Native на десктопе поставляется байткодом Java 24 (FFM API).
    // Gradle сам скачает JDK через foojay (settings.gradle.kts)
    jvmToolchain(25)

    android {
        namespace = "com.djmetry.shared"
        compileSdk = 37
        minSdk = 24
        compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
        // commonTest на JVM Android: ./gradlew :shared:testAndroidHostTest
        withHostTest {}
        // Ресурсы Compose (контуры стран для карт) — без этого они не попадают в APK
        androidResources { enable = true }
    }

    // Десктоп (macOS / Windows / Linux) — приложение в модуле :desktopApp
    jvm("desktop")

    // Фреймворк Shared подключается в Xcode (iosApp/DJMetryApp) через embedAndSignAppleFrameworkForXcode.
    // iosX64 (симулятор на Intel) не поддерживается Compose Multiplatform 1.12
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

            implementation("io.ktor:ktor-client-core:2.3.13")
            implementation("io.ktor:ktor-client-content-negotiation:2.3.13")
            implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.13")
            implementation("io.ktor:ktor-client-logging:2.3.13")

            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)
            // Системное «Назад» (Android, жест iOS, Esc на десктопе) — BackHandler
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.1")

            // Графики аналитики (docs/RULES.md → «Аналитика»)
            implementation("com.patrykandpatrick.vico:multiplatform:2.5.2")
            // Карта аналитики: MapLibre Native на Android, iOS и десктопе
            implementation("org.maplibre.compose:maplibre-compose:0.18.0")
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.ktor:ktor-client-mock:2.3.13")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
        }

        androidMain.dependencies {
            implementation("io.ktor:ktor-client-android:2.3.13")
            implementation("io.ktor:ktor-client-okhttp:2.3.13")

            // OAuth во встроенном браузере и защищённое хранение токена
            implementation("androidx.browser:browser:1.7.0")
            implementation("androidx.security:security-crypto:1.1.0-alpha06")

            // Firebase
            implementation(project.dependencies.platform("com.google.firebase:firebase-bom:32.7.0"))
            implementation("com.google.firebase:firebase-messaging")
            implementation("com.google.firebase:firebase-analytics")

            // Движок карты: OpenGL — работает и на эмуляторах, и на старых устройствах
            runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-opengl-android:0.18.0")
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("io.ktor:ktor-client-okhttp:2.3.13")
                // Токен — в системном хранилище: macOS Keychain, Windows Credential Manager, Linux Secret Service
                implementation("com.github.javakeyring:java-keyring:1.0.4")
            }
        }

        val desktopTest by getting {
            dependencies {
                // Скриншот-тесты экранов (ImageComposeScene) — нужны нативные библиотеки Skia текущей ОС
                implementation(compose.desktop.currentOs)
            }
        }

        iosMain.dependencies {
            implementation("io.ktor:ktor-client-darwin:2.3.13")
        }
    }
}

// Ресурсы приложения (контуры стран для карты аналитики): com.djmetry.resources.Res
compose.resources {
    packageOfResClass = "com.djmetry.resources"
}
