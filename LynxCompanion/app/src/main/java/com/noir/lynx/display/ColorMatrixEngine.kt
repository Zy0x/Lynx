package com.noir.lynx.display

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ColorMatrixProfile(
    val name: String,
    val red: Float = 1.0f,
    val green: Float = 1.0f,
    val blue: Float = 1.0f,
    val temperatureK: Int = 6500,
    val saturation: Float = 1.0f,
    val contrast: Float = 1.0f,
    val hueDegrees: Float = 0f,
    val kcalGamma: Int? = null
) {
    companion object {
        val ACCURATE = ColorMatrixProfile("Akurat", 1.0f, 1.0f, 1.0f, 6500, 1.0f, 1.0f, 0f)
        val IPS_D65_NATURAL = ColorMatrixProfile("D65 Natural IPS", 1.00f, 0.98f, 0.93f, 6500, 1.00f, 1.02f, 0f)
        val IPS_VIVID = ColorMatrixProfile("IPS Vivid", 1.01f, 1.00f, 0.98f, 6700, 1.24f, 1.08f, 0f)
        val GAMING = ColorMatrixProfile("Gaming", 1.0f, 1.0f, 1.0f, 7200, 1.15f, 1.10f, 0f)
        val CINEMA = ColorMatrixProfile("Cinema", 1.0f, 0.98f, 0.92f, 5800, 1.05f, 1.00f, 0f)
        val READING = ColorMatrixProfile("Membaca", 1.0f, 0.90f, 0.70f, 4500, 0.85f, 0.95f, 0f)
    }
}

object ColorMatrixEngine {

    private const val REF_6500K_R = 255.0
    private const val REF_6500K_G = 254.11005
    private const val REF_6500K_B = 250.04224

    fun kelvinToRgbGain(kelvin: Int): Triple<Float, Float, Float> {
        val temp = (kelvin / 100.0).coerceIn(10.0, 120.0)

        // Tanner Helland approximation normalized so 6500K equals neutral (1.0, 1.0, 1.0)
        val r = if (temp <= 66.0) 255.0 else 329.698727446 * Math.pow(temp - 60.0, -0.1332047592)
        val g = if (temp <= 66.0) 99.4708025861 * Math.log(temp) - 161.1195681661 else 288.1221695283 * Math.pow(temp - 60.0, -0.0755148492)
        val b = if (temp >= 66.0) 255.0 else if (temp <= 19.0) 0.0 else 138.5177312231 * Math.log(temp - 10.0) - 305.0447927307

        val normR = (r.coerceIn(0.0, 255.0) / REF_6500K_R).toFloat().coerceIn(0.2f, 1.5f)
        val normG = (g.coerceIn(0.0, 255.0) / REF_6500K_G).toFloat().coerceIn(0.2f, 1.5f)
        val normB = (b.coerceIn(0.0, 255.0) / REF_6500K_B).toFloat().coerceIn(0.2f, 1.5f)

        return Triple(normR, normG, normB)
    }

    /**
     * Builds a 4x4 SurfaceFlinger color matrix in column-major ui::mat4 layout:
     * [col0(R->RGB), col1(G->RGB), col2(B->RGB), col3(offset RGB + 1.0)].
     * White-point gain multiplies each output channel after Rec.709 saturation so neutral gray
     * always preserves the calibrated white balance regardless of saturation level, while contrast
     * pivots around mid-gray (0.5) using the translation column.
     */
    fun buildMatrix(profile: ColorMatrixProfile): FloatArray {
        val (tempR, tempG, tempB) = kelvinToRgbGain(profile.temperatureK)
        val gainR = (profile.red * tempR).coerceIn(0.2f, 1.5f)
        val gainG = (profile.green * tempG).coerceIn(0.2f, 1.5f)
        val gainB = (profile.blue * tempB).coerceIn(0.2f, 1.5f)

        val s = profile.saturation.coerceIn(0.0f, 2.0f)
        val c = profile.contrast.coerceIn(0.5f, 1.5f)

        // Rec.709 luminance weights
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f

        val sr0 = (1f - s) * lr + s
        val sr1 = (1f - s) * lg
        val sr2 = (1f - s) * lb

        val sg0 = (1f - s) * lr
        val sg1 = (1f - s) * lg + s
        val sg2 = (1f - s) * lb

        val sb0 = (1f - s) * lr
        val sb1 = (1f - s) * lg
        val sb2 = (1f - s) * lb + s

        // Row-major logical coefficients: out = diag(gain) * (c * S * in + (1 - c) * 0.5)
        val m00 = sr0 * gainR * c
        val m01 = sr1 * gainR * c
        val m02 = sr2 * gainR * c

        val m10 = sg0 * gainG * c
        val m11 = sg1 * gainG * c
        val m12 = sg2 * gainG * c

        val m20 = sb0 * gainB * c
        val m21 = sb1 * gainB * c
        val m22 = sb2 * gainB * c

        val baseOffset = (1.0f - c) * 0.5f
        val offR = baseOffset * gainR
        val offG = baseOffset * gainG
        val offB = baseOffset * gainB

        // Column-major ui::mat4 ordering required by SurfaceFlinger binder transaction 1015
        return floatArrayOf(
            m00, m10, m20, 0.0f,
            m01, m11, m21, 0.0f,
            m02, m12, m22, 0.0f,
            offR, offG, offB, 1.0f
        )
    }

    internal fun isIdentityMatrix(matrix: FloatArray, epsilon: Float = 1e-4f): Boolean {
        if (matrix.size != 16) return false
        for (i in 0 until 16) {
            val expected = if (i % 5 == 0) 1.0f else 0.0f
            if (kotlin.math.abs(matrix[i] - expected) > epsilon) return false
        }
        return true
    }

    suspend fun checkConflict(): String? = withContext(Dispatchers.IO) {
        val script = """
            night=${'$'}(settings get secure night_display_activated 2>/dev/null)
            dim=${'$'}(settings get secure reduce_bright_colors_activated 2>/dev/null)
            [ "${'$'}night" = "1" ] && echo "NIGHT"
            [ "${'$'}dim" = "1" ] && echo "DIM"
        """.trimIndent()
        val out = Shell.cmd(script).exec().out
        return@withContext when {
            out.contains("NIGHT") -> "Night Light / Mode Malam sedang aktif di sistem"
            out.contains("DIM") -> "Extra Dim / Redup Ekstra sedang aktif di sistem"
            else -> null
        }
    }

    private fun buildSfApplyScript(profile: ColorMatrixProfile): String {
        val matrix = buildMatrix(profile)
        val matrixCmd = if (isIdentityMatrix(matrix)) {
            "1015 i32 0"
        } else {
            val matrixArgs = matrix.joinToString(" ") {
                String.format(java.util.Locale.US, "f %.5f", it)
            }
            "1015 i32 1 $matrixArgs"
        }

        val (tempR, tempG, tempB) = kelvinToRgbGain(profile.temperatureK)
        val rAdj = (profile.red * tempR).coerceIn(0.2f, 1.5f)
        val gAdj = (profile.green * tempG).coerceIn(0.2f, 1.5f)
        val bAdj = (profile.blue * tempB).coerceIn(0.2f, 1.5f)
        val sat = profile.saturation.coerceIn(0.5f, 2.0f)

        return String.format(
            java.util.Locale.US,
            """
            call_sf() {
                local res
                res=${'$'}(su 1000 service call SurfaceFlinger "${'$'}@" 2>&1)
                if [ ${'$'}? -eq 0 ] && ! echo "${'$'}res" | grep -qiE "denied|not permitted|failed|error"; then
                    return 0
                fi
                res=${'$'}(service call SurfaceFlinger "${'$'}@" 2>&1)
                [ ${'$'}? -eq 0 ] && ! echo "${'$'}res" | grep -qiE "denied|not permitted|failed|error"
            }
            cur_adj=${'$'}(settings get system display_color_adjustment 2>/dev/null)
            if [ "${'$'}cur_adj" != "1.0 1.0 1.0" ] && [ -n "${'$'}cur_adj" ] && [ "${'$'}cur_adj" != "null" ]; then
                settings put system display_color_adjustment '1.0 1.0 1.0' 2>/dev/null
            fi
            setprop persist.sys.sf.color_saturation 1.0 2>/dev/null
            call_sf 1022 f 1.0 >/dev/null 2>&1
            if call_sf %s; then
                call_sf 1006 >/dev/null 2>&1
            else
                settings put system display_color_adjustment '%.2f %.2f %.2f' 2>/dev/null
                setprop persist.sys.sf.color_saturation %.2f 2>/dev/null
                call_sf 1022 f %.2f >/dev/null 2>&1
                call_sf 1006 >/dev/null 2>&1
            fi
            """.trimIndent(),
            matrixCmd, rAdj, gAdj, bAdj, sat, sat
        )
    }

    suspend fun apply(profile: ColorMatrixProfile): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = Shell.cmd(buildSfApplyScript(profile)).exec()

            // KCAL gamma only if hardware node actually exists on the device
            profile.kcalGamma?.let { gammaVal ->
                val kcalScript = """
                    if [ -f /sys/devices/platform/kcal_ctrl.0/kcal_val ]; then
                        echo $gammaVal > /sys/devices/platform/kcal_ctrl.0/kcal_val 2>/dev/null
                    fi
                """.trimIndent()
                Shell.cmd(kcalScript).exec()
            }

            res.isSuccess
        } catch (e: Exception) {
            false
        }
    }

    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        try {
            val resetScript = """
                call_sf() {
                    su 1000 service call SurfaceFlinger "${'$'}@" >/dev/null 2>&1 || \
                        service call SurfaceFlinger "${'$'}@" >/dev/null 2>&1
                }
                call_sf 1015 i32 0
                setprop persist.sys.sf.color_saturation 1.0 2>/dev/null
                call_sf 1022 f 1.0
                cur_adj=${'$'}(settings get system display_color_adjustment 2>/dev/null)
                if [ "${'$'}cur_adj" != "1.0 1.0 1.0" ] && [ -n "${'$'}cur_adj" ] && [ "${'$'}cur_adj" != "null" ]; then
                    settings put system display_color_adjustment '1.0 1.0 1.0' 2>/dev/null
                fi
                call_sf 1006
                if [ -f /sys/devices/platform/kcal_ctrl.0/kcal_val ]; then
                    echo 256 > /sys/devices/platform/kcal_ctrl.0/kcal_val 2>/dev/null
                fi
            """.trimIndent()
            Shell.cmd(resetScript).exec().isSuccess
        } catch (e: Exception) {
            false
        }
    }
}
