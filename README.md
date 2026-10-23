# Happ & Gemini Optimizer для Google Pixel 9 Pro XL (Kotlin / Android 15+)

Нативное Android-приложение (Jetpack Compose + `TileService`), разработанное специально для **Google Pixel 9 Pro XL**.
Автоматизирует работу прокси-клиента **Happ** (`com.happproxy`), управляет DNS-серверами (DoH / DoT), выводит кнопку **Вкл/Выкл** в верхнюю шторку быстрых настроек Android и настраивает раздельную маршрутизацию (Split-Tunneling), чтобы **Google Gemini** и зарубежные сервисы работали на максимальной скорости, а российские банки, Госуслуги, маркетплейсы и мессенджеры — напрямую без отключений.

## Ключевые возможности
1. **Кнопка Вкл/Выкл в шторке быстрых настроек (`HappQuickTileService.kt`)**:
   - Добавляет плитку **«Gemini + Happ»** в шторку Google Pixel.
   - По нажатию автоматически проверяет доступность узлов, активирует умный маршрут и запускает туннель в Happ (`com.happproxy`).
2. **Умная маршрутизация (`HappConfigManager.kt`)**:
   - Формирует и отправляет системный диплинк `happ://routing/onadd/{base64}` в приложение Happ.
   - Направляет трафик `gemini.google.com`, `generativelanguage.googleapis.com`, `aistudio.google.com` и приложения Google через зарубежный VLESS / Hysteria2 сервер с минимальным пингом (`lowestdelay`).
   - Направляет все домены `geosite:category-ru`, `geoip:ru` (Сбербанк, Т-Банк, Госуслуги, Яндекс, Ozon, ВК) напрямую (`DIRECT`), чтобы все остальные приложения на телефоне работали без сбоев.
3. **Управление DNS (`DnsOptimizer.kt`)**:
   - Переключает `RemoteDNS` (Cloudflare DoH `1.1.1.1`, Google DoH `8.8.8.8`, Quad9 `9.9.9.9`, AdGuard DNS) внутри Xray-ядра Happ и `DomesticDNS` (`77.88.8.8` Яндекс DNS) для российских сайтов.
   - Открывает системные настройки Private DNS на Pixel 9 Pro XL с автоматическим копированием DoT-адреса.

## Как собрать и установить APK прямо с телефона БЕЗ ПК (через GitHub Actions)
1. Зайдите на [github.com](https://github.com) через браузер Chrome на вашем Pixel 9 Pro XL и создайте новый репозиторий (например, `happ-gemini-pixel`).
2. Нажмите **Add file → Upload files** и загрузите файлы из этого архива.
3. Перейдите во вкладку **Actions** в вашем репозитории — облачный сервер GitHub автоматически запустит сборку файла `.github/workflows/build-apk.yml`.
4. Через 2 минуты скачайте готовый установочный файл **`HappGeminiOptimizer-debug.apk`** из раздела **Artifacts** прямо в память телефона и установите в один тап!
