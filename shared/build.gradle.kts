import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization") version "1.9.22"
    id("com.android.library")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }
    
    // Десктоп (macOS / Windows / Linux) — приложение в модуле :desktopApp
    jvm("desktop")

    // Фреймворк Shared подключается в Xcode (iosApp/DJMetryApp) через embedAndSignAppleFrameworkForXcode
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlinx Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Ktor Client
                implementation("io.ktor:ktor-client-core:2.3.6")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.6")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.6")
                implementation("io.ktor:ktor-client-logging:2.3.6")
                
                // Kotlinx Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                
                // Compose Multiplatform
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                @OptIn(ExperimentalComposeLibrary::class)
                implementation(compose.components.resources)
                
                
                // Kotlinx DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.ktor:ktor-client-mock:2.3.6")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-android:2.3.6")
                implementation("io.ktor:ktor-client-okhttp:2.3.6")

                // OAuth во встроенном браузере и защищённое хранение токена
                implementation("androidx.browser:browser:1.7.0")
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
                
                // Firebase
                implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
                implementation("com.google.firebase:firebase-messaging")
                implementation("com.google.firebase:firebase-analytics")
            }
        }
        
        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.common)
                implementation("io.ktor:ktor-client-okhttp:2.3.6")
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

        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            
            dependencies {
                implementation("io.ktor:ktor-client-darwin:2.3.6")
            }
        }
    }
}

android {
    namespace = "com.djmetry.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 24
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

