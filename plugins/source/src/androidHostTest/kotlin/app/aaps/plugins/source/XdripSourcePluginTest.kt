package app.aaps.plugins.source

import android.os.Bundle
import app.aaps.core.data.model.GV
import app.aaps.core.data.model.TrendArrow
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.receivers.Intents
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.BundleMock
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class XdripSourcePluginTest : TestBaseWithProfile() {

    private val persistenceLayer: PersistenceLayer = mock()
    private val oApsxdriponeminuteFlow = MutableStateFlow(true)
    private lateinit var xdripSourcePlugin: XdripSourcePlugin

    @BeforeEach
    fun setup() {
        XdripSourcePlugin.XdripSourceWorker.lastDataTimestamp = 0L
        oApsxdriponeminuteFlow.value = true
        whenever(preferences.observe(BooleanKey.OApsxdriponeminute)).thenReturn(oApsxdriponeminuteFlow)
        whenever(preferences.get(BooleanKey.OApsxdriponeminute)).thenAnswer { oApsxdriponeminuteFlow.value }
        xdripSourcePlugin = XdripSourcePlugin(rh, aapsLogger, preferences, config, notificationManager, persistenceLayer, context, dateUtil)
    }

    @Test
    fun `plugin is created`() {
        assertThat(xdripSourcePlugin).isNotNull()
    }

    @Test
    fun `preference screen content is provided`() {
        // exercises AbstractBgSourceWithSensorInsertLogPlugin.getPreferenceScreenContent
        assertThat(xdripSourcePlugin.getPreferenceScreenContent()).isInstanceOf(PreferenceSubScreenDef::class.java)
    }

    @Test
    fun `isValidPolledReading accepts valid fresh reading`() {
        val now = 1_000_000L
        val ts = now - 60_000L // 1 minute ago
        assertThat(xdripSourcePlugin.isValidPolledReading(ts, 115.0, now, 0L, 0L)).isTrue()
    }

    @Test
    fun `isValidPolledReading rejects duplicates or older readings`() {
        val now = 1_000_000L
        val ts = now - 60_000L
        // duplicate with lastPolledTs
        assertThat(xdripSourcePlugin.isValidPolledReading(ts, 115.0, now, ts, 0L)).isFalse()
        // older than lastPolledTs
        assertThat(xdripSourcePlugin.isValidPolledReading(ts - 1000L, 115.0, now, ts, 0L)).isFalse()
        // duplicate with lastWorkerTs
        assertThat(xdripSourcePlugin.isValidPolledReading(ts, 115.0, now, 0L, ts)).isFalse()
        // older than lastWorkerTs
        assertThat(xdripSourcePlugin.isValidPolledReading(ts - 1000L, 115.0, now, 0L, ts)).isFalse()
    }

    @Test
    fun `isValidPolledReading rejects stale readings older than 10 minutes`() {
        val now = 1_000_000L
        val ts = now - 600_001L // > 10 min
        assertThat(xdripSourcePlugin.isValidPolledReading(ts, 115.0, now, 0L, 0L)).isFalse()
    }

    @Test
    fun `isValidPolledReading rejects future readings over 60 seconds ahead`() {
        val now = 1_000_000L
        val ts = now + 60_001L // > 60s ahead
        assertThat(xdripSourcePlugin.isValidPolledReading(ts, 115.0, now, 0L, 0L)).isFalse()
    }

    @Test
    fun `isValidPolledReading accepts future readings within 60 seconds clock drift tolerance`() {
        val now = 1_000_000L
        val ts = now + 45_000L // +45s ahead
        assertThat(xdripSourcePlugin.isValidPolledReading(ts, 115.0, now, 0L, 0L)).isTrue()
    }

    @Test
    fun `isValidPolledReading rejects non-positive timestamp and non-positive sgv`() {
        val now = 1_000_000L
        assertThat(xdripSourcePlugin.isValidPolledReading(0L, 115.0, now, 0L, 0L)).isFalse()
        assertThat(xdripSourcePlugin.isValidPolledReading(-10L, 115.0, now, 0L, 0L)).isFalse()
        assertThat(xdripSourcePlugin.isValidPolledReading(now, 0.0, now, 0L, 0L)).isFalse()
        assertThat(xdripSourcePlugin.isValidPolledReading(now, -5.0, now, 0L, 0L)).isFalse()
    }

    @Test
    fun `reactive polling lifecycle transitions properly on preference changes and onStop`() = kotlinx.coroutines.runBlocking {
        val flow = MutableStateFlow(false)
        whenever(preferences.observe(BooleanKey.OApsxdriponeminute)).thenReturn(flow)
        whenever(preferences.get(BooleanKey.OApsxdriponeminute)).thenAnswer { flow.value }

        fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
            val deadline = System.currentTimeMillis() + timeoutMs
            while (!condition()) {
                if (System.currentTimeMillis() > deadline) error("Condition not met within ${timeoutMs}ms")
                Thread.sleep(20)
            }
        }

        // Initial state before onStart
        assertThat(xdripSourcePlugin.isPollingActive).isFalse()
        assertThat(xdripSourcePlugin.currentPrefObserverJob).isNull()

        // Enable plugin so isEnabled() returns true and onStart is invoked
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)

        waitUntil { xdripSourcePlugin.currentPrefObserverJob?.isActive == true }
        assertThat(xdripSourcePlugin.isPollingActive).isFalse()

        // Toggle preference to true -> polling job becomes active
        flow.value = true
        waitUntil { xdripSourcePlugin.isPollingActive }
        val firstJob = xdripSourcePlugin.currentPollingJob

        // Duplicate true emission -> must keep the exact same active job without recreating/duplicating
        flow.value = true
        Thread.sleep(100)
        assertThat(xdripSourcePlugin.isPollingActive).isTrue()
        assertThat(xdripSourcePlugin.currentPollingJob).isSameInstanceAs(firstJob)

        // Toggle preference to false -> polling job is cancelled
        flow.value = false
        waitUntil { xdripSourcePlugin.currentPollingJob == null }
        assertThat(xdripSourcePlugin.isPollingActive).isFalse()
        assertThat(xdripSourcePlugin.currentPollingJob).isNull()

        // Toggle back to true -> new polling job is started
        flow.value = true
        waitUntil { xdripSourcePlugin.isPollingActive }

        // onStop (via disable) -> both polling job and pref observer job are cancelled
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, false)
        assertThat(xdripSourcePlugin.isPollingActive).isFalse()
        assertThat(xdripSourcePlugin.currentPollingJob).isNull()
        assertThat(xdripSourcePlugin.currentPrefObserverJob).isNull()
    }

    @Test
    fun `handleDirectBroadcast ignores when plugin is disabled`() = runBlocking {
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, false)
        val bundle = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, 1_000_000L)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 120.0)
        }

        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle)

        verify(persistenceLayer, never()).insertCgmSourceData(any(), any(), any(), any())
    }

    @Test
    fun `handleDirectBroadcast inserts reading for ACTION_NEW_BG_ESTIMATE`() = runBlocking {
        oApsxdriponeminuteFlow.value = true
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val timestamp = 1_000_000L
        val bundle = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, timestamp)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 135.0)
            putDouble(Intents.EXTRA_RAW, 134.0)
            putString(Intents.EXTRA_BG_SLOPE_NAME, "Flat")
            putString(Intents.XDRIP_DATA_SOURCE, "G6 Native")
            putInt(Intents.EXTRA_SENSOR_BATTERY, 85)
        }

        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle)

        verify(persistenceLayer).insertCgmSourceData(
            eq(Sources.Xdrip),
            check { gvs ->
                assertThat(gvs).hasSize(1)
                val gv = gvs.first()
                assertThat(gv.timestamp).isEqualTo(timestamp)
                assertThat(gv.value).isEqualTo(135.0)
                assertThat(gv.raw).isEqualTo(134.0)
                assertThat(gv.trendArrow).isEqualTo(TrendArrow.FLAT)
            },
            eq(emptyList()),
            eq(null)
        )
        assertThat(xdripSourcePlugin.sensorBatteryLevel).isEqualTo(85)
        assertThat(XdripSourcePlugin.XdripSourceWorker.lastDataTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun `handleDirectBroadcast inserts reading for GLUCODATA_MINUTE with mgdl`() = runBlocking {
        oApsxdriponeminuteFlow.value = true
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val timestamp = 1_000_000L
        val bundle = BundleMock.mocked().apply {
            putLong("glucodata.Minute.Time", timestamp)
            putInt("glucodata.Minute.mgdl", 110)
            putFloat("glucodata.Minute.Rate", 1.5f)
            putString("glucodata.Minute.SerialNumber", "FreeStyleLibre")
        }

        xdripSourcePlugin.handleDirectBroadcast(Intents.GLUCODATA_MINUTE, bundle)

        verify(persistenceLayer).insertCgmSourceData(
            eq(Sources.Xdrip),
            check { gvs ->
                assertThat(gvs).hasSize(1)
                val gv = gvs.first()
                assertThat(gv.timestamp).isEqualTo(timestamp)
                assertThat(gv.value).isEqualTo(110.0)
                assertThat(gv.trendArrow).isEqualTo(TrendArrow.FORTY_FIVE_UP)
            },
            eq(emptyList()),
            eq(null)
        )
        assertThat(XdripSourcePlugin.XdripSourceWorker.lastDataTimestamp).isEqualTo(timestamp)
    }

    @Test
    fun `handleDirectBroadcast inserts reading for GLUCODATA_MINUTE with mmol glucose converted to mgdl`() = runBlocking {
        oApsxdriponeminuteFlow.value = true
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val timestamp = 1_000_000L
        val bundle = BundleMock.mocked().apply {
            putLong("glucodata.Minute.Time", timestamp)
            putFloat("glucodata.Minute.glucose", 6.0f) // 6.0 mmol/l -> 108 mg/dl
            putFloat("glucodata.Minute.Rate", -2.5f)
        }

        xdripSourcePlugin.handleDirectBroadcast(Intents.GLUCODATA_MINUTE, bundle)

        verify(persistenceLayer).insertCgmSourceData(
            eq(Sources.Xdrip),
            check { gvs ->
                assertThat(gvs).hasSize(1)
                val gv = gvs.first()
                assertThat(gv.timestamp).isEqualTo(timestamp)
                assertThat(gv.value).isEqualTo(108.0)
                assertThat(gv.trendArrow).isEqualTo(TrendArrow.SINGLE_DOWN)
            },
            eq(emptyList()),
            eq(null)
        )
    }

    @Test
    fun `handleDirectBroadcast skips duplicate readings`() = runBlocking {
        oApsxdriponeminuteFlow.value = true
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val timestamp = 1_000_000L
        val bundle = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, timestamp)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 120.0)
        }

        // First broadcast succeeds
        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle)
        verify(persistenceLayer).insertCgmSourceData(any(), any(), any(), any())

        // Duplicate broadcast with same timestamp is skipped
        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle)
        // verify called only once in total
        verify(persistenceLayer).insertCgmSourceData(any(), any(), any(), any())
    }

    @Test
    fun `handleDirectBroadcast throttles when OApsxdriponeminute is false and interval under 4 minutes`() = runBlocking {
        oApsxdriponeminuteFlow.value = false
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val firstTs = 1_000_000L
        val bundle1 = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, firstTs)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 120.0)
        }
        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle1)
        verify(persistenceLayer).insertCgmSourceData(any(), any(), any(), any())

        // 60 seconds later, but OApsxdriponeminute is false
        val secondTs = firstTs + 60_000L
        val bundle2 = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, secondTs)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 122.0)
        }
        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle2)
        // second broadcast should have been ignored
        verify(persistenceLayer).insertCgmSourceData(any(), any(), any(), any())
    }

    @Test
    fun `handleDirectBroadcast processes 1-minute updates when OApsxdriponeminute is true`() = runBlocking {
        oApsxdriponeminuteFlow.value = true
        xdripSourcePlugin.setPluginEnabledBlocking(PluginType.BGSOURCE, true)
        val firstTs = 1_000_000L
        val bundle1 = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, firstTs)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 120.0)
        }
        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle1)

        val secondTs = firstTs + 60_000L
        val bundle2 = BundleMock.mocked().apply {
            putLong(Intents.EXTRA_TIMESTAMP, secondTs)
            putDouble(Intents.EXTRA_BG_ESTIMATE, 122.0)
        }
        xdripSourcePlugin.handleDirectBroadcast(Intents.ACTION_NEW_BG_ESTIMATE, bundle2)

        // Both calls should insert
        org.mockito.kotlin.verify(persistenceLayer, org.mockito.kotlin.times(2)).insertCgmSourceData(any(), any(), any(), any())
    }
}

