package com.noir.lynx.feature.hud.data

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import com.noir.lynx.feature.hud.HudApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext

/** Only enabled launcher apps in the current Android user; no fixed compatibility whitelist. */
class HudAppCatalog(private val context: Context) {
    val apps = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        trySend(Unit)
        awaitClose { context.unregisterReceiver(receiver) }
    }.mapLatest { withContext(Dispatchers.IO) { load() } }

    @Suppress("DEPRECATION")
    fun loadTargetPackages(targetPackages: List<String>): List<HudApp> {
        val pm = context.packageManager
        return targetPackages.distinct().filter { it != context.packageName }.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                if (!info.enabled || pm.getLaunchIntentForPackage(pkg) == null) return@runCatching null
                val icon = pm.getApplicationIcon(info)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 96, 96); icon.draw(Canvas(bitmap))
                HudApp(pkg, pm.getApplicationLabel(info).toString(), bitmap)
            }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private fun load(): List<HudApp> {
        val pm = context.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }.distinct().filter { it != context.packageName }
            .mapNotNull { pkg -> runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                if (!info.enabled || pm.getLaunchIntentForPackage(pkg) == null) return@runCatching null
                val icon = pm.getApplicationIcon(info)
                val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
                icon.setBounds(0, 0, 96, 96); icon.draw(Canvas(bitmap))
                HudApp(pkg, pm.getApplicationLabel(info).toString(), bitmap)
            }.getOrNull() }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
}
