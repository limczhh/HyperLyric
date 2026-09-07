package com.lidesheng.hyperlyric.plugin.amll.ttml

import com.lidesheng.hyperlyric.plugin.api.PluginConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** AmllTtmlConfig 设置读取测试 */
class AmllTtmlConfigTest {

    private class FakePluginConfig(
        private val booleans: Map<String, Boolean> = emptyMap(),
        private val strings: Map<String, String> = emptyMap()
    ) : PluginConfig {
        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            booleans[key] ?: defaultValue

        override fun getString(key: String, defaultValue: String?): String? =
            strings[key] ?: defaultValue

        override fun getLong(key: String, defaultValue: Long): Long = defaultValue
        override fun getFloat(key: String, defaultValue: Float): Float = defaultValue
        override fun getStringSet(key: String, defaultValue: Set<String>): Set<String> = defaultValue
    }

    @Test
    fun duetPerformanceDefaultsToTrueWhenKeyMissing() {
        val config = AmllTtmlConfig.from(FakePluginConfig())
        assertTrue(config.duetPerformance)
    }

    @Test
    fun duetPerformanceExplicitFalseIsRespected() {
        val config = AmllTtmlConfig.from(FakePluginConfig(booleans = mapOf("duet_performance" to false)))
        assertFalse(config.duetPerformance)
    }

    @Test
    fun duetPerformanceExplicitTrueIsRespected() {
        val config = AmllTtmlConfig.from(FakePluginConfig(booleans = mapOf("duet_performance" to true)))
        assertTrue(config.duetPerformance)
    }
}
