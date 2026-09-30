#!/bin/bash
# Сборка и запуск DJMetry на реальном iPhone или iPad, подключённом к Mac (кабелем или по Wi‑Fi).
#
#   ./run-iphone.sh                 — первое подключённое устройство
#   ./run-iphone.sh "iPhone 15"     — устройство, в имени которого есть эта строка
#   ./run-iphone.sh "iPad"          — iPad (новое устройство само регистрируется в профиле подписи)
#
# Подпись — автоматическая, команда LOVETECH (PV2426KP9B) из проекта; нужен вход в Xcode → Settings → Accounts.
set -euo pipefail
cd "$(dirname "$0")"

FILTER="${1:-}"
BUNDLE_ID="com.djmetry.ios"
DERIVED="build/ios-device"
echo "Ищу подключённое устройство…"
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
# devicectl иногда не показывает сопряжённое устройство (так было с iPad) — ищем через xctrace:
# строка «<имя> (<iOS>) (<UDID>)», без симуляторов
if [ -z "$UDID" ]; then
  LINE=$(xcrun xctrace list devices 2>/dev/null \
    | grep -E '^[^=]+ \([0-9.]+\) \([0-9A-Fa-f-]{25,40}\)$' | grep -v Simulator \
    | { if [ -n "$FILTER" ]; then grep -i -- "$FILTER"; else cat; fi; } | head -1 || true)
  UDID=$(printf '%s' "$LINE" | grep -oE '[0-9A-F]{8}-[0-9A-F]{16}' | tail -1 || true)
  NAME=$(printf '%s' "$LINE" | sed -E 's/ \([0-9.]+\) \(.*$//')
fi
DEVICE=${UDID:+x}
if [ -z "$DEVICE" ]; then
  echo "Ошибка: устройство не найдено. Подключите iPhone или iPad, разблокируйте и нажмите «Доверять этому компьютеру»."
  exit 1
fi
echo "${NAME} (${UDID})"

echo "Сборка (первый раз ~10 мин, дальше быстрее)…"
# Полный журнал — в файл, на экран только шаги; при ошибке показываем строки с error.
# Сборка — под «любой iOS»: телефон нужен только для установки (devicectl), а не Xcode-подготовка устройства,
# которая по сети часто зависает («previously reported preparation errors»).
LOG="$DERIVED/build.log"
mkdir -p "$DERIVED"
if ! xcodebuild -project iosApp/DJMetryApp/DJMetryApp.xcodeproj -scheme DJMetryApp \
  -sdk iphoneos -destination "generic/platform=iOS" -derivedDataPath "$DERIVED" \
  -allowProvisioningUpdates build -quiet > "$LOG" 2>&1; then
  echo "Ошибка: Сборка не удалась. Ошибки:"
  grep -E "error:|^e: |needs to be unlocked|BUILD FAILED" "$LOG" | head -20 || true
  echo "Полный журнал: $LOG"
  exit 1
fi

APP="$DERIVED/Build/Products/Debug-iphoneos/DJMetry.app"
echo "Установка…"
if ! xcrun devicectl device install app --device "${UDID}" "$APP" > "$DERIVED/install.log" 2>&1; then
  if grep -q "ApplicationVerificationFailed" "$DERIVED/install.log"; then
    # Новое устройство (например, iPad) ещё не в профиле подписи — один раз собираем под него с регистрацией
    echo "Устройства нет в профиле подписи — регистрирую и пересобираю…"
    xcodebuild -project iosApp/DJMetryApp/DJMetryApp.xcodeproj -scheme DJMetryApp \
      -destination "id=${UDID}" -derivedDataPath "$DERIVED" \
      -allowProvisioningUpdates -allowProvisioningDeviceRegistration build -quiet >> "$LOG" 2>&1
    xcrun devicectl device install app --device "${UDID}" "$APP" >/dev/null
  else
    echo "Ошибка установки:"; tail -5 "$DERIVED/install.log"; exit 1
  fi
fi
echo "Запуск…"
if xcrun devicectl device process launch --device "${UDID}" "$BUNDLE_ID" >/dev/null 2>&1; then
  echo "Готово: DJMetry обновлён и открыт на «${NAME}»."
else
  echo "Готово: DJMetry обновлён на «${NAME}». Открыть не получилось — телефон заблокирован: разблокируйте и откройте DJMetry."
fi
