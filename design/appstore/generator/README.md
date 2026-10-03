# Скриншоты App Store — генератор (стиль A · Dark & glow)

1. `python3 fetch.py` — реальные данные из открытого API DJMetry (концерты, точки карты) и фото топа — только для справки.
2. `python3 fake.py` — вымышленные артисты с фото Unsplash (лицензия Unsplash: бесплатно, коммерчески, без указания автора); концерты — настоящие площадки и даты; обложки релизов — градиенты.
3. `python3 build.py` — PNG через headless Chrome: `out/iphone` 1290×2796 (6,9″), `out/ipad` 2064×2752 (13″). Нужен `countries.geojson` из `shared/src/commonMain/composeResources/files/`.

Запускать в отдельной папке (скачанные фото и PNG в git не кладём).
