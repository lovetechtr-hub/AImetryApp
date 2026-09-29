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
