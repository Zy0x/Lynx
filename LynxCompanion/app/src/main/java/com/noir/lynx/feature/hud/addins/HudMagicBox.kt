package com.noir.lynx.feature.hud.addins

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

data class HudTapPoint(val x: Int = 50, val y: Int = 50) {
    fun valid() = x in 1..99 && y in 1..99
}
internal fun validMagicPackage(value: String) = value.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")) && value !in setOf(
    "com.android.systemui", "com.android.settings", "com.android.permissioncontroller",
    "com.google.android.permissioncontroller", "com.android.packageinstaller", "com.google.android.packageinstaller",
    "me.bmax.apatch", "me.weishu.kernelsu", "com.topjohnwu.magisk"
)
internal fun canRunMagic(expected: String, actual: String, open: Boolean, screenOn: Boolean, originalWidth: Int, originalHeight: Int, width: Int, height: Int) =
    validMagicPackage(expected) && expected == actual && !open && screenOn && width == originalWidth && height == originalHeight && width > 0 && height > 0

/** User-started, finite tap sequences. Screen/app/geometry changes stop the run; opening HUD pauses it. */
class HudMagicBox(private val context: Context, private val scope: CoroutineScope, private val prefs: SharedPreferences) {
    private val mutableRunning = MutableStateFlow(false)
    val running = mutableRunning.asStateFlow()
    private val mutablePlacing = MutableStateFlow(false)
    val isPlacing = mutablePlacing.asStateFlow()
    private val mutablePoints = MutableStateFlow(loadPoints())
    val points = mutablePoints.asStateFlow()
    private val mutableInterval = MutableStateFlow(prefs.getInt("magic_interval", 1000).coerceIn(750, 5000))
    val interval = mutableInterval.asStateFlow()
    private val mutablePackage = MutableStateFlow("")
    val foreground = mutablePackage.asStateFlow()
    @Volatile private var open = true
    @Volatile private var width = 0
    @Volatile private var height = 0
    private var job: Job? = null
    private val generation = AtomicLong()
    private fun loadPoints(): List<HudTapPoint> = runCatching {
        val values = JSONArray(prefs.getString("magic_points", "[[50,50]]"))
        (0 until minOf(values.length(), 5)).map { values.getJSONArray(it).let { p -> HudTapPoint(p.getInt(0), p.getInt(1)) } }
            .filter { it.valid() }.ifEmpty { listOf(HudTapPoint()) }
    }.getOrDefault(listOf(HudTapPoint()))
    fun setPoints(value: List<HudTapPoint>) {
        stop()
        mutablePoints.value = value.filter { it.valid() }.take(5).ifEmpty { listOf(HudTapPoint()) }
        val array = JSONArray()
        mutablePoints.value.forEach { array.put(JSONArray(listOf(it.x, it.y))) }
        prefs.edit().putString("magic_points", array.toString()).apply()
    }
    fun startPlacing() {
        stop()
        mutablePlacing.value = true
    }
    fun stopPlacing() {
        mutablePlacing.value = false
    }
    fun updatePoint(index: Int, x: Int, y: Int) {
        val current = mutablePoints.value.toMutableList()
        if (index in current.indices) {
            current[index] = HudTapPoint(x.coerceIn(1, 99), y.coerceIn(1, 99))
            setPoints(current)
        }
    }
    fun addPoint() {
        if (mutablePoints.value.size < 5) {
            setPoints(mutablePoints.value + HudTapPoint())
        }
    }
    fun removePoint(index: Int) {
        if (mutablePoints.value.size > 1 && index in mutablePoints.value.indices) {
            setPoints(mutablePoints.value.filterIndexed { i, _ -> i != index })
        }
    }
    fun setInterval(value: Int) {
        stop(); mutableInterval.value = value.coerceIn(750, 5000)
        prefs.edit().putInt("magic_interval", mutableInterval.value).apply()
    }
    fun update(pkg: String, hudOpen: Boolean, w: Int, h: Int) {
        if (running.value && (pkg != mutablePackage.value || w != width || h != height)) stop()
        if (mutablePlacing.value && (pkg != mutablePackage.value || w != width || h != height)) stopPlacing()
        mutablePackage.value = pkg
        open = hudOpen; width = w; height = h
    }
    fun start() {
        stop()
        val token = generation.incrementAndGet()
        val expected = foreground.value
        if (!validMagicPackage(expected) || width <= 0 || height <= 0) return
        val initialWidth = width; val initialHeight = height
        val sequence = points.value.toList(); val pause = interval.value.toLong()
        mutableRunning.value = true
        job = scope.launch(Dispatchers.IO) {
            try {
                var taps = 0
                // A run expires after one minute even if HUD stays open; maximum 30 taps.
                withTimeout(60_000) {
                    while (isActive && taps < 30) {
                        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true
                        if (!screenOn || expected != foreground.value || width != initialWidth || height != initialHeight) break
                        if (open) { delay(150); continue }
                        if (!canRunMagic(expected, foreground.value, open, screenOn, initialWidth, initialHeight, width, height)) break
                        val point = sequence[taps % sequence.size]
                        val x = initialWidth * point.x / 100; val y = initialHeight * point.y / 100
                        // Verify actual Android foreground again inside the same command as the tap.
                        val command = """
                            pkg=${'$'}(dumpsys activity activities | sed -nE '/Display #0 /,/Display #[1-9][0-9]* /{/topResumedActivity|mResumedActivity/{s/.* u[0-9][0-9]* //;s,/.*,,;p;q;}}')
                            [ "${'$'}pkg" = '$expected' ] || exit 7
                            input tap $x $y
                        """.trimIndent()
                        val ok = runInterruptible {
                            val child = ProcessBuilder("su", "-c", command).redirectErrorStream(true).start()
                            try { child.waitFor(3, TimeUnit.SECONDS) && child.exitValue() == 0 }
                            finally { child.destroy() }
                        }
                        if (!ok) break
                        taps++
                        delay(pause)
                    }
                }
            } finally { if (generation.get() == token) mutableRunning.value = false }
        }
    }
    fun stop() { generation.incrementAndGet(); job?.cancel(); job = null; mutableRunning.value = false }
}
