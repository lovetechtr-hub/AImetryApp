import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.7.3")
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.djmetry.desktop.MainKt"

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
