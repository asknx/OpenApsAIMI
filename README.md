# OpenApsAIMI: Test Subject

![Base](https://img.shields.io/badge/Base-AndroidAPS_4.0.0_(KMP_%2B_Compose_M3)-0288D1?style=flat-square)
![Engine](https://img.shields.io/badge/Engine-OpenAPS_AIMI_3.3_%2B_Autodrive_MPC-7B1FA2?style=flat-square)
![DI](https://img.shields.io/badge/DI-Metro_(Zac_Sweers)-00897B?style=flat-square)
![Subject](https://img.shields.io/badge/Test_Subject-N%3D1_(The_Developer)-D32F2F?style=flat-square)

> **"The only test subject is the developer."**

This is an independent personal fork of **OpenAPSAIMI**, migrated onto the **AndroidAPS 4.0.0** architecture (Kotlin Multiplatform, Jetpack Compose Material 3, Metro DI) and maintained primarily for the author's own daily use.

The repository is public for transparency and to share architectural, algorithmic, and UI modifications that others may find useful.

* **Not an official release:** This project is not an official AndroidAPS or AIMI release.
* **No binaries and no warranty:** No prebuilt APKs are distributed, and no support, medical advice, or fitness-for-use guarantees are provided.
* **Maintenance policy:** Issues, pull requests, and feature requests may not receive a response.

---

## Branch Topology

| Branch | Description |
| :--- | :--- |
| **`master`** | Current development branch built on **AndroidAPS 4.0.0** (KMP + Jetpack Compose M3 + Metro DI + AIMI 3.3). |
| **`pre-aaps-4.0`** | Archived legacy branch based on **AndroidAPS 3.x** (XML UI + Dagger 2, frozen at commit `9f9249d2a7`). |

---

## Key Modifications and Changelog

### 1. Full Architecture Migration to AndroidAPS 4.0.0 (KMP + Compose M3 + Metro DI)
* **Upstream 4.0.0 Merge and Metro DI Transition** (`9f776dad99`, `d2b04f56c0`, `8cfbf88522`, `acc49d5217`, `133b9b7b38`, `3c8580aeb9`, `cf2988fd2b`):
  * Complete migration of dependency injection graphs from Dagger 2 to `dev.zacsweers.metro` (`AppRootGraph`, `SharedImplBindings`, `ApsAndroidPluginRegistrations`, `WCycleBindings`, `SourceMetroGraph`, `SyncMemberInjectors`, `ApexBindings`).
  * Full preservation of the AIMI 3.3 mathematical core (`DetermineBasalAIMI2`, `AIMIAdaptiveBasal`, `AutodriveEngine`, `MpcController`, `ControlBarrierShield`, `SafetyNet`, `PkPdCore`, `TrajectoryGuard`, `IsfBlender`, `KalmanFilter`, `UnifiedReactivityLearner`, `BasalNeuralLearner`, `WCycleFacade`, `EndometriosisAdjuster`) within the KMP module structure without altering closed-loop dosing equations.
  * Removal of 99 tracked build artifacts, binary dumps, and temporary files prior to the upstream merge.
* **Native Jetpack Compose and Material 3 UI** (`69af1abf28`, `1e3e23a0f5`, `272b6e8436`, `31fe5e258c`):
  * Full refactoring of legacy XML Activities and Fragments into native Compose M3 screens: `MealAdvisorScreen`, `MealAdvisorCameraScreen`, `AimiProfileAdvisorScreen`, `AimiModeSettingsScreen`, `ContextScreen`, `RemoteControlScreen`, `ApexPumpScreen`, `AdjustmentDetailsActivity`, `LoopStateActivity`, and `UserManualActivity`.
  * Integration of `AimiOverviewCard` into the native 4.0.0 `OverviewScreen` featuring live physiological telemetry, direct Meal Advisor launch, and Dual-Brain Auditor status badges.
  * Visual separation of the graph container via a rounded surface card, vertical scroll lock when secondary graphs are not configured, explicit temporary basal rate display inside `TbrChip`, and updated `PumpActivityFab` behavior (hidden when connected and idle; displayed in high-contrast red `errorContainer` upon pump disconnection).
  * Complete 1-to-1 restoration of the AIMI 3.3 preference hierarchy (18 subscreens, 130+ preference keys, and interactive `.withClick` action handlers) with full Russian localization (`7aaa56d5c8`, `31fe5e258c`).

### 2. Juggluco NG Clinical Analytics and Profile Generator (`8431181fae`, `5631f8c4a2`, `fd39deb8eb`)
* **Deep Glycemic Statistics in Patient Units (`mmol/L` / `mg/dL`):**
  * Computation of 5-zone TIR distribution (`<3.0`, `3.0-3.9`, `3.9-10.0`, `10.0-13.9`, `>13.9 mmol/L`), Tight Range (`3.9-7.8 mmol/L`), `GMI`, `SD`, `CV%`, `IQR`, `GVI` (Glycemic Variability Index), `PSG` (Patient Glycemic Status), and **Glycemia Risk Index (`GRI`, 0-100, Zones A-E)** with separate hypo and hyper components.
  * Native 24-hour **AGP (Ambulatory Glucose Profile)** Compose Canvas rendering median curves alongside `P10-P90` and `P25-P75` percentile bands.
  * Day-by-day TIR breakdown grid, clinical hypo/hyper episode detector (`>= 15 min` with time-to-peak tracking), and meal outcome evaluation with pre-bolus timing analysis.
* **Multi-Block Profile Generator (`createAndActivateAimiProfile`):**
  * Automated generation and activation of `LocalProfile` records with proportional scaling across all existing time blocks of `IC` (`carbratio`) and `ISF` (`sens`) strictly in patient units (`mmol/L` or `mg/dL`), clamped to `+-15%` (`coerceIn(0.85, 1.15)`).

### 3. AI Ecosystem (2026 Models) and Credential Hardening (`66b7c05469`, `272b6e8436`, `31fe5e258c`, `fd39deb8eb`)
* **Multi-Provider LLM and Vision Integration:**
  * Direct API-key support for `Gemini 3.8 Flash / 3.1 Pro`, `OpenAI GPT-6 Astra / Sol`, `Claude Sonnet 5`, `DeepSeek V4`, `GLM-5.2`, `xAI Grok-4`, `OpenRouter`, `Groq`, and custom OpenAI-compatible endpoints (deprecated Gemini OAuth removed).
  * Context-aware preference visibility (`ElementVisibility`) for API keys and centralized model selection across all AI services (`AuditorAIService`, `AiCoachingService`, `ContextLLMClient`, `FoodRecognitionService`).
  * Sequential Mixed Mode barcode lookup (`OpenFoodFacts -> FatSecret` fallback).
* **Network and Credential Security:**
  * Strict pre-connection validation for `Custom Endpoint URL` (eliminating fallback to `api.openai.com` to prevent accidental `Bearer` token leakage when the custom URL is blank) and explicit disabling of cross-host HTTP redirects (`instanceFollowRedirects = false`) across all AI HTTP clients.

### 4. Apex Pump Driver, 1-Minute xDrip+ Poller, and System Reliability
* **Apex Pump (`Apex TruCare III`) and Bluetooth Stability:**
  * Registration of `:pump:apex` in the `@PumpDriver` multibinding map for the `full` build flavor (`63e22ddb56`).
  * Support for protocol 4.12, fix for IOB double-counting during bolus delivery, and resolution of bolus delivery deadlocks (`e930eb03a2`, `f49f8b79be`, `9f9249d2a7`, `f83d3d2a65`).
  * Preservation of `BluetoothWakeLock` and `DozeMonitor` for uninterrupted overnight pump communication.
* **Reactive 1-Minute xDrip+ Polling (`XdripSourcePlugin`):**
  * Background coroutine poller querying `http://127.0.0.1:17580/sgv.json?count=1` every 60 seconds with reactive lifecycle management via `preferences.observe(BooleanKey.OApsxdriponeminute)` and two-way timestamp validation (rejecting stale readings `>10 min` old and future readings `>60s` ahead) without triggering calibration intents (`31fe5e258c`, `fd39deb8eb`).
* **System Safeguards and Full State Backup (`5631f8c4a2`, `294286b575`, `78f3c0d393`):**
  * 7-tap developer unlock in `AboutDialog` and `UserManualActivity` exposing the AIMI Engineering Bypass toggle in the navigation drawer and settings, with automatic state preservation during upgrades.
  * Prevention of mutual recursion between `PreferencesImpl` and `ConfigImpl.isEngineeringMode()`, and alignment of `DummyService` `foregroundServiceType="specialUse|dataSync"` on Android 14/15 (API 34/35).
  * Complete exportability of all AIMI, Apex, Garmin, and profile preference prefixes (`ExportableKeys.isCustomExportableKeyPrefix`) alongside automated cloud backup routing (`CloudConstants.CLOUD_PATH_AIMI`).
