# Карта миграции: asknx/OpenApsAIMI -> upstream/AndroidAPS 4.0.0

> [!IMPORTANT]
> Данный документ является единым источником правды для поэтапной миграции кастомных доработок и алгоритмов AIMI из форка на целевую архитектуру AndroidAPS 4.0.0 (Kotlin Multiplatform + Jetpack Compose + Material 3 + Metro DI).

## 1. Сводка аудита и параметры репозиториев

- **Локальная ветка:** `feature/aaps-4.0.0-migration`
- **Форк (origin):** `https://github.com/asknx/OpenApsAIMI.git`
- **Форк-источник (parent):** `https://github.com/MTR93600/OpenApsAIMI.git`
- **Целевой апстрим (upstream):** `https://github.com/nightscout/AndroidAPS.git`
- **Целевой релизный тег:** `4.0.0` (commit `f77abd9b82b308e8fc330da0d7a31370fb4a0f49`)
- **Точка расхождения (merge-base):** `02052250ac0ecd0de5a5d0244ebd0523f158119f` (AAPS ~3.4.2.2)
- **Всего изменённых / добавленных файлов в форке:** `914`

### Статистика по модулям

| № | Модуль / Категория | Файлов | Описание |
|---|---|---|---|
| 1 | Сборка, Gradle, Manifest, DI | 33 | Скрипты сборки, каталоги версий, модули DI (Dagger -> Metro) |
| 2 | Ядро AIMI и алгоритмы | 89 | Adaptive Basal, Autodrive, Neural Net, ISF, Kalman, TFLite |
| 3 | PKPD, Trajectory Guard & Safety | 26 | Профайлер инсулина, поглощение, SafetyNet, SMB Damping |
| 4 | Физиология, Health Connect & SOS | 39 | Шагомеры, щитовидная модель, гестация, экстренные оповещения |
| 5 | AI Coaching, Советники и AI-Аудитор | 22 | AiCoachingService, Dual Brain Auditor, отчеты, LLM клиент |
| 6 | Meal Advisor & AI Vision | 10 | Мультимодельное зрение (Gemini, Claude, GPT, GLM, DeepSeek), FatSecret |
| 7 | Женский цикл и эндометриоз (WCycle) | 8 | Оценка гормональной резистентности, WCycleFacade, логирование |
| 8 | Базовые модели данных, БД и ключи | 54 | Data models, Room DAO/Entities, Keys, PersistenceLayer |
| 9 | Драйверы помп и плагины синхронизации | 98 | Apex Pump, Combo v2 BT fixes, Smoothing, Ottai CGM, Garmin, Remote |
| 10 | UI-экраны, разметки и стили (подлежат Compose) | 210 | Экраны Activity/Fragment, диалоги, XML-ресурсы для миграции на M3 |
| 11 | Автоматизированные тесты | 58 | Unit-тесты алгоритмов AIMI, PKPD, WCycle, Autodrive |
| 12 | Документация и исследования | 168 | Архитектурные описания, диаграммы, симуляции, руководства |
| 13 | Технические артефакты под очистку | 99 | Случайные bin/, *.bak, *.rej, baselineProfiles, build_output (удалены в Этапе 2) |

### Статус выполнения этапов миграции

- [x] **Этап 1: Аудит и создание карты миграции** (Завершено, коммит `9f776dad99`)
- [x] **Этап 2: Слияние с upstream AndroidAPS 4.0.0 и разрешение конфликтов** (Завершено, коммит `513b6b9f70`)
  - [x] Очистка 99 мусорных артефактов (Модуль 13) — коммит `d2b04f56c0`
  - [x] Слияние ветки с `upstream/4.0.0` без отката архитектуры 4.0.0
  - [x] Разрешение конфигурационных файлов (`libs.versions.toml`, `Versions.kt`, `build.gradle.kts`, `settings.gradle`)
  - [x] Разрешение конфликтов ядра (`ManufacturerType`, `PumpType`, `PumpCapability`, `InterfaceIDs`, `APSResultExtension`, `RT.kt`, `DetermineBasalSMB.kt`, `AndroidBluetoothDevice.kt`)
  - [x] Разрешение конфликтов Application & Config (`MainApp.kt`, `ConfigImpl.kt`, `UiInteractionImpl.kt`, `AndroidManifest.xml`)
  - [x] Разрешение строковых ресурсов и плагинов (`strings.xml`, `LocationService`, `ObjectivesPlugin`, `ExponentialSmoothingPlugin`, `GarminPlugin`, `LoopHubImpl`, `NsIncomingDataProcessor`, `StepCountListener`)
  - [x] Завершение merge-коммита: `merge: upstream AndroidAPS 4.0.0 base` (`513b6b9f70`)
- [x] **Этап 3: Адаптация логики и компиляция ядра (Модули 1, 2, 3, 4, 7, 8, 9)** (Завершено, сборка `:app:assembleFullDebug` успешна)
  - [x] Перевод всех DI-модулей на Metro DI (`dev.zacsweers.metro`): `SharedImplBindings`, `AppRootGraph`, `ApsAndroidPluginRegistrations`, `WCycleBindings`, `SourceMetroGraph`, `SyncMemberInjectors`, `ApexBindings`, `ApexMemberInjectors`
  - [x] Модуль 1: Адаптация Gradle build-скриптов к KMP (commonMain/androidMain), обновление TFLite до 2.14.0, CameraX, Health Connect, Metro plugin
  - [x] Модули 8 и 9: PersistenceLayer, базовые модели данных, TIR, DstHelper, CloudBackup, Apex Pump (`:pump:apex`), Ottai CGM plugin (`@IntKey(560)`), Adaptive Smoothing (`@IntKey(650)`), Garmin Sync, Remote Control
  - [x] Модули 2, 3, 4, 7: Сохранение 100% математической логики AIMI (Adaptive Basal, Autodrive, Neural Net, ISF blender, Kalman, TFLite, PKPD, Trajectory Guard, SafetyNet, SMB Damping, Physio, Steps, SOS, WCycle)
  - [x] Полная компиляция и упаковка APK: `:app:compileFullDebugKotlin` и `:app:assembleFullDebug` **BUILD SUCCESSFUL**
- [x] **Двухконтурный Git-Diff аудит и верификация Этапа 4 (Модули 5, 6 — AICoaching, DualBrain, Vision, Apex, Loop)**
  - [x] **Контур А (Сохранность фич форка против `pre-aaps4-backup`):**
    - `pump/apex`: восстановлена проверка `applyBasalConstraints()`, локализованные `Apex*Key` (`app.aaps.pump.apex.R.string.*`), `getPreferenceScreenContent()` с `PreferenceSubScreenDef` и `withEntries`.
    - `LoopPlugin.kt`: восстановлены `skipQueueCheck` для `MealAdvisor_Direct` и `tempBasalFallback`, трекинг `lastGlucoseWorkerLoopWallClock`, фоновый `autodrive` таймер с периодичностью `ApsMaxSmbFrequency` при `OApsAIMIautoDrive`, исполнение SMB в `acceptChangeRequest()` при `constraintsProcessed.isBolusRequested`.
    - `WCycleBindings.kt`: зарегистрирован `@Provides fun provideEndometriosisAdjuster()`.
    - Очищены UTF-8 BOM маркеры в `DetermineBasalAIMI2.kt`, `ContextManager.kt`, `ContextActivity.kt`, `MealAdvisorActivity.kt`.
    - Заглушка `DashboardPreviewActivity.kt` с ресурсом `dashboard_preview_title` для сборки `:app`.
  - [x] **Контур Б (Целостность базы upstream 4.0.0):**
    - Ядро AAPS 4.0.0 (`core/*`, `shared/*`, KMP структура, Metro DI, драйверы помп Dash/Dana/Eros/Medtronic, NSClient v3) сохранено в неприкосновенности.
  - [x] **Верификация компиляции и ключей:**
    - `ApexPumpPlugin.kt`: проверены все 6 групп ключей (`SerialNumber`, `MaxBasal`, `MaxBolus`, `FirmwareVer`, `AlarmSoundLength`, `CalcBatteryType`).
    - `OpenAPSAIMIPlugin.kt`: интегрированы все 45 пользовательских настроек (оставшиеся 5 ключей `OApsAIMILastEstimatedCarbs`, `OApsAIMILastEstimatedCarbTime`, `OApsAIMIUnstableModeState`, `OApsAIMIContextStorage`, `LastPrebolusTime` являются внутренними переменными состояния алгоритма).
    - `GoogleDriveManager.kt` / `GoogleDriveProvider.kt`: поддержка `.jsonl` и маршрутизация `.json`, `.jsonl`, `.csv` с "aimi"/"autodrive" в `CloudConstants.CLOUD_PATH_AIMI`. Удалены мёртвые дубли из `plugins/configuration/src/main`.
    - `:plugins:aps:assemble` — **BUILD SUCCESSFUL**
    - `:pump:apex:assemble` — **BUILD SUCCESSFUL**
    - `:app:assembleFullDebug` — **BUILD SUCCESSFUL** (успешная компоновка и упаковка APK)
- [x] **Этап 5: Миграция UI-экранов на Jetpack Compose / Material 3 (Модуль 10)**
  - [x] **Этап 5A: Помпа Apex, Meal Advisor, Aimi Profile Advisor, Aimi Mode Settings, Context, Remote Control (Модули 10.1, 10.2, 10.3, 10.7, 10.8)** (Завершено, сборка `:app:assembleFullDebug` успешна)
  - [x] **Этап 5B: Dashboard, Overview, карточки статуса, круговой индикатор, график глюкозы, кастомные View (Модули 10.4, 10.5)** (Завершено: легаси XML DashboardFragment и устаревшие View удалены; нативный Compose OverviewScreen.kt расширен слотом `aimiOverview` и интегрирован с M3-виджетом `AimiOverviewCard`: быстрый запуск Meal Advisor, компактный ряд физиологических метрик, бейдж Dual-Brain Auditor)
  - [x] **Этап 5C: Диалоги болюса, меню, вспомогательные экраны и экраны Wear OS (Модули 10.6, 10.9, 10.10)** (Завершено: 3-dots overflow в MainTopBar/MainScreen и боковое меню MainDrawer с нативными иконками Ic* для всех 8 подменю AIMI, роутинг LLM Gemini по умолчанию с нормализацией endpoint URL и диагностикой HTTP/URL, активный болюс ApexPump с прогресс-баром и отменой)
  - [x] **Финальная полировка Этапа 5, клиническая аналитика Juggluco NG и генератор профилей:**
    - `AdjustmentDetailsActivity`: мигрирована на `MetroAppCompatActivity` с автосборкой актуального состояния петли/глюкозы/дозировок и кнопкой «Обновить».
    - `ContextActivity`: прозрачный вывод реальной ошибки LLM (`contextLLMClient.lastError`) при отсутствии намерений.
    - `MealAdvisorActivity`: реактивное переключение между 9 AI-провайдерами, поддержка кастомных моделей для OpenAI/Gemini.
    - `StringKey` / `AimiStringKey`: строгая типизация `PreferenceType.LIST` и `entriesLiterals` для списочных настроек режимов.
    - Глубокая аналитика в стиле Juggluco NG: строгая поддержка единиц пациента (`mmol/L` vs `mg/dL`), 5 зон TIR, статистика (IQR, CV%, GVI, PSG, GRI 0-100 с зоной и компонентами гипо/гипер), 24-часовой холст AGP Canvas с процентильными полосами (P10, P25, медиана, P75, P90), подневная сетка TIR, анализ эпизодов (>=15 мин с интервалами до пика), преболюсный анализ приёмов пищи.
    - Интерактивный диалог предложений и создание реального профиля в `LocalProfile` («AIMI dd.MM HH:mm») с переключением и корректировкой параметров AIMI.
- [x] **Этап 6: Адаптация и запуск unit-тестов (Модуль 11), верификация и стабилизация**
  - [x] Адаптированы unit-тесты `:pump:apex:testDebugUnitTest`: исправлены вызовы конструкторов `TDDEntry` и `StatusV1` в `CommandsTest.kt` (8 passed, 0 skipped, 0 failed).
  - [x] Запущены host unit-тесты `:plugins:smoothing:testAndroidHostTest`: 8 passed, 0 skipped, 0 failed.
  - [x] Запущены host unit-тесты `:plugins:aps:testAndroidHostTest`: 350 passed, 0 skipped, 0 failed.
  - [x] Полная верификация сборки `:app:assembleFullDebug`: **BUILD SUCCESSFUL**. APK собран и готов к установке.
- [x] **Этап 7: Предполётный медицинский аудит (Pre-Flight Medical & Upgrade Audit)**
  - [x] **Задача 1: Исправление медицинского бага с единицами ISF (`mmol/L` vs `mg/dL`) и сохранением временных блоков в `AimiAdvisorService.kt`**:
    - `currentIsf` в `generateFullProposal()` пересчитывается в `mmol/L/U` при `profileFunction.getUnits() == GlucoseUnit.MMOL`.
    - Добавлен флаг `val isMmol: Boolean = false` в `FullAdvisorProposal`.
    - В `createAndActivateAimiProfile()` устранено затирание расписания: для `carbratio` и `sens` сохраняются все существующие временные блоки расписания с масштабированием значений по факторам `(proposed / current).coerceIn(0.85, 1.15)` с округлением до 2 знаков.
    - В `AimiProfileAdvisorActivity.kt` отображение и экспорт предложений ISF выводятся в единицах профиля (`ммоль/л/U` с 2 знаками или `mg/dL/U`).
  - [x] **Задача 2: Бесшовный апгрейд и инженерный режим AIMI**:
    - Добавлены ключи `AimiDeveloperOptionsUnlocked` и `AimiEngineeringBypassEnabled` в `BooleanKey.kt` (`exportable = true`).
    - Ключ `BooleanKey.AimiEngineeringBypassEnabled` вынесен в `Global User Preferences` в настройках плагина `OpenAPSAIMIPlugin.kt`.
    - В `OpenAPSAIMIPlugin.onStart()` реализована автомиграция: при наличии файла `engineering_mode` или ранее запущенных целях (Objectives 2/3 / ApsUseSmb) ключи автоматически активируются.
    - Пасхалка 7 нажатий интегрирована в `AboutDialog.kt` (по иконке и заголовку) и `UserManualActivity.kt` (по заголовку TopAppBar) с тостами/снекбарами на 5/6/7 тапах.
    - Интеграция в ядро: `ConfigImpl.kt` (`isEngineeringMode` и `isEngineeringModeOrRelease`), `SafetyPlugin.kt` (`isClosedLoopAllowed` и `isAdvancedFilteringEnabled`), `ObjectivesPlugin.kt` (беспрепятственный пропуск всех ограничений при активном байпасе).
  - [x] **Задача 3: 100% гарантия сохранения настроек и бэкапа**:
    - `OpenAPSAIMIPlugin` переведён на `PluginBaseWithPreferences` с регистрацией `AimiLongKey.entries + AimiStringKey.entries` в `ownPreferences`.
    - В `PreferencesImpl.isExportableKey()` добавлены префиксы `"aimi_"`, `"key_oaps_aimi_"`, `"oa_aimi_"`, `"key_aimi_"`, `"apex_"`, `"garmin_"`, `"LocalProfile_"`, `"Objectives_"`.
    - Гарантирована инициализация `AimiBackupManager` через Metro DI.
  - [x] **Задача 4: Синтетический предполётный тест безопасности**:
    - Создан `AimiPreFlightSafetyVerificationTest.kt` (гипо-защита SafetyNet & ControlBarrierShield, сохранение расписания профиля в mmol/L и mg/dL, метрики Juggluco NG, экспорт ключей бэкапа). Все 350 тестов в `:plugins:aps:testAndroidHostTest` пройдены успешно.
  - [x] **Задача 5: Итоговая сборка APK**:
    - `:app:assembleFullDebug` успешно скомпилирован и упакован.

---

## 2. Эталоны Compose и стандарты upstream/AndroidAPS 4.0.0

В AndroidAPS 4.0.0 произошла фундаментальная архитектурная модернизация:
1. **Переход на Kotlin Multiplatform (KMP) & Compose Multiplatform** (общие модули `commonMain`, Android-специфичные `androidMain`).
2. **Полный отказ от Dagger 2 в пользу Metro DI** (`dev.zacsweers.metro`):
   - Вместо Dagger `@Inject` используется `dev.zacsweers.metro.Inject`.
   - Вместо `@IntoMap` / `@Binds` используется `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`.
   - Регистрация ViewModel через `@ViewModelKey`.
   - Инъекция во View/Composable через `metroViewModel()` (из `app.aaps.core.ui.compose.metroViewModel`).
3. **Material 3 Theming и экраны-обёртки:**
   - Тема: `AapsTheme { ... }` (обёртка над `MaterialTheme` с динамическим light/dark colorScheme).
   - Адаптивное масштабирование планшетов: `typographyScaleFor(shortSideDp, longSideDp)` и `LocalAapsScale` (порог `TABLET_MIN_SW_DP = 600`).
   - Стандартные CompositionLocal: `LocalPreferences`, `LocalDateUtil`, `LocalConfig`, `LocalDecimalFormatter`, `LocalSnackbarHostState`, `LocalElementColors`, `LocalGeneralColors`.
   - Отступы и размеры строго через `AapsSpacing.*` (`AapsSpacing.small`, `medium`, `large`, `extraLarge`, `xxLarge`, `fabClearance`).
   - Текстовые ресурсы через мультиплатформенные `stringResource(CoreUiStrings.xxx)` или `stringResource(InterfacesStrings.xxx)`.
4. **Паттерн архитектуры экрана (MVI / StateFlow / SideEffects):**
   - `StateFlow<UiState>` + `@Immutable data class UiState(...)`.
   - Потребление состояния: `val state by viewModel.uiState.collectAsStateWithLifecycle()`.
   - Побочные эффекты (Side Effects) через Kotlin Channel / Flow, сбор в Composable через `LaunchedEffect(Unit) { viewModel.sideEffect.collect { ... } }`.

### Детальные эталоны из upstream/4.0.0

#### Эталон 1: Полнофункциональный экран списка/редактора (CRUD) — `FoodManagementScreen.kt`
- **Путь:** `ui/src/commonMain/kotlin/app/aaps/ui/compose/foodManagement/`
- **Компоненты Material 3:** `Scaffold`, `AapsTopAppBar` с встроенным `AapsSearchField`, `IconButton` с `Icons.AutoMirrored.Filled.ArrowBack`, `FloatingActionButton`, `ElevatedCard` (`CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)`), `FlowRow` с чипами `FilterChip`, `ModalBottomSheet` с `rememberModalBottomSheetState(skipPartiallyExpanded = true)`, `OutlinedTextField`.
- **Снекбары и Undo:** Использование `LocalSnackbarHostState.current` для показа `showSnackbar(message, actionLabel = "Undo")`.
- **ViewModel:** Наследование от `ViewModel()`, аннотации `@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())`, `@ViewModelKey`, `@Inject`.

#### Эталон 2: Диалог действия / формы подтверждения — `ExtendedBolusDialogScreen.kt`
- **Путь:** `ui/src/commonMain/kotlin/app/aaps/ui/compose/extendedBolusDialog/`
- **Паттерн:** Полноэкранный Scaffold с TopAppBar (кнопка закрытия `Icons.Filled.Close`), форма ввода на карточке `Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface))` с компонентами `NumberInputRow`, нижняя панель действий `bottomBar` с `Button(modifier = Modifier.fillMaxWidth().bottomBarSafeArea()...)`.
- **Подтверждение и защита:** Использование стандартного `ElementConfirmationDialog` и `OkCancelDialog`.

#### Эталон 3: Глобальная тема и стили — `AapsTheme.kt`
- **Путь:** `core/ui/src/commonMain/kotlin/app/aaps/core/ui/compose/AapsTheme.kt`
- **Структура цветов:** Базовый Material 3 `lightColorScheme()` / `darkColorScheme()` расширяется специализированными палитрами: `AapsTheme.profileHelperColors`, `AapsTheme.elementColors`, `AapsTheme.generalColors`, `AapsTheme.snackbarColors`.
- **Чистый рендеринг текста:** Отключение лишних системных отступов шрифтов через `.withoutFontPadding()` и `LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)`.

---

## 3. Чек-лист файлов по модулям

### Модуль 1: Сборка, Gradle, Manifest, DI-модули (33 файлов)
*Ключевая задача: Обновление конфигурации Gradle под KMP/Compose, обновление Manifest и миграция модулей Dagger на синтаксис Metro DI (`@ContributesIntoMap`, `@Inject`).*

- [x] `[M]` `.github/workflows/aaps-ci.yml`
- [x] `[A]` `.github/workflows/android-build.yml`
- [x] `[M]` `.github/workflows/cherry-pick-ci.yml`
- [x] `[M]` `.github/workflows/pr-ci.yml`
- [x] `[M]` `.gitignore`
- [x] `[A]` `.vscode/settings.json`
- [x] `[M]` `app/src/androidTest/kotlin/app/aaps/di/AlgModule.kt`
- [x] `[M]` `app/src/main/AndroidManifest.xml`
- [x] `[M]` `app/src/main/kotlin/app/aaps/di/ActivitiesModule.kt`
- [x] `[M]` `app/src/main/kotlin/app/aaps/di/AppComponent.kt`
- [x] `[M]` `app/src/main/kotlin/app/aaps/di/PluginsListModule.kt`
- [x] `[M]` `buildSrc/src/main/kotlin/Versions.kt`
- [x] `[M]` `gradle.properties`
- [x] `[M]` `gradle/libs.versions.toml`
- [x] `[M]` `plugins/aps/src/main/AndroidManifest.xml`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/di/AIMIPhysioModuleMTR.kt`
- [x] `[M]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/di/ApsModule.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/di/AIMIStepsProviderModuleMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/di/WCycleModule.kt`
- [x] `[M]` `plugins/automation/src/main/AndroidManifest.xml`
- [x] `[M]` `plugins/main/src/main/AndroidManifest.xml`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/di/OverviewModule.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/di/SkinsModule.kt`
- [x] `[M]` `plugins/source/src/main/kotlin/app/aaps/plugins/source/di/SourceModule.kt`
- [x] `[M]` `plugins/sync/src/main/AndroidManifest.xml`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/di/SyncModule.kt`
- [x] `[A]` `pump/apex/proguard-rules.pro`
- [x] `[A]` `pump/apex/src/main/AndroidManifest.xml`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/di/ApexModule.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/di/ApexServicesModule.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/di/ApexUiModule.kt`
- [x] `[M]` `settings.gradle`
- [x] `[M]` `ui/src/main/AndroidManifest.xml`

### Модуль 2: Ядро AIMI и алгоритмы управления (89 файлов)
*Ключевая задача: Полное сохранение математического ядра AIMI, включая нейросеть, адаптивный базал, Autodrive MPC, фильтр Калмана, расчет ночной резистентности и интеграцию TFLite моделей.*

- [x] `[A]` `app/src/main/assets/model.tflite`
- [x] `[A]` `app/src/main/assets/modelUAM.tflite`
- [x] `[M]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/loop/LoopPlugin.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/AIMIAdaptiveBasal.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/AimiModelHandler.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAIMI2.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalCoordinator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/GlucoseStatusCalculatorAimi.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/ISF/IsfAdjustmentEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/ISF/IsfBlender.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/KalmanFilter.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/NightGrowthResistanceLearner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/NightGrowthResistanceMonitor.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/OpenAPSAIMIPlugin.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/StepService.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/TrainingConfig.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/activity/ActivityContext.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/activity/ActivityManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/aimiNeuralNetwork.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/AutodriveEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/controller/MpcController.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/estimator/ContinuousStateEstimator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/AutodriveAuditor.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/AutodriveBackfillWorker.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/AutodriveDataBackfiller.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/AutodriveDataLake.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/AutodriveNeuralTrainer.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/AutodriveNeuralTrainerWorker.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/MechanismAttentionGate.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/learning/OnlineLearner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/models/AutoDriveModels.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/safety/AutoDriveGater.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/safety/ControlBarrierShield.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/BasalDecisionEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/BasalHistoryUtils.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/BasalPlanner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/DynamicBasalController.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/T3cTrajectoryContext.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/carbs/CarbsAdvisor.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/AimiSmbComparator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/AimiSmbSimulator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/ComparisonCsvParser.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/ComparisonData.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/KpiCalculator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/PerformanceScorer.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/VirtualGlucoseEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/VirtualSmbState.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ContextInfluenceEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ContextIntent.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ContextIntentDeserializer.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ContextLLMClient.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ContextManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ContextParser.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/decision/DecisionPolicy.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/decision/DecisionPolicyFactory.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/extensions/GlucoseStatusExtensionAIMI.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/inflammatory/InflammationAdjuster.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/keys/AimiLongKey.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/keys/AimiStringKey.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/learning/BasalAdaptiveTrainerWorker.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/learning/BasalLearner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/learning/BasalNeuralLearner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/learning/T3cNeuralTrainerWorker.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/learning/UnifiedReactivityLearner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/llm/gemini/GeminiModelResolver.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/llm/gemini/GeminiOAuthManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/ml/AimiSmbModelStore.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/ml/AimiSmbTrainer.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/model/AdvisorSeverity.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/model/Constants.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/model/ContextIntent.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/model/DecisionModels.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/model/Models.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/plugins/AimiPluginSystem.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/plugins/impl/SafetyAggressionPlugin.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/plugins/impl/StableControlPlugin.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/plugins/impl/TherapyAdvisorPlugin.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/ports/Ports.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/therapy.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/AimiBackupManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/AimiLogger.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/AimiStorageHelper.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/ContextExtensions.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/JsonSafeLogger.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/LogcatExporter.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/RtInstrumentationHelpers.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/ValidationUtils.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/validation/PumpCapabilityValidator.kt`
- [x] `[M]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSSMB/DetermineBasalSMB.kt`

### Модуль 3: PKPD, Trajectory Guard & Системы безопасности (26 файлов)
*Ключевая задача: Сохранение контуров безопасности, демпфирования SMB, контроля поглощения углеводов/инсулина в реальном времени и фазового анализа траекторий глюкозы.*

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/AdaptivePkPdEstimator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/AdvancedPredictionEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/InsulinActionProfiler.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/InsulinActionState.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/IsfFusion.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/PkPdCore.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/PkPdCsvLogger.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/PkPdIntegration.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/PkpdAbsorptionGuard.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/RealTimeInsulinObserver.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/SmbDamping.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/SmbTbrThrottleLogic.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/HighBgOverride.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/HypoTools.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/SafetyDecision.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/SafetyNet.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/BypassHeuristics.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/MealHighIobPolicy.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbDampingUsecase.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbInstructionExecutor.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbQuantizer.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/trajectory/PhaseSpaceModels.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/trajectory/TrajectoryGuard.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/trajectory/TrajectoryHistoryProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/trajectory/TrajectoryMetricsCalculator.kt`

### Модуль 4: Физиология, Health Connect, Steps, Thyroid & SOS (39 файлов)
*Ключевая задача: Сохранение физиологического контекста (шагомеры, сон, вариабельность ритма, щитовидный эстиматор, гестационный автопилот, экстренные SMS-оповещения).*

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIHealthConnectPermissionActivityMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIHealthConnectPermissions.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIHealthConnectPermissionsHandlerMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIInsulinDecisionAdapterMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMILLMPhysioAnalyzerMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioBaselineModelMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioContextEngineMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioContextStoreMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioDataModelsMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioDataRepositoryMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioFeatureExtractorMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioManagerMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioOutcomes.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioPipelineWatchdogMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIPhysioWorkersMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/AIMIVectorModels.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/HealthContextRepository.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/HealthContextSnapshot.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/PhysioAggregator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/gate/CosineTrajectoryGate.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidDiagnosticsLogger.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidEffectModel.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidModels.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidPreferences.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidSafetyGates.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidStateEstimator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/sos/AIMIEmergencySosPermissionActivityMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/sos/EmergencySosManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMICompositeStepsProviderMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIDatabaseStepsProviderMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIHealthConnectStepsProviderMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIHealthConnectSyncServiceMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIHealthConnectWorkerMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIPhoneStepsProviderMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIPhoneStepsSyncServiceMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIStepsManagerMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/AIMIStepsProviderMTR.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/ActivityVitalsProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/steps/UnifiedActivityProviderMTR.kt`

### Модуль 5: AI Coaching, Советники и AI-Аудитор (Dual Brain) (22 файлов)
*Ключевая задача: Сохранение автономного ИИ-аудитора решений (LocalSentinel, DualBrain), сервиса генерации клинических отчетов и сервиса коучинга AICoachingService.*

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/AdvisorModels.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/AiCoachingService.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/AimiAdvisorService.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/AimiClinicalReportEngine.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/PkpdAdvisor.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AimiStateTransitionManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorAIService.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorDataCollector.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorDataStructures.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorOrchestrator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorPromptBuilder.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorStatusTracker.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorVerdictCache.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/DecisionModulator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/DualBrainHelpers.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/LocalSentinel.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/TherapyPromptBuilder.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/model/AuditorUIState.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/model/StateTransitionManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/data/AdvisorHistoryRepository.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/diag/AimiDiagnosticsManager.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/gestation/GestationalAutopilot.kt`

### Модуль 6: Meal Advisor & AI Vision Providers (10 файлов)
*Ключевая задача: Сохранение логики распознавания еды, интеграций с LLM Vision (Gemini, Claude, GPT, GLM, DeepSeek) и внешними базами (FatSecret, OpenFoodFacts).*

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/AIVisionProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/BarcodeScanner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/ClaudeVisionProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/DeepSeekVisionProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/FatSecretService.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/FoodRecognitionService.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/GeminiVisionProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/GlmVisionProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/OpenAIVisionProvider.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/OpenFoodFactsService.kt`

### Модуль 7: Женский цикл и эндометриоз (WCycle) (8 файлов)
*Ключевая задача: Сохранение фасадного модуля WCycle, эстиматора фаз цикла, корректора резистентности и модуля эндометриоза.*

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/EndometriosisAdjuster.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleAdjuster.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleCsvLogger.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleEstimator.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleFacade.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleLearner.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCyclePreferences.kt`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleTypes.kt`

### Модуль 8: Базовые модели данных, БД, интерфейсы и ключи (54 файлов)
*Ключевая задача: Синхронизация сущностей Room, DAOs, интерфейсов PersistenceLayer, Preference Keys с 4.0.0 KMP-моделями.*

- [x] `[M]` `app/src/main/assets/logback.xml`
- [x] `[M]` `app/src/main/kotlin/app/aaps/MainApp.kt`
- [x] `[M]` `app/src/main/kotlin/app/aaps/implementations/ConfigImpl.kt`
- [x] `[M]` `app/src/main/kotlin/app/aaps/implementations/UiInteractionImpl.kt`
- [x] `[M]` `app/src/main/kotlin/app/aaps/receivers/ChargingStateReceiver.kt`
- [x] `[M]` `app/src/main/kotlin/app/aaps/receivers/KeepAliveWorker.kt`
- [x] `[M]` `core/data/src/main/kotlin/app/aaps/core/data/configuration/Constants.kt`
- [x] `[M]` `core/data/src/main/kotlin/app/aaps/core/data/pump/defs/DoseStepSize.kt`
- [x] `[M]` `core/data/src/main/kotlin/app/aaps/core/data/pump/defs/ManufacturerType.kt`
- [x] `[M]` `core/data/src/main/kotlin/app/aaps/core/data/pump/defs/PumpCapability.kt`
- [x] `[M]` `core/data/src/main/kotlin/app/aaps/core/data/pump/defs/PumpType.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/aps/APSResult.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/aps/AutosensResult.kt`
- [x] `[A]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/aps/GlucoseStatusAIMI.kt`
- [x] `[A]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/aps/OapsProfileAimi.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/aps/RT.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/db/PersistenceLayer.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/iob/IobCobCalculator.kt`
- [x] `[A]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/maintenance/CloudBackupConstants.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/maintenance/ImportExportPrefs.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/notifications/Notification.kt`
- [x] `[A]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/rx/events/EventAdaptiveSmoothingQuality.kt`
- [x] `[A]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/rx/events/EventAimiCloudBackupResult.kt`
- [x] `[A]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/rx/events/EventAimiCloudBackupTrigger.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/smoothing/Smoothing.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/stats/TIR.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/stats/TirCalculator.kt`
- [x] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/utils/HardLimits.kt`
- [x] `[M]` `core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt`
- [x] `[M]` `core/keys/src/main/kotlin/app/aaps/core/keys/BooleanNonKey.kt`
- [x] `[M]` `core/keys/src/main/kotlin/app/aaps/core/keys/DoubleKey.kt`
- [x] `[M]` `core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt`
- [x] `[M]` `core/keys/src/main/kotlin/app/aaps/core/keys/IntentKey.kt`
- [x] `[M]` `core/keys/src/main/kotlin/app/aaps/core/keys/StringKey.kt`
- [x] `[M]` `core/nssdk/src/main/kotlin/app/aaps/core/nssdk/NSAndroidClientImpl.kt`
- [x] `[M]` `core/objects/src/main/kotlin/app/aaps/core/objects/profile/ProfileSealed.kt`
- [x] `[M]` `core/utils/src/main/kotlin/app/aaps/core/utils/extensions/BluetoothAdapterExtension.kt`
- [x] `[M]` `database/impl/src/main/kotlin/app/aaps/database/AppRepository.kt`
- [x] `[M]` `database/impl/src/main/kotlin/app/aaps/database/daos/CarbsDao.kt`
- [x] `[M]` `database/impl/src/main/kotlin/app/aaps/database/daos/TherapyEventDao.kt`
- [x] `[M]` `database/impl/src/main/kotlin/app/aaps/database/daos/UserEntryDao.kt`
- [x] `[M]` `database/impl/src/main/kotlin/app/aaps/database/entities/APSResult.kt`
- [x] `[M]` `database/impl/src/main/kotlin/app/aaps/database/entities/embedments/InterfaceIDs.kt`
- [x] `[M]` `database/persistence/src/main/kotlin/app/aaps/database/persistence/PersistenceLayerImpl.kt`
- [x] `[M]` `database/persistence/src/main/kotlin/app/aaps/database/persistence/converters/APSResultExtension.kt`
- [x] `[M]` `database/persistence/src/main/kotlin/app/aaps/database/persistence/converters/PumpTypeExtension.kt`
- [x] `[M]` `implementation/src/main/kotlin/app/aaps/implementation/aps/DetermineBasalResult.kt`
- [x] `[M]` `implementation/src/main/kotlin/app/aaps/implementation/stats/TirCalculatorImpl.kt`
- [x] `[M]` `implementation/src/main/kotlin/app/aaps/implementation/stats/TirImpl.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/persistentNotification/DummyService.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/smsCommunicator/SmsCommunicatorPlugin.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/smsCommunicator/otp/OneTimePassword.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/iob/iobCobCalculator/IobCobCalculatorPlugin.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/iob/iobCobCalculator/data/AutosensDataStoreObject.kt`

### Модуль 9: Драйверы помп и плагины синхронизации (98 файлов)
*Ключевая задача: Перенос драйвера помпы Apex, улучшений стабильности Bluetooth Combo v2 (DozeMonitor, WakeLock), адаптивного сглаживания и плагина удалённого контроля.*

- [x] `[M]` `plugins/automation/src/main/kotlin/app/aaps/plugins/automation/elements/InputPercent.kt`
- [x] `[M]` `plugins/automation/src/main/kotlin/app/aaps/plugins/automation/services/LocationService.kt`
- [x] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/AndroidPermissionImpl.kt`
- [x] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/ImportExportPrefsImpl.kt`
- [x] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/MaintenancePlugin.kt`
- [x] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/cloud/CloudConstants.kt`
- [x] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/cloud/providers/googledrive/GoogleDriveManager.kt`
- [x] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/setupwizard/SWDefinition.kt`
- [x] `[M]` `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/bgQualityCheck/BgQualityCheckPlugin.kt`
- [x] `[x]` `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/objectives/ObjectivesPlugin.kt`
- [x] `[M]` `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/safety/SafetyPlugin.kt`
- [x] `[A]` `plugins/smoothing/src/main/kotlin/app/aaps/plugins/smoothing/AdaptiveSmoothingPlugin.kt`
- [x] `[M]` `plugins/smoothing/src/main/kotlin/app/aaps/plugins/smoothing/ExponentialSmoothingPlugin.kt`
- [x] `[A]` `plugins/source/src/main/kotlin/app/aaps/plugins/source/OttaiPlugin.kt`
- [x] `[M]` `plugins/source/src/main/kotlin/app/aaps/plugins/source/XdripSourcePlugin.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/garmin/GarminHealthSync.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/garmin/GarminPlugin.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/garmin/LoopHub.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/garmin/LoopHubImpl.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/ActiveModeItem.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/LicenseKeyValidator.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/ModePreset.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/NSClientPinManager.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/NsIncomingDataProcessor.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/RemoteAccessAuthManager.kt`
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/RemoteControlPlugin.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclient/workers/NSClientAddUpdateWorker.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclientV3/NSClientV3Plugin.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclientV3/workers/LoadBgWorker.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclientV3/workers/LoadLastModificationWorker.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclientV3/workers/LoadStatusWorker.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsclientV3/workers/LoadTreatmentsWorker.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/openhumans/OpenHumansUploaderPlugin.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/wear/wearintegration/DataHandlerMobile.kt`
- [x] `[M]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/wear/wearintegration/DataLayerListenerServiceMobile.kt`
- [x] `[A]` `pump/apex/.gitignore`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/ApexDriverStatus.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/ApexPump.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/ApexPumpPlugin.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/ApexService.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/ApexBluetooth.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/FirmwareVersion.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/ProtocolVersion.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/CommandId.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/BaseValueCommand.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/Bolus.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/CancelBolus.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/CancelTemporaryBasal.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/DeviceCommand.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/DualBolus.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/ExtendedBolus.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/GetValue.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/RequestHeartbeat.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/SetConnectionProfile.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/SyncDateTime.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/TemporaryBasal.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/UpdateBasalProfileRates.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/UpdateSettingsV1.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/UpdateSystemState.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/device/UpdateUsedBasalProfile.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/AlarmObject.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/BasalProfile.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/BolusEntry.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/CommandResponse.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/Heartbeat.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/PumpCommand.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/PumpObjects.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/StatusV1.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/StatusV2.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/TDDEntry.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/connectivity/commands/pump/Version.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/events/EventApexPumpDataChanged.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/interfaces/ApexBluetoothCallback.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/interfaces/ApexDeviceInfo.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/misc/ApexDeviceInfoImpl.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/misc/ApexSettingsEnums.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/utils/ApexCrypto.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/utils/DataUtils.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/utils/keys/ApexBooleanKey.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/utils/keys/ApexDoubleKey.kt`
- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/utils/keys/ApexStringKey.kt`
- [x] `[M]` `pump/combov2/comboctl/src/androidMain/kotlin/info/nightscout/comboctl/android/AndroidBluetoothDevice.kt`
- [x] `[A]` `pump/combov2/comboctl/src/androidMain/kotlin/info/nightscout/comboctl/android/BluetoothWakeLock.kt`
- [x] `[A]` `pump/combov2/comboctl/src/androidMain/kotlin/info/nightscout/comboctl/android/DozeMonitor.kt`
- [x] `[M]` `pump/combov2/comboctl/src/commonMain/kotlin/info/nightscout/comboctl/main/Pump.kt`
- [x] `[M]` `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/MedtrumPlugin.kt`
- [x] `[M]` `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/MedtrumPump.kt`
- [x] `[M]` `pump/medtrum/src/main/kotlin/app/aaps/pump/medtrum/services/BLEComm.kt`
- [x] `[M]` `pump/omnipod/common/src/main/kotlin/app/aaps/pump/omnipod/common/bledriver/comm/legacy/io/BleIO.kt`
- [x] `[M]` `pump/rileylink/src/main/kotlin/app/aaps/pump/common/hw/rileylink/ble/operations/CharacteristicReadOperation.kt`
- [x] `[M]` `pump/virtual/src/main/kotlin/app/aaps/pump/virtual/VirtualPumpPlugin.kt`
- [x] `[M]` `wear/src/main/assets/logback.xml`
- [x] `[M]` `wear/src/main/kotlin/app/aaps/wear/heartrate/HeartRateListener.kt`
- [x] `[M]` `wear/src/main/kotlin/app/aaps/wear/interaction/utils/Constants.kt`
- [x] `[M]` `wear/src/main/kotlin/app/aaps/wear/wearStepCount/StepCountListener.kt`
- [x] `[M]` `workflow/src/main/kotlin/app/aaps/workflow/InvokeLoopWorker.kt`
- [x] `[M]` `workflow/src/main/kotlin/app/aaps/workflow/iob/IobCobOref1Worker.kt`
- [x] `[M]` `workflow/src/main/kotlin/app/aaps/workflow/iob/IobCobOrefWorker.kt`

### Модуль 10: UI-экраны, разметки и ресурсы (Миграция на Jetpack Compose / M3) (210 файлов)
*Ключевая задача: Полный рефакторинг UI с устаревшего XML / Fragment / Activity на Jetpack Compose / Material 3 в соответствии со стандартами AndroidAPS 4.0.0.*

#### 10.1. Экраны Meal Advisor (AI Vision & Анализ питания) (2 файлов)
- **Целевая реализация Compose:** `MealAdvisorScreen.kt`, `MealAdvisorCameraScreen.kt` (Jetpack Compose + Material 3 + CameraX)

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/MealAdvisorActivity.kt` (мигрирован на `MealAdvisorScreen`)
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/MealAdvisorCameraActivity.kt` (мигрирован на `MealAdvisorCameraScreen`)

#### 10.2. Экраны настроек AIMI и профильных рекомендаций (2 файлов)
- **Целевая реализация Compose:** `AimiModeSettingsScreen.kt`, `AimiProfileAdvisorScreen.kt` (Material 3 Preference Sheets)

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/AimiModeSettingsActivity.kt` (мигрирован на `AimiModeSettingsScreen`)
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/AimiProfileAdvisorActivity.kt` (мигрирован на `AimiProfileAdvisorScreen`)

#### 10.3. Экран контекстных намерений (Context UI) (5 файлов)
- **Целевая реализация Compose:** `ContextScreen.kt` (Compose Material 3)

- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ui/ContextActivity.kt` (мигрирован на `ContextScreen`)
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ui/ContextIntentAdapter.kt` (удалён за ненадобностью, заменён LazyColumn)
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/context/ui/ContextViewModel.kt` (мигрирован на StateFlow / Metro DI)
- [x] `[A]` `plugins/aps/src/main/res/layout/activity_context.xml` (удалён)
- [x] `[A]` `plugins/aps/src/main/res/layout/item_active_intent.xml` (удалён)

#### 10.4. Экраны компаратора и превью дашборда (2 файлов)
- **Целевая реализация Compose:** `ComparatorActivity.kt` (Compose Material 3); `DashboardPreviewActivity.kt` удалён за ненадобностью (архитектурное решение Stage 5B/5C: отказ от дублирующего дашборда 3.x в пользу родного 4.0.0 OverviewScreen).

- [x] `[A]` `app/src/main/kotlin/app/aaps/activities/ComparatorActivity.kt`
- [x] `[A]` `app/src/main/kotlin/app/aaps/activities/DashboardPreviewActivity.kt` (удалён)

#### 10.5. Дашборд AIMI, детали коррекций, режимы и состояние замкнутого цикла (50 файлов)
- **Целевая реализация Compose:** Интеграция в родной Compose `OverviewScreen.kt` (виджет `AimiOverviewCard` с кнопкой быстрого запуска Meal Advisor, компактным рядом физиологических метрик и кликабельным бейджем Dual-Brain Auditor). Устаревший XML `DashboardFragment`, `OverviewViewModel`, кастомные XML View и селекторы удалены. `AdjustmentDetailsActivity` и `LoopStateActivity` адаптированы под Compose/M3.

- [x] `[A]` `app/src/main/res/layout/activity_dashboard_preview.xml` (удалён)
- [x] `[A]` `implementation/src/main/kotlin/app/aaps/implementation/overview/OverviewDataImpl.kt`
- [x] `[A]` `plugins/main/src/androidMain/kotlin/app/aaps/plugins/main/general/dashboard/AdjustmentDetailsActivity.kt`
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/DashboardFragment.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/DashboardModesActivity.kt` (удалён)
- [x] `[A]` `plugins/main/src/androidMain/kotlin/app/aaps/plugins/main/general/dashboard/LoopStateActivity.kt`
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/viewmodel/OverviewViewModel.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/views/AdjustmentStatusView.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/views/CircleTopDashboardView.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/views/GlucoseGraphView.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/dashboard/views/StatusCardView.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewEntryFragment.kt` (удалён)
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewFragment.kt` (заменён нативным 4.0.0 OverviewScreen)
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewMenusImpl.kt`
- [x] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/OverviewPlugin.kt`
- [x] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/overview/notifications/NotificationUiBinder.kt` (удалён)
- [x] `[A]` `plugins/main/src/main/res/color/dashboard_chip_bg_selector.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/color/dashboard_chip_stroke_selector.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/color/dashboard_chip_text_selector.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_card_surface.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_chip_background.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_chip_background_quality_bad.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_chip_background_quality_ok.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_chip_background_quality_uncertain.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_chip_background_warning.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/dashboard_loop_indicator.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/ic_dashboard_battery.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/ic_dashboard_droplet.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/ic_dashboard_shoe.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/drawable/ic_dashboard_wave.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/activity_dashboard_modes.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/component_adjustment_status.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/component_circle_top_status_hybrid.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/component_status_card.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/fragment_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/fragment_overview_entry.xml` (удалён)
- [x] `[M]` `plugins/main/src/main/res/layout/overview_buttons_layout.xml` (удалён)
- [x] `[M]` `plugins/main/src/main/res/layout/overview_fragment.xml` (удалён)
- [x] `[M]` `plugins/main/src/main/res/layout/overview_info_layout.xml` (удалён)
- [x] `[M]` `plugins/main/src/main/res/layout/overview_notification_item.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/layout/view_glucose_graph_placeholder.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/menu/dashboard_menu.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/menu/menu_dashboard_aimi.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values-land/dimens_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values-land/fractions_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values-sw600dp/dimens_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values/colors_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values/dimens_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values/fractions_dashboard.xml` (удалён)
- [x] `[A]` `plugins/main/src/main/res/values/styles_dashboard.xml` (удалён)

#### 10.6. Экран руководства пользователя (User Manual) (5 файлов)
- **Целевая реализация Compose:** `UserManualActivity.kt` с локализацией в `androidMain/res`.

- [x] `[A]` `plugins/main/src/androidMain/kotlin/app/aaps/plugins/main/general/manual/UserManualActivity.kt`
- [x] `[A]` `plugins/main/src/main/res/layout/activity_user_manual.xml` (удалён)
- [x] `[A]` `plugins/main/src/androidMain/res/values-fr-rFR/manual_strings.xml`
- [x] `[A]` `plugins/main/src/androidMain/res/values-ru-rRU/manual_strings.xml`
- [x] `[A]` `plugins/main/src/androidMain/res/values/manual_strings.xml`

#### 10.7. Экран удалённого управления (Remote Control / NS Client) (11 файлов)
- **Целевая реализация Compose:** `RemoteControlScreen.kt`, `RemoteControlComposeContent.kt`, `RemoteControlViewModel.kt` (Compose Material 3 Tabs, Cards, Dialogs)

- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/ActiveModesAdapter.kt` (удалён за ненадобностью, заменён Compose)
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/LicenseKeyDialog.kt` (удалён за ненадобностью, заменён Compose Dialog)
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/ModesAdapter.kt` (удалён за ненадобностью, заменён Compose)
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/RemoteAccessLoginDialog.kt` (удалён за ненадобностью, заменён Compose Dialog)
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/RemoteControlFragment.kt` (удалён за ненадобностью, заменён `RemoteControlComposeContent`)
- [x] `[A]` `plugins/sync/src/main/kotlin/app/aaps/plugins/sync/nsShared/RemoteControlPagerAdapter.kt` (удалён за ненадобностью, заменён Compose PrimaryTabRow)
- [x] `[A]` `plugins/sync/src/main/res/layout/tab_active.xml` (удалён)
- [x] `[A]` `plugins/sync/src/main/res/layout/tab_contexts.xml` (удалён)
- [x] `[A]` `plugins/sync/src/main/res/layout/tab_llm.xml` (удалён)
- [x] `[A]` `plugins/sync/src/main/res/layout/tab_modes.xml` (удалён)
- [x] `[A]` `plugins/sync/src/main/res/values/strings_remote.xml`

#### 10.8. Экран управления помпой Apex (Apex UI) (6 файлов)
- **Целевая реализация Compose:** `ApexPumpScreen.kt`, `ApexPumpComposeContent.kt`, `ApexPumpViewModel.kt` (Compose Material 3)

- [x] `[A]` `pump/apex/src/main/kotlin/app/aaps/pump/apex/ui/ApexFragment.kt` (удалён, заменён `ApexPumpComposeContent`)
- [x] `[A]` `pump/apex/src/main/res/drawable/ic_apex.xml`
- [x] `[A]` `pump/apex/src/main/res/drawable/ic_apex_detailed.xml`
- [x] `[A]` `pump/apex/src/main/res/layout/apex_fragment.xml` (удалён)
- [x] `[A]` `pump/apex/src/main/res/values-ru-rRU/strings.xml`
- [x] `[A]` `pump/apex/src/main/res/values/strings.xml`

#### 10.9. Экраны разрешений Health Connect и Emergency SOS (2 файлов)
- **Целевая реализация Compose:** Compose Permission Hosts / BottomSheets

- [ ] `[A]` `plugins/aps/src/main/res/layout/activity_aimi_hc_permission.xml`
- [ ] `[A]` `plugins/aps/src/main/res/layout/activity_aimi_sos_permission.xml`

#### 10.10. Кастомные графические индикаторы (Glucose Ring & Circle) (3 файлов)
- **Целевая реализация Compose:** Compose `GlucoseRing` / `GlucoseCircle` (Canvas-компоненты)

- [ ] `[A]` `core/ui/src/main/kotlin/app/aaps/core/ui/elements/GlucoseCircleView.kt`
- [ ] `[A]` `core/ui/src/main/kotlin/app/aaps/core/ui/views/GlucoseRingView.kt`
- [ ] `[A]` `core/ui/src/main/res/values/attrs_glucose_ring.xml`

#### 10.11. Прочие XML-ресурсы, стили, селекторы и локализация (122 файлов)
- **Целевая реализация Compose:** Миграция в Compose Theme / ColorScheme / StringResources (Multiplatform)

- [ ] `[M]` `app/src/main/kotlin/app/aaps/MainActivity.kt`
- [ ] `[A]` `app/src/main/kotlin/app/aaps/activities/AbbreviationsActivity.kt`
- [ ] `[M]` `app/src/main/kotlin/app/aaps/activities/MyPreferenceFragment.kt`
- [ ] `[A]` `app/src/main/res/layout/activity_comparator.xml`
- [ ] `[M]` `app/src/main/res/menu/menu_main.xml`
- [ ] `[M]` `app/src/main/res/values-fr-rFR/strings.xml`
- [ ] `[M]` `app/src/main/res/values-ru-rRU/strings.xml`
- [ ] `[M]` `app/src/main/res/values/dimens.xml`
- [ ] `[M]` `app/src/main/res/values/strings.xml`
- [ ] `[M]` `core/interfaces/src/main/kotlin/app/aaps/core/interfaces/ui/UiInteraction.kt`
- [ ] `[M]` `core/interfaces/src/main/res/values-lt-rLT/strings.xml`
- [ ] `[M]` `core/interfaces/src/main/res/values-pt-rBR/strings.xml`
- [ ] `[M]` `core/interfaces/src/main/res/values-ru-rRU/strings.xml`
- [ ] `[A]` `core/keys/src/main/res/values/keys.xml`
- [ ] `[A]` `core/keys/src/main/res/values/strings.xml`
- [ ] `[A]` `core/objects/src/main/res/drawable/ic_ottai.xml`
- [ ] `[M]` `core/ui/src/main/kotlin/app/aaps/core/ui/dialogs/OKDialog.kt`
- [ ] `[A]` `core/ui/src/main/res/anim/auditor_badge_slide_in.xml`
- [ ] `[A]` `core/ui/src/main/res/anim/auditor_bounce.xml`
- [ ] `[A]` `core/ui/src/main/res/anim/auditor_pulse.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/auditor_badge_background.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_audit_monitor.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_reservoir.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_reservoir_10.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_reservoir_100.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_reservoir_25.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_reservoir_50.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_reservoir_75.xml`
- [ ] `[A]` `core/ui/src/main/res/drawable/ic_shield.xml`
- [ ] `[M]` `core/ui/src/main/res/values-it-rIT/strings.xml`
- [ ] `[M]` `core/ui/src/main/res/values/colors.xml`
- [ ] `[A]` `core/ui/src/main/res/values/ids.xml`
- [ ] `[M]` `core/ui/src/main/res/values/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/OpenAPSFragment.kt`
- [ ] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/ui/AuditorNotificationManager.kt`
- [ ] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/ui/AuditorStatusIndicator.kt`
- [ ] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/ui/AuditorStatusLiveData.kt`
- [ ] `[M]` `plugins/aps/src/main/res/values-ar-rSA/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-bg-rBG/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-ca-rES/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-cs-rCZ/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-da-rDK/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-de-rDE/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-el-rGR/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-es-rES/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-fr-rFR/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-hr-rHR/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-hu-rHU/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-it-rIT/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-iw-rIL/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-ko-rKR/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-lt-rLT/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-nb-rNO/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-nl-rNL/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-pl-rPL/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-pt-rBR/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-pt-rPT/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-ru-rRU/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-sk-rSK/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-sr-rCS/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-sv-rSE/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-tr-rTR/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-uk-rUA/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-zh-rCN/strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values-zh-rTW/strings.xml`
- [ ] `[A]` `plugins/aps/src/main/res/values/missing_resolved_strings.xml`
- [ ] `[M]` `plugins/aps/src/main/res/values/strings.xml`
- [ ] `[A]` `plugins/aps/src/main/res/values/wcycle_strings.xml`
- [ ] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/MaintenanceFragment.kt`
- [ ] `[M]` `plugins/configuration/src/main/kotlin/app/aaps/plugins/configuration/maintenance/cloud/ExportOptionsDialog.kt`
- [ ] `[M]` `plugins/configuration/src/main/res/layout/dialog_export_options.xml`
- [ ] `[M]` `plugins/configuration/src/main/res/layout/maintenance_fragment.xml`
- [ ] `[M]` `plugins/configuration/src/main/res/values/strings.xml`
- [ ] `[M]` `plugins/constraints/src/main/kotlin/app/aaps/plugins/constraints/objectives/ObjectivesFragment.kt`
- [ ] `[M]` `plugins/constraints/src/main/res/layout/objectives_fragment.xml`
- [ ] `[M]` `plugins/constraints/src/main/res/values-pt-rBR/exam.xml`
- [ ] `[M]` `plugins/constraints/src/main/res/values/strings.xml`
- [ ] `[M]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/general/smsCommunicator/activities/SmsCommunicatorOtpActivity.kt`
- [ ] `[A]` `plugins/main/src/main/kotlin/app/aaps/plugins/main/skins/SkinMinimal.kt`
- [ ] `[A]` `plugins/main/src/main/res/drawable/ic_cp_basal.xml`
- [ ] `[A]` `plugins/main/src/main/res/drawable/ic_graduation.xml`
- [ ] `[A]` `plugins/main/src/main/res/drawable/ic_menu_info_details.xml`
- [ ] `[A]` `plugins/main/src/main/res/drawable/ic_sensor_reading.xml`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn.png`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_alert_up.png`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_hyper_stable.jpg`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_hyper_stable2.png`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_hypo_down.jpg`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_hypo_juice.png`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_hypo_stable.jpg`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_hypo_up.jpg`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_normal_down.jpg`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_normal_stable.png`
- [ ] `[A]` `plugins/main/src/main/res/drawable/unicorn_normal_up.png`
- [ ] `[A]` `plugins/main/src/main/res/layout/activity_adjustment_details.xml`
- [ ] `[A]` `plugins/main/src/main/res/layout/activity_loop_state.xml`
- [ ] `[A]` `plugins/main/src/main/res/layout/item_adjustment_detail.xml`
- [ ] `[A]` `plugins/main/src/main/res/layout/item_manual_section.xml`
- [ ] `[M]` `plugins/main/src/main/res/values-fr-rFR/strings.xml`
- [ ] `[M]` `plugins/main/src/main/res/values/strings.xml`
- [ ] `[M]` `plugins/sensitivity/src/main/res/values-es-rES/strings.xml`
- [ ] `[M]` `plugins/sensitivity/src/main/res/values-fr-rFR/strings.xml`
- [ ] `[M]` `plugins/smoothing/src/main/res/values/strings.xml`
- [ ] `[M]` `plugins/source/src/main/res/values/strings.xml`
- [ ] `[A]` `plugins/sync/src/main/res/layout/dialog_license_key.xml`
- [ ] `[A]` `plugins/sync/src/main/res/layout/dialog_password_setup.xml`
- [ ] `[A]` `plugins/sync/src/main/res/layout/dialog_remote_access_login.xml`
- [ ] `[A]` `plugins/sync/src/main/res/layout/fragment_remote_control.xml`
- [ ] `[A]` `plugins/sync/src/main/res/layout/item_active_mode.xml`
- [ ] `[A]` `plugins/sync/src/main/res/layout/item_remote_mode.xml`
- [ ] `[M]` `plugins/sync/src/main/res/values-iw-rIL/strings.xml`
- [ ] `[M]` `pump/combov2/src/main/res/values-lt-rLT/strings.xml`
- [ ] `[M]` `pump/combov2/src/main/res/values-pt-rPT/strings.xml`
- [ ] `[M]` `ui/src/main/kotlin/app/aaps/ui/dialogs/BolusProgressDialog.kt`
- [ ] `[M]` `ui/src/main/kotlin/app/aaps/ui/dialogs/InsulinDialog.kt`
- [ ] `[M]` `ui/src/main/kotlin/app/aaps/ui/services/AlarmSoundService.kt`
- [ ] `[M]` `ui/src/main/kotlin/app/aaps/ui/services/AlarmSoundServiceHelper.kt`
- [ ] `[M]` `ui/src/main/res/values-da-rDK/strings.xml`
- [ ] `[M]` `ui/src/main/res/values-ko-rKR/strings.xml`
- [ ] `[M]` `ui/src/main/res/values-sv-rSE/strings.xml`
- [ ] `[M]` `wear/src/main/kotlin/app/aaps/wear/interaction/actions/ProfileSwitchActivity.kt`
- [ ] `[M]` `wear/src/main/kotlin/app/aaps/wear/interaction/menus/MainMenuActivity.kt`

### Модуль 11: Автоматизированные тесты (58 файлов)
*Ключевая задача: Адаптация и запуск всех существующих unit-тестов форка на новой кодовой базе 4.0.0 для гарантии отсутствия регрессий в алгоритмах.*

- [ ] `[M]` `app/src/androidTest/kotlin/app/aaps/plugins/aps/openAPS/APSResultObject.kt`
- [ ] `[M]` `app/src/androidTest/kotlin/app/aaps/plugins/aps/openAPSSMB/DetermineBasalResultSMBFromJS.kt`
- [ ] `[M]` `app/src/test/kotlin/app/aaps/receivers/KeepAliveWorkerTest.kt`
- [ ] `[M]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/loop/LoopPluginTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/AIMIAdaptiveBasalTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/AimiSubjects.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/AuditorStateValidationTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/BypassHeuristicsTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/GlucoseStatusCalculatorAimiTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/ISF/IsfAdjustmentEngineTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/ISF/IsfBlenderTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/KalmanFilterTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/NightGrowthResistanceLearnerTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/NightGrowthResistanceMonitorTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/PredictionBlockingTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/PredictionSanityTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/SmbDomainHeuristicsTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/auditor/AuditorPromptSafeguardsTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/FatSecretServiceTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/advisor/meal/OpenFoodFactsServiceTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/aimiNeuralNetworkTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/autodrive/AutoDrivePipelineTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/BasalDecisionEngineTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/BasalHistoryUtilsTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/BasalPlannerTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/basal/DynamicBasalControllerT3cTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/benchmarks/EngineBenchmarks.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/carbs/CarbsAdvisorTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/comparison/ComparisonCsvParserTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/gate/CosineTrajectoryGateTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidEffectModelTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidSafetyGatesTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/physio/thyroid/ThyroidStateEstimatorTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/AdaptivePkPdEstimatorTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/AdvancedPredictionEngineTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/InsulinActionProfilerTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/IsfFusionTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/PkPdCoreTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/PkPdIntegrationTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/pkpd/SmbDampingTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/BasalFirstPolicyTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/HighBgOverrideTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/HypoToolsTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/safety/SafetyNetTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/MealHighIobPolicyTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbDampingUsecaseTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbInstructionExecutorTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/smb/SmbQuantizerTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/utils/AimiLoggerTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleAdjusterTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleEstimatorTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleFacadeTest.kt`
- [ ] `[A]` `plugins/aps/src/test/kotlin/app/aaps/plugins/aps/openAPSAIMI/wcycle/WCycleLearnerTest.kt`
- [ ] `[A]` `plugins/smoothing/src/test/kotlin/app/aaps/plugins/smoothing/AdaptiveSmoothingPluginTest.kt`
- [ ] `[M]` `plugins/sync/src/test/kotlin/app/aaps/plugins/sync/garmin/GarminPluginTest.kt`
- [ ] `[M]` `plugins/sync/src/test/kotlin/app/aaps/plugins/sync/garmin/LoopHubTest.kt`
- [ ] `[A]` `pump/apex/src/test/kotlin/app/aaps/pump/apex/CommandsTest.kt`
- [ ] `[M]` `shared/tests/src/main/kotlin/app/aaps/shared/tests/TestBaseWithProfile.kt`

### Модуль 12: Документация, исследования и спецификации (168 файлов)
*Ключевая задача: Сохранение проектной документации AIMI, математических моделей, симуляторов и руководств.*

- [ ] `[A]` `API_KEYS_VERIFICATION_COMPLETE.md`
- [ ] `[A]` `COMMIT_MESSAGE_PHASE1.md`
- [ ] `[A]` `DawnGuardSim.kts`
- [ ] `[A]` `PHASE2_COMPLETE.md`
- [ ] `[A]` `PKPD_GUARD_FILES.txt`
- [ ] `[A]` `PKPD_GUARD_README.md`
- [ ] `[A]` `SESSION_SUMMARY_2025-12-31.md`
- [ ] `[A]` `WCYCLE_LOGGING_PATCH.diff`
- [ ] `[A]` `_docs/OpenAPS_AIMI_User_Manual.md`
- [ ] `[M]` `app/build.gradle.kts`
- [ ] `[M]` `benchmark/build.gradle.kts`
- [ ] `[M]` `build.gradle.kts`
- [ ] `[M]` `buildSrc/src/main/kotlin/android-module-dependencies.gradle.kts`
- [ ] `[A]` `docs/AIMI_ADVISOR_MULTI_MODEL_COMPLETE.md`
- [ ] `[A]` `docs/AIMI_COMPLETE_ANALYSIS_PHASE1.md`
- [ ] `[A]` `docs/AIMI_MANUAL_EN_PART1.xml`
- [ ] `[A]` `docs/AIMI_STORAGE_SECURITY_STATUS.md`
- [ ] `[A]` `docs/AIMI_USER_MANUAL.md`
- [ ] `[A]` `docs/AIMI_USER_MANUAL_FR.md`
- [ ] `[A]` `docs/AI_AUDITOR_ACTIVATION_DEBUG.md`
- [ ] `[A]` `docs/AI_AUDITOR_CRITICAL_DEBUG.md`
- [ ] `[A]` `docs/AI_AUDITOR_DIAGNOSTIC.md`
- [ ] `[A]` `docs/AI_AUDITOR_FILES_CREATED.md`
- [ ] `[A]` `docs/AI_AUDITOR_FIX_VERIFICATION.md`
- [ ] `[A]` `docs/AI_AUDITOR_INTEGRATION_GUIDE.md`
- [ ] `[A]` `docs/AI_AUDITOR_LOGGING_IMPLEMENTATION.md`
- [ ] `[A]` `docs/AI_AUDITOR_MODES_AND_LOGGING.md`
- [ ] `[A]` `docs/AI_AUDITOR_RECAP.md`
- [ ] `[A]` `docs/AI_AUDITOR_STATUS_FIX.md`
- [ ] `[A]` `docs/AI_AUDITOR_STATUS_FIX_COMPLETE.md`
- [ ] `[A]` `docs/AI_AUDITOR_SUCCESS.md`
- [ ] `[A]` `docs/AI_AUDITOR_SUMMARY.md`
- [ ] `[A]` `docs/AI_AUDITOR_TEST_CASES.md`
- [ ] `[A]` `docs/AI_AUDITOR_TODO.md`
- [ ] `[A]` `docs/AI_AUDITOR_TRIGGER_FIX.md`
- [ ] `[A]` `docs/AI_AUDITOR_UI_COMPLETE.md`
- [ ] `[A]` `docs/AI_DECISION_AUDITOR.md`
- [ ] `[A]` `docs/AJOUT_TBR_LEGACY_MODES.md`
- [ ] `[A]` `docs/ANALYSE_ZONE_120_180_MAXSMB.md`
- [ ] `[A]` `docs/ARCHITECTURE.md`
- [ ] `[A]` `docs/AUDIT_CHEMINS_FICHIERS_AIMI.md`
- [ ] `[A]` `docs/AUDIT_CODE_OBSOLETE_AFTER_LEGACY.md`
- [ ] `[A]` `docs/BUG_HYPER_KICKER_BLOCKS_SMB.md`
- [ ] `[A]` `docs/BUILD_SUCCESS_MEAL_ADVISOR_FIX.md`
- [ ] `[A]` `docs/CASE_STUDY_RECURRENT_NIGHT_HYPOS.md`
- [ ] `[A]` `docs/CAS_LIMITE_PLATEAU_HAUT.md`
- [ ] `[A]` `docs/COMBO_V2_ARCHITECTURAL_ANALYSIS_2026-01-02.md`
- [ ] `[A]` `docs/COMBO_V2_IMPROVEMENTS_2-4_IMPLEMENTED.md`
- [ ] `[A]` `docs/COMBO_V2_NIGHTLY_BT_FIX_2026-01-02.md`
- [ ] `[A]` `docs/COMMIT_MSG_PKPD_GUARD.md`
- [ ] `[A]` `docs/COMPLETE_LEARNERS_RT_EXPOSURE.md`
- [ ] `[A]` `docs/CRITICAL_BUG_NO_SMB_270.md`
- [ ] `[A]` `docs/CRITICAL_SECTIONS_EN_TRANSLATIONS.md`
- [ ] `[A]` `docs/DEV_USER_MANUAL_LOCATION.md`
- [ ] `[A]` `docs/DIAGNOSTIC_GLYCEMIE_297_ISF_BLOCAGE.md`
- [ ] `[A]` `docs/DIAGNOSTIC_MAXSMB_DYNAMIQUE.md`
- [ ] `[A]` `docs/DIAGNOSTIC_PREBOLUS_BLOCAGE.md`
- [ ] `[A]` `docs/DUAL_BRAIN_AUDITOR_DESIGN.md`
- [ ] `[A]` `docs/DUAL_BRAIN_IMPLEMENTATION_PHASE1.md`
- [ ] `[A]` `docs/DUAL_BRAIN_STATUS.md`
- [ ] `[A]` `docs/ENGLISH_TRANSLATION_PLAN.md`
- [ ] `[A]` `docs/FINAL_DELIVERY_REPORT.md`
- [ ] `[A]` `docs/FIX_COMPLETE_HYPER_KICKER.md`
- [ ] `[A]` `docs/FIX_HYPER_KICKER_EARLY_RETURN.md`
- [ ] `[A]` `docs/FIX_JSON_UNICODE_ARROWS.md`
- [ ] `[A]` `docs/FIX_JSON_UNICODE_COMPLETE.md`
- [ ] `[A]` `docs/FIX_PREBOLUS_DIAGNOSTIC_LOGS.md`
- [ ] `[A]` `docs/FIX_SURCORRECTION_UAM_PKPD.md`
- [ ] `[A]` `docs/GUARANTEE_PREBOLUS_FORCE_SEND.md`
- [ ] `[A]` `docs/JSON_LOCALE_CRITICAL_BUG_ANALYSIS.md`
- [ ] `[A]` `docs/JSON_LOCALE_FIX_HOTFIX.md`
- [ ] `[A]` `docs/JSON_SANITIZATION_SERIALIZER_SOLUTION.md`
- [ ] `[A]` `docs/JSON_SPECIAL_CHARACTERS_ANALYSIS.md`
- [ ] `[A]` `docs/KMP_IOS_AUTOMATED_LOOP_WORKAROUNDS.md`
- [ ] `[A]` `docs/KMP_IOS_FEASIBILITY_STUDY.md`
- [ ] `[A]` `docs/KMP_IOS_FEATURE_LIMITATIONS.md`
- [ ] `[A]` `docs/KMP_IOS_TECHNICAL_ARCHITECTURE.md`
- [ ] `[A]` `docs/KMP_IOS_TRIO_SOLUTION.md`
- [ ] `[A]` `docs/KMP_MIGRATION_MASTER_PLAN.md`
- [ ] `[A]` `docs/KMP_MIGRATION_PART2.md`
- [ ] `[A]` `docs/KMP_ML_FEASIBILITY.md`
- [ ] `[A]` `docs/KMP_PROJECT_CREATION_GUIDE.md`
- [ ] `[A]` `docs/LEARNERS_CODE_SNIPPET_1.kt`
- [ ] `[A]` `docs/LEARNERS_CODE_SNIPPET_2.kt`
- [ ] `[A]` `docs/LEARNERS_MANUAL_IMPLEMENTATION.md`
- [ ] `[A]` `docs/LEARNERS_RT_VISIBILITY_FIX.md`
- [ ] `[A]` `docs/LEARNER_MONITOR_ANALYSIS.md`
- [ ] `[A]` `docs/LEGACY_MEAL_MODES_COMPLETE.md`
- [ ] `[A]` `docs/MAINTENANCE.md`
- [ ] `[A]` `docs/MANUAL_EN_PART1_TEMP.xml`
- [ ] `[A]` `docs/MANUEL_UPDATE_RECAP_FINAL.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_ANSWERS.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_BUG_FIX_DELTA.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_CALC_BUG.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_FIX_FINAL.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_FLOW_ANALYSIS.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_INDEX.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_INTELLIGENT_FIX.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_MISSION_REPORT.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_MULTI_MODEL_COMPLETED.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_MULTI_MODEL_IMPLEMENTATION.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_QUICK_REF.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_README.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_TEST_GUIDE.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_TEST_SCENARIOS.kt`
- [ ] `[A]` `docs/MEAL_ADVISOR_VALIDATION.md`
- [ ] `[A]` `docs/MEAL_ADVISOR_VERIFICATION_COMPLETE.md`
- [ ] `[A]` `docs/MEAL_MODES_AUDIT.md`
- [ ] `[A]` `docs/MEAL_MODES_DEGRADE_DIAGNOSTIC.md`
- [ ] `[A]` `docs/MEAL_MODES_DEGRADE_EXEC_SUMMARY.md`
- [ ] `[A]` `docs/MEAL_MODES_DEGRADE_FINAL_DELIVERY.md`
- [ ] `[A]` `docs/MEAL_MODES_FINAL_DELIVERY.md`
- [ ] `[A]` `docs/MEAL_MODES_LGS_BYPASS_FINAL.md`
- [ ] `[A]` `docs/MEAL_MODES_PREBOLUS_CAP_FIX.md`
- [ ] `[A]` `docs/MODIFICATION_FINALE_GRADATION_120_140.md`
- [ ] `[A]` `docs/PKPD_ABSORPTION_GUARD_AUDIT.md`
- [ ] `[A]` `docs/PKPD_ABSORPTION_GUARD_COMPLETE.md`
- [ ] `[A]` `docs/PKPD_GUARD_MONITORING.md`
- [ ] `[A]` `docs/PKPD_IMPROVEMENTS_IMPLEMENTATION.md`
- [ ] `[A]` `docs/PKPD_LEARNING_ANALYSIS.md`
- [ ] `[A]` `docs/PKPD_REALTIME_EXEC_SUMMARY.md`
- [ ] `[A]` `docs/PKPD_REALTIME_IMPLEMENTATION_COMPLETE.md`
- [ ] `[A]` `docs/PKPD_REALTIME_IMPL_SPEC.md`
- [ ] `[A]` `docs/PKPD_REALTIME_OBSERVER_AUDIT.md`
- [ ] `[A]` `docs/PKPD_SAFETY_AUDIT.md`
- [ ] `[A]` `docs/PKPD_THROTTLE_COMPLETE_FINAL.md`
- [ ] `[A]` `docs/PKPD_THROTTLE_TBR_INTERVAL_IMPL.md`
- [ ] `[A]` `docs/PROJECT_FINAL_REPORT.md`
- [ ] `[A]` `docs/PROPOSITION_CORRECTIONS_MAXSMB_ISF.md`
- [ ] `[A]` `docs/REACTIVITY_LEARNER_RT_EXPOSURE_ANALYSIS.md`
- [ ] `[A]` `docs/REACTIVITY_LEARNER_RT_EXPOSURE_SOLUTION.md`
- [ ] `[A]` `docs/RECAPITULATIF_MODIFICATIONS_ISF_MAXSMB.md`
- [ ] `[A]` `docs/REEVALUATION_CRITIQUE_MAXSMB_ISF.md`
- [ ] `[A]` `docs/RESTORE_LEGACY_MEAL_MODES.md`
- [ ] `[A]` `docs/RESUME_DIAGNOSTIC_COMPLET.md`
- [ ] `[A]` `docs/RT_INSTRUMENTATION_ANALYSIS.md`
- [ ] `[A]` `docs/RT_INSTRUMENTATION_COMPLETE.md`
- [ ] `[A]` `docs/RT_OUTPUT_LEARNERS_VISIBILITY.md`
- [ ] `[A]` `docs/SAFETY_AUDIT_BG120_COMPLETE.md`
- [ ] `[A]` `docs/SAFETY_IMPLEMENTATION_BG120_FINAL.md`
- [ ] `[A]` `docs/SESSION_SUMMARY_2026-01-03.md`
- [ ] `[A]` `docs/SESSION_TRAJECTORY_IMPLEMENTATION_2026-01-01.md`
- [ ] `[A]` `docs/SMB_SAFETY_AUDIT_FINAL.md`
- [ ] `[A]` `docs/SYNTHESE_MODIFICATIONS.md`
- [ ] `[A]` `docs/TRAJECTORY_GUARD_INTEGRATION_COMPLETE.md`
- [ ] `[A]` `docs/TRAJECTORY_GUARD_READY_FOR_INTEGRATION.md`
- [ ] `[A]` `docs/TRAJECTORY_GUARD_UI_PREFERENCE.md`
- [ ] `[A]` `docs/TRAJECTORY_RT_STRUCTURED_FIELDS.md`
- [ ] `[A]` `docs/TRANSLATION_COMPLETE_FINAL.md`
- [ ] `[A]` `docs/TRANSLATION_STATUS.md`
- [ ] `[A]` `docs/UNIFIED_REACTIVITY_STORAGE_FIX.md`
- [ ] `[A]` `docs/USE_CASES.md`
- [ ] `[A]` `docs/WCYCLE_MAP.md`
- [ ] `[A]` `docs/features/ENDOMETRIOSIS_LOGIC.md`
- [ ] `[A]` `docs/features/TRAJECTORY_ANALYSIS.md`
- [ ] `[A]` `docs/features/TRAJECTORY_GUARD_ASCII_VISUALIZATION.md`
- [ ] `[A]` `docs/features/TRAJECTORY_GUARD_DISPLAY_IMPROVEMENT.md`
- [ ] `[A]` `docs/research/PKPD_TRAJECTORY_CONTROLLER.md`
- [ ] `[A]` `docs/research/TRAJECTORY_SIGNATURE_CLASSIFICATION.md`
- [ ] `[A]` `docs/trajectory_integration_snippet.kt`
- [ ] `[A]` `docs/wcycle_flow.mmd`
- [ ] `[A]` `docs/wcycle_integration.mmd`
- [ ] `[M]` `plugins/aps/build.gradle.kts`
- [ ] `[M]` `plugins/main/build.gradle.kts`
- [ ] `[M]` `plugins/smoothing/build.gradle.kts`
- [ ] `[M]` `plugins/sync/build.gradle.kts`
- [ ] `[A]` `pump/apex/build.gradle.kts`
- [ ] `[M]` `wear/build.gradle.kts`

### Модуль 13: Технические артефакты под очистку (99 файлов) — ОЧИЩЕНО В ЭТАПЕ 2
*Ключевая задача: Исключить из миграции и удалить мусорные/бинарные артефакты, случайно закомиченные в форке (выполнено в коммите `d2b04f56c0`).*

- [x] `[A]` `app/fulldev/release/baselineProfiles/0/app-fulldev-release.dm`
- [x] `[A]` `app/fulldev/release/baselineProfiles/1/app-fulldev-release.dm`
- [x] `[A]` `app/fulldev/release/output-metadata.json`
- [x] `[A]` `app/userfull/release/baselineProfiles/0/app-userfull-release.dm`
- [x] `[A]` `app/userfull/release/baselineProfiles/1/app-userfull-release.dm`
- [x] `[A]` `app/userfull/release/output-metadata.json`
- [x] `[A]` `build_output.txt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/aps/ApsMode.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/aps/AverageTDD.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/aps/BasalData.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/aps/SMBDefaults.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/configuration/Constants.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/firebase/RemoteConfigKeys.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/iob/CobInfo.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/iob/InMemoryGlucoseValue.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/iob/Iob.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/BCR.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/BS.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/CA.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/DS.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/EB.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/EPS.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/FD.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/GV.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/GlucoseUnit.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/HR.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/HasIDs.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/ICfg.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/IDs.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/NE.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/OE.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/PS.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/RM.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/SC.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/SourceSensor.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/TB.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/TDD.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/TE.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/TT.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/TrendArrow.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/UE.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/data/Block.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/model/data/TargetBlock.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/plugin/PluginType.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/Capability.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/DoseSettings.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/DoseStepSize.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/ManufacturerType.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/PumpCapability.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/PumpDescription.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/PumpTempBasalType.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/PumpType.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/pump/defs/TimeChangeType.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/time/T.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/ue/Action.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/ue/Sources.kt`
- [x] `[A]` `core/data/bin/main/app/aaps/core/data/ue/ValueWithUnit.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/BooleanComposedKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/BooleanKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/BooleanKey.kt.rej`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/BooleanNonKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/DoubleKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/IntComposedKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/IntKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/IntKey.kt.rej`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/IntNonKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/IntentKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/LongComposedKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/LongNonKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/StringKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/StringNonKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/UnitDoubleKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/BooleanComposedNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/BooleanNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/BooleanPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/ComposedKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/DoubleComposedNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/DoubleNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/DoublePreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/IntComposedNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/IntNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/IntPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/IntentPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/LongComposedNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/LongNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/LongPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/NonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/PreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/Preferences.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/StringComposedNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/StringNonPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/StringPreferenceKey.kt`
- [x] `[A]` `core/keys/bin/main/app/aaps/core/keys/interfaces/UnitDoublePreferenceKey.kt`
- [x] `[A]` `core/keys/src/main/kotlin/app/aaps/core/keys/BooleanKey.kt.rej`
- [x] `[A]` `core/keys/src/main/kotlin/app/aaps/core/keys/IntKey.kt.rej`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAIMI2.kt.bak`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/DetermineBasalAIMI2.kt.rej`
- [x] `[A]` `plugins/aps/src/main/kotlin/app/aaps/plugins/aps/openAPSAIMI/OpenAPSAIMIPlugin.kt.bak`
- [x] `[A]` `pump/combov2/comboctl/src/androidMain/kotlin/info/nightscout/comboctl/android/AndroidBluetoothDevice.kt.bak`
