# AGENTS.md — Инструкции и правила разработки для ИИ-агентов

Этот файл определяет архитектурные ограничения, технологический стек, правила работы с зависимостями и стандарты кодирования для проекта **AirAlert KMP** (см. `SPECIFICATION.md`). Следуй этим правилам при генерации любого кода.

---

## 1. Архитектурные границы (Kotlin Multiplatform)

Проект использует **Kotlin Multiplatform (KMP)** с **Compose Multiplatform** (на текущем этапе активен только Android-таргет, но архитектура должна быть строго готова к добавлению iOS/Desktop).

### Строгое разделение модулей:
1. **`commonMain` (85–90% кода):**
   * **ЗАПРЕЩЕНО** импортировать классы из пакетов `android.*`, `androidx.core.*`, `com.google.android.*` или `org.osmdroid.*`.
   * Здесь находятся: UI-экраны (Compose Multiplatform), ViewModel/StateFlow, навигация, Ktor HTTP-клиенты, модели `kotlinx.serialization`, логика «Тихих часов» (`kotlinx-datetime`), алгоритм Гаверсинуса, дедупликация уведомлений и интерфейсы (`expect`) для платформенных функций.
2. **`androidMain` (10–15% кода):**
   * Реализация `actual`-объявлений для Android.
   * Здесь находятся: `ForegroundService`, `BroadcastReceiver` (`BOOT_COMPLETED`), управление `NotificationChannel` и `VibrationEffect`, интеграция `osmdroid` через `AndroidView`, Google Play Services Location API и системный `Geocoder`.

---

## 2. Стек библиотек и зависимости (`libs.versions.toml`)

Используй только KMP-совместимые библиотеки в `commonMain`. **Не используй** Retrofit, Gson, Moshi или Android-only Room в общем коде.

### Рекомендуемый шаблон версий (актуально для Android SDK 35 / Kotlin 2.1+):
* **Kotlin:** `2.1.20` (или новее в ветке 2.1.x)
* **Compose Multiplatform:** `1.8.0` (или новее)
* **Android Gradle Plugin (AGP):** `8.9.0` (`compileSdk = 35`, `targetSdk = 35`, `minSdk = 26`)
* **Ktor Client:** `3.1.1` (`ktor-client-core`, `ktor-client-content-negotiation`, `ktor-serialization-kotlinx-json` в `commonMain`; `ktor-client-okhttp` в `androidMain`)
* **Kotlinx Serialization:** `1.8.0`
* **Kotlinx DateTime:** `0.6.2`
* **Koin (DI):** `4.0.2` (`koin-core`, `koin-compose`, `koin-compose-viewmodel`)
* **DataStore KMP:** `1.1.3` (`androidx.datastore:datastore-preferences-core` в `commonMain`)
* **Навигация:** `org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10` (или `cafe.adriel.voyager:voyager-navigator:1.1.0-beta03`)
* **Карты (только `androidMain`):** `org.osmdroid:osmdroid-android:6.1.20`
* **Геолокация (только `androidMain`):** `com.google.android.gms:play-services-location:21.3.0`
* **Секреты/Конфиг:** `com.codingfeline.buildkonfig` (для передачи API-ключа из `local.properties` в `commonMain`)

---

## 3. Критические правила реализации (Android 14/15 & KMP)

### 3.1. Фоновая служба (`ForegroundService`)
* В `AndroidManifest.xml` обязательно указать разрешение `FOREGROUND_SERVICE` и специализированный тип `FOREGROUND_SERVICE_DATA_SYNC` (или `FOREGROUND_SERVICE_SPECIAL_USE` с `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" ... />`), иначе приложение упадет с `MissingForegroundServiceTypeException` на Android 14+.
* Внутри сервиса опрашивай **только** лёгкий эндпоинт статуса тревог (`ukrainealarm.com`).
* Опрос радара целей (`neptun.in.ua`) должен запускаться **строго при открытом экране карты** (привязано к `Lifecycle.State.STARTED` / `RESUMED`) и останавливаться при сворачивании приложения.

### 3.2. Каналы уведомлений и вибрация
* В Android настройки вибрации `NotificationChannel` замораживаются системой сразу после первого создания канала.
* При смене пользователем пресета вибрации в настройках агент обязан реализовать удаление старого канала (`notificationManager.deleteNotificationChannel(oldId)`) и создание нового с инкрементируемым ID (`alert_channel_v1`, `alert_channel_v2` и т.д.).
* Используй `VibrationEffect.createWaveform(timings, amplitudes, -1)` с проверкой `vibrator.hasAmplitudeControl()` для мягкой вибрации.

### 3.3. Карта `osmdroid` в Compose
* Перед инициализацией `MapView` обязательно задать `Configuration.getInstance().userAgentValue = context.packageName` (иначе тайлы OpenStreetMap не будут загружаться из-за блокировки пустого User-Agent).
* Встраивай `MapView` через `androidx.compose.ui.viewinterop.AndroidView` с корректной проброской жизненных циклов `mapView.onResume()` и `mapView.onPause()` через `DisposableEffect` + `LifecycleEventObserver`.

### 3.4. Безопасность ключей
* **НИКОГДА** не записывай реальный API-ключ `ukrainealarm.com` в исходный код или Git.
* Считывай `UKRAINE_ALARM_API_KEY` из `local.properties` (с фолбэком на пустую строку или переменную окружения CI, чтобы сборка не падала при отсутствии файла).

---

## 4. Стандарты кода и обработка ошибок
* **Асинхронность:** Никаких блокирующих вызовов на `Dispatchers.Main`. Все сетевые запросы и геокодирование выполняются на `Dispatchers.IO`.
* **Отказоустойчивость сети:** Любой сетевой запрос оборачивать в `runCatching` или `try/catch` с таймаутами (`requestTimeoutMillis = 10_000`). Падение API радара (`neptun.in.ua`) не должно вызывать краш или блокировать обновление статусов тревог (`ukrainealarm.com`).
* **Дедупликация:** Перед отправкой уведомления всегда сверяй новый статус и `alertId` с сохранённым состоянием в `DataStore`. Уведомление отправляется только при фактическом изменении статуса.
* **UI/UX:** Следуй единой дизайн-системе **Tactical Dark HUD** (см. `DESIGN_PHILOSOPHY.md`). Все строки интерфейса выноси в Compose Multiplatform Resources (`composeResources/values/strings.xml` и `values-uk/strings.xml`), основной язык интерфейса — украинский/английский согласно проекту.

---

## 5. Дизайн-система и UI-философия (`DESIGN_PHILOSOPHY.md`)

Проект использует стиль **Tactical Dark HUD** поверх Compose Material 3 (`RadarTheme` в `AirAlertsTheme.kt`) с единой **2-уровневой системой рамок (`1.dp`)**:
* **Уровень 1 — Пассивные контейнеры:** Все статичные карточки (`SettingsSectionCard`, `SavedPointCard`), информационные плашки без нажатия (`AlertRefreshStatusPill`, `RegionThreatLegendPill`), блоки деталей внутри штор и строка атрибуции используют **`BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)`** (`#3B4F69`). Не используй `0.5.dp` и не оставляй токен `outline` (`#566E8C`) на кнопках — он предназначен только для полей ввода (`OutlinedTextField`) и ручек `ModalBottomSheet`.
* **Уровень 2 — Интерактивные элементы и акценты:** Все нажимаемые вторичные кнопки, иконочные кнопки действий (`Surface` в `PointsTabScreen`), `OutlinedButton`, `FilledTonalButton`, кнопки карты (`MapZoomControlsPill`, `ZoomToUkraineButton`, `RadarLayerToggleButton`), кликабельные плашки HUD (`ThreatGuideButton`, `ActiveThreatsSummaryPill`) и кликабельные карточки-переходы (например, База знаний) используют **`BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.48f))`** (либо активный семантический цвет: `tertiary` для `MAPA.UA`, `AlertRed` для тревоги/удаления/срочных уведомлений, `WarningAmber` для взрывов).
* **Кнопки управления на карте и карточках:** Для компактных кнопок карты (`42.dp`, в landscape `38.dp`, `RoundedCornerShape(14.dp)`) и кнопок действий в списке точек (`40.dp`, `RoundedCornerShape(12.dp)`) используй `Surface(onClick = ..., border = BorderStroke(1.dp, ...))`, а **не** `SmallFloatingActionButton` с `.border(...)` (иначе рамка рисуется по внешнему `48.dp` touch-target с отступом вокруг кнопки).
* **Шкала скруглений:** `10.dp` (нижняя строка атрибуции, чипы, компактные tonal-кнопки), `12.dp` (плашки HUD, иконочные кнопки, `OutlinedButton`, кликабельные строки), `14.dp` (кнопки карты и блоки деталей), `16.dp–18.dp` (основные карточки экранов).
* **Адаптивность:** Учитывай `isCompactLandscape` (`maxWidth > maxHeight && maxHeight < 520.dp`) и задавай `defaultMinSize` парным плашкам в `FlowRow`, чтобы мелкие индикаторы (например, таймер обновления) не выглядели сжатыми рядом с текстовыми плашками.

---

## 6. Команды проверки перед завершением задачи
Перед тем как считать этап разработки завершённым, обязательно проверь сборку и отсутствие ошибок компиляции:
* `./gradlew :composeApp:testAndroidHostTest` — запуск юнит-тестов (обязательно покрыть тестами логику «Тихих часов», дедупликацию и расчёт дистанции Гаверсинуса в `commonTest`).
* `./gradlew :app:assembleDebug` — полная сборка тестового Android APK (проверяет компиляцию общего `commonMain` и Android-кода).
