# design/

Дизайн-исходники DJMetry. В приложение ничего отсюда напрямую не собирается.

| Папка | Что внутри | В git |
|---|---|---|
| `brand/` | Логотип DJMetry: `djmetry_logo.svg` (источник для `DJMetryLogo.kt`) и полный набор от дизайнера `brand/DjMetry/` | да |
| `icons/` | `generate_icons.swift` — генератор иконок приложения; `store/` — иконки для App Store (1024) и Google Play (512); `platforms/`, `social/` — SVG музыкальных платформ и соцсетей с сайта для страницы артиста | да |
| `onboarding/`, `login/`, `home/` | HTML-превью экранов, по которым делалась вёрстка | да |
| `web-public/` | Полная копия `public/` сайта (72 МБ) | **нет**, только локально (`.gitignore`) |

Обновить иконки приложения после смены логотипа:

```bash
swift design/icons/generate_icons.swift
```

Десктоп: после генератора собрать `.icns` и `.ico` (генератор пишет PNG в `desktopApp/icons/`):

```bash
iconutil -c icns desktopApp/icons/djmetry.iconset -o desktopApp/icons/djmetry.icns && python3 design/icons/make_ico.py && rm -rf desktopApp/icons/djmetry.iconset desktopApp/icons/ico
```

Если из `web-public/` понадобится новый ассет — скопируйте в `icons/` только нужный файл, а не всю папку.
