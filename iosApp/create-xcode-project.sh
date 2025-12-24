#!/bin/bash

# Скрипт для создания Xcode проекта для AImetryApp

echo "📱 Создание Xcode проекта для AImetryApp..."

# Проверяем наличие Xcode
if ! command -v xcodebuild &> /dev/null; then
    echo "❌ Xcode не найден. Установите Xcode из App Store."
    exit 1
fi

echo "✅ Xcode найден"
echo ""
echo "Для создания проекта:"
echo "1. Откройте Xcode"
echo "2. File → New → Project"
echo "3. Выберите 'App' под iOS"
echo "4. Настройте:"
echo "   - Product Name: AimetryApp"
echo "   - Team: Ваша команда"
echo "   - Organization Identifier: com.aimetry"
echo "   - Interface: SwiftUI"
echo "   - Language: Swift"
echo "5. Сохраните в папку: $(pwd)/iosApp/"
echo ""
echo "После создания проекта добавьте файлы:"
echo "  - App.swift"
echo "  - ContentView.swift"
echo "  - Info.plist"
echo ""
echo "Или используйте CocoaPods (см. README.md)"
