# 🚀 Запуск iOS приложения через терминал

## Быстрый способ

После создания Xcode проекта (см. QUICK_START.md):

```bash
./run-ios.sh
```

Скрипт автоматически:
- Соберет shared framework для iOS
- Запустит симулятор
- Соберет и установит приложение
- Запустит приложение

## Ручной запуск

### 1. Запустить симулятор

```bash
# Список доступных симуляторов
xcrun simctl list devices available

# Запустить конкретный симулятор
xcrun simctl boot "iPhone 16 Pro"

# Открыть Simulator.app
open -a Simulator
```

### 2. Собрать проект

```bash
cd iosApp
xcodebuild -project DJMetryApp.xcodeproj \
    -scheme DJMetryApp \
    -sdk iphonesimulator \
    -destination 'platform=iOS Simulator,name=iPhone 16 Pro' \
    clean build
```

### 3. Установить и запустить

```bash
# Найти собранное приложение
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -name "DJMetryApp.app" -path "*/Build/Products/*-iphonesimulator/*" | head -1)

# Установить
xcrun simctl install booted "$APP_PATH"

# Запустить
xcrun simctl launch booted com.djmetry.ios
```

## Полезные команды

**Список симуляторов:**
```bash
xcrun simctl list devices
```

**Остановить симулятор:**
```bash
xcrun simctl shutdown booted
```

**Удалить приложение:**
```bash
xcrun simctl uninstall booted com.djmetry.ios
```

**Просмотр логов:**
```bash
xcrun simctl spawn booted log stream --predicate 'processImagePath contains "DJMetryApp"'
```



