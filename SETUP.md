# Настройка проекта AImetryApp

## Требования

- JDK 11 или выше
- Android Studio (для Android разработки)
- Xcode (для iOS разработки, только на macOS)
- Gradle 8.2

## Установка

1. Клонируйте репозиторий:
```bash
git clone <repository-url>
cd AImetryApp
```

2. Синхронизируйте Gradle зависимости:
```bash
./gradlew build
```

## Настройка Android

1. Откройте проект в Android Studio
2. Добавьте файл `google-services.json` в папку `androidApp/`
   - Получите файл из Firebase Console
   - Добавьте его в `androidApp/`
3. Убедитесь, что `minSdk = 24` в `androidApp/build.gradle.kts`

## Настройка iOS

1. Откройте проект в Xcode:
```bash
cd iosApp
open AimetryApp.xcodeproj
```

2. Добавьте файл `GoogleService-Info.plist` в проект
   - Получите файл из Firebase Console
   - Добавьте его в Xcode проект

3. Настройте Signing & Capabilities в Xcode

## Настройка API

По умолчанию используется `http://127.0.0.1:8080/api`

Для изменения базового URL отредактируйте:
- `shared/src/commonMain/kotlin/com/aimetry/di/AppModule.kt`
- `shared/src/commonMain/kotlin/com/aimetry/api/ApiClient.kt`

## Запуск

### Android

1. Откройте проект в Android Studio
2. Выберите устройство/эмулятор
3. Нажмите Run

### iOS

1. Откройте проект в Xcode
2. Выберите симулятор или устройство
3. Нажмите Run

## Структура проекта

```
AImetryApp/
├── shared/                  # Общий код
│   ├── commonMain/          # Общая логика (Kotlin)
│   ├── androidMain/         # Android-специфичный код
│   └── iosMain/             # iOS-специфичный код
├── androidApp/              # Android приложение
└── iosApp/                  # iOS приложение
```

## Зависимости

Основные зависимости проекта:
- Kotlin Multiplatform
- Compose Multiplatform
- Ktor Client
- Kotlinx Serialization
- Kotlinx Coroutines
- Firebase (Android/iOS)
- Koin (Dependency Injection)

## Troubleshooting

### Проблемы с Gradle

Если возникают проблемы с Gradle:
```bash
./gradlew clean
./gradlew build --refresh-dependencies
```

### Проблемы с iOS

Если возникают проблемы с iOS сборкой:
1. Убедитесь, что используете Xcode 14+
2. Проверьте настройки Signing в Xcode
3. Очистите DerivedData

### Проблемы с API

Если не работает подключение к API:
1. Проверьте, что backend запущен
2. Убедитесь, что URL правильный
3. Проверьте настройки сети (для iOS может потребоваться настройка Info.plist)

