package app.aaps.pump.apex.utils.keys

import app.aaps.core.keys.interfaces.BooleanPreferenceKey
import app.aaps.core.keys.interfaces.StringPreferenceKey
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.pump.apex.connectivity.FirmwareVersion
import app.aaps.pump.apex.connectivity.commands.pump.AlarmLength
import app.aaps.pump.apex.misc.BatteryType

enum class ApexStringKey(
    override val key: String,
    override val defaultValue: String,
    override val defaultedBySM: Boolean = false,
    override val showInApsMode: Boolean = true,
    override val showInNsClientMode: Boolean = true,
    override val showInPumpControlMode: Boolean = true,
    override val dependency: BooleanPreferenceKey? = null,
    override val negativeDependency: BooleanPreferenceKey? = null,
    override val hideParentScreenIfHidden: Boolean = false,
    override val isPassword: Boolean = false,
    override val isPin: Boolean = false,
    override val exportable: Boolean = true,
    override val title: TextRef = TextRef.Literal(""),
    override val summary: TextRef? = null
) : StringPreferenceKey {
    SerialNumber("apex_serial_number", "", title = TextRef.AndroidRes(app.aaps.pump.apex.R.string.setting_serial_number)),
    LastConnectedSerialNumber("apex_last_connected_serial_number", ""),
    BluetoothAddress("apex_bt_address", ""),
    AlarmSoundLength("apex_alarm_length", AlarmLength.Short.name, title = TextRef.AndroidRes(app.aaps.pump.apex.R.string.setting_alarm_length)),
    CalcBatteryType("apex_battery_type", BatteryType.Custom.name, title = TextRef.AndroidRes(app.aaps.pump.apex.R.string.setting_calc_battery_type_title)),
    FirmwareVer("apex_fw_ver", FirmwareVersion.AUTO.name, title = TextRef.AndroidRes(app.aaps.pump.apex.R.string.firmware_version));
}
