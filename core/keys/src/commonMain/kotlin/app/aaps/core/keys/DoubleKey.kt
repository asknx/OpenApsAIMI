package app.aaps.core.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.DoublePreferenceKey
import app.aaps.core.keys.interfaces.ElementVisibility
import app.aaps.core.keys.interfaces.SyncChannel
import app.aaps.core.keys.interfaces.SyncDirection
import app.aaps.core.keys.interfaces.SyncSpec
import app.aaps.core.keys.interfaces.TextRef

enum class DoubleKey(
    override val key: String,
    override val defaultValue: Double,
    override val min: Double,
    override val max: Double,
    override val title: TextRef,
    override val summary: TextRef? = null,
    override val preferenceType: PreferenceType = PreferenceType.TEXT_FIELD,
    override val defaultedBySM: Boolean = false,
    override val calculatedBySM: Boolean = false,
    override val dependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val unitType: UnitType = UnitType.NONE,
    override val sync: SyncSpec? = null,
    override val visibility: ElementVisibility = ElementVisibility.ALWAYS
) : DoublePreferenceKey {

    OverviewInsulinButtonIncrement1(
        key = "insulin_button_increment_1",
        defaultValue = 0.5,
        min = -5.0,
        max = 5.0,
        title = KeysStrings.pref_title_insulin_button_increment_1,
        summary = KeysStrings.insulin_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowInsulinButton,
        unitType = UnitType.INSULIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewInsulinButtonIncrement2(
        key = "insulin_button_increment_2",
        defaultValue = 1.0,
        min = -5.0,
        max = 5.0,
        title = KeysStrings.pref_title_insulin_button_increment_2,
        summary = KeysStrings.insulin_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowInsulinButton,
        unitType = UnitType.INSULIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    OverviewInsulinButtonIncrement3(
        key = "insulin_button_increment_3",
        defaultValue = 2.0,
        min = -5.0,
        max = 5.0,
        title = KeysStrings.pref_title_insulin_button_increment_3,
        summary = KeysStrings.insulin_increment_button_message,
        defaultedBySM = true,
        dependency = BooleanKey.OverviewShowInsulinButton,
        unitType = UnitType.INSULIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    // Hidden where the pump cannot be primed from AAPS (patch pumps): the Fill dialog has no prime there.
    ActionsFillButton1(
        key = "fill_button1", defaultValue = 0.3, min = 0.05, max = 20.0, title = KeysStrings.pref_title_fill_button_1, defaultedBySM = true, hideParentScreenIfHidden = true,
        unitType = UnitType.INSULIN, visibility = ElementVisibility.REFILLING_CAPABLE
    ),
    ActionsFillButton2(
        key = "fill_button2", defaultValue = 0.0, min = 0.0, max = 20.0, title = KeysStrings.pref_title_fill_button_2, defaultedBySM = true,
        unitType = UnitType.INSULIN, visibility = ElementVisibility.REFILLING_CAPABLE
    ),
    ActionsFillButton3(
        key = "fill_button3", defaultValue = 0.0, min = 0.0, max = 20.0, title = KeysStrings.pref_title_fill_button_3, defaultedBySM = true,
        unitType = UnitType.INSULIN, visibility = ElementVisibility.REFILLING_CAPABLE
    ),
    SafetyMaxBolus(key = "treatmentssafety_maxbolus", defaultValue = 3.0, min = 0.1, max = 60.0, title = KeysStrings.pref_title_max_bolus, unitType = UnitType.INSULIN, sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)),
    ApsMaxBasal(
        key = "openapsma_max_basal",
        defaultValue = 1.0,
        min = 0.1,
        max = 25.0,
        title = KeysStrings.pref_title_max_basal,
        summary = KeysStrings.openapsma_max_basal_summary,
        defaultedBySM = true,
        calculatedBySM = true,
        unitType = UnitType.INSULIN_RATE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsSmbMaxIob(
        key = "openapsmb_max_iob",
        defaultValue = 3.0,
        min = 0.0,
        max = 70.0,
        title = KeysStrings.pref_title_smb_max_iob,
        summary = KeysStrings.openapssmb_max_iob_summary,
        defaultedBySM = true,
        calculatedBySM = true,
        unitType = UnitType.INSULIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAmaMaxIob(
        key = "openapsma_max_iob",
        defaultValue = 1.5,
        min = 0.0,
        max = 25.0,
        title = KeysStrings.pref_title_ama_max_iob,
        summary = KeysStrings.openapsma_max_iob_summary,
        defaultedBySM = true,
        calculatedBySM = true,
        unitType = UnitType.INSULIN,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsMaxDailyMultiplier(
        key = "openapsama_max_daily_safety_multiplier",
        defaultValue = 3.0,
        min = 1.0,
        max = 10.0,
        title = KeysStrings.pref_title_max_daily_multiplier,
        summary = KeysStrings.openapsama_max_daily_safety_multiplier_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsMaxCurrentBasalMultiplier(
        key = "openapsama_current_basal_safety_multiplier",
        defaultValue = 4.0,
        min = 1.0,
        max = 10.0,
        title = KeysStrings.pref_title_current_basal_multiplier,
        summary = KeysStrings.openapsama_current_basal_safety_multiplier_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAmaBolusSnoozeDivisor(
        key = "bolussnooze_dia_divisor",
        defaultValue = 2.0,
        min = 1.0,
        max = 10.0,
        title = KeysStrings.pref_title_bolus_snooze_divisor,
        summary = KeysStrings.openapsama_bolus_snooze_dia_divisor_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAmaMin5MinCarbsImpact(
        key = "openapsama_min_5m_carbimpact",
        defaultValue = 3.0,
        min = 1.0,
        max = 12.0,
        title = KeysStrings.pref_title_ama_min_5m_carbs_impact,
        summary = KeysStrings.openapsama_min_5m_carb_impact_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsSmbMin5MinCarbsImpact(
        key = "openaps_smb_min_5m_carbimpact",
        defaultValue = 8.0,
        min = 1.0,
        max = 12.0,
        title = KeysStrings.pref_title_smb_min_5m_carbs_impact,
        summary = KeysStrings.openapsama_min_5m_carb_impact_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AbsorptionCutOff(
        key = "absorption_cutoff",
        defaultValue = 6.0,
        min = 4.0,
        max = 10.0,
        title = KeysStrings.pref_title_absorption_cutoff,
        summary = KeysStrings.absorption_cutoff_summary,
        unitType = UnitType.HOURS_DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AbsorptionMaxTime(
        key = "absorption_maxtime",
        defaultValue = 6.0,
        min = 4.0,
        max = 10.0,
        title = KeysStrings.pref_title_absorption_maxtime,
        summary = KeysStrings.absorption_max_time_summary,
        unitType = UnitType.HOURS_DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AutosensMin(
        key = "autosens_min",
        defaultValue = 0.7,
        min = 0.1,
        max = 1.0,
        title = KeysStrings.pref_title_autosens_min,
        summary = KeysStrings.openapsama_autosens_min_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    AutosensMax(
        key = "autosens_max",
        defaultValue = 1.2,
        min = 0.5,
        max = 3.0,
        title = KeysStrings.pref_title_autosens_max,
        summary = KeysStrings.openapsama_autosens_max_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfMin(
        key = "autoISF_min",
        defaultValue = 1.0,
        min = 0.3,
        max = 1.0,
        title = KeysStrings.pref_title_autoisf_min,
        summary = KeysStrings.openapsama_autoISF_min_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfMax(
        key = "autoISF_max",
        defaultValue = 1.0,
        min = 1.0,
        max = 3.0,
        title = KeysStrings.pref_title_autoisf_max,
        summary = KeysStrings.openapsama_autoISF_max_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfBgAccelWeight(
        key = "bgAccel_ISF_weight",
        defaultValue = 0.0,
        min = 0.0,
        max = 1.0,
        title = KeysStrings.pref_title_bg_accel_weight,
        summary = KeysStrings.openapsama_bgAccel_ISF_weight_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfBgBrakeWeight(
        key = "bgBrake_ISF_weight",
        defaultValue = 0.0,
        min = 0.0,
        max = 1.0,
        title = KeysStrings.pref_title_bg_brake_weight,
        summary = KeysStrings.openapsama_bgBrake_ISF_weight_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfLowBgWeight(
        key = "lower_ISFrange_weight",
        defaultValue = 0.0,
        min = 0.0,
        max = 2.0,
        title = KeysStrings.pref_title_low_bg_weight,
        summary = KeysStrings.openapsama_lower_ISFrange_weight_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfHighBgWeight(
        key = "higher_ISFrange_weight",
        defaultValue = 0.0,
        min = 0.0,
        max = 2.0,
        title = KeysStrings.pref_title_high_bg_weight,
        summary = KeysStrings.openapsama_higher_ISFrange_weight_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfSmbDeliveryRatioBgRange(
        key = "openapsama_smb_delivery_ratio_bg_range",
        defaultValue = 0.0,
        min = 0.0,
        max = 100.0,
        title = KeysStrings.pref_title_smb_delivery_ratio_bg_range,
        summary = KeysStrings.openapsama_smb_delivery_ratio_bg_range_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfPpWeight(
        key = "pp_ISF_weight",
        defaultValue = 0.0,
        min = 0.0,
        max = 0.15,
        title = KeysStrings.pref_title_pp_weight,
        summary = KeysStrings.openapsama_pp_ISF_weight_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_3,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfDuraWeight(
        key = "dura_ISF_weight",
        defaultValue = 0.0,
        min = 0.0,
        max = 3.0,
        title = KeysStrings.pref_title_dura_weight,
        summary = KeysStrings.openapsama_dura_ISF_weight_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfSmbDeliveryRatio(
        key = "openapsama_smb_delivery_ratio",
        defaultValue = 0.5,
        min = 0.1,
        max = 1.0,
        title = KeysStrings.pref_title_smb_delivery_ratio,
        summary = KeysStrings.openapsama_smb_delivery_ratio_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfSmbDeliveryRatioMin(
        key = "openapsama_smb_delivery_ratio_min",
        defaultValue = 0.5,
        min = 0.1,
        max = 1.0,
        title = KeysStrings.pref_title_smb_delivery_ratio_min,
        summary = KeysStrings.openapsama_smb_delivery_ratio_min_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfSmbDeliveryRatioMax(
        key = "openapsama_smb_delivery_ratio_max",
        defaultValue = 0.5,
        min = 0.5,
        max = 1.0,
        title = KeysStrings.pref_title_smb_delivery_ratio_max,
        summary = KeysStrings.openapsama_smb_delivery_ratio_max_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE_2,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),
    ApsAutoIsfSmbMaxRangeExtension(
        key = "openapsama_smb_max_range_extension",
        defaultValue = 1.0,
        min = 1.0,
        max = 5.0,
        title = KeysStrings.pref_title_smb_max_range_extension,
        summary = KeysStrings.openapsama_smb_max_range_extension_summary,
        defaultedBySM = true,
        unitType = UnitType.DOUBLE,
        sync = SyncSpec(SyncChannel.Cold, SyncDirection.Bidirectional)
    ),

    // AIMI and custom keys
    EquilMaxBolus("equil_maxbolus", 10.0, 0.1, 25.0, title = TextRef.Literal("Equil max bolus")),

    OApsAIMIMaxSMB("key_openapsaimi_max_smb", 1.0, 0.05, 15.0, title = TextRef.Literal("AIMI Max SMB")),
    OApsAIMIHighBGMaxSMB("key_openapsaimi_high_bg_max_smb", 1.0, 0.05, 15.0, title = TextRef.Literal("AIMI High BG Max SMB")),

    OApsAIMIweight("key_aimiweight", 50.0, 1.0, 200.0, title = TextRef.Literal("AIMI Weight")),
    OApsAIMIMpcInsulinUPerKgPerStep("aimi_mpc_insulin_u_per_kg_per_5min", 0.065, 0.03, 0.12, title = TextRef.Literal("AIMI MPC Insulin U per kg per 5min")),
    OApsAIMICHO("key_cho", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI CHO")),
    OApsAIMITDD7("key_tdd7", 40.0, 1.0, 150.0, title = TextRef.Literal("AIMI TDD7")),

    OApsAIMIPkpdInitialDiaH("aimi_pkpd_initial_dia_h", 20.0, 6.0, 24.0, title = TextRef.Literal("AIMI PKPD Initial DIA (h)")),
    OApsAIMIPkpdInitialPeakMin("aimi_pkpd_initial_peak_min", 40.0, 35.0, 300.0, title = TextRef.Literal("AIMI PKPD Initial Peak (min)")),
    OApsAIMIPkpdBoundsDiaMinH("aimi_pkpd_bounds_dia_min_h", 4.0, 4.0, 24.0, title = TextRef.Literal("AIMI PKPD Bounds DIA Min (h)")),
    OApsAIMIPkpdBoundsDiaMaxH("aimi_pkpd_bounds_dia_max_h", 24.0, 6.0, 36.0, title = TextRef.Literal("AIMI PKPD Bounds DIA Max (h)")),
    OApsAIMIPkpdBoundsPeakMinMin("aimi_pkpd_bounds_peak_min_min", 30.0, 20.0, 240.0, title = TextRef.Literal("AIMI PKPD Bounds Peak Min (min)")),
    OApsAIMIPkpdBoundsPeakMinMax("aimi_pkpd_bounds_peak_min_max", 240.0, 60.0, 480.0, title = TextRef.Literal("AIMI PKPD Bounds Peak Max (min)")),
    OApsAIMIPkpdMaxDiaChangePerDayH("aimi_pkpd_max_dia_change_per_day_h", 3.0, 0.1, 6.0, title = TextRef.Literal("AIMI PKPD Max DIA Change/Day (h)")),
    OApsAIMIPkpdMaxPeakChangePerDayMin("aimi_pkpd_max_peak_change_per_day_min", 20.0, 1.0, 60.0, title = TextRef.Literal("AIMI PKPD Max Peak Change/Day (min)")),
    OApsAIMIPkpdStateDiaH("aimi_pkpd_state_dia_h", 20.0, 6.0, 24.0, title = TextRef.Literal("AIMI PKPD State DIA (h)")),
    OApsAIMIPkpdStatePeakMin("aimi_pkpd_state_peak_min", 180.0, 40.0, 300.0, title = TextRef.Literal("AIMI PKPD State Peak (min)")),
    OApsAIMIIsfFusionMinFactor("aimi_isf_fusion_min_factor", 0.75, 0.3, 1.0, title = TextRef.Literal("AIMI ISF Fusion Min Factor")),
    OApsAIMIIsfFusionMaxFactor("aimi_isf_fusion_max_factor", 2.0, 1.0, 2.0, title = TextRef.Literal("AIMI ISF Fusion Max Factor")),
    OApsAIMIIsfFusionMaxChangePerTick("aimi_isf_fusion_max_change_per_tick", 0.4, 0.0, 0.5, title = TextRef.Literal("AIMI ISF Fusion Max Change/Tick")),
    OApsAIMISmbTailThreshold("aimi_smb_tail_threshold", 0.25, 0.0, 1.0, title = TextRef.Literal("AIMI SMB Tail Threshold")),
    OApsAIMISmbTailDamping("aimi_smb_tail_damping", 0.5, 0.0, 1.0, title = TextRef.Literal("AIMI SMB Tail Damping")),
    OApsAIMISmbExerciseDamping("aimi_smb_exercise_damping", 0.6, 0.0, 1.0, title = TextRef.Literal("AIMI SMB Exercise Damping")),
    OApsAIMISmbLateFatDamping("aimi_smb_late_fat_damping", 0.7, 0.0, 1.0, title = TextRef.Literal("AIMI SMB Late Fat Damping")),
    OApsAIMIPkpdPragmaticReliefMinFactor("aimi_pkpd_pragmatic_relief_min_factor", 0.75, 0.50, 1.0, title = TextRef.Literal("AIMI PKPD Pragmatic Relief Min Factor")),
    OApsAIMIRedCarpetRestoreThreshold("aimi_red_carpet_restore_threshold", 0.75, 0.50, 0.95, title = TextRef.Literal("AIMI Red Carpet Restore Threshold")),
    OApsAIMIPriorityMaxIobFactor("aimi_priority_max_iob_factor", 1.20, 1.0, 1.6, title = TextRef.Literal("AIMI Priority Max IOB Factor")),
    OApsAIMIPriorityMaxIobExtraU("aimi_priority_max_iob_extra_u", 2.0, 0.0, 5.0, title = TextRef.Literal("AIMI Priority Max IOB Extra U")),

    OApsAIMIMealFactor("key_oaps_aimi_meal_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI Meal Factor")),
    OApsAIMIFCLFactor("key_oaps_aimi_FCL_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI FCL Factor")),
    OApsAIMIBFFactor("key_oaps_aimi_BF_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI BF Factor")),

    OApsAIMIBFPrebolus("key_prebolus_BF_mode", 2.5, 0.1, 10.0, title = TextRef.Literal("AIMI BF Prebolus")),
    OApsAIMIBFPrebolus2("key_prebolus2_BF_mode", 2.0, 0.1, 10.0, title = TextRef.Literal("AIMI BF Prebolus 2")),

    OApsAIMILunchFactor("key_oaps_aimi_lunch_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI Lunch Factor")),
    OApsAIMIDinnerFactor("key_oaps_aimi_dinner_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI Dinner Factor")),
    OApsAIMIHCFactor("key_oaps_aimi_HC_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI HC Factor")),
    OApsAIMISnackFactor("key_oaps_aimi_snack_factor", 50.0, 1.0, 150.0, title = TextRef.Literal("AIMI Snack Factor")),

    OApsAIMIsleepFactor("key_oaps_aimi_sleep_factor", 60.0, 1.0, 150.0, title = TextRef.Literal("AIMI Sleep Factor")),

    OApsAIMIMealPrebolus("key_prebolus_meal_mode", 2.0, 0.1, 10.0, title = TextRef.Literal("AIMI Meal Prebolus")),
    OApsAIMIautodrivePrebolus("key_prebolus_autodrive_mode", 1.0, 0.1, 10.0, title = TextRef.Literal("AIMI Autodrive Prebolus")),
    OApsAIMIautodrivesmallPrebolus("key_prebolussmall_autodrive_mode", 0.1, 0.05, 2.0, title = TextRef.Literal("AIMI Autodrive Small Prebolus")),

    OApsAIMIcombinedDelta("key_combinedDelta_autodrive_mode", 1.0, 0.1, 20.0, title = TextRef.Literal("AIMI Combined Delta")),
    OApsAIMIAutodriveDeviation("key_mindeviation_autodrive_mode", 1.0, 0.1, 5.0, title = TextRef.Literal("AIMI Autodrive Deviation")),
    OApsAIMIAutodriveAcceleration("key_Acceleration_autodrive_mode", 1.0, 0.1, 5.0, title = TextRef.Literal("AIMI Autodrive Acceleration")),

    autodriveMaxBasal("autodrive_max_basal", 1.0, 0.05, 25.0, title = TextRef.Literal("Autodrive Max Basal")),
    meal_modes_MaxBasal("meal_modes_max_basal", 1.0, 0.05, 25.0, title = TextRef.Literal("Meal Modes Max Basal")),

    OApsAIMILunchPrebolus("key_prebolus_lunch_mode", 2.5, 0.1, 10.0, title = TextRef.Literal("AIMI Lunch Prebolus")),
    OApsAIMILunchPrebolus2("key_prebolus2_lunch_mode", 2.0, 0.1, 10.0, title = TextRef.Literal("AIMI Lunch Prebolus 2")),
    OApsAIMIDinnerPrebolus("key_prebolus_dinner_mode", 2.5, 0.1, 10.0, title = TextRef.Literal("AIMI Dinner Prebolus")),
    OApsAIMIDinnerPrebolus2("key_prebolus2_dinner_mode", 2.0, 0.1, 10.0, title = TextRef.Literal("AIMI Dinner Prebolus 2")),
    OApsAIMISnackPrebolus("key_prebolus_snack_mode", 1.0, 0.1, 10.0, title = TextRef.Literal("AIMI Snack Prebolus")),
    OApsAIMIHighCarbPrebolus("key_prebolus_highcarb_mode", 5.0, 0.1, 10.0, title = TextRef.Literal("AIMI High Carb Prebolus")),
    OApsAIMIHighCarbPrebolus2("key_prebolus_highcarb_mode2", 5.0, 0.1, 10.0, title = TextRef.Literal("AIMI High Carb Prebolus 2")),

    OApsAIMIwcycledateday("key_wcycledateday", 1.0, 1.0, 31.0, title = TextRef.Literal("AIMI WCycle Date Day")),
    OApsAIMIWCycleClampMin("key_wcycle_clamp_min", 0.8, 0.5, 1.0, title = TextRef.Literal("AIMI WCycle Clamp Min")),
    OApsAIMIWCycleClampMax("key_wcycle_clamp_max", 1.25, 1.0, 2.0, title = TextRef.Literal("AIMI WCycle Clamp Max")),

    OApsAIMINightGrowthMinRiseSlope("key_oaps_aimi_ngr_min_rise_slope", 5.0, 0.5, 30.0, title = TextRef.Literal("AIMI Night Growth Min Rise Slope")),
    OApsAIMINightGrowthSmbMultiplier("key_oaps_aimi_ngr_smb_multiplier", 1.2, 1.0, 1.5, title = TextRef.Literal("AIMI Night Growth SMB Multiplier")),
    OApsAIMINightGrowthBasalMultiplier("key_oaps_aimi_ngr_basal_multiplier", 1.1, 1.0, 1.5, title = TextRef.Literal("AIMI Night Growth Basal Multiplier")),
    OApsAIMINightGrowthMaxSmbClamp("key_oaps_aimi_ngr_max_smb_clamp", 1.2, 0.1, 5.0, title = TextRef.Literal("AIMI Night Growth Max SMB Clamp")),
    OApsAIMINightGrowthMaxIobExtra("key_oaps_aimi_ngr_max_iob_extra", 0.5, 0.0, 3.0, title = TextRef.Literal("AIMI Night Growth Max IOB Extra")),

    // --- AIMI Adaptive Basal ---
    OApsAIMIHighBg(key = "OApsAIMIHighBg", defaultValue = 180.0, min = 140.0, max = 250.0, title = TextRef.Literal("AIMI High BG")),
    OApsAIMIPlateauBandAbs(key = "OApsAIMIPlateauBandAbs", defaultValue = 2.5, min = 0.5, max = 6.0, title = TextRef.Literal("AIMI Plateau Band Abs")),
    OApsAIMIR2Confident(key = "OApsAIMIR2Confident", defaultValue = 0.7, min = 0.3, max = 0.95, title = TextRef.Literal("AIMI R2 Confident")),
    OApsAIMIMaxMultiplier(key = "OApsAIMIMaxMultiplier", defaultValue = 1.6, min = 1.0, max = 2.5, title = TextRef.Literal("AIMI Max Multiplier")),
    OApsAIMIKickerStep(key = "OApsAIMIKickerStep", defaultValue = 0.15, min = 0.05, max = 0.5, title = TextRef.Literal("AIMI Kicker Step")),
    OApsAIMIKickerMinUph(key = "OApsAIMIKickerMinUph", defaultValue = 0.2, min = 0.05, max = 1.0, title = TextRef.Literal("AIMI Kicker Min U/h")),
    OApsAIMIZeroResumeFrac(key = "OApsAIMIZeroResumeFrac", defaultValue = 0.25, min = 0.05, max = 0.8, title = TextRef.Literal("AIMI Zero Resume Frac")),
    OApsAIMIAntiStallBias(key = "OApsAIMIAntiStallBias", defaultValue = 0.10, min = 0.0, max = 0.5, title = TextRef.Literal("AIMI Anti Stall Bias")),
    OApsAIMIDeltaPosRelease(key = "OApsAIMIDeltaPosRelease", defaultValue = 1.0, min = 0.5, max = 3.0, title = TextRef.Literal("AIMI Delta Pos Release")),
    AimiUamConfidence(key = "AIMI_UAM_CONFIDENCE", defaultValue = 0.5, min = 0.0, max = 1.0, title = TextRef.Literal("AIMI UAM Confidence")),
    OApsAIMILastEstimatedCarbs(key = "OApsAIMILastEstimatedCarbs", defaultValue = 0.0, min = 0.0, max = 300.0, title = TextRef.Literal("AIMI Last Estimated Carbs")),

    OApsAIMILastEstimatedCarbTime(key = "OApsAIMILastEstimatedCarbTime", defaultValue = 0.0, min = 0.0, max = 20000000000000.0, title = TextRef.Literal("AIMI Last Estimated Carb Time")),

    // Endometriosis & Cycle Management (MTR)
    AimiEndometriosisBasalMult("aimi_endo_basal_mult", 1.3, 1.0, 2.0, title = TextRef.Literal("AIMI Endo Basal Mult")),
    AimiEndometriosisSmbDampen("aimi_endo_smb_dampen", 0.7, 0.0, 1.0, title = TextRef.Literal("AIMI Endo SMB Dampen")),

    // Adaptive Kernel Bank (Cosine Gate)
    AimiCosineGateAlpha("aimi_cosine_gate_alpha", 2.0, 0.1, 10.0, title = TextRef.Literal("AIMI Cosine Gate Alpha")),
    AimiCosineGateMinDataQuality("aimi_cosine_gate_min_dq", 0.3, 0.0, 1.0, title = TextRef.Literal("AIMI Cosine Gate Min DQ")),
    AimiCosineGateMinSensitivity("aimi_cosine_gate_min_sens", 0.7, 0.5, 1.0, title = TextRef.Literal("AIMI Cosine Gate Min Sens")),
    AimiCosineGateMaxSensitivity("aimi_cosine_gate_max_sens", 1.3, 1.0, 2.0, title = TextRef.Literal("AIMI Cosine Gate Max Sens")),

    // --- T3C Enhancements ---
    OApsAIMIT3cActivationThreshold("key_aimi_t3c_activation_threshold", 130.0, 100.0, 250.0, title = TextRef.Literal("AIMI T3c Activation Threshold")),
    OApsAIMIT3cAggressiveness("key_aimi_t3c_aggressiveness", 1.0, 0.5, 3.0, title = TextRef.Literal("AIMI T3c Aggressiveness")),
    OApsAIMIAdaptiveBasalMaxScaling("key_aimi_adaptive_basal_max_scaling", 1.0, 0.5, 2.0, title = TextRef.Literal("AIMI Adaptive Basal Max Scaling")),

    ;

}
