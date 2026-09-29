import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
    // Движок карты MapLibre под ОС сборки: Metal на macOS, Vulkan на Windows и Linux
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch").let { it == "aarch64" || it == "arm64" }
    val runtime = when {
        os.contains("mac") -> "metal-macos-arm64"
        os.contains("win") -> if (arm) "vulkan-windows-arm64" else "vulkan-windows-x64"
        else -> if (arm) "vulkan-linux-arm64" else "vulkan-linux-x64"
    }
    runtimeOnly("org.maplibre.compose:maplibre-compose-runtime-$runtime:0.18.0")
}

kotlin {
    jvmToolchain(25)
}

compose.desktop {
    application {
        mainClass = "com.djmetry.desktop.MainKt"
        // Упаковываем со средой JDK 25 (toolchain): MapLibre Native требует Java 25 во время работы
        javaHome = javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(25)) }.get().metadata.installationPath.asFile.absolutePath
        // MapLibre Native работает через FFM API — нужен доступ к нативному коду
        jvmArgs += "--enable-native-access=ALL-UNNAMED"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "DJMetry"
            packageVersion = "1.0.0"
            description = "DJMetry — рейтинг DJ, радары и букинг"
            vendor = "DJMetry"
            copyright = "© 2026 DJMetry"
            // Keychain / Credential Manager (java-keyring) и HTTP-сервер для loopback-входа
            modules("java.naming", "jdk.httpserver", "jdk.crypto.ec")

            macOS {
                bundleID = "com.djmetry.desktop"
                iconFile.set(project.file("icons/djmetry.icns"))
                // Схема djmetry:// — ОС открывает приложение по ссылке после OAuth (docs/RULES.md)
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleURLTypes</key>
                        <array>
                            <dict>
                                <key>CFBundleURLName</key>
                                <string>com.djmetry.oauth</string>
                                <key>CFBundleURLSchemes</key>
                                <array><string>djmetry</string></array>
                            </dict>
                        </array>
                    """.trimIndent()
                }
            }
            windows {
                iconFile.set(project.file("icons/djmetry.ico"))
                menuGroup = "DJMetry"
                perUserInstall = true
                // Постоянный UUID — чтобы установщик обновлял приложение, а не ставил второе
                upgradeUuid = "6F2B8D3A-5C1E-4E7A-9B0D-3A1F2E4C5D6B"
            }
            linux {
                iconFile.set(project.file("icons/djmetry.png"))
                packageName = "djmetry"
            }
        }
    }
}
