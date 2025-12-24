# AImetry Mobile App

Мобильное приложение AImetry на Kotlin Multiplatform с поддержкой iOS и Android.

## Структура проекта

```
AImetryApp/
├── shared/              # Общий код для iOS и Android
│   ├── commonMain/      # Общая логика
│   ├── androidMain/     # Android-специфичный код
│   └── iosMain/         # iOS-специфичный код
├── androidApp/          # Android приложение
└── iosApp/              # iOS приложение
```

## Технологии

- **Kotlin Multiplatform (KMP)** - общий код для iOS и Android
- **Compose Multiplatform** - UI фреймворк
- **Ktor Client** - HTTP клиент для API
- **Kotlinx Serialization** - сериализация JSON
- **Kotlinx Coroutines** - асинхронность
- **Firebase Cloud Messaging** - push-уведомления

## Настройка

### Android

1. Добавьте `google-services.json` в `androidApp/`
2. Запустите приложение

### iOS

1. Добавьте `GoogleService-Info.plist` в `iosApp/`
2. Откройте проект в Xcode
3. Запустите приложение

## API

Base URL: `http://127.0.0.1:8080/api`

Для production замените на ваш домен.

## Функции

- ✅ Авторизация через OAuth (Spotify, Google, Apple, Facebook)
- ✅ Email/Password регистрация и вход
- ✅ Два типа пользователей (Обычный пользователь / Артист)
- ✅ Поиск артистов
- ✅ Управление коллекцией артистов (до 10)
- ✅ Верификация артистов
- ✅ Push-уведомления
- ✅ Публичные экраны (топы, тренды, DJ Mag)

## Лицензия

Copyright © 2025 AImetry

