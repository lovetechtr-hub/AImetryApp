# 🚀 Быстрый старт iOS

## Шаг 1: Создайте Xcode проект

В открытом Xcode:

1. **File → New → Project** (⌘⇧N)
2. Выберите **iOS** → **App** → **Next**
3. Настройте:
   - Product Name: `AimetryApp`
   - Team: Ваша команда
   - Organization Identifier: `com.aimetry`
   - Interface: **SwiftUI**
   - Language: **Swift**
4. Сохраните в: `/Users/viacheslavloie/AImetryApp/iosApp/`

## Шаг 2: Замените файлы

Замените содержимое созданных файлов на файлы из папки `AimetryApp/`:
- `App.swift`
- `ContentView.swift`  
- `Info.plist`

## Шаг 3: Добавьте shared framework

1. В Xcode: **File → Add Files to "AimetryApp"**
2. Перейдите в: `../shared/build/bin/iosSimulatorArm64/sharedFramework/`
3. Выберите `shared.framework`
4. **Важно:** Снимите галочку "Copy items if needed"
5. Убедитесь, что добавлено в Target: AimetryApp

## Шаг 4: Настройте Build Settings

1. Выберите проект в навигаторе
2. Target: AimetryApp → Build Settings
3. Deployment Target: **iOS 14.0** или выше

## Шаг 5: Запустите

1. Выберите симулятор (например, iPhone 15)
2. Нажмите **Run** (⌘R)

---

**Готово!** Приложение должно запуститься с теми же экранами, что и на Android.

