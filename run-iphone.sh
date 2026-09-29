#!/bin/bash
# Сборка и запуск DJMetry на реальном iPhone, подключённом к Mac (кабелем или по Wi‑Fi).
#
#   ./run-iphone.sh                 — первый подключённый iPhone
#   ./run-iphone.sh "iPhone 15"     — iPhone, в имени которого есть эта строка
#
# Подпись — автоматическая, команда LOVETECH (PV2426KP9B) из проекта; нужен вход в Xcode → Settings → Accounts.
set -euo pipefail
cd "$(dirname "$0")"

FILTER="${1:-}"
BUNDLE_ID="com.djmetry.ios"
DERIVED="build/ios-device"
echo "🔎 Ищу подключённый iPhone…"
# Без python: разбираем текстовый список devicectl — только реальные iPhone/iPad, не «unavailable».
# Строка: «<имя>   <UDID> (UDID)   <состояние>   <модель>   physical»
LINE=$(xcrun devicectl list devices 2>/dev/null \
  | grep -E 'physical[[:space:]]*$' \
  | grep -E 'iPhone|iPad' \
  | grep -v 'unavailable' \
  | { if [ -n "$FILTER" ]; then grep -i -- "$FILTER"; else cat; fi; } \
  | head -1 || true)
UDID=$(printf '%s' "$LINE" | grep -oE '[0-9A-F]{8}-[0-9A-F]{16}|[0-9a-f]{40}' | head -1 || true)
NAME=$(printf '%s' "$LINE" | sed -E 's/[[:space:]]+[0-9A-Fa-f-]{25,40} \(UDID\).*//')
DEVICE=${UDID:+x}
if [ -z "$DEVICE" ]; then
  echo "❌ iPhone не найден. Подключите телефон, разблокируйте его и нажмите «Доверять этому компьютеру»."
  exit 1
fi
echo "📱 $NAME ($UDID)"

echo "🔨 Сборка (первый раз ~10 мин, дальше быстрее)…"
xcodebuild -project iosApp/DJMetryApp/DJMetryApp.xcodeproj -scheme DJMetryApp \
  -sdk iphoneos -destination "id=$UDID" -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates build -quiet

APP="$DERIVED/Build/Products/Debug-iphoneos/DJMetry.app"
echo "📲 Установка…"
xcrun devicectl device install app --device "$UDID" "$APP" >/dev/null
echo "🚀 Запуск…"
xcrun devicectl device process launch --device "$UDID" "$BUNDLE_ID" >/dev/null
echo "✅ Готово: DJMetry обновлён и открыт на «$NAME»."
