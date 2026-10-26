package com.noir.lynx.feature.hud.addins

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.util.DisplayMetrics
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

/** One HUD-owned session in :hud. UI never owns hardware or notification listeners. */
class HudGamingAddins(val context: Context, private val scope: CoroutineScope) {
    companion object { @Volatile var current: HudGamingAddins? = null; private set }
    val prefs = context.getSharedPreferences("hud_gaming_addins", Context.MODE_PRIVATE)
    private val mutableGlow = MutableStateFlow(prefs.getBoolean("glow", true))
    val glow = mutableGlow.asStateFlow()
    private val mutableColor = MutableStateFlow(prefs.getInt("glow_color", 0))
    val color = mutableColor.asStateFlow()
    val listenerConnected = HudNotificationGlowService.connected
    private val mutableRoot = MutableStateFlow(false)
    val rootAvailable = mutableRoot.asStateFlow()
    fun setRootAvailable(available: Boolean) { mutableRoot.value = available }
    val focus = HudFocusSession(context)
    val touch = HudTouchSession(context)
    val bypass = HudBypassSession(context)
    val qos = HudGameQos(context)
    val recorder = HudScreenRecorder(context)
    private val recordingIndicator = HudRecordingIndicator(context, recorder::stop)
    val screenshotPreview = HudScreenshotPreview(context)
    private var hudExpanded = true
    val console = HudGameConsole(context)
    val startedAt = SystemClock.elapsedRealtime()
    private val mutableBatteryStart = MutableStateFlow<Int?>(null)
    val batteryStart = mutableBatteryStart.asStateFlow()
    fun acceptBattery(value: Int?) { if (mutableBatteryStart.value == null && value != null && value in 0..100) mutableBatteryStart.value = value }
    val magic = HudMagicBox(context, scope, prefs)
    private var wifiLock: WifiManager.WifiLock? = null
    private val mutableWifi = MutableStateFlow(false)
    val wifiBoost = mutableWifi.asStateFlow()
    val glowOverlay = HudNotificationGlow(context)
    @Volatile var focused = false
    @Volatile private var closed = false
    private var previousDimensions: Pair<Int, Int>? = null
    var beforeMagicStart: () -> Unit = {}
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                magic.stop()
                recorder.stop()
                setWifiBoost(false)
                glowOverlay.close()
                screenshotPreview.close()
                scope.launch(Dispatchers.IO) { qos.restore() }
            }
        }
    }

    init {
        current = this
        scope.launch { recorder.state.collect { recordingIndicator.update(it, !hudExpanded) } }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED)
        else context.registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    fun startMagic() { beforeMagicStart(); magic.start() }
    fun setGameQos(enabled: Boolean) {
        val pkg = magic.foreground.value
        scope.launch(Dispatchers.IO) {
            if (!closed) qos.set(enabled, pkg)
            if (closed || magic.foreground.value != pkg) qos.restore()
        }
    }
    fun previewGlow() = glowOverlay.pulse(color.value)
    fun setAwake(enabled: Boolean) { scope.launch(Dispatchers.IO) { console.setAwake(enabled) } }

    fun setGlow(enabled: Boolean) {
        mutableGlow.value = enabled
        prefs.edit().putBoolean("glow", enabled).apply()
        if (!enabled) glowOverlay.close()
    }

    fun setColor(value: Int) {
        mutableColor.value = value.coerceIn(0, 2)
        prefs.edit().putInt("glow_color", mutableColor.value).apply()
    }

    fun notificationArrived() {
        val power = context.getSystemService(PowerManager::class.java)
        if (!closed && focused && glow.value && power?.isInteractive == true) {
            glowOverlay.pulse(color.value)
        }
    }

    fun openNotificationAccess() = openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    fun openSettings(action: String) {
        runCatching { context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun setWifiBoost(enabled: Boolean) {
        if (enabled && wifiLock?.isHeld == true) return
        if (!enabled) {
            runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
            wifiLock = null
        } else if (Build.VERSION.SDK_INT >= 29) {
            runCatching {
                val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
                wifiLock = wifi?.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "Lynx:GameNetwork")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onFailure { wifiLock = null }
        }
        mutableWifi.value = wifiLock?.isHeld == true
    }

    fun network(): HudGameNetwork {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return HudGameNetwork()
        return runCatching {
            val all = manager.allNetworks.mapNotNull { manager.getNetworkCapabilities(it) }
            val active = manager.getNetworkCapabilities(manager.activeNetwork)
            HudGameNetwork(
                wifi = all.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) },
                cellular = all.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) },
                validated = active?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                metered = manager.isActiveNetworkMetered,
                transport = when {
                    active?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true -> "VPN"
                    active?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
                    active?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile"
                    active?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                    else -> "—"
                }
            )
        }.getOrDefault(HudGameNetwork())
    }

    fun update(pkg: String, hudOpen: Boolean, width: Int, height: Int) {
        hudExpanded = hudOpen
        recordingIndicator.update(recorder.state.value, !hudExpanded)
        if (pkg != magic.foreground.value && qos.state.value.enabled) scope.launch(Dispatchers.IO) { qos.restore() }
        val windows = context.getSystemService(WindowManager::class.java)
        val dimensions = runCatching {
            if (Build.VERSION.SDK_INT >= 30) windows.maximumWindowMetrics.bounds.let { it.width() to it.height() }
            else DisplayMetrics().also { windows.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
        }.getOrDefault(width to height)
        if (previousDimensions != null && previousDimensions != dimensions && recorder.state.value.busy) recorder.stop()
        previousDimensions = dimensions
        magic.update(pkg, hudOpen, dimensions.first, dimensions.second)
        if (context.getSystemService(PowerManager::class.java)?.isInteractive != true) {
            setWifiBoost(false)
            glowOverlay.close()
        }
    }

    /** Teardown restoration runs on its caller's IO context, independently of the HUD scope. */
    fun close() {
        closed = true
        runCatching { context.unregisterReceiver(screenOff) }
        magic.stop()
        recorder.close()
        recordingIndicator.close()
        screenshotPreview.close()
        setWifiBoost(false)
        glowOverlay.close()
        if (current === this) {
            focus.restore()
            touch.restore()
            bypass.restore()
            qos.restore()
            console.restore()
        }
        if (current === this) current = null
    }
}

data class HudGameNetwork(
    val wifi: Boolean = false,
    val cellular: Boolean = false,
    val validated: Boolean = false,
    val metered: Boolean = false,
    val transport: String = "—"
)
