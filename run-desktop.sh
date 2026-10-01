#!/bin/bash
# Сборка и запуск DJMetry для macOS: приложение — в /Applications, схема djmetry:// — только за ним.
#
#   ./run-desktop.sh
#
# Почему так: macOS регистрирует схему djmetry:// за КАЖДОЙ копией DJMetry.app (папка сборки, временная папка
# jpackage). Браузер мог открыть не ту копию — вход через Google не возвращался в приложение.
set -euo pipefail
# Любая ошибка — с понятным сообщением, а не молчаливый выход
trap 'echo "Ошибка: команда «$BASH_COMMAND» (строка $LINENO) не выполнилась." >&2' ERR
cd "$(dirname "$0")"
LSR=/System/Library/Frameworks/CoreServices.framework/Frameworks/LaunchServices.framework/Support/lsregister
BUILT="desktopApp/build/compose/binaries/main/app/DJMetry.app"

echo "Сборка…"
./gradlew -q :desktopApp:createDistributable
# Старую копию удаляем, только когда новая точно собрана
[ -d "$BUILT" ] || { echo "Ошибка: сборка не найдена ($BUILT) — установленный DJMetry не тронут." >&2; exit 1; }
osascript -e 'quit app "DJMetry"' 2>/dev/null || true
sleep 1
rm -rf /Applications/DJMetry.app
cp -R "$BUILT" /Applications/
# Схема — только за установленной копией: снимаем регистрацию со сборки и временных папок jpackage
"$LSR" -u "$PWD/$BUILT" 2>/dev/null || true
{ "$LSR" -dump 2>/dev/null | grep -oE '/private/var/folders/[^ ]*/jdk\.jpackage[^ ]*/DJMetry\.app' || true; } | sort -u | while read -r p; do "$LSR" -u "$p" 2>/dev/null || true; done
"$LSR" -f /Applications/DJMetry.app
open /Applications/DJMetry.app
echo "Готово: DJMetry обновлён в /Applications и открыт."
