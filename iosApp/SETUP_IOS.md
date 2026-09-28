# Настройка iOS проекта для DJMetryApp

## Быстрый старт

### Вариант 1: Создание проекта вручную (рекомендуется)

1. **Откройте Xcode** (уже должен быть открыт)

2. **Создайте новый проект:**
   - File → New → Project (или ⌘⇧N)
   - Выберите **iOS** → **App**
   - Нажмите **Next**

3. **Настройте проект:**
   - **Product Name:** `DJMetryApp`
   - **Team:** Выберите вашу команду разработчика
   - **Organization Identifier:** `com.djmetry`
   - **Bundle Identifier:** `com.djmetry.ios` (автоматически)
   - **Interface:** **SwiftUI**
   - **Language:** **Swift**
   - **Storage:** None
   - **Include Tests:** По желанию

4. **Сохраните проект:**
   - Выберите папку: `/Users/viacheslavloie/DJMetryApp/iosApp/`
   - Нажмите **Create**

5. **Замените содержимое файлов:**
   - Замените `App.swift` содержимым из `DJMetryApp/App.swift`
   - Замените `ContentView.swift` содержимым из `DJMetryApp/ContentView.swift`
   - Добавьте `Info.plist` из `DJMetryApp/Info.plist`

6. **Настройте зависимости:**
   - Добавьте shared framework в проект
   - Или используйте CocoaPods (см. ниже)

### Вариант 2: Использование CocoaPods

1. **Установите CocoaPods** (если не установлен):
   ```bash
   sudo gem install cocoapods
   ```

2. **Создайте Podfile:**
   ```bash
   cd iosApp
   pod init
   ```

3. **Отредактируйте Podfile:**
   ```ruby
   platform :ios, '14.0'
   use_frameworks!

   target 'DJMetryApp' do
     # Добавьте зависимости здесь
   end
   ```

4. **Установите зависимости:**
   ```bash
   pod install
   ```

5. **Откройте workspace:**
   ```bash
   open DJMetryApp.xcworkspace
   ```

## Настройка shared framework

После создания проекта нужно добавить shared framework:

1. Соберите shared framework:
   ```bash
   cd /Users/viacheslavloie/DJMetryApp
   ./gradlew :shared:iosSimulatorArm64Binaries
   ```

2. В Xcode:
   - File → Add Files to "DJMetryApp"
   - Выберите `shared/build/bin/iosSimulatorArm64/sharedFramework/shared.framework`
   - Убедитесь, что "Copy items if needed" НЕ отмечено
   - Добавьте в Target: DJMetryApp

## Запуск

1. Выберите симулятор iOS в Xcode (например, iPhone 15)
2. Нажмите **Run** (⌘R) или кнопку Play

## Troubleshooting

- Если возникают ошибки компиляции, убедитесь, что shared framework собран
- Проверьте, что Deployment Target установлен на iOS 14.0 или выше
- Убедитесь, что все файлы добавлены в правильный target



