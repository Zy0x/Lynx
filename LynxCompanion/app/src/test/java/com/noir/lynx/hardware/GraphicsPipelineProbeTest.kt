package com.noir.lynx.hardware

import com.noir.lynx.data.LynxGraphicsEngineRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphicsPipelineProbeTest {

    @Test
    fun parseProbeOutputDistinguishesMinimalAndFullRoms() {
        val minimal = GraphicsPipelineProbe.parseProbeOutput(
            lines = listOf("CAP_VULKAN=1", "CAP_RESETPROP=1"),
            sdkInt = 31
        )
        assertTrue(minimal.isVulkanSupported)
        assertFalse(minimal.isGraphiteSupported)
        assertFalse(minimal.isAngleSupported)
        assertFalse(minimal.isTextureCacheSupported)
        assertFalse(minimal.isSfTouchBoostDurationSupported)
        assertFalse(minimal.isInputResamplingSupported)
        assertTrue(minimal.hasResetprop)

        val android15Minimal = GraphicsPipelineProbe.parseProbeOutput(
            lines = listOf("CAP_VULKAN=1"),
            sdkInt = 35
        )
        assertTrue(android15Minimal.isGraphiteSupported)

        val full = GraphicsPipelineProbe.parseProbeOutput(
            lines = listOf(
                "CAP_VULKAN=1",
                "CAP_GRAPHITE=1",
                "CAP_ANGLE=1",
                "CAP_TEX_CACHE=1",
                "CAP_ASYNC_SHADER=1",
                "CAP_CPUREND_VSYNC=1",
                "CAP_EGL_SWAP=1",
                "CAP_SF_LATCH=1",
                "CAP_SF_BP=1",
                "CAP_SF_EARLY_PHASE=1",
                "CAP_SF_TOUCH_DUR=1",
                "CAP_INPUT_RESAMPLE=1",
                "CAP_RESETPROP=1"
            ),
            sdkInt = 34
        )
        assertTrue(full.isGraphiteSupported)
        assertTrue(full.isAngleSupported)
        assertTrue(full.isTextureCacheSupported)
        assertTrue(full.isAsyncShaderSupported)
        assertTrue(full.isPureHwVsyncSupported)
        assertTrue(full.isEglSwapIntervalSupported)
        assertTrue(full.isSfLatchSupported)
        assertTrue(full.isSfDisableBackpressureSupported)
        assertTrue(full.isSfEarlyPhaseSupported)
        assertTrue(full.isSfTouchBoostDurationSupported)
        assertTrue(full.isInputResamplingSupported)
    }

    @Test
    fun buildSetPropCommandRefusesSetpropFallbackForReadOnlyKeys() {
        val roCmd = GraphicsPipelineProbe.buildSetPropCommand("ro.hwui.texture_cache_size", "72")
        assertTrue(roCmd.contains("resetprop"))
        assertFalse(roCmd.contains("setprop \"ro.hwui.texture_cache_size\""))

        val debugCmd = GraphicsPipelineProbe.buildSetPropCommand("debug.cpurend.vsync", "false")
        assertTrue(debugCmd.contains("setprop \"debug.cpurend.vsync\" \"false\""))
    }

    @Test
    fun angleSelectionParsingAndFormattingHandlesPairedLists() {
        val parsed = LynxGraphicsEngineRepository.parseAngleSelectionMap(
            pkgsRaw = "com.game.one,com.app.two,com.game.three",
            valsRaw = "angle,native,default"
        )
        assertEquals(mapOf("com.game.one" to "angle", "com.app.two" to "native"), parsed)

        val mismatched = LynxGraphicsEngineRepository.parseAngleSelectionMap(
            pkgsRaw = "com.game.one,com.game.two",
            valsRaw = "angle"
        )
        assertTrue(mismatched.isEmpty())

        val (pkgsOut, valsOut) = LynxGraphicsEngineRepository.formatAngleSelectionPairs(
            linkedMapOf("com.app.two" to "native", "com.game.one" to "angle", "com.skip" to "default")
        )
        assertEquals("com.app.two,com.game.one", pkgsOut)
        assertEquals("native,angle", valsOut)
    }
}
