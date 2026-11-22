package com.noir.lynx.feature.network.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LynxSpeedtestEngineTest {

    @Test
    fun sanitizeCustomUrlAddsHttpsWhenMissingAndTrimsTrailingSlash() {
        assertEquals("https://speed.example.id", LynxSpeedtestEngine.sanitizeCustomUrl("  speed.example.id/  "))
        assertEquals("http://192.168.1.10:8080", LynxSpeedtestEngine.sanitizeCustomUrl("http://192.168.1.10:8080///"))
        assertEquals("", LynxSpeedtestEngine.sanitizeCustomUrl("   "))
    }

    @Test
    fun parseCfRayExtractsIataCodeCleanly() {
        assertEquals("Cloudflare (CGK)", LynxSpeedtestEngine.parseCfRayEdge("8f9a12b34c56d78e-CGK"))
        assertEquals("Cloudflare (SIN)", LynxSpeedtestEngine.parseCfRayEdge("abc123-sin"))
        assertEquals("Cloudflare Edge", LynxSpeedtestEngine.parseCfRayEdge("opaque-99"))
        assertEquals("", LynxSpeedtestEngine.parseCfRayEdge(null))
    }

    @Test
    fun computeBufferbloatGradeClassifiesLoadedLatencyDeltaAccurately() {
        val (gradeAPlus, msgAPlus) = LynxSpeedtestEngine.computeBufferbloatGrade(18f, 25f, 30f)
        assertEquals("A+", gradeAPlus)
        assertTrue(msgAPlus.contains("+12 ms"))

        assertEquals("A", LynxSpeedtestEngine.computeBufferbloatGrade(18f, 45f, 40f).first)
        assertEquals("B", LynxSpeedtestEngine.computeBufferbloatGrade(20f, 80f, 65f).first)
        assertEquals("C", LynxSpeedtestEngine.computeBufferbloatGrade(20f, 120f, 140f).first)
        assertEquals("D", LynxSpeedtestEngine.computeBufferbloatGrade(20f, 190f, 95f).first)
        assertEquals("", LynxSpeedtestEngine.computeBufferbloatGrade(0f, 40f, 40f).first)
    }

    @Test
    fun computeJitterReturnsMeanConsecutiveDifference() {
        assertEquals(0f, LynxSpeedtestEngine.computeJitter(emptyList()), 0.001f)
        assertEquals(0f, LynxSpeedtestEngine.computeJitter(listOf(12f)), 0.001f)
        // |15 - 10| = 5, |11 - 15| = 4 -> mean = 4.5 ms
        assertEquals(4.5f, LynxSpeedtestEngine.computeJitter(listOf(10f, 15f, 11f)), 0.001f)
    }
}
