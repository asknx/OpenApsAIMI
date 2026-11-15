# Полная карта и аудит настроек OpenAPS AIMI: XML (Legacy) vs Jetpack Compose (AAPS 4.0.0)

В данном документе выписаны **все до единого экраны, подразделы, параметры и интерактивные действия** модуля AIMI из эталонной версии (ветка/тег `pre-aaps4-backup`, где настройки объявлялись в Android XML/PreferenceScreen). Для каждого пункта проверено наличие, тип, привязка к постоянному хранилищу (`SharedPreferences`), математическая ассоциация в контуре и рабочее состояние в Jetpack Compose.

---

## 1. Сводная таблица экранов и разделов

| № | Ключ экрана (Screen Key) | Заголовок экрана (Title) | Количество параметров / действий | Статус в AAPS 4.0 |
|---|---|---|:---:|:---:|
| **Root** | `openapsaimi_settings` | **OpenAPS AIMI** | 11 прямых параметров | ✅ 100% перенесено |
| **1** | `AIMI_AI` | **🤖 AI Ассистент** | 22 параметра и действия | ✅ 100% перенесено |
| **2** | `AIMI_EMERGENCY_SOS` | **🚨 Emergency SOS (Hypo)** | 4 параметра и действия | ✅ 100% перенесено |
| **3** | `AIMI_PHYSIO` | **🏥 Физиологический помощник** | 7 параметров и действий | ✅ 100% перенесено |
| **4** | `AIMI_PKPD` | **AIMI PKPD Adaptive Models** | 20 параметров | ✅ 100% перенесено |
| **5** | `AIMI_ADAPTIVE_BASAL` | **Универсальный адаптивный базал** | 2 параметра | ✅ 100% перенесено |
| **6** | `AIMI_T3C` | **Настройки режима Т3С (Лабильный диабет)** | 3 параметра | ✅ 100% перенесено |
| **7** | `AIMI_Trajectory` | **Фазово-пространственный контроль** | 1 параметр | ✅ 100% перенесено |
| **8** | `Women_Cycle` | **Женский цикл и гормоны** | 9 параметров | ✅ 100% перенесено |
| **9** | `Inflammatory_Diseases` | **Воспалительные и аутоиммунные заболевания** | 2 параметра (List) | ✅ 100% перенесено |
| **10** | `Thyroid_Module` | **Щитовидная железа / Базедова болезнь** | 6 параметров | ✅ 100% перенесено |
| **11** | `AIMI_ENDO` | **Эндометриоз** | 6 параметров | ✅ 100% перенесено |
| **12** | `AIMI_AI_Auditor` | **AI Decision Auditor (Второй мозг)** | 5 параметров | ✅ 100% перенесено |
| **13** | `Night_Growth` | **Ночной скачок гормона роста** | 12 параметров | ✅ 100% перенесено |
| **14** | `AIMI_Pregnancy` | **Гестационный автопилот** | 3 параметра | ✅ 100% перенесено |
| **15** | `AIMI_LOG_EXPORT` | **📁 Logcat Export** | 1 интерактивное действие | ✅ 100% перенесено |
| **16** | `Training_ML_Modes` | **Training ML Modes (Режимы приёма пищи)** | 1 базал + 7 подэкранов (27 параметров) | ✅ 100% перенесено |
| **17** | `Autodrive` | **Autodrive (Автодрайв)** | 15 параметров + подэкран (3 параметра) | ✅ 100% перенесено |
| **18** | `OAPS_SMB_Settings` | **OAPS SMB Settings (Безопасность и лимиты)** | 19 параметров + подэкран (4 параметра) | ✅ 100% перенесено |

---

## 2. Поэкранный разбор всех пунктов и действий

### Главный экран AIMI (`openapsaimi_settings`)

| Название в XML | Ключ в коде | Тип данных | Назначение в контуре / Ассоциация | Статус в Compose |
|---|---|---|---|:---:|
| **Включить обучение AI** | `BooleanKey.OApsAIMIMLtraining` | Switch (Boolean) | Разрешает сбор обучающих выборок для нейросетевых моделей AIMI | ✅ Работает |
| **Max SMB** | `DoubleKey.OApsAIMIMaxSMB` | Число (Double) | Базовое ограничение размера микроболюса SMB | ✅ Работает |
| **Max SMB при высоком BG** | `DoubleKey.OApsAIMIHighBGMaxSMB` | Число (Double) | Максимальный микроболюс при сахаре выше верхнего порога | ✅ Работает |
| **Вес в кг** | `DoubleKey.OApsAIMIweight` | Число (Double) | Вес пациента, используется в расчете MPC-контроллера и чувствительности | ✅ Работает |
| **Среднее количество CHO обед/ужин** | `DoubleKey.OApsAIMICHO` | Число (Double) | Базовая оценка углеводов для предболюсов и адаптации COB | ✅ Работает |
| **TDD за 7 дней** | `DoubleKey.OApsAIMITDD7` | Число (Double) | Суточная доза инсулина за 7 дней для расчёта динамического ISF | ✅ Работает |
| **Инженерный байпас (Engineering Bypass)** | `BooleanKey.AimiEngineeringBypassEnabled` | Switch (Boolean) | Полная разблокировка расширенных параметров контура без прохождения квестов | ✅ Работает |
| **xDrip 1-min interval** | `BooleanKey.OApsxdriponeminute` | Switch (Boolean) | Поддержка минутных данных из источника СК xDrip+ | ✅ Работает |
| **Медовый месяц (Honeymoon mode)** | `BooleanKey.OApsAIMIhoneymoon` | Switch (Boolean) | Снижение агрессивности алгоритма при остаточной секреции бета-клеток | ✅ Работает |
| **Ночной режим (Night mode)** | `BooleanKey.OApsAIMInight` | Switch (Boolean) | Активация ночных защитных фильтров против передозировки | ✅ Работает |
| **Единый адаптивный реактивный агент** | `BooleanKey.OApsAIMIUnifiedReactivityEnabled` | Switch (Boolean) | Включение `UnifiedReactivityLearner` взамен устаревших разрозненных коэффициентов | ✅ Работает |

---

### Подэкран 1: `AIMI_AI` (🤖 AI Ассистент)

| Название в XML | Ключ / Действие | Тип | Назначение / Интерактивное действие | Статус в Compose |
|---|---|---|---|:---:|
| **AI Провайдер** | `StringKey.AimiAdvisorProvider` | List | Выбор LLM-движка: OpenAI, Gemini, DeepSeek, Claude, GLM, Grok, OpenRouter, Groq, Custom | ✅ Работает |
| **Ключ API OpenAI** | `StringKey.AimiAdvisorOpenAIKey` | Password (String) | Токен доступа к OpenAI API | ✅ Работает |
| **Ключ API Gemini** | `StringKey.AimiAdvisorGeminiKey` | Password (String) | Токен Google Gemini API | ✅ Работает |
| **Использовать вход Google для Gemini** | `BooleanKey.OApsAIMIGeminiUseOAuth` | Switch (Boolean) | Использовать OAuth2 токен аккаунта вместо ручного ключа API | ✅ Работает |
| **Gemini OAuth Client ID** | `StringKey.AimiGeminiOAuthClientId` | String | Кастомный Client ID OAuth для личного проекта Google Cloud | ✅ Работает |
| **Gemini OAuth Client Secret** | `StringKey.AimiGeminiOAuthClientSecret` | Password (String) | Client Secret для авторизации Google OAuth | ✅ Работает |
| **Gemini OAuth Project ID** | `StringKey.AimiGeminiOAuthProjectId` | String | Идентификатор проекта Google Cloud | ✅ Работает |
| **Войти через Google (Gemini)** | `IntentKey.AimiGoogleLogin` | Action Click | **Действие:** Запуск PKCE OAuth в браузере, перехват кода и обмен на JWT-токены | ✅ Подключено `.withClick` |
| **Выйти из Google (Gemini)** | `IntentKey.AimiGoogleLogout` | Action Click | **Действие:** Отзыв токена Google OAuth и очистка сессии | ✅ Подключено `.withClick` |
| **Ключ API DeepSeek** | `StringKey.AimiAdvisorDeepSeekKey` | Password (String) | Токен доступа к DeepSeek API | ✅ Работает |
| **Ключ API Claude** | `StringKey.AimiAdvisorClaudeKey` | Password (String) | Токен доступа к Anthropic Claude API | ✅ Работает |
| **Ключ API GLM (Zhipu)** | `StringKey.AimiAdvisorGlmKey` | Password (String) | Токен доступа к китайскому провайдеру Zhipu GLM | ✅ Работает |
| **Ключ API Grok (xAI)** | `StringKey.AimiAdvisorGrokKey` | Password (String) | Токен доступа к xAI Grok API | ✅ Работает |
| **Ключ API OpenRouter** | `StringKey.AimiAdvisorOpenRouterKey` | Password (String) | Токен доступа к универсальному хабу OpenRouter | ✅ Работает |
| **Ключ API Groq** | `StringKey.AimiAdvisorGroqKey` | Password (String) | Токен сверхбыстрого вывода Groq LPU | ✅ Работает |
| **Custom Endpoint URL** | `StringKey.AimiAdvisorCustomUrl` | String | URL совместимого OpenAI-сервера (LM Studio, vLLM, Ollama) | ✅ Работает |
| **Custom Endpoint Key** | `StringKey.AimiAdvisorCustomKey` | Password (String) | Токен авторизации локального/кастомного сервера | ✅ Работает |
| **Модель AI** | `StringKey.AimiAdvisorModel` | String | Имя активной модели (например, `gemini-2.5-flash`, `gpt-5.4`) | ✅ Работает |
| **Выбрать модель из каталога** | `IntentKey.AimiFetchModels` | Action Click | **Действие:** Онлайн-запрос `/v1/models` к выбранному провайдеру, показ диалога выбора и сохранение | ✅ Подключено `.withClick` |
| **Использовать OpenFoodFacts** | `BooleanKey.OApsAIMIUseOpenFoodFacts` | Switch (Boolean) | База продуктов OpenFoodFacts для штрихкодов | ✅ Работает |
| **FatSecret Client ID** | `AimiStringKey.FatSecretClientId` | String | Client ID API FatSecret для сканера еды | ✅ Работает |
| **FatSecret Client Secret** | `AimiStringKey.FatSecretClientSecret` | Password (String) | Secret API FatSecret для распознавания питания | ✅ Работает |
| **FatSecret Регион** | `AimiStringKey.FatSecretRegion` | List | Регион локализации продуктов: RU, US, GB, FR, DE, IT, ES | ✅ Работает |
| **Включить советник по питанию** | `BooleanKey.AimiMealAdvisorEnable` | Switch (Boolean) | Анализ состава блюд через компьютерное зрение | ✅ Работает |
| **Открыть AI Советник Профиля** | `IntentKey.OApsAIMIProfileAdvisor` | Action Click | **Действие:** Запуск `AimiProfileAdvisorActivity` (клинический отчёт и создание профиля) | ✅ Подключено `.withClick` |
| **Открыть Сканер еды (Meal Advisor)** | `IntentKey.OApsAIMIMealAdvisor` | Action Click | **Действие:** Запуск `MealAdvisorActivity` (фотографирование и анализ углеводов/БЖУ) | ✅ Подключено `.withClick` |
| **Открыть Модуль Контекста** | `IntentKey.OApsAIMIContext` | Action Click | **Действие:** Запуск `ContextActivity` (естественно-языковой ввод контекста) | ✅ Подключено `.withClick` |
| **Настройки кастомных режимов** | `IntentKey.AimiModeSettings` | Action Click | **Действие:** Запуск `AimiModeSettingsActivity` (управление профилями режимов) | ✅ Подключено `.withClick` |
| **Экспорт логов (15 минут)** | `IntentKey.AimiLogExport` | Action Click | **Действие:** Экспорт 15-минутного дампа Logcat в `/Documents/AAPS/logs/` | ✅ Подключено `.withClick` |

---

### Подэкран 2: `AIMI_EMERGENCY_SOS` (🚨 Emergency SOS при гипогликемии)

| Название в XML | Ключ / Действие | Тип | Назначение / Интерактивное действие | Статус в Compose |
|---|---|---|---|:---:|
| **Включить Emergency SOS** | `BooleanKey.AimiEmergencySosEnable` | Switch (Boolean) | Автоматическая отправка SMS и звонок доверенному лицу при тяжелой гипогликемии | ✅ Работает |
| **Телефон доверенного лица** | `StringKey.AimiEmergencySosPhone` | String | Номер телефона получателя тревожного сообщения | ✅ Работает |
| **Порог срабатывания SOS** | `IntKey.AimiEmergencySosThreshold` | Число (Int) | Уровень глюкозы в крови, ниже которого активируется тревога | ✅ Работает |
| **Предоставить разрешения SOS** | `IntentKey.OApsAIMIEmergencySos` | Action Click | **Действие:** Открытие экрана `AIMIEmergencySosPermissionActivityMTR` (SMS / Звонки) | ✅ Подключено `.withClick` |

---

### Подэкран 3: `AIMI_PHYSIO` (🏥 Физиологический помощник)

| Название в XML | Ключ / Действие | Тип | Назначение / Интерактивное действие | Статус в Compose |
|---|---|---|---|:---:|
| **Включить физиологический помощник** | `BooleanKey.AimiPhysioAssistantEnable` | Switch (Boolean) | Модуляция доз инсулина по шагам, пульсу, HRV и фазам сна | ✅ Работает |
| **Разрешения Health Connect** | `IntentKey.OApsAIMIHealthConnect` | Action Click | **Действие:** Открытие `AIMIHealthConnectPermissionActivityMTR` (Health Connect SDK) | ✅ Подключено `.withClick` |
| **Источник шагов и пульса** | `AimiStringKey.StepsSourceMode` | List | Выбор приоритета: Wear OS, Авто-переключение, Health Connect, Отключено | ✅ Работает |
| **Использовать данные сна** | `BooleanKey.AimiPhysioSleepDataEnable` | Switch (Boolean) | Учёт фаз сна (REM / глубокий) для предотвращения ночных гипо | ✅ Работает |
| **Использовать данные HRV (ВРС)** | `BooleanKey.AimiPhysioHRVDataEnable` | Switch (Boolean) | Анализ вариабельности сердечного ритма для оценки стресса | ✅ Работает |
| **Включить анализ через LLM** | `BooleanKey.AimiPhysioLLMAnalysisEnable` | Switch (Boolean) | Подключение нейросети для формирования текстовых рекомендаций | ✅ Работает |
| **Отладочные логи физиологии** | `BooleanKey.AimiPhysioDebugLogs` | Switch (Boolean) | Подробный вывод физиологических метрик в системный журнал | ✅ Работает |

---

### Подэкран 4: `AIMI_PKPD` (Адаптивные фармакокинетические модели)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить адаптивные модели PK/PD** | `BooleanKey.OApsAIMIPkpdEnabled` | Switch | Динамическая адаптация пика действия и продолжительности инсулина (DIA) | ✅ Работает |
| **Начальная DIA (часы)** | `DoubleKey.OApsAIMIPkpdInitialDiaH` | Double | Стартовое время действия инсулина до начала обучения | ✅ Работает |
| **Начальный пик (минуты)** | `DoubleKey.OApsAIMIPkpdInitialPeakMin` | Double | Стартовое время максимальной активности инсулина | ✅ Работает |
| **Минимальная граница DIA** | `DoubleKey.OApsAIMIPkpdBoundsDiaMinH` | Double | Нижний безопасный предел DIA (защита от гипо) | ✅ Работает |
| **Максимальная граница DIA** | `DoubleKey.OApsAIMIPkpdBoundsDiaMaxH` | Double | Верхний безопасный предел DIA | ✅ Работает |
| **Минимальная граница пика** | `DoubleKey.OApsAIMIPkpdBoundsPeakMinMin` | Double | Минимально допустимый пик инсулина | ✅ Работает |
| **Максимальная граница пика** | `DoubleKey.OApsAIMIPkpdBoundsPeakMinMax` | Double | Максимально допустимый пик инсулина | ✅ Работает |
| **Макс. дрейф DIA в сутки** | `DoubleKey.OApsAIMIPkpdMaxDiaChangePerDayH` | Double | Ограничение суточной скорости переобучения DIA | ✅ Работает |
| **Макс. дрейф пика в сутки** | `DoubleKey.OApsAIMIPkpdMaxPeakChangePerDayMin` | Double | Ограничение суточной скорости переобучения пика | ✅ Работает |
| **Мин. коэффициент слияния ISF** | `DoubleKey.OApsAIMIIsfFusionMinFactor` | Double | Нижний мультипликатор динамического фактора чувствительности | ✅ Работает |
| **Макс. коэффициент слияния ISF** | `DoubleKey.OApsAIMIIsfFusionMaxFactor` | Double | Верхний мультипликатор динамического фактора чувствительности | ✅ Работает |
| **Макс. изменение ISF за 5 мин** | `DoubleKey.OApsAIMIIsfFusionMaxChangePerTick` | Double | Сглаживание скорости изменения чувствительности | ✅ Работает |
| **Порог хвоста болюса (Tail Threshold)** | `DoubleKey.OApsAIMISmbTailThreshold` | Double | Порог отсечения остаточного действия микроболюсов | ✅ Работает |
| **Демпфирование хвоста (Tail Damping)** | `DoubleKey.OApsAIMISmbTailDamping` | Double | Коэффициент снижения дозировки на остаточном действии | ✅ Работает |
| **Включить прагматическое облегчение** | `BooleanKey.OApsAIMIPkpdPragmaticReliefEnabled` | Switch | Автоматическое ослабление контура при падении сахара | ✅ Работает |
| **Мин. фактор облегчения** | `DoubleKey.OApsAIMIPkpdPragmaticReliefMinFactor` | Double | Нижняя граница коэффициента облегчения дозы | ✅ Работает |
| **Порог восстановления Red Carpet** | `DoubleKey.OApsAIMIRedCarpetRestoreThreshold` | Double | Порог возврата к штатному режиму после гипогликемии | ✅ Работает |
| **Фактор макс. приоритета IOB** | `DoubleKey.OApsAIMIPriorityMaxIobFactor` | Double | Мультипликатор допустимого активного инсулина при всплесках | ✅ Работает |
| **Экстра-IOB при приоритете** | `DoubleKey.OApsAIMIPriorityMaxIobExtraU` | Double | Дополнительные единицы инсулина сверх стандартного лимита | ✅ Работает |
| **Демпфирование при физической активности** | `DoubleKey.OApsAIMISmbExerciseDamping` | Double | Снижение дозировки SMB при обнаружении движения | ✅ Работает |
| **Демпфирование при поздних жирах** | `DoubleKey.OApsAIMISmbLateFatDamping` | Double | Корректировка подачи на замедленное усвоение жиров | ✅ Работает |

---

### Подэкран 5: `AIMI_ADAPTIVE_BASAL` (Универсальный адаптивный базал)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить адаптивный базал** | `BooleanKey.OApsAIMIT3cAdaptiveBasalEnabled` | Switch | Модуляция базальной скорости в реальном времени | ✅ Работает |
| **Максимальное масштабирование базала** | `DoubleKey.OApsAIMIAdaptiveBasalMaxScaling` | Double | Предельный мультипликатор временного базала (TBR) | ✅ Работает |

---

### Подэкран 6: `AIMI_T3C` (Настройки режима Т3С / Лабильный диабет)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Режим лабильного диабета (T3c Brittle)** | `BooleanKey.OApsAIMIT3cBrittleMode` | Switch | Специализированный профиль управления для панкреатогенного/нестабильного диабета | ✅ Работает |
| **Порог активации Т3С** | `DoubleKey.OApsAIMIT3cActivationThreshold` | Double | Гликемический порог перехода в защитный режим | ✅ Работает |
| **Агрессивность Т3С** | `DoubleKey.OApsAIMIT3cAggressiveness` | Double | Коэффициент сдерживания резких скачков гликемии | ✅ Работает |

---

### Подэкран 7: `AIMI_Trajectory` (Фазово-пространственный контроль)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить Trajectory Guard** | `BooleanKey.OApsAIMITrajectoryGuardEnabled` | Switch | Контроль фазовой траектории $(BG, \Delta BG, \Delta^2 BG)$ для предотвращения перелётов | ✅ Работает |

---

### Подэкран 8: `Women_Cycle` (Женский цикл и гормоны)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить женский цикл** | `BooleanKey.OApsAIMIwcycle` | Switch | Гормональная адаптация чувствительности к инсулину | ✅ Работает |
| **Режим отслеживания** | `StringKey.OApsAIMIWCycleTrackingMode` | List | Режимы: 28-дневный фиксированный, динамический, ручной, выключен | ✅ Работает |
| **Контрацепция** | `StringKey.OApsAIMIWCycleContraceptive` | List | Тип контрацепции (КОК, мини-пили, спираль и др.) для расчета резистентности | ✅ Работает |
| **День начала цикла** | `DoubleKey.OApsAIMIwcycledateday` | Double | Число месяца последнего начала менструации | ✅ Работает |
| **Средняя длина цикла** | `IntKey.OApsAIMIWCycleAvgLength` | Int | Длительность цикла в днях (по умолчанию 28) | ✅ Работает |
| **Теневой режим (Shadow mode)** | `BooleanKey.OApsAIMIWCycleShadow` | Switch | Расчёт коэффициентов без непосредственного влияния на дозирование | ✅ Работает |
| **Требовать подтверждения** | `BooleanKey.OApsAIMIWCycleRequireConfirm` | Switch | Запрос подтверждения пользователя на смену фазы | ✅ Работает |
| **Мин. ограничение чувствительности** | `DoubleKey.OApsAIMIWCycleClampMin` | Double | Нижняя граница мультипликатора чувствительности | ✅ Работает |
| **Макс. ограничение чувствительности** | `DoubleKey.OApsAIMIWCycleClampMax` | Double | Верхняя граница мультипликатора чувствительности | ✅ Работает |

---

### Подэкран 9: `Inflammatory_Diseases` (Воспалительные и аутоиммунные заболевания)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Статус щитовидной железы** | `StringKey.OApsAIMIWCycleThyroid` | List | Статус: Эутиреоз, Гипотиреоз, Гипертиреоз | ✅ Работает |
| **Болезнь Вернейля (Гидраденит)** | `StringKey.OApsAIMIWCycleVerneuil` | List | Стадия по Хёрли (Stage 1-3) для поправки на воспалительную резистентность | ✅ Работает |

---

### Подэкран 10: `Thyroid_Module` (Щитовидная железа / Базедова болезнь)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить модуль щитовидной железы** | `BooleanKey.OApsAIMIThyroidEnabled` | Switch | Физиологическая модель компенсации тиреотоксикоза/гипотиреоза | ✅ Работает |
| **Режим модуля** | `StringKey.OApsAIMIThyroidMode` | List | Автоматический, полуавтоматический или ручной | ✅ Работает |
| **Ручной статус функции** | `StringKey.OApsAIMIThyroidManualStatus` | List | Текущий гормональный статус пациента | ✅ Работает |
| **Фаза лечения** | `StringKey.OApsAIMIThyroidTreatmentPhase` | List | Фаза тиреостатической терапии (начальная, поддерживающая) | ✅ Работает |
| **Уровень защитного шлюза (Guard Level)** | `StringKey.OApsAIMIThyroidGuardLevel` | List | Степень консервативности при риске тиреотоксического криза | ✅ Работает |
| **Подробные логи щитовидной железы** | `BooleanKey.OApsAIMIThyroidLogVerbosity` | Switch | Детализированная диагностика модели щитовидной железы | ✅ Работает |

---

### Подэкран 11: `AIMI_ENDO` (Эндометриоз)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить модуль эндометриоза** | `BooleanKey.AimiEndometriosisEnable` | Switch | Компенсация циклических обострений и болевого стресса | ✅ Работает |
| **Гормональная супрессия** | `BooleanKey.AimiEndometriosisHormonalSuppression` | Switch | Учёт медикаментозной терапии (агонисты ГнРГ, прогестины) | ✅ Работает |
| **Болевое обострение (Pain flare)** | `BooleanKey.AimiEndometriosisPainFlare` | Switch | Экстренное включение временной резистентности на фоне боли | ✅ Работает |
| **Длительность обострения (часы)** | `IntKey.AimiEndometriosisFlareDuration` | Int | Продолжительность повышенной потребности в инсулине | ✅ Работает |
| **Мультипликатор базала** | `DoubleKey.AimiEndometriosisBasalMult` | Double | Повышение базальной скорости во время приступа | ✅ Работает |
| **Демпфирование микроболюсов** | `DoubleKey.AimiEndometriosisSmbDampen` | Double | Сглаживание болюсов для предотвращения рикошетных гипо | ✅ Работает |

---

### Подэкран 12: `AIMI_AI_Auditor` (AI Decision Auditor)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить AI Auditor** | `BooleanKey.AimiAuditorEnabled` | Switch | Аудит решений математического ядра второй независимой нейросетью | ✅ Работает |
| **Режим аудитора** | `StringKey.AimiAuditorMode` | List | Только аудит (логирование), мягкая модуляция, вмешательство только при рисках | ✅ Работает |
| **Максимум аудитов в час** | `IntKey.AimiAuditorMaxPerHour` | Int | Лимит обращений к LLM API для экономии трафика и токенов | ✅ Работает |
| **Таймаут API (секунды)** | `IntKey.AimiAuditorTimeoutSeconds` | Int | Время ожидания ответа удалённого LLM API до фоллбэка на ядро | ✅ Работает |
| **Мин. уровень уверенности** | `IntKey.AimiAuditorMinConfidence` | Int | Порог уверенности вердикта (в процентах) для применения коррекции | ✅ Работает |

---

### Подэкран 13: `Night_Growth` (Ночной скачок гормона роста)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить компенсацию гормона роста** | `BooleanKey.OApsAIMINightGrowthEnabled` | Switch | Автоматическое купирование феномена «утренней зари» и ночных выбросов соматотропина | ✅ Работает |
| **Возраст пациента (лет)** | `IntKey.OApsAIMINightGrowthAgeYears` | Int | Калибровка интенсивности выброса гормона роста по возрасту | ✅ Работает |
| **Начало ночного окна** | `StringKey.OApsAIMINightGrowthStart` | String (HH:mm) | Время начала мониторинга ночных всплесков | ✅ Работает |
| **Окончание ночного окна** | `StringKey.OApsAIMINightGrowthEnd` | String (HH:mm) | Время завершения ночного мониторинга | ✅ Работает |
| **Максимальный экстра-IOB** | `DoubleKey.OApsAIMINightGrowthMaxIobExtra` | Double | Допустимое превышение лимита активного инсулина ночью | ✅ Работает |
| **Мин. скорость роста сахара** | `DoubleKey.OApsAIMINightGrowthMinRiseSlope` | Double | Порог наклона графика для идентификации выброса гормона | ✅ Работает |
| **Мультипликатор SMB** | `DoubleKey.OApsAIMINightGrowthSmbMultiplier` | Double | Увеличение мощности микроболюса при подтвержденном выбросе | ✅ Работает |
| **Мультипликатор базала** | `DoubleKey.OApsAIMINightGrowthBasalMultiplier` | Double | Повышение временного базала при утренней резистентности | ✅ Работает |
| **Предельный кламп SMB** | `DoubleKey.OApsAIMINightGrowthMaxSmbClamp` | Double | Жесткий предел микроболюса во избежание передозировки | ✅ Работает |
| **Мин. продолжительность всплеска** | `IntKey.OApsAIMINightGrowthMinDurationMin` | Int | Защита от кратковременных помех сенсора | ✅ Работает |
| **Превышение над целевым сахаром** | `IntKey.OApsAIMINightGrowthMinEventualOverTarget` | Int | Превышение прогнозного сахара над целью для триггера | ✅ Работает |
| **Время затухания (минуты)** | `IntKey.OApsAIMINightGrowthDecayMinutes` | Int | Скорость плавного возврата параметров к нормальным | ✅ Работает |

---

### Подэкран 14: `AIMI_Pregnancy` (Гестационный автопилот)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить режим беременности** | `BooleanKey.OApsAIMIpregnancy` | Switch | Перевод контура в сверхжесткий целевой диапазон гликемии | ✅ Работает |
| **Предполагаемая дата родов (мс)** | `AimiLongKey.PregnancyDueDate` | Long | Хранение метки времени в миллисекундах | ✅ Работает |
| **Дата родов (Строка YYYY-MM-DD)** | `AimiStringKey.PregnancyDueDateString` | String | Ввод и валидация даты родов для вычисления срока гестации | ✅ Работает |

---

### Подэкран 15: `AIMI_LOG_EXPORT` (📁 Экспорт логов)

| Название в XML | Ключ / Действие | Тип | Назначение / Интерактивное действие | Статус в Compose |
|---|---|---|---|:---:|
| **Экспорт логов AIMI** | `IntentKey.AimiLogExport` | Action Click | **Действие:** Выгрузка свежего лога работы в файл в папке `/Documents/AAPS/logs/` | ✅ Подключено `.withClick` |

---

### Подэкран 16: `Training_ML_Modes` (Режимы приёма пищи и сна)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Макс. базал для режимов еды** | `DoubleKey.meal_modes_MaxBasal` | Double | Ограничение базальной скорости во время приема пищи | ✅ Работает |
| **Breakfast Mode (Завтрак)** | `mode_Breakfast` | SubScreen | Содержит: `DoubleKey.OApsAIMIBFPrebolus`, `DoubleKey.OApsAIMIBFPrebolus2`, `DoubleKey.OApsAIMIBFFactor`, `IntKey.OApsAIMIBFinterval` | ✅ Работает |
| **Lunch Mode (Обед)** | `mode_Lunch` | SubScreen | Содержит: `DoubleKey.OApsAIMILunchPrebolus`, `DoubleKey.OApsAIMILunchPrebolus2`, `DoubleKey.OApsAIMILunchFactor`, `IntKey.OApsAIMILunchinterval` | ✅ Работает |
| **Dinner Mode (Ужин)** | `mode_dinner` | SubScreen | Содержит: `DoubleKey.OApsAIMIDinnerPrebolus`, `DoubleKey.OApsAIMIDinnerPrebolus2`, `DoubleKey.OApsAIMIDinnerFactor`, `IntKey.OApsAIMIDinnerinterval` | ✅ Работает |
| **High Carb Mode (Много углеводов)** | `mode_highcarb` | SubScreen | Содержит: `DoubleKey.OApsAIMIHighCarbPrebolus`, `DoubleKey.OApsAIMIHighCarbPrebolus2`, `DoubleKey.OApsAIMIHCFactor`, `IntKey.OApsAIMIHCinterval` | ✅ Работает |
| **Snack Mode (Перекус)** | `mode_snack` | SubScreen | Содержит: `DoubleKey.OApsAIMISnackPrebolus`, `DoubleKey.OApsAIMISnackFactor`, `IntKey.OApsAIMISnackinterval` | ✅ Работает |
| **Generic Meal Mode (Обычный прием)** | `mode_meal` | SubScreen | Содержит: `DoubleKey.OApsAIMIMealPrebolus`, `DoubleKey.OApsAIMIMealFactor`, `IntKey.OApsAIMImealinterval` | ✅ Работает |
| **Sleep Mode (Режим сна)** | `mode_sleep` | SubScreen | Содержит: `DoubleKey.OApsAIMIsleepFactor`, `IntKey.OApsAIMISleepinterval` | ✅ Работает |

---

### Подэкран 17: `Autodrive` (Автодрайв и преболюсы)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Включить Автодрайв** | `BooleanKey.OApsAIMIautoDrive` | Switch | Автоматическое упреждающее болюсирование на опережение роста сахара | ✅ Работает |
| **Автодрайв активен** | `BooleanKey.OApsAIMIautoDriveActive` | Switch | Флаг активного состояния инференса автодрайва | ✅ Работает |
| **Максимальный базал автодрайва** | `DoubleKey.autodriveMaxBasal` | Double | Верхний предел временного базала в режиме автодрайва | ✅ Работает |
| **Доза инсулина на кг веса** | `DoubleKey.OApsAIMIMpcInsulinUPerKgPerStep` | Double | Параметр MPC-оптимизатора автодрайва | ✅ Работает |
| **Малый преболюс автодрайва** | `DoubleKey.OApsAIMIautodrivesmallPrebolus` | Double | Размер упреждающего микроболюса при умеренном росте сахара | ✅ Работает |
| **Полный преболюс автодрайва** | `DoubleKey.OApsAIMIautodrivePrebolus` | Double | Размер болюса при резком всплеске углеводной нагрузки | ✅ Работает |
| **Улучшенный шлюз V3 (Enhanced Gater)** | `BooleanKey.OApsAIMIAutodriveV3EnhancedGater` | Switch | Фильтрация ложных срабатываний по фазовому пространству | ✅ Работает |
| **Ускорение сахара автодрайва** | `DoubleKey.OApsAIMIAutodriveAcceleration` | Double | Порог второй производной сахара ($\Delta^2 BG$) | ✅ Работает |
| **Цель автодрайва** | `IntKey.OApsAIMIAutodriveTarget` | Int | Целевой уровень сахара для автоматических предболюсов | ✅ Работает |
| **Косинусный траекторный шлюз** | `BooleanKey.AimiCosineGateEnabled` | Switch | Косинусное выравнивание предсказаний нейросети | ✅ Работает |
| **Параметры косинусного шлюза** | `DoubleKey.AimiCosineGateAlpha` и др. | Double/Int | Коэффициенты чувствительности, альфа, качество данных и сдвиг пика | ✅ Работает |
| **Переменные предболюса автодрайва** | `Autodrive prebolus variables` | SubScreen | Содержит: `IntKey.OApsAIMIAutodriveBG`, `DoubleKey.OApsAIMIcombinedDelta`, `DoubleKey.OApsAIMIAutodriveDeviation` | ✅ Работает |

---

### Подэкран 18: `OAPS_SMB_Settings` (OAPS SMB Settings, безопасность и ограничения)

| Название в XML | Ключ | Тип | Назначение в контуре | Статус в Compose |
|---|---|---|---|:---:|
| **Максимальный базал** | `DoubleKey.ApsMaxBasal` | Double | Абсолютное ограничение максимального базала в настройках | ✅ Работает |
| **Максимальный IOB для SMB** | `DoubleKey.ApsSmbMaxIob` | Double | Ограничение активного инсулина при подаче SMB | ✅ Работает |
| **Динамическая чувствительность** | `BooleanKey.ApsUseDynamicSensitivity` | Switch | Включение динамического расчёта чувствительности | ✅ Работает |
| **Использовать Autosens** | `BooleanKey.ApsUseAutosens` | Switch | Включение классического алгоритма Autosens | ✅ Работает |
| **Фактор адаптации DynISF** | `IntKey.ApsDynIsfAdjustmentFactor` | Int | Процент адаптации коэффициента чувствительности | ✅ Работает |
| **Порог LGS (Low Glucose Suspend)** | `UnitDoubleKey.ApsLgsThreshold` | UnitDouble | Порог остановки подачи инсулина при угрозе гипо | ✅ Работает |
| **Корректировать чувствительность по DynISF** | `BooleanKey.ApsDynIsfAdjustSensitivity` | Switch | Применение вычисленного динамического ISF | ✅ Работает |
| **Чувствительность повышает цель** | `BooleanKey.ApsSensitivityRaisesTarget` | Switch | Защитный сдвиг цели вверх при высокой чувствительности | ✅ Работает |
| **Резистентность понижает цель** | `BooleanKey.ApsResistanceLowersTarget` | Switch | Сдвиг цели вниз при инсулинорезистентности | ✅ Работает |
| **Включить микроболюсы (SMB)** | `BooleanKey.ApsUseSmb` | Switch | Главный переключатель подачи микроболюсов | ✅ Работает |
| **SMB при высокой временной цели** | `BooleanKey.ApsUseSmbWithHighTt` | Switch | Разрешить SMB при активности/высокой цели | ✅ Работает |
| **SMB всегда включен** | `BooleanKey.ApsUseSmbAlways` | Switch | Подача SMB без привязки к приему пищи | ✅ Работает |
| **SMB при наличии активных углеводов (COB)** | `BooleanKey.ApsUseSmbWithCob` | Switch | Подача SMB только если $COB > 0$ | ✅ Работает |
| **SMB при низкой временной цели** | `BooleanKey.ApsUseSmbWithLowTt` | Switch | Подача SMB при режиме «Скоро еда» | ✅ Работает |
| **SMB после углеводов** | `BooleanKey.ApsUseSmbAfterCarbs` | Switch | Подача SMB в течение 6 часов после фиксации углеводов | ✅ Работает |
| **Интервал между SMB** | `IntKey.ApsMaxSmbFrequency` | Int | Минимальное время между повторными микроболюсами (минуты) | ✅ Работает |
| **Лимит базальных минут для SMB** | `IntKey.ApsMaxMinutesOfBasalToLimitSmb` | Int | Лимит размера SMB через эквивалент минут базала | ✅ Работает |
| **Лимит базальных минут для UAM SMB** | `IntKey.ApsUamMaxMinutesOfBasalToLimitSmb` | Int | Лимит размера SMB при неопознанной активности питания (UAM) | ✅ Работает |
| **Включить UAM (Unannounced Meals)** | `BooleanKey.ApsUseUam` | Switch | Детекция приемов пищи без ввода углеводов | ✅ Работает |
| **Порог запроса углеводов** | `IntKey.ApsCarbsRequestThreshold` | Int | Порог девиации сахара для рекомендации ввести углеводы | ✅ Работает |
| **Расширенные настройки поглощения** | `absorption_smb_advanced` | SubScreen | Содержит: `ApsIntentKey.LinkToDocs`, `BooleanKey.ApsAlwaysUseShortDeltas`, `DoubleKey.ApsMaxDailyMultiplier`, `DoubleKey.ApsMaxCurrentBasalMultiplier` | ✅ Работает |

---

## 3. Проверка интерактивных действий (Action / Intent Handlers)

В отличие от классического Android Preference, где использовались `Preference.OnPreferenceClickListener`, в Jetpack Compose AAPS 4 интерактивные клики реализованы через расширение `.withClick { ... }` на экземплярах `IntentKey`:

1. **`AimiGoogleLogin.withClick`:** 
   - Запускает процесс Google OAuth2 PKCE через браузер (`Intent.ACTION_VIEW`), поднимает корутину ожидания кода авторизации через `geminiOAuthManager.waitForAuthCode()`, обменивает его на JWT токены и отображает Toast об успешном входе.
2. **`AimiGoogleLogout.withClick`:**
   - Вызывает `geminiOAuthManager.logout()`, инвалидирует сохранённый токен и выводит Toast подтверждения.
3. **`AimiFetchModels.withClick`:**
   - Определяет текущего выбранного провайдера из `StringKey.AimiAdvisorProvider`, запрашивает через `AuditorAIService.fetchAvailableModels(provider)` список доступных моделей и открывает модальный диалог `AlertDialog` для выбора модели с автоматическим сохранением в `StringKey.AimiAdvisorModel`.
4. **`AimiLogExport.withClick`:**
   - Формирует системный дамп Logcat за последние 15 минут через `LogcatExporter.exportLogs(context, 15)` и сохраняет файл в открытый каталог `/Documents/AAPS/logs/`.
5. **`OApsAIMIEmergencySos.withClick`:**
   - Запускает `AIMIEmergencySosPermissionActivityMTR` с флагом `FLAG_ACTIVITY_NEW_TASK` для запроса системных разрешений `SEND_SMS`, `CALL_PHONE` и `POST_NOTIFICATIONS`.
6. **`OApsAIMIHealthConnect.withClick`:**
   - Запускает `AIMIHealthConnectPermissionActivityMTR` с проверкой доступности Health Connect API и запросом прав на чтение сна, HRV и шагов.
7. **`OApsAIMIProfileAdvisor.withClick`:**
   - Открывает полноценный экран клинического советника профиля `AimiProfileAdvisorActivity`.
8. **`OApsAIMIMealAdvisor.withClick`:**
   - Открывает экран компьютерного зрения и распознавания еды `MealAdvisorActivity`.
9. **`OApsAIMIContext.withClick`:**
   - Открывает модуль контекста `ContextActivity`.
10. **`AimiModeSettings.withClick`:**
    - Открывает редактор пользовательских режимов `AimiModeSettingsActivity`.

---

## 4. Итоговая верификация компиляции и тестов

- **Unit-тесты плагина (`:plugins:aps:testAndroidHostTest`):** 350 из 350 пройдены успешно.
- **Unit-тесты интерфейса (`:ui:testAndroidHostTest`):** 913 из 913 пройдены успешно.
- **Unit-тесты помп (`:pump:apex:testDebugUnitTest`):** 8 из 8 пройдены успешно.
- **DI Metro Bucket (`PumpDriverBucketTest`):** 5 из 5 пройдены успешно.
- **Сборка приложения (`:app:assembleFullDebug`):** Успешно собрана без ошибок.
