package com.noir.lynx.hardware

import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime binary and platform capability descriptor for HWUI, EGL,
 * SurfaceFlinger, ANGLE, Vulkan, Graphite, and InputFlinger.
 *
 * Prevents writing dead, fake, or unsupported properties on ROMs that
 * do not compile support for a given property string.
 */
data class GraphicsPipelineCapabilities(
    val isVulkanSupported: Boolean = false,
    val isGraphiteSupported: Boolean = false,
    val isAngleSupported: Boolean = false,
    val isTextureCacheSupported: Boolean = false,
    val isAsyncShaderSupported: Boolean = false,
    val isPureHwVsyncSupported: Boolean = false,
    val isEglSwapIntervalSupported: Boolean = false,
    val isSfLatchSupported: Boolean = false,
    val isSfDisableBackpressureSupported: Boolean = false,
    val isSfEarlyPhaseSupported: Boolean = false,
    val isSfTouchBoostDurationSupported: Boolean = false,
    val isInputResamplingSupported: Boolean = false,
    val hasResetprop: Boolean = false
)

object GraphicsPipelineProbe {

    @Volatile
    private var cached: GraphicsPipelineCapabilities? = null

    fun parseProbeOutput(lines: List<String>, sdkInt: Int): GraphicsPipelineCapabilities {
        val tokens = lines.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return GraphicsPipelineCapabilities(
            isVulkanSupported = "CAP_VULKAN=1" in tokens,
            isGraphiteSupported = ("CAP_GRAPHITE=1" in tokens) || sdkInt >= 35,
            isAngleSupported = "CAP_ANGLE=1" in tokens,
            isTextureCacheSupported = "CAP_TEX_CACHE=1" in tokens,
            isAsyncShaderSupported = "CAP_ASYNC_SHADER=1" in tokens,
            isPureHwVsyncSupported = "CAP_CPUREND_VSYNC=1" in tokens,
            isEglSwapIntervalSupported = "CAP_EGL_SWAP=1" in tokens,
            isSfLatchSupported = "CAP_SF_LATCH=1" in tokens,
            isSfDisableBackpressureSupported = "CAP_SF_BP=1" in tokens,
            isSfEarlyPhaseSupported = "CAP_SF_EARLY_PHASE=1" in tokens,
            isSfTouchBoostDurationSupported = "CAP_SF_TOUCH_DUR=1" in tokens,
            isInputResamplingSupported = "CAP_INPUT_RESAMPLE=1" in tokens,
            hasResetprop = "CAP_RESETPROP=1" in tokens
        )
    }

    /**
     * Builds a shell snippet that sets [key] via resetprop across KSU,
     * Magisk, and APatch binaries. When [readOnlyProp] is true (ro.* keys),
     * it refuses to fall back to setprop because setprop cannot mutate ro.* keys.
     */
    fun buildSetPropCommand(key: String, value: String, readOnlyProp: Boolean = key.startsWith("ro.")): String {
        return """
            rp=""
            for b in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop /data/adb/ap/bin/resetprop; do
                if [ -x "${'$'}b" ]; then rp="${'$'}b"; break; fi
            done
            if [ -z "${'$'}rp" ]; then rp="${'$'}(command -v resetprop 2>/dev/null)"; fi
            if [ -n "${'$'}rp" ]; then
                "${'$'}rp" -n "$key" "$value" 2>/dev/null || "${'$'}rp" "$key" "$value"
            else
                ${if (readOnlyProp) "false" else "setprop \"$key\" \"$value\""}
            fi
        """.trimIndent()
    }

    fun buildDeletePropCommand(key: String): String {
        return """
            rp=""
            for b in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop /data/adb/ap/bin/resetprop; do
                if [ -x "${'$'}b" ]; then rp="${'$'}b"; break; fi
            done
            if [ -z "${'$'}rp" ]; then rp="${'$'}(command -v resetprop 2>/dev/null)"; fi
            if [ -n "${'$'}rp" ]; then
                "${'$'}rp" -d "$key" 2>/dev/null || "${'$'}rp" --delete "$key" 2>/dev/null || true
            fi
        """.trimIndent()
    }

    suspend fun detect(forceRefresh: Boolean = false): GraphicsPipelineCapabilities = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cached?.let { return@withContext it }
        }
        val script = """
            has_str() {
                needle="${'$'}1"
                shift
                for f in "${'$'}@"; do
                    if [ -r "${'$'}f" ] && grep -Fq "${'$'}needle" "${'$'}f" 2>/dev/null; then
                        return 0
                    fi
                done
                return 1
            }

            HWUI_LIBS="/system/lib64/libhwui.so /system/lib/libhwui.so /system_ext/lib64/libhwui.so /system_ext/lib/libhwui.so"
            EGL_LIBS="/system/lib64/libEGL.so /system/lib/libEGL.so"
            SF_BINS="/system/bin/surfaceflinger /system/lib64/libsurfaceflinger.so /system/lib/libsurfaceflinger.so /system_ext/lib64/libsurfaceflinger.so"
            INPUT_LIBS="/system/lib64/libinput.so /system/lib/libinput.so /system/lib64/libinputflinger.so /system/lib/libinputflinger.so /vendor/lib64/libinput.so"

            if pm list features 2>/dev/null | grep -q "android.hardware.vulkan" || [ -f /system/lib64/libvulkan.so ] || ls /vendor/lib64/hw/vulkan.*.so >/dev/null 2>&1; then
                echo "CAP_VULKAN=1"
            fi
            if has_str "graphite" ${'$'}HWUI_LIBS || has_str "skiagraphite" ${'$'}HWUI_LIBS; then
                echo "CAP_GRAPHITE=1"
            fi
            if [ -d /apex/com.android.angle ] || ls /apex/com.android.angle* >/dev/null 2>&1 || \
               ls /vendor/lib64/egl/*ANGLE*.so /system/lib64/libEGL_angle.so >/dev/null 2>&1 || \
               pm list packages 2>/dev/null | grep -q "com.android.angle" || \
               settings list global 2>/dev/null | grep -q "angle_gl_driver"; then
                echo "CAP_ANGLE=1"
            fi
            if has_str "ro.hwui.texture_cache_size" ${'$'}HWUI_LIBS; then
                echo "CAP_TEX_CACHE=1"
            fi
            if has_str "debug.hwui.use_gpu_pixel_buffers" ${'$'}HWUI_LIBS; then
                echo "CAP_ASYNC_SHADER=1"
            fi
            if has_str "debug.cpurend.vsync" ${'$'}HWUI_LIBS; then
                echo "CAP_CPUREND_VSYNC=1"
            fi
            if has_str "debug.egl.swapinterval" ${'$'}EGL_LIBS; then
                echo "CAP_EGL_SWAP=1"
            fi
            if has_str "debug.sf.latch_unsignaled" ${'$'}SF_BINS || has_str "debug.sf.auto_latch_unsignaled" ${'$'}SF_BINS; then
                echo "CAP_SF_LATCH=1"
            fi
            if has_str "debug.sf.disable_backpressure" ${'$'}SF_BINS || has_str "debug.sf.enable_gl_backpressure" ${'$'}SF_BINS; then
                echo "CAP_SF_BP=1"
            fi
            if has_str "debug.sf.early_phase_offset_ns" ${'$'}SF_BINS || has_str "debug.sf.early_app_phase_offset_ns" ${'$'}SF_BINS; then
                echo "CAP_SF_EARLY_PHASE=1"
            fi
            if has_str "debug.sf.touch_boost_duration_ms" ${'$'}SF_BINS; then
                echo "CAP_SF_TOUCH_DUR=1"
            fi
            if has_str "ro.input.resampling" ${'$'}INPUT_LIBS; then
                echo "CAP_INPUT_RESAMPLE=1"
            fi
            for b in /data/adb/ksu/bin/resetprop /data/adb/magisk/resetprop /data/adb/ap/bin/resetprop; do
                if [ -x "${'$'}b" ]; then echo "CAP_RESETPROP=1"; break; fi
            done
            if command -v resetprop >/dev/null 2>&1; then
                echo "CAP_RESETPROP=1"
            fi
        """.trimIndent()

        val lines = try {
            Shell.cmd(script).exec().out
        } catch (_: Exception) {
            emptyList()
        }
        val result = parseProbeOutput(lines, Build.VERSION.SDK_INT)
        cached = result
        result
    }
}
