package com.noir.lynx.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MtkPqModelsTest {

    @Test
    fun stockBaselineMatchesX698Defaults() {
        val cfg = MtkPqConfig.STOCK_BALANCED
        assertFalse(cfg.aalBypass)
        assertEquals(2, cfg.dreMode)
        assertTrue(cfg.vpDreEnabled)
        assertEquals(2, cfg.shpIndex)
        assertTrue(cfg.isoShpEnabled)
        assertTrue(cfg.ultraResEnabled)
        assertEquals(0, cfg.ultraResStrength)
        assertTrue(cfg.debugShpEnabled)
        assertTrue(cfg.debugDshpEnabled)
        assertTrue(cfg.applyOnBoot)
    }

    @Test
    fun sanitizedClampsOutOfRangeValues() {
        val dirty = MtkPqConfig(dreMode = 9, shpIndex = 42, ultraResStrength = -3)
        val clean = dirty.sanitized()
        assertEquals(MtkPqConfig.MAX_DRE_MODE, clean.dreMode)
        assertEquals(MtkPqConfig.MAX_SHP_INDEX, clean.shpIndex)
        assertEquals(MtkPqConfig.MIN_ULTRARES_STRENGTH, clean.ultraResStrength)
    }

    @Test
    fun matchesOpticalPresetIgnoresBootFlag() {
        val withoutBoot = MtkPqConfig.PURE_NATURAL.copy(applyOnBoot = false)
        assertTrue(withoutBoot.matchesOpticalPreset(MtkPqConfig.PURE_NATURAL))
        val modified = withoutBoot.copy(aalBypass = false)
        assertFalse(modified.matchesOpticalPreset(MtkPqConfig.PURE_NATURAL))
    }

    @Test
    fun parseProbeLinesDetectsMtkHardwareAndSnapdragonEmptyFallback() {
        val mtk = LynxMtkPqRepository.parseProbeLines(
            listOf(
                "PQ_SUP=2",
                "AAL_SUP=1",
                "COL_MODE=1",
                "SVC=pq-2-2",
                "AAL_BYP=1",
                "DRE_EN=1",
                "VP_DRE=1",
                "SHP_IDX=4",
                "ISO_SHP=1",
                "UR_EN=1",
                "UR_STR=2",
                "DBG_SHP=1",
                "DBG_DSHP=0",
            ),
        )
        assertTrue(mtk.supported)
        assertEquals("pq-2-2", mtk.pqServiceName)
        assertTrue(mtk.activeAalBypass)
        assertEquals(1, mtk.activeDreMode)
        assertEquals(4, mtk.activeShpIndex)
        assertEquals(2, mtk.activeUltraResStrength)
        assertFalse(mtk.activeDebugDshpEnabled)

        val qcom = LynxMtkPqRepository.parseProbeLines(
            listOf("PQ_SUP=", "AAL_SUP=", "SVC="),
        )
        assertFalse(qcom.supported)
    }

    @Test
    fun buildApplyScriptWritesPersistPropsAndReloadsPqService() {
        val script = LynxMtkPqRepository.buildApplyScript(
            config = MtkPqConfig.SHARP_GAMING,
            detectedService = "pq-2-2",
            restartPqService = true,
        )
        assertTrue(script.contains("persist.vendor.sys.pq.disp.aal.bypass 1"))
        assertTrue(script.contains("persist.vendor.sys.pq.mdp.dre.en 2"))
        assertTrue(script.contains("persist.vendor.sys.pq.shp.idx 4"))
        assertTrue(script.contains("persist.vendor.sys.pq.ultrares.strength 2"))
        assertTrue(script.contains("stop \"\$SVC\""))
    }
}
