package com.noir.lynx.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorMatrixEngineTest {

    private fun multiplyVec4ColumnMajor(m: FloatArray, v: FloatArray): FloatArray {
        return FloatArray(4) { row ->
            m[row] * v[0] + m[4 + row] * v[1] + m[8 + row] * v[2] + m[12 + row] * v[3]
        }
    }

    @Test
    fun kelvin6500ReturnsNeutralUnitGain() {
        val (r, g, b) = ColorMatrixEngine.kelvinToRgbGain(6500)
        assertEquals(1.0f, r, 0.001f)
        assertEquals(1.0f, g, 0.001f)
        assertEquals(1.0f, b, 0.001f)
    }

    @Test
    fun buildMatrixAccurateIsIdentity() {
        val matrix = ColorMatrixEngine.buildMatrix(ColorMatrixProfile.ACCURATE)
        assertTrue(ColorMatrixEngine.isIdentityMatrix(matrix))
    }

    @Test
    fun buildMatrixUsesColumnMajorOrderAndPreservesWhiteBalance() {
        val profile = ColorMatrixProfile(
            name = "Test",
            red = 1.00f,
            green = 0.98f,
            blue = 0.93f,
            temperatureK = 6500,
            saturation = 1.30f,
            contrast = 1.00f,
        )
        val m = ColorMatrixEngine.buildMatrix(profile)

        // Column 0 corresponds to Red input weights: [m00, m10, m20, 0]
        assertEquals((1f - 1.30f) * 0.2126f + 1.30f, m[0], 0.0005f)
        assertEquals((1f - 1.30f) * 0.2126f * 0.98f, m[1], 0.0005f)
        assertEquals((1f - 1.30f) * 0.2126f * 0.93f, m[2], 0.0005f)
        assertEquals(0.0f, m[3], 0.0001f)

        // Neutral white input must retain exact calibrated white point regardless of saturation
        val whiteOut = multiplyVec4ColumnMajor(m, floatArrayOf(1f, 1f, 1f, 1f))
        assertEquals(1.00f, whiteOut[0], 0.001f)
        assertEquals(0.98f, whiteOut[1], 0.001f)
        assertEquals(0.93f, whiteOut[2], 0.001f)
        assertEquals(1.00f, whiteOut[3], 0.0001f)
    }

    @Test
    fun buildMatrixContrastPivotsAroundMidGrayWithOffsetColumn() {
        val m = ColorMatrixEngine.buildMatrix(ColorMatrixProfile.IPS_D65_NATURAL)
        assertFalse(ColorMatrixEngine.isIdentityMatrix(m))
        // Mid-gray (0.5, 0.5, 0.5) remains invariant in relative channel proportions at contrast 1.02
        val midOut = multiplyVec4ColumnMajor(m, floatArrayOf(0.5f, 0.5f, 0.5f, 1f))
        assertEquals(0.5f * 1.00f, midOut[0], 0.001f)
        assertEquals(0.5f * 0.98f, midOut[1], 0.001f)
        assertEquals(0.5f * 0.93f, midOut[2], 0.001f)
    }

    @Test
    fun ipsPresetsMatchExpectedWhitePointAndSaturation() {
        val d65 = ColorMatrixProfile.IPS_D65_NATURAL
        assertEquals(6500, d65.temperatureK)
        assertEquals(1.00f, d65.saturation, 0.001f)
        assertEquals(1.02f, d65.contrast, 0.001f)
        assertTrue(d65.blue < d65.green && d65.green < d65.red)

        val vivid = ColorMatrixProfile.IPS_VIVID
        assertEquals(6700, vivid.temperatureK)
        assertEquals(1.24f, vivid.saturation, 0.001f)
        assertEquals(1.08f, vivid.contrast, 0.001f)
    }
}
