package com.noir.lynx.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.noir.lynx.data.LynxRepository
import com.noir.lynx.safety.OEMRestoreManager
import kotlinx.coroutines.*

/**
 * LynxThermalGuardService — Active background safety watchdog running every 3.0 seconds.
 * 
 * Safety Rules:
 * 1. Battery Temp >= 45°C -> Automatic de-escalation of aggressive thermal mode & battery protection.
 * 2. SoC Temp >= 85°C -> Immediate Emergency Failsafe Restore via OEMRestoreManager to prevent thermal runaway.
 * 3. Deep Sleep Shield -> Automatically suspends polling loops when display is OFF.
 */
class LynxThermalGuardService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isWatchdogRunning = false
    private var isScreenOn = true

    // Dynamic thresholds (configurable per device profile)
    private var batteryWarningC = 45.0f
    private var socEmergencyC = 85.0f

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    Log.d(TAG, "Screen OFF: Suspending active thermal watchdog polling for deep sleep")
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    Log.d(TAG, "Screen ON: Resuming active thermal watchdog polling")
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification("Thermal Safety Guard Aktif"))
        startWatchdog()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            batteryWarningC = it.getFloatExtra(EXTRA_BATT_WARN, 45.0f)
            socEmergencyC = it.getFloatExtra(EXTRA_SOC_EMERGENCY, 85.0f)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isWatchdogRunning = false
        serviceScope.cancel()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        Log.i(TAG, "LynxThermalGuardService stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startWatchdog() {
        if (isWatchdogRunning) return
        isWatchdogRunning = true

        serviceScope.launch {
            Log.i(TAG, "Thermal Guard Watchdog loop started. Batt Limit: ${batteryWarningC}°C, SoC Emergency: ${socEmergencyC}°C")
            while (isActive && isWatchdogRunning) {
                if (isScreenOn) {
                    try {
                        val zones = LynxRepository.readThermalZones()
                        val battTemp = zones.firstOrNull { 
                            val t = it.type.lowercase()
                            t.contains("batt") || t.contains("bms") || t.contains("charger")
                        }?.tempC ?: (LynxRepository.readTelemetry()?.temp?.toFloatOrNull() ?: 0f)

                        val socTemp = zones.filter { 
                            val t = it.type.lowercase()
                            t.contains("soc") || t.contains("cpu") || t.contains("tsens") || t.contains("ap")
                        }.maxOfOrNull { it.tempC } ?: battTemp

                        // Rule 1: Battery Protection Check
                        if (battTemp >= batteryWarningC) {
                            Log.w(TAG, "EMERGENCY: Battery temperature reached ${battTemp}°C (>= ${batteryWarningC}°C). Applying protective clamp!")
                            LynxRepository.applyThermalMode("stable")
                        }

                        // Rule 2: Silicon SoC Emergency Check
                        if (socTemp >= socEmergencyC) {
                            Log.e(TAG, "CRITICAL: Silicon SoC temperature reached ${socTemp}°C (>= ${socEmergencyC}°C). Executing Emergency Baseline Restore!")
                            OEMRestoreManager.restoreCapturedBaseline()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Watchdog polling cycle error: ${e.message}")
                    }
                }
                delay(3000L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lynx Thermal Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Pemantauan keselamatan termal perangkat real-time"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(statusText: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("Lynx Thermal Guard")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "LynxThermalGuard"
        private const val CHANNEL_ID = "lynx_thermal_guard_channel"
        private const val NOTIFICATION_ID = 4099

        const val EXTRA_BATT_WARN = "extra_batt_warn"
        const val EXTRA_SOC_EMERGENCY = "extra_soc_emergency"

        fun start(context: Context, battWarnC: Float? = null, socEmergC: Float? = null) {
            val profile = com.noir.lynx.hardware.ThermalProfileManager.resolveActiveProfile()
            val finalBattWarn = battWarnC ?: profile.limits.batteryWarningC
            val finalSocEmerg = socEmergC ?: profile.limits.socEmergencyC
            val intent = Intent(context, LynxThermalGuardService::class.java).apply {
                putExtra(EXTRA_BATT_WARN, finalBattWarn)
                putExtra(EXTRA_SOC_EMERGENCY, finalSocEmerg)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start LynxThermalGuardService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, LynxThermalGuardService::class.java))
            } catch (_: Exception) {}
        }
    }
}
