#!/bin/bash

# Скрипт для открытия iOS проекта в Xcode

echo "📱 Открытие iOS проекта..."

# Проверяем наличие Xcode
if ! command -v xcodebuild &> /dev/null; then
    echo "❌ Xcode не найден. Установите Xcode из App Store."
    exit 1
fi

# Собираем shared framework для iOS
echo "🔨 Сборка shared framework для iOS..."
cd "$(dirname "$0")/.."
./gradlew :shared:iosSimulatorArm64Binaries

if [ $? -eq 0 ]; then
    echo "✅ Framework собран успешно!"
    echo ""
    echo "📂 Следующие шаги:"
    echo "1. В Xcode: File → New → Project"
    echo "2. Выберите 'App' под iOS"
    echo "3. Настройте проект (см. SETUP_IOS.md)"
    echo "4. Добавьте файлы из папки AimetryApp/"
    echo ""
    echo "Или откройте папку iosApp в Xcode:"
    open -a Xcode iosApp
else
    echo "❌ Ошибка при сборке framework"
    exit 1
fi

