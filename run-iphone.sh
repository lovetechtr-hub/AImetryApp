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
TMP_JSON="$(mktemp)"
trap 'rm -f "$TMP_JSON"' EXIT

echo "🔎 Ищу подключённый iPhone…"
xcrun devicectl list devices --json-output "$TMP_JSON" >/dev/null 2>&1
DEVICE=$(python3 - "$TMP_JSON" "$FILTER" <<'EOF'
import json, sys
data = json.load(open(sys.argv[1]))
flt = sys.argv[2].lower()
for d in data.get("result", {}).get("devices", []):
    hw = d.get("hardwareProperties", {})
    name = d.get("deviceProperties", {}).get("name", "")
    if hw.get("reality") != "physical" or hw.get("platform") != "iOS":
        continue
    if flt and flt not in name.lower():
        continue
    if d.get("connectionProperties", {}).get("tunnelState") == "unavailable":
        continue
    print(f'{hw.get("udid")}\t{name}')
    break
EOF
)
if [ -z "$DEVICE" ]; then
  echo "❌ iPhone не найден. Подключите телефон, разблокируйте его и нажмите «Доверять этому компьютеру»."
  exit 1
fi
UDID="${DEVICE%%$'\t'*}"
NAME="${DEVICE#*$'\t'}"
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
