package com.noir.lynx.feature.hud.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Process
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject

/** Same-UID, package-scoped preference patches keep the app and :hud process caches coherent. */
internal object HudPreferenceSync {
    private const val ACTION = "com.noir.lynx.HUD_PREFERENCE_PATCH"
    private const val REQUEST = "com.noir.lynx.HUD_PREFERENCE_SNAPSHOT_REQUEST"
    private var connected = false
    private var receiving = false
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Synchronized fun connect(context: Context, preferences: SharedPreferences) {
        if (connected) return
        val app = context.applicationContext
        val publish: (String) -> Unit = publish@ { key ->
                val patch = JSONObject().put("key", key)
                val value = preferences.all[key]
                when (value) {
                    null -> patch.put("type", "remove")
                    is Boolean -> patch.put("type", "boolean").put("value", value)
                    is Int -> patch.put("type", "int").put("value", value)
                    is Long -> patch.put("type", "long").put("value", value)
                    is Float -> patch.put("type", "float").put("value", value.toDouble())
                    is String -> patch.put("type", "string").put("value", value)
                    is Set<*> -> patch.put("type", "set").put("value", JSONArray(value.toList()))
                    else -> return@publish
                }
                app.sendBroadcast(Intent(ACTION).setPackage(app.packageName)
                    .putExtra("origin", Process.myPid()).putExtra("patch", patch.toString()))
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.getIntExtra("origin", -1) == Process.myPid()) return
                if (intent.action == REQUEST) {
                    preferences.all.keys.filter { allowed(it) }.forEach(publish)
                    return
                }
                if (intent.action != ACTION) return
                val patch = runCatching { JSONObject(intent.getStringExtra("patch") ?: return) }.getOrNull() ?: return
                val key = patch.optString("key")
                if (!allowed(key)) return
                receiving = true
                try {
                    val editor = preferences.edit()
                    when (patch.optString("type")) {
                        "boolean" -> editor.putBoolean(key, patch.getBoolean("value"))
                        "int" -> editor.putInt(key, patch.getInt("value"))
                        "long" -> editor.putLong(key, patch.getLong("value"))
                        "float" -> editor.putFloat(key, patch.getDouble("value").toFloat())
                        "string" -> editor.putString(key, patch.getString("value"))
                        "set" -> patch.getJSONArray("value").let { values ->
                            editor.putStringSet(key, (0 until values.length()).map { values.getString(it) }.toSet())
                        }
                        "remove" -> editor.remove(key)
                        else -> return
                    }
                    editor.apply()
                } catch (_: Exception) {
                    // Malformed local patches leave the current preferences intact.
                } finally { receiving = false }
            }
        }
        ContextCompat.registerReceiver(app, receiver, IntentFilter(ACTION).apply { addAction(REQUEST) }, ContextCompat.RECEIVER_NOT_EXPORTED)
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (!receiving && key != null && allowed(key)) publish(key)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        connected = true
        app.sendBroadcast(Intent(REQUEST).setPackage(app.packageName).putExtra("origin", Process.myPid()))
    }

    private fun allowed(key: String) = key == "pin_mini_fps" || key == "mini_metrics_v1" ||
        key.startsWith("hud_") || key.startsWith("autoskip_")
}
