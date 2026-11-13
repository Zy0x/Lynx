package com.noir.lynx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TouchPrecisionModelsTest {
    @Test
    fun validatedDefaultsMatchApprovedCalibration() {
        val cfg = TouchPrecisionConfig.VALIDATED_DEFAULT
        assertTrue(cfg.applyOnBoot)
        assertEquals(4, cfg.nvtMicroGain)
        assertEquals(48, cfg.nvtEdgeGuardPx)
        assertEquals(15_000_000, cfg.nvtSpiSpeedHz)
        assertTrue(cfg.nvtGameMode)
        assertTrue(cfg.nvtHopFix)
        assertEquals(0, cfg.nvtJitterLevel)
        assertEquals(0, cfg.nvtSmoothLevel)
        assertEquals(280, cfg.ktchDurationMs)
        assertEquals(80, cfg.ktchEasBoost)
        assertEquals(TouchPrecisionConfig.OVERLAY_NARROW, cfg.backGestureOverlay)
        assertEquals(0.18f, cfg.backGestureScaleLeft, 0.0001f)
        assertEquals(0.18f, cfg.backGestureScaleRight, 0.0001f)
        assertEquals(0.96f, cfg.backGestureMlThreshold, 0.0001f)
        assertFalse(cfg.inputResampling)
        assertTrue(cfg.touchIrqBigCores)
    }

    @Test
    fun expandedGainMultiplierAndDriftScaleProperly() {
        assertEquals("1.0x", TouchPrecisionConfig.gainMultiplierText(0))
        assertEquals(0, TouchPrecisionConfig.maxDriftPx(0))
        assertEquals("3.0x", TouchPrecisionConfig.gainMultiplierText(4))
        assertEquals(36, TouchPrecisionConfig.maxDriftPx(4))
        assertEquals("5.0x", TouchPrecisionConfig.gainMultiplierText(8))
        assertEquals(80, TouchPrecisionConfig.maxDriftPx(8))
        assertEquals("7.0x", TouchPrecisionConfig.gainMultiplierText(12))
        assertEquals(120, TouchPrecisionConfig.maxDriftPx(12))
    }

    @Test
    fun edgeRoundingClampsBoundsCleanly() {
        assertEquals(0.0f, LynxTouchPrecisionRepository.roundScale2(-1.0f), 0.0001f)
        assertEquals(2.0f, LynxTouchPrecisionRepository.roundScale2(3.5f), 0.0001f)
        assertEquals(0.18f, LynxTouchPrecisionRepository.roundScale2(0.179f), 0.0001f)
        assertEquals(0.50f, LynxTouchPrecisionRepository.roundThreshold2(0.10f), 0.0001f)
        assertEquals(0.99f, LynxTouchPrecisionRepository.roundThreshold2(1.20f), 0.0001f)
    }
}
