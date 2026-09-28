# 🚀 Быстрый запуск приложения

## Реальный iPhone (одна команда)

```bash
./run-iphone.sh
```

Скрипт сам находит подключённый iPhone, собирает, подписывает (команда LOVETECH), ставит и открывает DJMetry.
Если подключено несколько телефонов — `./run-iphone.sh "15 Pro"` (часть имени).
Первая сборка ~10 минут, дальше быстрее. Нужно: вход в Xcode → Settings → Accounts, на iPhone — «Режим разработчика».

## Через скрипт (самый простой способ)

```bash
./run-android.sh
```

Скрипт автоматически:
- Проверит наличие запущенного эмулятора
- Запустит эмулятор, если его нет
- Соберет и установит приложение
- Запустит приложение

## Вручную через терминал

### 1. Запустить эмулятор (если не запущен)
```bash
~/Library/Android/sdk/emulator/emulator -avd Pixel_2_API_34 &
```

### 2. Дождаться запуска эмулятора
```bash
adb wait-for-device
```

### 3. Собрать, установить и запустить
```bash
./gradlew :androidApp:installDebug && adb shell am start -n com.djmetry.android/.MainActivity
```

## Проверка подключенных устройств

```bash
adb devices
```

## Только запуск (если уже установлено)

```bash
adb shell am start -n com.djmetry.android/.MainActivity
```

## Остановка приложения

```bash
adb shell am force-stop com.djmetry.android
```

## Просмотр логов

```bash
adb logcat | grep -i djmetry
```



