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
- `djmag/rankings` иногда отдаёт `"previousYearRank":NaN` — это невалидный JSON; клиент читает поле как `null`
  (`LenientIntSerializer`, тест `ProdPayloadsTest.djMagRankingsSurviveNaN`).

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

### Экран «Карточка артиста» (публичная, вариант A «Постер»)

Открывается из рейтинга, поиска, TOP 10 и «Страница» в профиле. Все запросы параллельно, обязательна только карточка.

| Блок | Эндпоинт | Показывать |
|---|---|---|
| Постер: фото, имя, печать, жанры, DJMetry #N | `GET /artists/spotify/:id?lang=` | всегда; `isVerified`, `position`, `genres` |
| DJ Mag #N · год | `GET /djmag/rankings?latest=true` (раз за сессию), иначе `djMagRank` из карточки | если есть |
| Score, место, Spotify, популярность | карточка: `aimetryScore`, `trend.score24h`, `position`, `followers`, `popularity` | всегда |
| Следить / Голос | `POST·DELETE /artists/:id/follow`, `GET /me/follows`, `GET /vote/status`, `POST /vote {votes:[…]}` | авторизованный; гость — тост «войдите» |
| Музыка | `GET /artists/spotify/:id/tracks?limit=5` | если есть треки |
| Концерты, «Билеты» | `GET /artists/:id/events` — ссылка: первая `offers[].url`, иначе `url` | всегда (пусто — «Концертов пока нет») |
| «Забронировать» | `GET /booking/artists/public/:id/booking` | только если `companies` не пуст |
| YouTube | карточка: `youtube.subscribers/views/isOAC/url` | если есть |
| Соцсети, «Ссылка» | карточка: `socialMedia.*` (хэндл или URL), ссылка — `canonicalUrl` или `/artist/:id` | всегда |

### Экран «Рейтинг» (навигация — вариант A «Капсула и шкала сотен»)

| Уровень | Что | Эндпоинт |
|---|---|---|
| Капсула | DJMetry / Ambient / DJ Mag / Итоги года | — |
| Шкала (Score) | диапазоны от бэкенда | `GET /artists/available-limits?category=` → `["100"…"1000","talents"]` (у ambient — до 600) |
| Сотня мест | места N−99…N, место — из API (не по индексу) | `GET /artists/top{N}?category=&genre=&country=` |
| Talents | счёт — `talent_score` («Talent score») | `GET /artists/talents?limit=200&category=&genre=&country=` |
| DJ Mag | годы и списки одним запросом (кэш), сдвиг — `previousYearRank` | `GET /djmag/rankings/all` → `{years, rankings:{"2025":[…]}}` |
| Итоги года | 3 последних сезона; не подведены — `finalized:false` и пусто → «Итоги ещё не подведены» | `GET /dj/year-ranking?year=` |
| Жанр | фильтр на сервере, места остаются исходными | `GET /artists/available-genres?category=` |

Подиум — только если первые строки — места 1, 2, 3. Фильтры страна/жанр — только у DJMetry и Ambient.
**Вопрос бэкенду:** `country` у `top{N}` принимается, но список для US и DE почти одинаковый (меняется только количество) —
это «популярен в стране»? На сайте страна — только UI-фильтр загруженного списка. Нужна семантика «артист из страны» на сервере.

### Экран «Настройки» (вариант A «Сгруппированный список»)

Открывается шестерёнкой рядом с колокольчиком в профиле и строкой «Настройки» внизу профиля.
Загрузка — все секции параллельно, каждая fail-safe (упала — секция пустая, экран работает).

| Раздел | Эндпоинт | Сохранение |
|---|---|---|
| Регион, дата рождения | `PATCH /me/settings/profile {country, city, region, birthDate}` — **не** `profile-region` из веб-спеки; `''` очищает поле. Страна — строго ISO2 из справочника (или «Другая» — ручной ввод), город — подсказки только после страны, дата — системный календарь 1900…текущий год, `YYYY-MM-DD` | кнопка «Сохранить»; после — перечитать Concert Radar |
| Страны / города | `GET /location/countries`, `GET /location/cities?country=&q=&limit=` | — |
| Push по типам | `GET/PUT /me/push-preferences {enabled?, types?}` — 12 типов, нет типа = включён | сразу, с откатом при ошибке |
| Колокольчик | `GET/PUT /me/notifications-settings {inAppEnabled?, types?}` — release_radar, pre_save, booking, concert | сразу |
| Smart Link, письма | `GET/PUT /me/{smart-link-notifications, track-support-emails, weekly-digest, venues-digest, ranking-digest, talents-digest}` — тело `{<field>Enabled: bool}` | сразу |
| Release Radar | `GET/PUT /me/release-radar {releaseRadarEnabled?, releaseRadarFrequency?}` | сразу; частота видна, только когда включено |
| Concert Radar | `GET/PUT /me/concert-alerts {concertAlertsEnabled, concertAlertsFrequency, concertAlertCountry, concertAlertCity}`; `''` = брать из профиля | кнопка «Сохранить» |
| Язык | `PUT /me/settings/language {language}` (коды как в приложении: `zh-CN`, `pt-BR`) | сразу, язык в приложении меняется мгновенно |
| Жанры (только фанат) | каталог — `GET /artists/available-genres` (готовый список бэкенда вместо сборки на клиенте «статика + top1000», как на сайте); `POST /me/settings/music-genres {genres}` — до 5, trim, дубли без учёта регистра, порядок = приоритет | кнопка «Сохранить», видна только при изменении |
| Удаление аккаунта | `DELETE /me {confirmed:true}` после диалога | — |

Гейтинг: дайджесты рейтинга и Talents — только проверенным артистам (`artistVerification.isVerified`), жанры — только не-артистам.
**Нет на бэкенде:** регистрации токена устройства для push (APNs/FCM) — пока в приложении только пер-аккаунт настройки push.

### Раздел «Музыка» (из дашборда)

| Экран | Эндпоинты | В мобилке |
|---|---|---|
| Music Page (BIO) — редактор | `GET /me/music-page?locale=`, `PUT /me/music-page` (**страница сохраняется целиком**), `GET /music-pages/check-slug`, черновики `/me/music-page/drafts`, ассеты `POST /me/music-page/assets/*` (multipart, поле `file`) | полноценный редактор: секции + карточки контента, превью |
| Smart Links — отдельная страница | `GET /me/smart-links?page=&limit=&tab=&kind=&q=&sort=`, `POST /smart-links`, `GET/PUT /smart-links/:id`, `POST /smart-links/:id/duplicate`, `GET /smart-links/:id/insights?from=&to=&tz=` | список + редактор + инсайты; удаления нет — только архив `is_archived`; лимит тарифа — ошибка `smart_link_limit_reached` (не разлогинивать) |
| Настройки ссылок по умолчанию | `GET/PUT /me/default-link-settings` | форма |
| Аналитика | BIO: `/me/music-page/analytics/{bio-network,breakdown,timeseries,geo-options}`; каталог (только верифицированный): `/me/artist-catalog/analytics/{network,breakdown,timeseries,geo-options}` | графики клики / визиты / страны |

Ошибки, при которых **не** разлогиниваем: `smart_link_limit_reached`, `feature_required`, `not_eligible` (бизнес-403 → показать апселл).

## Задача для бэкенда: понятная ошибка лимита попыток входа

**Проблема.** Лимитеры входа (`authRateLimit` — 5 колбэков за 15 мин с IP, `oauthStartRateLimit` — 10 стартов в минуту)
отвечают JSON `429 {"error":"too_many_requests",…}`. Но `/:provider/start` и `/:provider/callback` открываются **в браузере**:
пользователь видит сырой JSON на английском (или страницу ошибки), приложение не получает ничего и висит на входе.

**Контракт (одинаковый для веба и мобилки):**

| Где сработал лимит | Что вернуть |
|---|---|
| `GET /:provider/start?mobile=1&app_redirect=…` (app_redirect прошёл allowlist) | `302 → {app_redirect}?error=too_many_requests&retry_after=<сек>` |
| `GET·POST /:provider/callback`, в сессии state с `mobile=true` | `302 → {appRedirect}?error=too_many_requests&retry_after=<сек>` |
| то же для веба (не mobile) | `302 → /auth/error?error=too_many_requests&retry_after=<сек>` |
| `POST /auth/mobile/token` (JSON API) | `429 {"error":"too_many_requests","retry_after":<сек>}` + заголовок `Retry-After` |

`retry_after` — целые секунды до сброса окна (в express-rate-limit: `handler` → `req.rateLimit.resetTime`).
Реализация — свой `handler` у лимитеров, который ищет state в сессии так же, как колбэк (`getOAuthStates(req)`), и использует `buildAppRedirect`.

**Статус:** реализовано на бэкенде 2026-09-28 (`oauthRateLimitHandler`, `resolveMobileAppRedirect` в `auth-multi.ts`,
тест `oauth-mobile-redirect-resolve.test.ts`), ждёт деплоя. На всех редиректах ещё и заголовок `Retry-After`.
Мобильный вход определяется: у `/start` — по `mobile=1` + `app_redirect`, у колбэка — по `state` в сессии (у Apple POST — `state` в теле).

**Проверено 2026-09-28:** прод отдаёт `RateLimit-Limit: 20` на старте; поведение при лимите проверено на локальном бэкенде —
совпадает с таблицей. **Проблема:** на проде лимитеры считают IP узла Cloudflare (`trust proxy = 1`, прокси два) —
ключ нужно брать из `cf-connecting-ip`, иначе лимит и не держит одного клиента, и общий для чужих людей.

**Лимиты:** колбэк 5 → **20** за 15 мин, старт 10 → **20** в минуту (env `OAUTH_CALLBACK_RATE_LIMIT`, `OAUTH_START_RATE_LIMIT`).
`skipSuccessfulRequests` не подходит: OAuth и на успех, и на ошибку отвечает `302`, лимитер не отличает неудачный вход.

**Клиенты показывают** (12 языков): «Слишком много попыток входа. Попробуйте через N мин.» (N = ⌈retry_after/60⌉),
без `retry_after` — «…через несколько минут». Мобилка: `ApiException.isRateLimited`, `retryAfterSeconds`, тесты
`AuthRepositoryTest.rateLimit*`, `UiLogicTest.rateLimitIsExplainedWithMinutes`. Веб: `AuthErrorPage` — новый код `too_many_requests`.

## Задача для бэкенда: страница «Вернитесь в DJMetry» только для десктопа

**Проблема.** На десктопе вход идёт в обычном браузере пользователя. После успешного колбэка
(`302 → djmetry://oauth?code=…`) Chrome показал пустой 503 («Страница недоступна»), хотя бэкенд вход обработал.
Точная причина не доказана (service worker сайта во встроенном браузере 503 не дал), но страница-хендофф убирает её в любом случае.

**Контракт:**

| Клиент | `start` | Ответ колбэка (успех, ошибка, лимит, неверный PKCE) |
|---|---|---|
| Android (Custom Tabs), iOS (ASWebAuthenticationSession) | без `handoff` | **как сейчас**: `302 → djmetry://oauth?…` |
| Десктоп (системный браузер) | `&handoff=page` | `200 text/html`: «Вход выполнен — вернитесь в DJMetry», кнопка `href=djmetry://oauth?…`, автопереход JS |

`handoff` сохранить в OAuth-state вместе с `mobile`/`appRedirect`, чтобы колбэк знал, какой ответ отдавать.

**Почему не для всех:** Chrome (и Custom Tabs на Android) открывает внешнюю схему по `302`, но JS-переход
`window.location = 'djmetry://…'` без жеста пользователя может заблокировать («user gesture is required»).
На Android вход сейчас проходит сам — нельзя заставлять нажимать кнопку. Десктопу кнопка подходит.

Клиент: `OAuthRedirect.handoffPage` (десктоп — `true`), `AuthApi.mobileStartUrl(handoffPage)`, тесты
`ApiClientTest.desktopAsksForHandoffPage`, `phoneStrategyDoesNotAskForHandoffPage`, `DesktopOAuthTest`.

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

## Редактор артиста (спека §14)

Доступ — только проверенному артисту (`artistVerification.isVerified` + `verifiedSpotifyArtistId`); остальным клиент запросов не шлёт.

| Раздел | Запрос | Особенности для клиента |
|---|---|---|
| Данные | `GET /artists/spotify/{id}` | соцсети, жанры, страна/город |
| Соцсети | `POST /me/artist/socials` | бэк меняет **только пришедшие поля** → шлём все 9, очищенное — явным `null` |
| Жанры | `POST /me/artist/genres` `{genres}` | ≤5, без дублей, в нижнем регистре |
| Локация | `POST /me/artist/update-location` `{country, city, region}` | страна ISO2 обязательна; пустое — явный `null` (пустая строка не проходит `min(1)`) |
| Треки | `GET/POST /me/artist/tracks`, `DELETE /me/artist/tracks/{id}`, `PUT /me/artist/tracks/reorder` `{trackIds}` | только ссылка на **трек** Spotify (альбом/артист/плейлист — отказ на клиенте), шлём каноничный `https://open.spotify.com/track/{id}`; лимит 5 держит клиент — **бэк сейчас режет до 20** (задача в журнале) |
| Райдер / пресс-кит | `GET/PUT/DELETE /booking/artists/{id}/rider\|press-kit` | `PUT` — multipart, поле `file`, только PDF ≤10 МБ (проверяем расширение, MIME и сигнатуру `%PDF`); `404` на `GET` = «не загружен» |

«Тема карточки» на мобильном не делается.

## Аналитика (спека §15)

Все запросы — с Bearer; BIO: `/api/me/music-page/analytics/…`, карточка DJMetry: `/api/me/artist-catalog/analytics/…` (одинаковые параметры и форма).

| Запрос | Что берём | Особенности |
|---|---|---|
| `GET …/bio-network` · `…/network` | `totals` (визиты, клики, сегменты), `by_segment[].unique_viewers`, `click_breakdown`, `top_viewers` | клики = `total_clicks` (ось CTR), без него — `total_click_events`; уникальные = сумма по сегментам; у `top_viewers` ключи `linked_spotify_artist_id` / `booking_company_slug` могут отсутствовать |
| `GET …/breakdown` | `countries[]` (ISO2), `cities[]`, `referrals[]`, `devices/browsers/os[]`, `secret_link_leads.total` | CTR и подпись CTR считает бэкенд; имён и координат стран нет — имена из `/location/countries`, центры — таблица клиента (как `countryCoords.ts` сайта) |
| `GET …/geo-options` | страны и города для фильтра | читает только период, геофильтр игнорирует |

Параметры: `range` = `all|7d|30d|90d|180d|ytd|custom` (по умолчанию в приложении `7d`, как на сайте); `from_date`/`to_date` — только при `custom`, ≤ 732 дней (`range_too_long`); `country_code` (ISO2) и `city` (только со страной, иначе `city_requires_country_code`). Пресеты считает сервер (UTC).
Ошибки: 403 `no_music_page` → «создайте BIO-страницу на сайте», 403 `catalog_analytics_unavailable` → карточка только проверенному артисту (доступ по `verified_spotify_artist_id`; `linked_artist_id` больше не даёт доступ).
График активности строится из итогов периода (`distributeShaped` сайта), `timeseries` не используется — продуктовое решение «единая правда = totals».
⚠️ `affiliate_clicks` бэкенд не отдаёт — плитки нет (задача в журнале).

## Карта диджеев (спека §16)

Все `/api/map/…` публичные (без входа), snake_case (кроме `spotifyArtistId` в `/dj/:id/tour`), лимит 100 запросов в минуту на IP (429 `too_many_requests`). Клиент кэширует ответы на 120 с.

| Запрос | Что берём | Особенности |
|---|---|---|
| `GET /map/performances` | точки событий (фото, жанры, место, дата, `url` → «Билеты») | без `zoom` — сервер отдаёт точки, кластеризует клиент (сетка 62 px); `country` — ИМЯ страны; bbox `запад,юг,восток,север`, запрос с запасом +20%; все DJ — одна точка на артиста (ближайшее будущее, иначе свежее прошедшее) |
| `GET /map/performances?artist_id=` + `GET /map/dj/:id/tour` | тур одного DJ | дуги — Безье в пикселях Web-Mercator, изгиб min(0.14·длины, 140 px); лента городов — подряд идущие одинаковые схлопываются |
| `GET /map/dj/:id/summary` | `has_points`, `point_count`, `country_count` | кнопка на карточке артиста — только при `has_points` |
| `GET /map/top-touring?limit=12` | лидеры «Выступлений» | |
| `GET /map/event-density?level=country\|city\|venue` | «ТОП стран» | уровень по зуму: round(z) < 4 страны, < 6 города, иначе площадки; цвет страны — `genreColor(iso)`; топ-10 — клиентский срез |
| `GET /map/top-artists?country=ISO2` / `GET /map/origin-artists?country=ISO2` | диджеи в попапе страны | здесь страна — ISO2; `total` у top-artists — длина страницы (задача бэкенду) |
| `GET /map/dj-origins?genre=` | «Откуда диджеи» | цвет — доминирующий жанр (`genreColor`, таблица сайта) |
| `GET /map/venues` · `GET /map/venues/:id/artists` · `event-density?level=venue&limit=10` | «Фестивали и клубы», «Кто играет», топ площадок | `zoom < 6` — серверные кластеры; «Музыка» → `open.spotify.com/artist/{id}` (`spotify_url` нет) |
| `GET /map/filters` | жанры, страны (имена) | |

⚠️ `/map/performances/search` на бэкенде нет — поиск DJ идёт через `/artists/search` (задача в журнале).

## Задача для бэкенда: «Аудитория» не принимает Bearer (блокер мобилки)

`audienceRouter.use(requireAuth)` пропускает запрос с `Authorization: Bearer <session_id>`, но каждый хендлер заново берёт пользователя через локальную `sessionUserId(req)` (`src/api/routes/audience.ts:52-62`), а она читает **только** cookie (`req.session.sessionId`). Итог: у приложения на всех `/api/audience/*` (segments, filter-catalog, preview, leads, export) — `401 {"error":"unauthorized"}`.

**Нужно:** брать userId так же, как остальные роуты (`getSessionIdFromRequest` из `session-utils.ts` / `req.user.id` после `requireAuth`).

**Проверка:** `curl -H "Authorization: Bearer <токен>" "https://djmetry.com/api/audience/segments?audience_scope=bio_owner"` → 200 `{segments:[…]}`.

Попутно: `filter-catalog.operators` — массив строк (веб ждёт объекты и падает на запасной каталог); 8 пресетов (`top_fans`, `influencers`, `recent_fans`, …) существуют только на веб-клиенте — лучше отдавать все пресеты с бэка.


## Голосование (`/api/vote`)

Голоса бессрочные: не больше 3 артистов одновременно. Каждый `POST` заменяет весь набор голосов. Суточных лимитов и кулдаунов нет, есть только rate limit — 30 `POST` в минуту.

| Метод | Путь | Авторизация | Ответ |
|---|---|---|---|
| GET | `/api/vote/status` | сессия (без неё — пустой список) | `{ votes: string[], count }` |
| POST | `/api/vote` | `requireAuth` | тело `{ votes: string[] }` (0–3 шт.; `[]` снимает все голоса) → `{ success, message, votes }` |
| GET | `/api/vote/top?limit=10` | публичный | `{ artists: [{spotifyArtistId, name, imageUrl, votes}], count }` |
| GET | `/api/vote/artist/:id` | публичный | `{ spotifyArtistId, name, votes }` |
| GET | `/api/me/votes/history?limit=` | Bearer работает | `{ history: [{spotifyArtistId, name, imageUrl, action, year, createdAt}], count }` |

Правила:
- голосовать можно только за артиста из подписок (иначе `not_following`);
- у артиста должен быть рейтинг (иначе `no_rating`);
- легенды в голосовании не участвуют (`legend_not_votable`);
- при отписке голос снимается автоматически;
- голосовать за себя можно;
- email подтверждать не нужно.

Ошибки POST:
- 400: `missing_parameter`, `too_many_votes`, `invalid_votes`, `not_following`, `no_rating`, `legend_not_votable`;
- 401: `unauthorized`;
- 429: `rate_limited` / `too_many_requests`, с заголовком `Retry-After`.

Клиент считает оставшиеся голоса сам, как `3 − count`: отдельного поля в ответе нет.

### Задача для бэкенда: голосование не принимает Bearer (блокер мобилки)

`requireAuth` пропускает `Authorization: Bearer <token>`. Но потом хендлеры снова читают id только из cookie:
- `src/api/routes/voting/index.ts:106` — `POST /api/vote` отвечает 401 `{"error":"Unauthorized"}`;
- `src/api/routes/voting/index.ts:19` — `GET /api/vote/status` отдаёт пустые голоса.

Как исправить: заменить чтение cookie на `getSessionIdFromRequest(req)` (в POST — `req.user.id`) и добавить интеграционный тест на Bearer.

Проверка после исправления:

```bash
curl -s -X POST https://djmetry.com/api/vote -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"votes":["<spotifyArtistId>"]}'
```

Ожидаемый ответ — 200 `{ success: true, votes: [...] }`.


### Аудитория в приложении (вариант B «Воронка фанов»)

Какие запросы делает приложение:
- Сегменты: `GET /api/audience/segments?spotify_artist_id=…`. Если нет проверенной карточки артиста — `?audience_scope=bio_owner`.
- Плитки воронки: 6 параллельных запросов `POST /api/audience/preview` с `page_size: 1`:
  - один без доп. фильтра — общий итог, `stats.countries_top` для карты;
  - по одному на каждый `fan_segment` (`super_fan`, `casual`, `cold`, `fading`, `former`), фильтр `{op:"and", rules:[…фильтры сегмента, {field:"fan_segment", operator:"eq", value}]}`.
- Люди в выбранной плитке: тот же `preview`, `page_size: 25`, с кнопкой «Показать ещё».
- Лиды: `GET /api/audience/leads?source_type=bio_url|smart_link|tour`. По `emails_hidden` показывается плашка тарифа Start.
- Экспорт: `POST /api/audience/export`, затем `GET /api/audience/export/:id?download=1`. Пока приходит 409, повторяем каждую секунду, до 10 раз.

**Пожелание бэкенду:** добавить в `stats` превью `fan_segments: [{ segment, count }]`. Тогда воронка будет считаться одним запросом.


## Колода «Открытий» — подборки

Подборки в порядке меню:

| Подборка | Запрос |
|---|---|
| TOP 10 | `GET /api/artists/top100` — первые 10 |
| Растут сейчас | `GET /api/artists/trends?category=growing&limit=40&sortBy=score24h` |
| Новые прорывы | `GET /api/artists/trends?category=breakthrough&limit=40&sortBy=score24h` |
| Самые стабильные | `GET /api/artists/trends?category=stable&limit=40` |
| Теряют импульс | `GET /api/artists/trends?category=losing_momentum&limit=40&sortBy=score3d` (падение сверху — бэкенд) |

Кэш трендов на бэкенде — 30 минут. Уже подписанных артистов клиент убирает из колоды.

## Release Radar — уведомления о релизах

- **Рубильник по артисту один — подписка.** `POST` / `DELETE /api/artists/:id/follow`. После отписки бэкенд сразу перестаёт слать письма и колокольчик по этому артисту.
- **Письма:** `GET` / `PUT /api/me/release-radar`.
  - Поле `releaseRadarEnabled` — включены ли письма.
  - Поле `releaseRadarFrequency` — частота: `immediate` или `weekly_digest`. По умолчанию `weekly_digest`.
  - `PUT` принимает частичный патч: можно прислать только одно поле.
  - Ошибки: `400 invalid_release_radar_enabled`, `invalid_release_radar_frequency`, `empty_body`.
- **Частота:**
  - `weekly_digest` — одна сводка раз в 7 дней.
  - `immediate` — письмо по мере выхода релизов; за один прогон рассылки бэкенд собирает все релизы в одно письмо.
- **Колокольчик:** `GET` / `PUT /api/me/notifications-settings`.
  - `inAppEnabled` — общий переключатель.
  - `types.release_radar` — переключатель уведомлений о релизах.
  - От переключателя писем колокольчик не зависит.
- **Открыто:** в режиме `immediate` колокольчик пока создаёт по записи на каждый релиз. Группировку колокольчика бэкенд сделает отдельной задачей, если понадобится.

## Пуши на десктопе

Десктоп-приложение сделано на Compose Multiplatform for Desktop (Kotlin/JVM) и упаковано через jpackage в `.app`, `.msi` или `.deb`. FCM, APNs и Web Push здесь недоступны.

**Предложение:**
- Бэкенд даёт поток `GET /api/me/notifications/stream` (SSE, `Authorization: Bearer`) с событиями в формате записей `/api/me/notifications`.
- Пока приложение открыто, оно держит соединение и показывает системное уведомление: на macOS — в Центре уведомлений, на Windows — всплывающее.
- Если потока нет — запасной вариант: опрос `/api/me/notifications?since=…` раз в 60 секунд.
- Когда приложение закрыто, доставка идёт по email.


## Пуши на телефоны (FCM)

- **Firebase-проект:** `djmetry-aab4a`.
  - Android: `com.djmetry.android`, файл `androidApp/google-services.json`.
  - iOS: `com.djmetry.ios`, файл `iosApp/DJMetryApp/DJMetryApp/GoogleService-Info.plist`.
  - Оба файла в `.gitignore`.
- **Регистрация:** `POST /api/push/devices` с телом `{ transport: "fcm", token, platform: "android" | "ios", app: "mobile" }`. Ответ `204`. Пользователь берётся из сессии (Bearer). iOS тоже шлёт FCM-токен, не APNs.
  - Приложение шлёт токен после входа, при новом токене (`onNewToken` / делегат Messaging) и при смене аккаунта.
  - Повтор того же токена для того же пользователя не отправляется.
- **Снятие:** `DELETE /api/push/devices` с телом `{ token }`, ответ `204`. Вызывается перед выходом и перед удалением аккаунта, пока сессия ещё жива.
- **Сообщение:**
  - `notification { title, body }` — в фоне показывает система;
  - в открытом приложении показываем сами (Android — канал `djmetry_default`, iOS — баннер);
  - `data.url` — куда ведёт тап: `/artist/<id>` или `…/release-radar?artist=<id>` открывают карточку артиста в приложении, остальное — сайт;
  - `data.type` — тип события.
- **Бэкенду нужен** сервисный аккаунт Firebase из проекта `djmetry-aab4a` в переменной `FCM_SERVICE_ACCOUNT_JSON`. Для iOS в Firebase → Cloud Messaging загружается APNs-ключ `.p8`.
- **Настройки пушей:** общий переключатель и переключатели по типам — `GET` / `PUT /api/me/push-preferences`.


## Пуши на десктопе (SSE)

Полный контракт — djmetry-api `docs/PUSH_NOTIFICATIONS_API.md` §3.

- **Запрос:** `GET /api/me/notifications/stream` с заголовками `Accept: text/event-stream`, `Authorization: Bearer`, `Last-Event-ID` (после переподключения).
- **Формат потока:**
  - кадр `event: notification`, в `data:` — запись уведомления (`stream_id`, `id`, `type`, `title`, `body`, `url`, `image_url`);
  - `id:` — это `stream_id`;
  - `: ping` примерно каждые 25 с;
  - `retry: 3000`.
- **Переподключение:** сервер сам закрывает поток раз в 15 минут. Клиент переподключается через паузу из `retry:`. При ошибке пауза растёт до 60 с.
- **Когда работает:** только пока пользователь вошёл. После выхода поток закрывается.


## Радар (релизы и концерты подписок)

- **Релизы:** `GET /api/me/release-radar/feed?per_artist=5`.
  - Ответ: `artists[]`, у каждого `latest[]`, `total_releases`, `showing_older`, `genres`.
  - `has_new_releases` значит «есть релизы с 2026-01-01», а не «вы ещё не видели».
- **Все релизы артиста:** `GET /api/me/release-radar/artist/:id/releases`.
  - Параметры: `limit=24&offset&q&sort=date_desc|date_asc|name`.
  - Ответ: `{ releases, total }`.
  - Если пользователь не подписан на артиста — `403 not_following`.
- **Отметка «новое»:** непрочитанные записи `GET /api/me/notifications?type=release_radar|concert`, артист берётся из `meta.spotify_artist_id`.
- **Концерты:** `GET /api/artists/:id/events` по каждой подписке, не больше 4 запросов одновременно, кэш в приложении 15 минут. В список идут только будущие.
- **«Рядом»:** город и страна берутся из `effectiveCity` / `effectiveCountry` ответа `GET /api/me/concert-alerts`. Сравнение как у бэкенда в `eventMatchesUserLocation`:
  - город — совпадение или вхождение;
  - страна — ISO2 точно, название по вхождению.
- **«На карте»:** карта диджеев на туре артиста, `/api/map/dj/:id/tour`.
- **Предложение бэкенду:** `GET /api/me/concerts?from=&to=&near=1` — концерты всех подписок одним ответом (`artist + event`), чтобы не делать N запросов.


### Пожелания к бэкенду для Радара (отправлено 2026-09-30)

1. `GET /api/me/radar/artists` — подписки для «историй»:
   - поля на каждого артиста: `unread_releases`, `unread_concerts`, `next_concert{event_id, datetime, city, country ISO2, near}`, `upcoming_concerts`, `total_releases`;
   - `image_url` всегда заполнен;
   - сортировка на сервере: сначала непрочитанное, потом концерт в ближайшие 30 дней, потом по имени.
2. `GET /api/me/concerts?from&to&near=1&artist_id&cursor&limit` — все будущие концерты подписок одним ответом:
   - `artist{…}`;
   - `venue{…, country_code, lat, lng}`;
   - `ticket_url`, `near`;
   - `location{city, country, known}`, `total`, `near_total`, `next_cursor`;
   - ETag.
3. `POST /api/me/radar/seen {spotify_artist_id?, kind}` — пометить уведомления `release_radar` / `concert` артиста прочитанными.
4. Доработки существующих эндпоинтов:
   - `is_new` у релиза в `release-radar/feed`;
   - `artist_image_url` всегда заполнен;
   - `url` уведомления `concert` → `/artist/<id>`.

Пока этого нет, приложение работает через текущие эндпоинты; после появления переключимся, старый путь оставим запасным.


## Букинг — пожелания к бэкенду для приложения (2026-09-30)

Контракт — спека, раздел 18. Для вкладки «Букинг» в приложении нужны следующие доработки.

1. **`GET /api/booking/me/overview`** — одним запросом всё для шапки вкладки и бейджа в таббаре:
   - `roles`: `requester`; `companies[]` вида `{id, name, image, role: owner|manager, moderation_status}`; `artists[]` вида `{spotify_artist_id, name, company_id?}`;
   - счётчики `new` / `unread` по каждой роли;
   - `next_show` вида `{request_id, datetime, city, venue, status}`;
   - доход за месяц `by_currency` (after-tax) для компании и артиста.
2. **Поля в каждой заявке**, на любом эндпоинте списка или карточки:
   - `stage` 0..5 (declined = 0) — прогресс-бар без логики на клиенте;
   - `my_role` — `requester` | `company` | `artist`;
   - `allowed_statuses[]` — какие статусы текущий пользователь может поставить сейчас; сейчас это видно только из ошибки `invalid_status_transition`;
   - `unread` — флаг непрочитанного;
   - `event_datetime` в локальном времени площадки и `event_timezone`, чтобы «Сегодня · 23:00» на карточке артиста было правильным.
3. **Прочитано:** `POST /api/booking/requests/:id/read`. После этого снимаются `unread` и бейдж.
4. **Уведомления и пуши:**
   - тип `booking` на события: новая заявка → компании; смена статуса или оплаты → заказчику и артисту; путевой статус артиста → компании и заказчику;
   - `meta`: `{request_id, company_id, status, payment_status}`;
   - `url`: `/booking/requests/<id>` — по тапу приложение откроет заявку;
   - те же события в SSE `/me/notifications/stream` для десктопа.
5. **Поиск агентства для заказчика:** `GET /api/booking/companies/search?q=&artist_id=&country=`.
   - Только `approved`.
   - Ответ: `{id, slug, name, image, country, city, artists[{spotify_artist_id, name, image}]}`.
   - Сейчас агентство можно найти только через карточку артиста.
6. **Райдер и пресс-кит в приложении:** `GET /api/booking/artists/:id/rider/url` (и `/press-kit/url`) → `{url, expires_at}`.
   - Короткоживущая подписанная ссылка — открыть PDF в системном просмотрщике, где Bearer-заголовка нет.
7. **Токен подтверждения компании:** ссылка вида `https://djmetry.com/booking/confirm?token=…`.
   - Приложение перехватывает её и показывает `preview` → «Подтвердить».
   - Нужна настройка universal links / App Links: `apple-app-site-association` и `assetlinks.json` на домене. Идентификаторы: iOS `com.djmetry.ios`, команда `PV2426KP9B`; Android `com.djmetry.android` + SHA-256 подписи (дадим).
8. **`PATCH` статуса артиста** (`/artists/:id/requests/:requestId`) должен возвращать обновлённую заявку целиком, с новыми `stage` и `allowed_statuses`, чтобы кнопка «следующий этап» обновилась без перезагрузки.
9. **Пагинация** у `/my-requests`, `/companies/:id/requests`, `/artists/:id/requests`: `cursor` / `limit`.

### Букинг: кабинет и новая заявка — чего не хватает приложению (2026-09-30)

Приложение делает кабинет агентства и артиста (заработок, артисты, команда, токен, налоги, карта выступлений, райдер и пресс-кит) и форму «Оставить заявку». Ниже — что мешает сделать это без обходных путей. Сначала ошибки, потом доработки.

**Ошибки и утечки**

1. **Фото агентства у артиста.** В `GET /booking/artists/:id/requests` и `.../requests/:id` агентство приходит как `company: {id, name}` — без `image_url`. Сейчас приложение делает лишний запрос `/artists/:id/companies`, чтобы подставить логотип. Нужно: `company: {id, name, slug, image_url}`.
2. **Заказчик получает финансовые поля.** В `/booking/my-requests` уходят `company_fee_amount`, `artist_fee_amount`, налоги. Заказчику нужны только `payment_amount`, `payment_currency`, `payment_status`, `payment_percent`. Остальное — убрать из ответа.
3. **`is_read` общий у агентства и артиста.** Если заявку открыл менеджер, у артиста она тоже становится прочитанной. Нужно хранить отдельно (`read_by_company_at`, `read_by_artist_at`) и отдавать каждому свой `is_read`.
4. **Подписи периодов заработка не совпадают с расчётом.** `period_descriptions` пишут «Last 7 days / Last 30 days / Last 365 days», а считается с понедельника, с 1-го числа и с 1 января (UTC). Нужно поправить тексты (или отдавать `from`/`to` каждого периода).
5. **Превью токена.** `GET /booking/confirm-by-token/preview` отдаёт `company.name`, а сайт ждёт `company_name`. Отдавать оба поля.
6. **Налог «артист считает сам» по артисту теряется.** `PUT /companies/:id/artist-tax-defaults` молча выбрасывает `default_artist_calculates_own_tax` (сайт его шлёт). Сохранять по каждому артисту или явно убрать из формы сайта.
7. **Приглашение в команду нельзя отозвать.** У `pending`-приглашения `user_id = null`, а `DELETE .../members/:userId` ищет по `user_id`. Нужно удаление по `id` записи: `DELETE .../members/by-id/:memberId`.
8. **`POST /booking/requests` не проверяет, что артисты из `spotify_artist_ids` привязаны (approved) к `booking_company_id`.** Можно отправить заявку в чужое агентство на любого артиста — нужна проверка и ошибка `artist_not_in_company`.
9. **Переходы статусов артиста.** `invalid_status_transition` у `PATCH /artists/:id/requests/:id` не содержит `allowed[]`. Правило «выступил — только в день события или позже» и запрет отмены заказчиком есть только на сайте — перенести в бэкенд, чтобы приложение не дублировало логику.

**Доработки**

10. **Помесячный ряд заработка** для столбиков на карточке: в ответ `/earnings` добавить `months: [{month: "2026-09", company_fee, company_fee_after_tax, artist_fee, artist_fee_after_tax}]` по главной валюте (или `by_currency` внутри) за последние 12 месяцев. Сейчас приложение считает столбики из ленты заявок — это неточно (по дате события, а не оплаты).
11. **Сравнение с прошлым периодом:** `previous` в каждом периоде (та же форма) — для «+18% к прошлому месяцу».
12. **`all_time` у агентства** — как у артиста.
13. **Координаты выступлений.** `GET .../performances` отдаёт только `event_country` и `event_location` строкой. Нужно `lat`, `lng`, `city` (геокодировать на сервере при создании или смене места), чтобы точки на карте стояли по городам, а не по центру страны.
14. **Три трека в карточке артиста агентства.** В `GET /booking/companies/:id` и `/by-slug/:slug` добавить `top_tracks: [{spotifyTrackId, name, albumImageUrl, externalUrl}]` (до 3, сначала `curated_tracks`), чтобы не делать по запросу на каждого артиста.
15. **Форма заявки одним запросом.** `GET /booking/artists/public/:id/booking` отдаёт агентства без `slug` и без `artists[]` — чтобы показать галочки артистов, нужен второй запрос `by-slug`. Добавить в каждое агентство `slug`, `image_url` и `artists[{spotify_artist_id, name, image_url}]` (только approved).
16. **Токен артисту:** в ответ `artist-confirm-token` добавить `expires_at` (показываем «действует до …») и уточнить права — сервер пускает только owner, а сайт пишет «owner/manager».
17. **Райдер и пресс-кит:** `GET .../rider` и `.../press-kit` отдают только внутренний путь. Нужно `file_name`, `size_bytes`, `updated_at` — показать «rider.pdf · 2,4 МБ · обновлён 12 сен». Плюс подписанная ссылка из п. 6 выше.
18. **Заработок менеджеру.** Сайт показывает заработок только owner, а API отдаёт любому члену агентства. Какое правило верное? Приложение сейчас показывает всем членам.
19. **Типы событий.** На сайте тип — свободный текст. Если нужна аналитика по типам, стоит завести список ключей (`club`, `festival`, `private`, `corporate`, `wedding`, `other`) и принимать `event_type_key` рядом со строкой.


### Ответ приложения на сверку бэкенда (2026-09-30)

Спасибо за аудит. Что приложение уже сделало само, чтобы не ждать:
- фото агентства в заявках артиста подставляем из `/artists/:id/companies`;
- тап по booking-уведомлению (в списке и по пушу) теперь открывает вкладку «Букинг», а не сайт; роль и заявку берём из `meta` (`request_id`, `company_id`, `spotify_artist_id`);
- картинку артиста в feed берём из `/me/follows`, если `artist_image_url = null`;
- концерты подписок собираем сами (по `/artists/:id/events`, до 4 запросов параллельно, кэш 15 минут).

**Порядок, который просим (сверху — важнее):**

1. **Утечка финансов заказчику** в `/my-requests` — согласны, первым делом.
2. **Пуши букинга ведут на заявку.** В FCM `data` рядом с `url` положить `request_id`, `company_id`, `spotify_artist_id` (сейчас пуш несёт только `url`, а `meta` есть лишь в списке уведомлений). `url` сменить на `/booking/requests/<id>` — приложение уже понимает такую ссылку и откроет заявку. Для пуша об оплате без смены статуса — отдельное событие `payment_changed`.
3. **Концерт-пуши:** менять не нужно — приложение само открывает `/artist/<slug>` (узнаёт Spotify id через `/artists/by-slug/:slug`). Ссылку со slug оставляйте.
4. **Быстрые фиксы:** фолбэк `artist_image_url` в feed; `is_new` у релиза (14 дней); `allowed[]` в ошибке artist PATCH и правило «выступил — не раньше даты события» на сервере; `company_name` + `expires_at` в превью токена; `company: {id, name, slug, image_url}` в заявках агентства и артиста.
5. **Раздельное «прочитано»** для агентства и артиста — сейчас бейдж непрочитанных у артиста гаснет, когда заявку открыл менеджер.
6. **Радар-агрегаты:** `GET /me/radar/artists`, `GET /me/concerts`, `POST /me/radar/seen` — заменят десятки запросов при открытии вкладки.
7. **Букинг-агрегат:** `GET /booking/me/overview`, `POST /booking/requests/:id/read`, `stage`, `my_role`, `allowed_statuses[]`, `event_timezone`. Пагинация: `limit/offset` + `total` нас устраивает, `cursor` не обязателен.
8. **Заработок:** `months[]` и `previous` в `/earnings`, `all_time` у агентства.
9. **Инфраструктура:** подписанные ссылки на райдер и пресс-кит (+ `file_name`, `size_bytes`, `updated_at`); universal links (`apple-app-site-association`, `assetlinks.json`); `lat/lng/city` у выступлений; `top_tracks[]` и `slug` у артистов агентства; `slug` + `artists[]` в `/artists/public/:id/booking`; отзыв pending-приглашения по id.

**Про проверку артистов в `POST /booking/requests`:** приложение шлёт только подтверждённых артистов выбранного агентства, но сервер сейчас примет заявку «в агентство X на артиста Y», который с X не связан, и она уйдёт чужому агентству. Просим всё же проверять связь (`artist_not_in_company`) — не срочно, но это защита от спама.

### Рейтинг и загрузка таблиц — пожелания приложения (2026-09-30)

Аудит загрузки всех списков приложения. Клиентская часть исправлена (картинки уменьшаются и декодируются вне UI-потока, DJ Mag дополняется через `POST /artists/batch`, дубли запросов склеены). Со стороны бэкенда:

1. **DJ Mag без фото, жанров и Score.** `/djmag/rankings` и `/djmag/rankings/all` отдают только место и имя; `imageUrl` заполнен лишь за 2020 и 2025. Просим JOIN с `artists`: `imageUrl` (фолбэк из artists), `genres`, `aimetryScore`, `slug`. Тогда приложению и сайту не нужен второй запрос `artists/batch`.
2. **`previousYearRank: "NEW"`** — строка в числовом поле. Лучше отдельное `isNew: true` и `previousYearRank: null`. Приложение уже понимает оба варианта.
3. **`/djmag/rankings/all` тяжёлый** (22 года × 100, ~300 КБ) и считается O(n²) (`getDJMagRankByNameAndYear` на каждую запись). Предрасчёт `previousYearRank` при импорте; для шкалы годов — лёгкий `GET /djmag/years`.
4. **`artists/top*` делает N+1** (`getTopMarkets`, `getRegionPopularityByArtist`, `getArtistVoteCount` на каждого) — приложению регионы не нужны: параметр `?lite=1` без них.
5. **Итоги года (`/dj/year-ranking`)** без `genres` — жанр в таблице пустой.
6. **Концерты подписок** (N запросов `artists/:id/events`) и **сводка аудитории** (6 запросов `preview` ради счётчиков) — нужны агрегаты (`GET /me/concerts` — см. выше; счётчики сегментов фанов одним ответом).
7. **`artists/batch`** ограничен 30 запросами в минуту на IP — у мобильных за одним NAT (оператор) лимит быстро кончится. Лучше лимит по пользователю/сессии.