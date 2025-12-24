#!/bin/bash

# Скрипт для запуска Android приложения

# Пути к Android SDK инструментам
ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMULATOR="$HOME/Library/Android/sdk/emulator/emulator"

echo "🚀 Запуск AImetry Android приложения..."

# Проверяем наличие эмулятора
DEVICES=$($ADB devices 2>/dev/null | grep -v "List" | grep "device" | wc -l | tr -d ' ')

if [ "$DEVICES" -eq "0" ]; then
    echo "📱 Эмулятор не найден. Запускаю эмулятор..."
    $EMULATOR -avd Pixel_2_API_34 > /dev/null 2>&1 &
    
    echo "⏳ Ожидание запуска эмулятора..."
    $ADB wait-for-device
    sleep 5
    echo "✅ Эмулятор запущен!"
fi

echo "🔨 Сборка и установка приложения..."
./gradlew :androidApp:installDebug

if [ $? -eq 0 ]; then
    echo "✅ Приложение установлено!"
    echo "🎬 Запуск приложения..."
    $ADB shell am start -n com.aimetry.android/.MainActivity
    echo "✨ Готово! Приложение должно открыться на эмуляторе."
else
    echo "❌ Ошибка при сборке приложения"
    exit 1
fi

