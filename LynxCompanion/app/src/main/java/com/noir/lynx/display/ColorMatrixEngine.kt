package com.noir.lynx.display

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

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
        val GAMING = ColorMatrixProfile("Gaming", 1.0f, 1.0f, 1.0f, 7200, 1.15f, 1.10f, 0f)
        val CINEMA = ColorMatrixProfile("Cinema", 1.0f, 0.98f, 0.92f, 5800, 1.05f, 1.00f, 0f)
        val READING = ColorMatrixProfile("Membaca", 1.0f, 0.90f, 0.70f, 4500, 0.85f, 0.95f, 0f)
    }
}

object ColorMatrixEngine {

    fun kelvinToRgbGain(kelvin: Int): Triple<Float, Float, Float> {
        val temp = (kelvin / 100.0).coerceIn(10.0, 120.0)

        // Tanner Helland approximation
        val r = if (temp <= 66.0) 255.0 else 329.698727446 * Math.pow(temp - 60.0, -0.1332047592)
        val g = if (temp <= 66.0) 99.4708025861 * Math.log(temp) - 161.1195681661 else 288.1221695283 * Math.pow(temp - 60.0, -0.0755148492)
        val b = if (temp >= 66.0) 255.0 else if (temp <= 19.0) 0.0 else 138.5177312231 * Math.log(temp - 10.0) - 305.0447927307

        // Normalize so 6500K is ~1.0
        val baseR = 255.0
        val baseG = 249.0
        val baseB = 255.0

        val normR = (r.coerceIn(0.0, 255.0) / baseR).toFloat().coerceIn(0.2f, 1.5f)
        val normG = (g.coerceIn(0.0, 255.0) / baseG).toFloat().coerceIn(0.2f, 1.5f)
        val normB = (b.coerceIn(0.0, 255.0) / baseB).toFloat().coerceIn(0.2f, 1.5f)

        return Triple(normR, normG, normB)
    }

    fun buildMatrix(profile: ColorMatrixProfile): FloatArray {
        // Compute gains from sliders and kelvin
        val (tempR, tempG, tempB) = kelvinToRgbGain(profile.temperatureK)
        val gainR = profile.red * tempR
        val gainG = profile.green * tempG
        val gainB = profile.blue * tempB

        val s = profile.saturation.coerceIn(0.0f, 2.0f)
        val c = profile.contrast.coerceIn(0.5f, 1.5f)

        // Rec.709 Luma weights
        val lr = 0.2126f
        val lg = 0.7152f
        val lb = 0.0722f

        // Saturation matrix elements
        val sr0 = (1f - s) * lr + s
        val sr1 = (1f - s) * lg
        val sr2 = (1f - s) * lb

        val sg0 = (1f - s) * lr
        val sg1 = (1f - s) * lg + s
        val sg2 = (1f - s) * lb

        val sb0 = (1f - s) * lr
        val sb1 = (1f - s) * lg
        val sb2 = (1f - s) * lb + s

        // Combine gain * saturation * contrast
        val m00 = (sr0 * gainR) * c
        val m01 = (sr1 * gainG) * c
        val m02 = (sr2 * gainB) * c
        val m03 = 0.0f

        val m10 = (sg0 * gainR) * c
        val m11 = (sg1 * gainG) * c
        val m12 = (sg2 * gainB) * c
        val m13 = 0.0f

        val m20 = (sb0 * gainR) * c
        val m21 = (sb1 * gainG) * c
        val m22 = (sb2 * gainB) * c
        val m23 = 0.0f

        val m30 = 0.0f
        val m31 = 0.0f
        val m32 = 0.0f
        val m33 = 1.0f

        return floatArrayOf(
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            m30, m31, m32, m33
        )
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

    suspend fun apply(profile: ColorMatrixProfile): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Calculate color gains from temperature Kelvin & RGB gains
            val (tempR, tempG, tempB) = kelvinToRgbGain(profile.temperatureK)
            val rAdj = (profile.red * tempR).coerceIn(0.2f, 1.5f)
            val gAdj = (profile.green * tempG).coerceIn(0.2f, 1.5f)
            val bAdj = (profile.blue * tempB).coerceIn(0.2f, 1.5f)

            // 2. Safe Universal Color Adjustment (Strict Locale.US with period)
            val adjCmd = String.format(
                java.util.Locale.US,
                "settings put system display_color_adjustment '%.2f %.2f %.2f'",
                rAdj, gAdj, bAdj
            )
            Shell.cmd(adjCmd).exec()

            // 3. Saturation via prop & SF 1022 (with Strict Locale.US)
            val sat = profile.saturation.coerceIn(0.5f, 2.0f)
            val satCmd = String.format(
                java.util.Locale.US,
                "setprop persist.sys.sf.color_saturation %.2f 2>/dev/null; service call SurfaceFlinger 1022 f %.2f 2>/dev/null",
                sat, sat
            )
            Shell.cmd(satCmd).exec()

            // 4. Ensure SurfaceFlinger raw matrix is cleared (identity) to prevent black screen
            Shell.cmd("service call SurfaceFlinger 1015 i32 0 2>/dev/null").exec()

            // 5. KCAL gamma only if hardware node actually exists on the device
            profile.kcalGamma?.let { gammaVal ->
                val kcalScript = """
                    if [ -f /sys/devices/platform/kcal_ctrl.0/kcal_val ]; then
                        echo $gammaVal > /sys/devices/platform/kcal_ctrl.0/kcal_val 2>/dev/null
                    fi
                """.trimIndent()
                Shell.cmd(kcalScript).exec()
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun reset(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Reset SF 1015 matrix to identity
            Shell.cmd("service call SurfaceFlinger 1015 i32 0 2>/dev/null").exec()
            // Reset SF 1022 saturation to 1.0
            Shell.cmd("setprop persist.sys.sf.color_saturation 1.0 2>/dev/null; service call SurfaceFlinger 1022 f 1.0 2>/dev/null").exec()
            // Reset display_color_adjustment to 1.0
            Shell.cmd("settings put system display_color_adjustment '1.0 1.0 1.0' 2>/dev/null").exec()
            // Reset KCAL only if present
            Shell.cmd("if [ -f /sys/devices/platform/kcal_ctrl.0/kcal_val ]; then echo 256 > /sys/devices/platform/kcal_ctrl.0/kcal_val; fi 2>/dev/null").exec()
            true
        } catch (e: Exception) {
            false
        }
    }
}
