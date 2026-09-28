#!/bin/bash

# Автоматическое создание Xcode проекта для Compose Multiplatform

echo "📱 Автоматическое создание Xcode проекта..."

cd "$(dirname "$0")"

# Проверяем наличие Xcode
if ! command -v xcodebuild &> /dev/null; then
    echo "❌ Xcode не найден"
    exit 1
fi

# Создаем структуру проекта
mkdir -p DJMetryApp.xcodeproj

echo "⚠️  Автоматическое создание Xcode проекта требует ручной настройки."
echo ""
echo "Рекомендуется создать проект вручную:"
echo "1. Откройте Xcode"
echo "2. File → New → Project"
echo "3. iOS → App → SwiftUI"
echo "4. Сохраните в: $(pwd)/"
echo ""
echo "После создания проекта используйте:"
echo "  ../../run-ios.sh"



