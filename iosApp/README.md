# iOS App Setup

## Создание Xcode проекта

1. Откройте Xcode
2. File → New → Project
3. Выберите "App" под iOS
4. Настройте проект:
   - Product Name: `AimetryApp`
   - Team: Ваша команда разработчика
   - Organization Identifier: `com.aimetry`
   - Interface: SwiftUI
   - Language: Swift
   - Storage: None

5. Сохраните проект в папку `iosApp/`

## Настройка проекта

1. Добавьте файлы из папки `AimetryApp/` в проект:
   - `App.swift`
   - `ContentView.swift`
   - `Info.plist`

2. Настройте Build Settings:
   - Deployment Target: iOS 14.0 или выше
   - Swift Language Version: Swift 5

3. Добавьте shared framework:
   - В Xcode: File → Add Files to "AimetryApp"
   - Выберите папку `../shared/build/bin/iosSimulatorArm64/sharedFramework/shared.framework`
   - Или используйте CocoaPods для автоматической интеграции

## Альтернативный способ через CocoaPods

Создайте `Podfile` в папке `iosApp/`:

```ruby
platform :ios, '14.0'
use_frameworks!

target 'AimetryApp' do
  pod 'shared', :path => '../shared'
end
```

Затем выполните:
```bash
cd iosApp
pod install
open AimetryApp.xcworkspace
```

## Запуск

1. Выберите симулятор iOS в Xcode
2. Нажмите Run (⌘R)

