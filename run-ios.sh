#!/bin/bash

# Скрипт для запуска iOS приложения через терминал

echo "📱 Запуск DJMetry iOS приложения..."

# Проверяем наличие Xcode
if ! command -v xcodebuild &> /dev/null; then
    echo "❌ Xcode не найден. Установите Xcode из App Store."
    exit 1
fi

# Проверяем наличие симуляторов
if ! command -v xcrun &> /dev/null; then
    echo "❌ xcrun не найден. Установите Xcode Command Line Tools."
    exit 1
fi

# Переходим в корень проекта
cd "$(dirname "$0")"

# Собираем shared framework для iOS
echo "🔨 Сборка shared framework для iOS..."
./gradlew :shared:iosSimulatorArm64Binaries

if [ $? -ne 0 ]; then
    echo "❌ Ошибка при сборке framework"
    exit 1
fi

# Проверяем наличие Xcode проекта
XCODE_PROJECT=$(find iosApp -name "*.xcodeproj" -o -name "*.xcworkspace" 2>/dev/null | head -1)

if [ -z "$XCODE_PROJECT" ]; then
    echo "⚠️  Xcode проект не найден!"
    echo ""
    echo "Сначала создайте Xcode проект:"
    echo "1. Откройте Xcode"
    echo "2. File → New → Project"
    echo "3. Выберите iOS → App"
    echo "4. Настройте проект (см. iosApp/QUICK_START.md)"
    echo "5. Сохраните в папку iosApp/"
    echo ""
    echo "Или используйте команду для создания проекта:"
    echo "  ./iosApp/create-xcode-project.sh"
    exit 1
fi

echo "✅ Найден проект: $XCODE_PROJECT"

# Выбираем симулятор (используем первый доступный iPhone)
SIMULATOR=$(xcrun simctl list devices available | grep -i "iPhone" | head -1 | sed 's/.*(\(.*\))/\1/' | tr -d ' ')

if [ -z "$SIMULATOR" ]; then
    echo "❌ Не найден доступный симулятор iPhone"
    exit 1
fi

echo "📱 Используется симулятор: $SIMULATOR"

# Запускаем симулятор
echo "🚀 Запуск симулятора..."
xcrun simctl boot "$SIMULATOR" 2>/dev/null || echo "Симулятор уже запущен"

# Открываем Simulator.app
open -a Simulator

# Ждем запуска симулятора
echo "⏳ Ожидание запуска симулятора..."
sleep 5

# Определяем тип проекта (workspace или project)
if [[ "$XCODE_PROJECT" == *.xcworkspace ]]; then
    BUILD_CMD="xcodebuild -workspace"
    PROJECT_ARG="$XCODE_PROJECT"
else
    BUILD_CMD="xcodebuild -project"
    PROJECT_ARG="$XCODE_PROJECT"
fi

# Собираем проект
echo "🔨 Сборка iOS приложения..."
$BUILD_CMD "$PROJECT_ARG" \
    -scheme DJMetryApp \
    -sdk iphonesimulator \
    -destination "id=$SIMULATOR" \
    clean build

if [ $? -ne 0 ]; then
    echo "❌ Ошибка при сборке проекта"
    echo ""
    echo "Убедитесь, что:"
    echo "1. Xcode проект правильно настроен"
    echo "2. Shared framework добавлен в проект"
    echo "3. Все файлы добавлены в правильный target"
    exit 1
fi

# Находим собранное приложение
APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -name "DJMetry.app" -path "*/Build/Products/*-iphonesimulator/*" 2>/dev/null | head -1)

if [ -z "$APP_PATH" ]; then
    echo "❌ Не найдено собранное приложение"
    exit 1
fi

echo "✅ Приложение собрано: $APP_PATH"

# Устанавливаем приложение на симулятор
echo "📲 Установка приложения на симулятор..."
xcrun simctl install "$SIMULATOR" "$APP_PATH"

if [ $? -ne 0 ]; then
    echo "❌ Ошибка при установке приложения"
    exit 1
fi

# Получаем bundle identifier
BUNDLE_ID="com.djmetry.ios"

# Запускаем приложение
echo "🎬 Запуск приложения..."
xcrun simctl launch "$SIMULATOR" "$BUNDLE_ID"

if [ $? -eq 0 ]; then
    echo "✨ Готово! Приложение должно открыться на симуляторе."
else
    echo "⚠️  Приложение установлено, но не удалось запустить автоматически."
    echo "   Откройте его вручную на симуляторе."
fi



