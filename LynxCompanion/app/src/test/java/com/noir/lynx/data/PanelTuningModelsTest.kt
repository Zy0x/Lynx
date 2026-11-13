package com.noir.lynx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelTuningModelsTest {

    @Test
    fun stockBaselineMatchesVerifiedAriseR1025Defaults() {
        val cfg = PanelTuningConfig.STOCK_BASELINE
        assertEquals(0, cfg.cabcMode)
        assertEquals(15_000, cfg.esdPeriodMs)
        assertTrue(cfg.applyOnBoot)
    }

    @Test
    fun sanitizedClampsCabcAndEsdSafelyWhilePreservingPause() {
        val dirtyHigh = PanelTuningConfig(cabcMode = 9, esdPeriodMs = 120_000)
        val cleanHigh = dirtyHigh.sanitized()
        assertEquals(PanelTuningConfig.MAX_CABC_MODE, cleanHigh.cabcMode)
        assertEquals(PanelTuningConfig.MAX_ESD_PERIOD_MS, cleanHigh.esdPeriodMs)

        val tooShort = PanelTuningConfig(cabcMode = -2, esdPeriodMs = 450).sanitized()
        assertEquals(PanelTuningConfig.MIN_CABC_MODE, tooShort.cabcMode)
        assertEquals(PanelTuningConfig.MIN_ACTIVE_ESD_PERIOD_MS, tooShort.esdPeriodMs)

        val paused = PanelTuningConfig(cabcMode = 2, esdPeriodMs = 0).sanitized()
        assertEquals(2, paused.cabcMode)
        assertEquals(PanelTuningConfig.ESD_PAUSED_MS, paused.esdPeriodMs)

        val negPaused = PanelTuningConfig(esdPeriodMs = -100).sanitized()
        assertEquals(PanelTuningConfig.ESD_PAUSED_MS, negPaused.esdPeriodMs)
    }

    @Test
    fun parseProbeLinesExtractsKernelCapabilitiesAndActiveValues() {
        val full = LynxPanelTuningRepository.parseProbeLines(
            listOf(
                "CABC_SUP=1",
                "CABC_VAL=2",
                "ESD_SUP=1",
                "ESD_VAL=30000",
                "WRITE_OK=1",
            ),
        )
        assertTrue(full.probed)
        assertTrue(full.cabcSupported)
        assertTrue(full.esdSupported)
        assertTrue(full.hasAnySupport)
        assertEquals(2, full.activeCabcMode)
        assertEquals(30_000, full.activeEsdPeriodMs)
        assertTrue(full.lastWriteOk)

        val unsupported = LynxPanelTuningRepository.parseProbeLines(
            listOf(
                "CABC_SUP=0",
                "CABC_VAL=-1",
                "ESD_SUP=0",
                "ESD_VAL=-1",
                "WRITE_OK=1",
            ),
        )
        assertFalse(unsupported.hasAnySupport)
    }

    @Test
    fun buildApplyScriptWritesVerifiedNodesAndVerifiesWriteStatus() {
        val script = LynxPanelTuningRepository.buildApplyScript(
            PanelTuningConfig(cabcMode = 3, esdPeriodMs = 0, applyOnBoot = true),
        )
        assertTrue(script.contains(LynxPanelTuningRepository.CABC_NODE))
        assertTrue(script.contains(LynxPanelTuningRepository.ESD_PERIOD_NODE))
        assertTrue(script.contains("echo \"3\""))
        assertTrue(script.contains("echo \"0\""))
        assertTrue(script.contains("WRITE_OK="))
    }
}
