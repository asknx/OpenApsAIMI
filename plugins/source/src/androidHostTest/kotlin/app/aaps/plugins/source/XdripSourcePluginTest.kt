package app.aaps.plugins.source

import app.aaps.core.data.plugin.PluginType
import app.aaps.core.keys.BooleanKey
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class XdripSourcePluginTest : TestBaseWithProfile() {

    private lateinit var xdripSourcePlugin: XdripSourcePlugin

    @BeforeEach
    fun setup() {
        xdripSourcePlugin = XdripSourcePlugin(rh, aapsLogger, preferences, config, mock(), mock())
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
        waitUntil { !xdripSourcePlugin.isPollingActive }
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
}
