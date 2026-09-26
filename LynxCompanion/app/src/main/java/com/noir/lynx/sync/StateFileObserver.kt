package com.noir.lynx.sync

import android.os.FileObserver
import java.io.File

/**
 * StateFileObserver: Watches /data/adb/modules/Lynx/ for config.json changes
 * via Linux inotify (CLOSE_WRITE | MOVED_TO events).
 *
 * This enables real-time 2-way sync: when Smart-AI.sh or WebUI writes config.json,
 * this observer fires and the Native APK immediately re-reads state without polling.
 *
 * Mirrors the server-side `inotifyd` watcher in service.sh.
 */
class StateFileObserver(
    private val configPath: String = "/data/adb/modules/Lynx/config.json",
    private val onStateChanged: () -> Unit
) : FileObserver(
    File(configPath).parentFile ?: File("/data/adb/modules/Lynx"),
    CLOSE_WRITE or MOVED_TO
) {

    private val targetFileName = File(configPath).name

    override fun onEvent(event: Int, path: String?) {
        // Only react to changes on config.json itself, not other files in the dir
        if (path != null && path == targetFileName) {
            onStateChanged()
        }
    }
}
