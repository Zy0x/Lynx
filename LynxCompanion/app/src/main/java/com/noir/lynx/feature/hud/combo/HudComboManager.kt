package com.noir.lynx.feature.hud.combo

import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Universal Macro Combo Manager.
 * Handles storage, live editing, TouchShare export/import, and low-latency root execution.
 */
class HudComboManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val prefs: SharedPreferences
) {
    private val mutableCombos = MutableStateFlow(loadCombos())
    val combos = mutableCombos.asStateFlow()

    private val mutableActiveRunning = MutableStateFlow<String?>(null)
    val activeRunning = mutableActiveRunning.asStateFlow()

    private val mutableEditingComboId = MutableStateFlow<String?>(null)
    val editingComboId = mutableEditingComboId.asStateFlow()

    private val mutableForeground = MutableStateFlow("")
    val foreground = mutableForeground.asStateFlow()

    @Volatile private var screenWidth = 0
    @Volatile private var screenHeight = 0
    @Volatile private var hudOpen = false

    private var executionJob: Job? = null
    private val runToken = AtomicLong(0)

    init {
        runCatching {
            val dm = context.resources.displayMetrics
            screenWidth = dm.widthPixels
            screenHeight = dm.heightPixels
        }
    }

    private fun loadCombos(): List<MacroCombo> = runCatching {
        val raw = prefs.getString("macro_combos_v2", null)
        if (raw.isNullOrBlank()) {
            // Default preset C1 (disabled by default for touch safety)
            listOf(
                MacroCombo(
                    id = "c1",
                    name = "Kombo Cepat 1",
                    enabled = false,
                    executionMode = ExecutionMode.ONE_SHOT,
                    triggerXRatio = 0.88f,
                    triggerYRatio = 0.35f,
                    taps = listOf(
                        RecordedTouch(0.50f, 0.50f, 0L),
                        RecordedTouch(0.55f, 0.50f, 200L)
                    )
                )
            )
        } else {
            val array = JSONArray(raw)
            val list = mutableListOf<MacroCombo>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id", "c${i + 1}")
                val name = obj.optString("name", "Kombo Cepat ${i + 1}")
                val enabled = obj.optBoolean("enabled", false)
                val mode = runCatching { ExecutionMode.valueOf(obj.optString("mode", "ONE_SHOT")) }.getOrDefault(ExecutionMode.ONE_SHOT)
                val tx = obj.optDouble("tx", 0.88).toFloat().coerceIn(0.01f, 0.99f)
                val ty = obj.optDouble("ty", 0.35).toFloat().coerceIn(0.01f, 0.99f)
                val loopDelay = obj.optLong("loopDelay", 150L).coerceIn(50L, 5000L)

                val tapsList = mutableListOf<RecordedTouch>()
                val tapsArray = obj.optJSONArray("taps")
                if (tapsArray != null) {
                    for (j in 0 until tapsArray.length()) {
                        val tObj = tapsArray.getJSONObject(j)
                        val x = tObj.optDouble("x", 0.5).toFloat().coerceIn(0.001f, 0.999f)
                        val y = tObj.optDouble("y", 0.5).toFloat().coerceIn(0.001f, 0.999f)
                        val d = tObj.optLong("d", 100L).coerceIn(0L, 10_000L)
                        tapsList.add(RecordedTouch(x, y, d))
                    }
                }
                list.add(MacroCombo(id, name, enabled, mode, tx, ty, loopDelay, tapsList))
            }
            list.ifEmpty {
                listOf(MacroCombo())
            }
        }
    }.getOrDefault(listOf(MacroCombo()))

    fun saveCombos(updated: List<MacroCombo>) {
        mutableCombos.value = updated
        val array = JSONArray()
        updated.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("enabled", c.enabled)
            obj.put("mode", c.executionMode.name)
            obj.put("tx", c.triggerXRatio.toDouble())
            obj.put("ty", c.triggerYRatio.toDouble())
            obj.put("loopDelay", c.loopDelayMs)

            val tapsArray = JSONArray()
            c.taps.forEach { t ->
                val tObj = JSONObject()
                tObj.put("x", t.xRatio.toDouble())
                tObj.put("y", t.yRatio.toDouble())
                tObj.put("d", t.delayBeforeMs)
                tapsArray.put(tObj)
            }
            obj.put("taps", tapsArray)
            array.put(obj)
        }
        prefs.edit().putString("macro_combos_v2", array.toString()).apply()
    }

    fun updateCombo(combo: MacroCombo) {
        val current = mutableCombos.value.toMutableList()
        val index = current.indexOfFirst { it.id == combo.id }
        if (index >= 0) {
            current[index] = combo
        } else {
            current.add(combo)
        }
        saveCombos(current)
    }

    fun addCombo(): MacroCombo {
        val current = mutableCombos.value
        val nextNum = (current.mapNotNull { it.id.filter { c -> c.isDigit() }.toIntOrNull() }.maxOrNull() ?: current.size) + 1
        val newCombo = MacroCombo(
            id = "c$nextNum",
            name = "Kombo Cepat $nextNum",
            enabled = true,
            executionMode = ExecutionMode.ONE_SHOT,
            triggerXRatio = 0.88f,
            triggerYRatio = (0.25f + (nextNum * 0.10f)).coerceIn(0.15f, 0.85f),
            taps = emptyList()
        )
        saveCombos(current + newCombo)
        return newCombo
    }

    fun removeCombo(id: String) {
        val current = mutableCombos.value
        if (current.size > 1) {
            saveCombos(current.filter { it.id != id })
        }
    }

    fun setEditing(comboId: String?) {
        stopExecution()
        mutableEditingComboId.value = comboId
    }

    fun updateDimensions(pkg: String, isHudOpen: Boolean, w: Int, h: Int) {
        if (mutableActiveRunning.value != null && (pkg != mutableForeground.value || w != screenWidth || h != screenHeight)) {
            stopExecution()
        }
        mutableForeground.value = pkg
        hudOpen = isHudOpen
        screenWidth = w
        screenHeight = h
    }

    fun executeCombo(comboId: String, forceStopIfRunning: Boolean = true) {
        if (mutableActiveRunning.value == comboId) {
            if (forceStopIfRunning) {
                stopExecution()
            }
            return
        }

        stopExecution()
        val target = mutableCombos.value.find { it.id == comboId } ?: return
        val dm = runCatching { context.resources.displayMetrics }.getOrNull()
        val w = if (screenWidth > 0) screenWidth else (dm?.widthPixels ?: 1080).coerceAtLeast(1080)
        val h = if (screenHeight > 0) screenHeight else (dm?.heightPixels ?: 2400).coerceAtLeast(1920)
        if (target.taps.isEmpty() || w <= 0 || h <= 0) return

        val token = runToken.incrementAndGet()
        mutableActiveRunning.value = comboId
        android.util.Log.i("LynxCombo", "Executing combo ${target.id} (${target.name}) with ${target.taps.size} taps on ${w}x${h}")

        executionJob = scope.launch(Dispatchers.IO) {
            try {
                val expectedPkg = mutableForeground.value

                var loopCount = 0
                val maxLoops = if (target.executionMode == ExecutionMode.ONE_SHOT) 1 else 50

                while (isActive && loopCount < maxLoops && runToken.get() == token) {
                    val powerManager = context.getSystemService(PowerManager::class.java)
                    if (powerManager?.isInteractive != true) break
                    if (expectedPkg.isNotBlank() && expectedPkg != mutableForeground.value) break

                    for (touch in target.taps) {
                        if (!isActive || runToken.get() != token) break

                        if (touch.delayBeforeMs > 0L) {
                            delay(touch.delayBeforeMs.coerceIn(5L, 5000L))
                        }

                        val pixelX = touch.toPixelX(w)
                        val pixelY = touch.toPixelY(h)
                        val cmd = "input tap $pixelX $pixelY"

                        val success = runCatching {
                            Shell.cmd(cmd).submit()
                            true
                        }.getOrElse {
                            runInterruptible {
                                val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
                                try { p.waitFor(2, TimeUnit.SECONDS) && p.exitValue() == 0 }
                                finally { p.destroy() }
                            }
                        }
                        if (!success) break
                    }

                    loopCount++
                    if (target.executionMode == ExecutionMode.HOLD_LOOP && loopCount < maxLoops) {
                        delay(target.loopDelayMs.coerceIn(50L, 5000L))
                    }
                }
            } finally {
                if (runToken.get() == token) {
                    mutableActiveRunning.value = null
                }
            }
        }
    }

    fun stopExecution() {
        runToken.incrementAndGet()
        executionJob?.cancel()
        executionJob = null
        mutableActiveRunning.value = null
    }
}
