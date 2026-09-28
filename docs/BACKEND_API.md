# Интеграция с бэкендом DJMetry

Как мобильное приложение подключено к `https://djmetry.com/api`. Полный контракт эндпоинтов ведёт
бэкенд-команда; здесь — что и где реализовано в приложении.

## Настройки

`shared/.../config/AppConfig.kt`:

| Что | Значение |
|---|---|
| Base URL | `https://djmetry.com`, префикс API `/api` (вынесен отдельно — за прокси может отличаться) |
| Deep-link OAuth | `djmetry://oauth` |
| Условия / политика | `https://djmetry.com/terms`, `/privacy` |

> Схема `djmetry://` — дефолт бэкенда (с 2026-09-28, после деплоя). Env `MOBILE_OAUTH_REDIRECT_SCHEMES`
> нужен, только если понадобятся дополнительные схемы.

## Авторизация

Только OAuth: **Apple, Google, Facebook**. Email/пароля на бэкенде нет, вход через Spotify в приложении не используется.

Флоу (`AuthRepository.signIn`):
1. Генерируем PKCE S256 (`auth/Pkce.kt`).
2. Открываем `GET /api/auth/:provider/start?mobile=1&code_challenge=…&app_redirect=djmetry://oauth`
   в системном браузере: Android — Custom Tabs (`AndroidOAuthBridge`), iOS — `ASWebAuthenticationSession`.
3. Ловим `djmetry://oauth?code=…` (или `?error=…`).
4. `POST /api/auth/mobile/token { code, code_verifier }` → `token`.
5. Токен — в Keychain (iOS) / EncryptedSharedPreferences (Android), дальше на все запросы
   `Authorization: Bearer <token>`. Живёт 30 дней.
6. `GET /api/me` → профиль. Без сессии бэкенд отвечает `200 { isAuthed:false }`, не 401.

При запуске `AuthRepository.restore()` проверяет токен через `/api/me`. Без сети пользователь остаётся
в аккаунте; при `isAuthed:false` или 401 токен удаляется. `POST /api/logout` при выходе.

### Десктоп

Та же схема флоу; `app_redirect` — `djmetry://oauth` (установленное приложение) или
`http://127.0.0.1:<порт>/oauth` (loopback, нужна доработка бэкенда — [ниже](#задача-для-бэкенда-loopback-для-десктопа)).
Как выбирается стратегия — [RULES.md §5](RULES.md#5-платформы-и-запуск).

## Эндпоинты в коде

| Код | Эндпоинты |
|---|---|
| `AuthApi` | `auth/:provider/start` (URL), `auth/mobile/token`, `logout`, `DELETE me` |
| `UserApi` | `me`, `me/select-artist`, `me/claim-artist`, `me/search-artist`, `me/artist`, `me/snapshot/latest`, `me/snapshots`, `me/music-page/link-spotify-artist`, `me/unverify-as-artist`, `me/verification-request`, `me/artist/update-location`, `me/artist/youtube-handle`, `me/register-device-token`, `me/unregister-device-token`, `me/push-preferences`, `me/release-radar`, `me/concert-alerts`, `me/notifications/unread-count` |
| `UserApi` — подписки и голоса | `POST`/`DELETE artists/:id/follow`, `GET me/follows` → `{follows:[…]}`, `GET vote/status` → `{votes:[id…]}`, `POST vote {votes:[id…]}` — заменяет весь набор, максимум 3. Этих эндпоинтов нет в доке бэкенда — взяты из кода сайта |
| `ArtistApi` (публичные) | `artists/search`, `artists/spotify/:id`, `artists/by-slug/:slug`, `…/tracks`, `artists/:id/events`, `artists/top`, `artists/top100…top1000`, `artists/ranking`, `artists/trends`, `artists/available-genres`, `artists/available-countries`, `djmag/rankings`, `talents/top100` |

Ошибки бэкенда (`{ error, code }`) приходят в приложение как `ApiException(status, code)`.

## Учтённые особенности бэкенда

- `verify-as-artist` удалён (410) — в приложении его нет; артист привязывается через `link-spotify-artist` / `claim-artist`.
- В JSON рейтинга поле называется `aimetryScore`; в коде это `djmetryScore` с `@SerialName("aimetryScore")`.
- `spotifyUserId` в `/api/me` — внутренний userId, не Spotify id.
- Заголовок `Origin` не отправляется: иначе бэкенд включает CSRF-проверку.
- `DELETE me/unregister-device-token` отправляется с телом.
- `artists/top100…top1000` отдают **snake_case** (`spotify_artist_id`, `image_url`, `score`, `djmag_rank`),
  а `artists/trends` и `artists/top` — camelCase (`spotifyArtistId`, `imageUrl`, `aimetryScore`). `RankedArtist`
  понимает оба варианта; тест `ProdPayloadsTest` держит реальные ответы прода.
- Публичного списка релизов (Release Radar) через API нет — `release-radar` отвечает 404.

## Ограничения бэкенда (сейчас)

- **Мобильные пуши не доставляются**: регистрация токена работает, отправки через FCM/APNS на бэке нет.
- Регистрация push-токена требует привязанного Spotify — пользователи Apple/Google/Facebook получат
  `400 Spotify account not linked`.
- Talents пусты, если на проде не включён `ENABLE_TALENT_RANKING`.

## Кабинет: профиль, дашборд артиста, музыка, букинг, радары

Источник — спецификация веб-фронта «DJMetry Mobile — спецификация» (2026-09-28). **Приоритет — бэкенд и мобильная
реализация:** если спецификация веба расходится с контрактом бэкенда, берём бэкенд; из веба берём только то, что нужно в
мобилке каждый день. Считает всё бэкенд (RULES.md §2).

### Расхождения веб-спеки и бэкенда

| Тема | Веб-спецификация | Решение для мобилки |
|---|---|---|
| Авторизация | «на вебе только cookie, бэкенду нужен Bearer» | Уже есть: PKCE + `/api/auth/mobile/token` + Bearer (раздел «Авторизация») |
| Лимит подписок | 250 (`FOLLOW_LIMIT`) | `/api/me` отдаёт `stats.maxFollows = 10` — берём с бэкенда, расхождение — задача бэкенду |
| Разбивка Score (стриминг 40 / соцсети 30 / поиск 20 / стабильность 10) | считается на клиенте | не считаем на клиенте; нужен готовый breakdown в ответе — задача бэкенду |
| Уведомления | web push (VAPID) | нативно — свой Firebase (FCM/APNs), подключим позже; сейчас — колокольчик `/me/notifications*` |
| Логин с карточки артиста | Spotify OAuth | в мобилке только Apple / Google / Facebook |

### Экран «Профиль / дашборд артиста»

| Блок | Эндпоинт | Показывать |
|---|---|---|
| Личность, флаг артиста | `GET /me` | всегда; артист = `artistVerification.verifiedSpotifyArtistId` |
| Герой артиста + метрики | `GET /artists/spotify/:id?lang=` | место `position`, Score `aimetryScore`, голоса `votes`, подписчики `followsCount`, дельта `trend.score24h/7d` |
| История Score (график) | `GET /me/snapshots?days=30` | артист |
| Кнопка «Букинг» | `GET /booking/artists/:id/companies` (публично — `GET /booking/artists/public/:id/booking`) | только если у артиста есть привязанные компании |
| Заявки и статусы | `GET /booking/artists/:id/requests`, `PATCH /booking/artists/:id/requests/:requestId` | допустимые переходы — из `serverAllowedTransitions`, клиент их не вычисляет |
| Заработок, выступления | `GET /booking/artists/:id/earnings`, `GET /booking/artists/:id/performances` | артист |
| Release Radar (превью) | `GET /me/release-radar/feed?per_artist=5` | любой авторизованный |
| Concert Radar (превью) | `GET /me/concert-alerts`, `GET /map/dj/:id/summary` | любой авторизованный |
| Колокольчик | `GET /me/notifications/unread-count`, `GET /me/notifications?limit=15`, `POST /me/notifications/read` | всегда |
| Выход / удаление | `POST /logout`, `DELETE /me {confirmed:true}` | всегда |

### Раздел «Музыка» (из дашборда)

| Экран | Эндпоинты | В мобилке |
|---|---|---|
| Music Page (BIO) — редактор | `GET /me/music-page?locale=`, `PUT /me/music-page` (**страница сохраняется целиком**), `GET /music-pages/check-slug`, черновики `/me/music-page/drafts`, ассеты `POST /me/music-page/assets/*` (multipart, поле `file`) | полноценный редактор: секции + карточки контента, превью |
| Smart Links — отдельная страница | `GET /me/smart-links?page=&limit=&tab=&kind=&q=&sort=`, `POST /smart-links`, `GET/PUT /smart-links/:id`, `POST /smart-links/:id/duplicate`, `GET /smart-links/:id/insights?from=&to=&tz=` | список + редактор + инсайты; удаления нет — только архив `is_archived`; лимит тарифа — ошибка `smart_link_limit_reached` (не разлогинивать) |
| Настройки ссылок по умолчанию | `GET/PUT /me/default-link-settings` | форма |
| Аналитика | BIO: `/me/music-page/analytics/{bio-network,breakdown,timeseries,geo-options}`; каталог (только верифицированный): `/me/artist-catalog/analytics/{network,breakdown,timeseries,geo-options}` | графики клики / визиты / страны |

Ошибки, при которых **не** разлогиниваем: `smart_link_limit_reached`, `feature_required`, `not_eligible` (бизнес-403 → показать апселл).

## Задача для бэкенда: loopback для десктопа

Схему `djmetry://` десктоп использует без изменений на сервере; loopback (RFC 8252 §7.3) нужен для запуска из исходников
и окружений, где схему нельзя зарегистрировать.

### Что поменять

`GET /api/auth/:provider/start?mobile=1&code_challenge=…&app_redirect=…` — сейчас `app_redirect` допускает
только кастомные схемы из `MOBILE_OAUTH_REDIRECT_SCHEMES`. Нужно **дополнительно** разрешить loopback:

| Правило | Значение |
|---|---|
| Схема | только `http` |
| Хост | только IP-литералы `127.0.0.1` или `[::1]` (не `localhost` — RFC 8252 §8.3) |
| Порт | любой `1024–65535` — приложение берёт свободный порт при каждом входе |
| Путь | ровно `/oauth` |
| Query / fragment | в `app_redirect` отсутствуют |

Всё остальное — `http(s)` на другие хосты, `localhost`, другой путь — по-прежнему `400 invalid_app_redirect`.

### Флоу (не меняется)

1. Приложение: `start?mobile=1&code_challenge=<S256>&app_redirect=http://127.0.0.1:53682/oauth`.
2. После входа у провайдера бэкенд делает `302` на
   `http://127.0.0.1:53682/oauth?code=<одноразовый код>` или `…?error=<code>`.
3. Приложение: `POST /api/auth/mobile/token { code, code_verifier }` → `{ token, tokenType, expiresAt }`.

Безопасность обеспечивают PKCE (код без `code_verifier` бесполезен) и одноразовость кода (~5 минут), как и
для мобильных. Порт в `app_redirect` нужно сохранить вместе с `code_challenge` и редиректить ровно на него.

### Проверка

```bash
curl -s -D - -o /dev/null "https://djmetry.com/api/auth/google/start?mobile=1&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&app_redirect=http%3A%2F%2F127.0.0.1%3A53682%2Foauth"
```

Ожидается `302` на провайдера (не `400`). В сессии сохранены `code_challenge` и
`app_redirect=http://127.0.0.1:53682/oauth`. Негативные случаи (`http://localhost:53682/oauth`,
`http://127.0.0.1:53682/other`, `https://evil.example/oauth`) → `400`.

Тесты клиента на этот контракт: `shared/src/desktopTest/.../DesktopOAuthTest.kt`.
