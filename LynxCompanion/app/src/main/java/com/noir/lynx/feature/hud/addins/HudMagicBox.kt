package com.noir.lynx.feature.hud.addins

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToInt

data class HudTapPoint(
    val x: Int = 50,
    val y: Int = 50,
    val xRatio: Float = (x.coerceIn(1, 99) / 100f),
    val yRatio: Float = (y.coerceIn(1, 99) / 100f),
    val delayMs: Long = 500L
) {
    fun valid() = x in 1..99 && y in 1..99

    fun toPixelX(screenWidth: Int): Int =
        if (xRatio > 0.0001f && xRatio < 0.9999f) (screenWidth * xRatio).roundToInt().coerceIn(1, screenWidth - 1)
        else (screenWidth * x / 100).coerceIn(1, screenWidth - 1)

    fun toPixelY(screenHeight: Int): Int =
        if (yRatio > 0.0001f && yRatio < 0.9999f) (screenHeight * yRatio).roundToInt().coerceIn(1, screenHeight - 1)
        else (screenHeight * y / 100).coerceIn(1, screenHeight - 1)
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
    private val mutableComboName = MutableStateFlow(prefs.getString("magic_name", "Kombo Cepat1") ?: "Kombo Cepat1")
    val comboName = mutableComboName.asStateFlow()
    private val mutableInterval = MutableStateFlow(prefs.getInt("magic_interval", 1000).coerceIn(100, 5000))
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
        (0 until minOf(values.length(), 10)).map { idx ->
            val p = values.getJSONArray(idx)
            val x = p.getInt(0)
            val y = p.getInt(1)
            val xRatio = if (p.length() >= 4) p.getDouble(2).toFloat() else (x / 100f)
            val yRatio = if (p.length() >= 4) p.getDouble(3).toFloat() else (y / 100f)
            val delay = if (p.length() >= 5) p.getLong(4) else 500L
            HudTapPoint(x, y, xRatio, yRatio, delay)
        }.filter { it.valid() }.ifEmpty { listOf(HudTapPoint()) }
    }.getOrDefault(listOf(HudTapPoint()))

    fun setPoints(value: List<HudTapPoint>) {
        stop()
        mutablePoints.value = value.filter { it.valid() }.take(10).ifEmpty { listOf(HudTapPoint()) }
        val array = JSONArray()
        mutablePoints.value.forEach {
            val item = JSONArray()
            item.put(it.x)
            item.put(it.y)
            item.put(it.xRatio.toDouble())
            item.put(it.yRatio.toDouble())
            item.put(it.delayMs)
            array.put(item)
        }
        prefs.edit().putString("magic_points", array.toString()).apply()
    }

    fun setComboName(value: String) {
        val clean = value.trim().take(30).ifBlank { "Kombo Cepat1" }
        mutableComboName.value = clean
        prefs.edit().putString("magic_name", clean).apply()
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
            val cx = x.coerceIn(1, 99)
            val cy = y.coerceIn(1, 99)
            current[index] = HudTapPoint(cx, cy, cx / 100f, cy / 100f, current[index].delayMs)
            setPoints(current)
        }
    }

    fun addPoint() {
        if (mutablePoints.value.size < 10) {
            setPoints(mutablePoints.value + HudTapPoint())
        }
    }

    fun removePoint(index: Int) {
        if (mutablePoints.value.size > 1 && index in mutablePoints.value.indices) {
            setPoints(mutablePoints.value.filterIndexed { i, _ -> i != index })
        }
    }

    fun setInterval(value: Int) {
        stop(); mutableInterval.value = value.coerceIn(100, 5000)
        prefs.edit().putInt("magic_interval", mutableInterval.value).apply()
    }

    fun update(pkg: String, hudOpen: Boolean, w: Int, h: Int) {
        if (running.value && (pkg != mutablePackage.value || w != width || h != height)) stop()
        if (mutablePlacing.value && (pkg != mutablePackage.value && pkg.isNotBlank())) stopPlacing()
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
        if (sequence.isEmpty()) return
        mutableRunning.value = true
        job = scope.launch(Dispatchers.IO) {
            try {
                var taps = 0
                // A run expires after one minute even if HUD stays open; maximum 60 taps.
                withTimeout(60_000) {
                    while (isActive && taps < 60) {
                        val screenOn = context.getSystemService(PowerManager::class.java)?.isInteractive == true
                        if (!screenOn || expected != foreground.value || width != initialWidth || height != initialHeight) break
                        if (open) { delay(150); continue }
                        if (!canRunMagic(expected, foreground.value, open, screenOn, initialWidth, initialHeight, width, height)) break
                        val point = sequence[taps % sequence.size]
                        val x = point.toPixelX(initialWidth)
                        val y = point.toPixelY(initialHeight)
                        val cmd = "input tap $x $y"
                        val ok = runCatching {
                            Shell.cmd(cmd).submit()
                            true
                        }.getOrElse {
                            runInterruptible {
                                val child = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                                try { child.waitFor(2, TimeUnit.SECONDS) && child.exitValue() == 0 }
                                finally { child.destroy() }
                            }
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
