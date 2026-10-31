package com.noir.lynx.feature.hud.data

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.util.LruCache
import com.noir.lynx.feature.hud.HudApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** High-performance cached launcher apps catalog optimized for eMMC and low-end devices. */
class HudAppCatalog(private val context: Context) {

    companion object {
        @Volatile
        private var memoryCache: List<HudApp>? = null
        private val iconCache = LruCache<String, Bitmap>(200)
        private val labelCache = ConcurrentHashMap<String, String>()

        fun getCachedLabel(pkg: String): String? = labelCache[pkg]
        fun putCachedLabel(pkg: String, label: String) { labelCache[pkg] = label }

        fun invalidateCache() {
            memoryCache = null
        }
    }

    private val appContext = context.applicationContext ?: context

    val apps: Flow<List<HudApp>> = callbackFlow {
        // 1. Emit existing memory cache immediately if available (0 ms latency)
        memoryCache?.let { trySend(it) }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                invalidateCache()
                val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
                scope.async {
                    val updated = load()
                    trySend(updated)
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) appContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else appContext.registerReceiver(receiver, filter)

        // Perform load if memoryCache was null
        if (memoryCache == null) {
            withContext(Dispatchers.IO) {
                val loaded = load()
                trySend(loaded)
            }
        }

        awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
    }

    @Suppress("DEPRECATION")
    fun loadTargetPackages(targetPackages: List<String>): List<HudApp> {
        val pm = appContext.packageManager
        val distinctTargets = targetPackages.distinct().filter { it != appContext.packageName }

        // Fast path: if memoryCache exists, extract directly
        val cached = memoryCache
        if (cached != null) {
            val byPkg = cached.associateBy { it.packageName }
            val resolved = distinctTargets.mapNotNull { byPkg[it] }
            if (resolved.size == distinctTargets.size) return resolved
        }

        return distinctTargets.mapNotNull { pkg ->
            runCatching {
                val info = pm.getApplicationInfo(pkg, 0)
                if (!info.enabled) return@runCatching null

                val label = labelCache[pkg] ?: pm.getApplicationLabel(info).toString().also { labelCache[pkg] = it }
                val bitmap = iconCache.get(pkg) ?: run {
                    val icon = pm.getApplicationIcon(info)
                    val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                    icon.setBounds(0, 0, 48, 48)
                    icon.draw(Canvas(bmp))
                    iconCache.put(pkg, bmp)
                    bmp
                }
                HudApp(pkg, label, bitmap)
            }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private suspend fun load(): List<HudApp> = withContext(Dispatchers.IO) {
        memoryCache?.let { return@withContext it }

        val pm = appContext.packageManager
        val resolveInfos = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        }.getOrDefault(emptyList())

        val candidates = resolveInfos.filter {
            val pkg = it.activityInfo?.packageName
            pkg != null && pkg != appContext.packageName && (it.activityInfo?.applicationInfo?.enabled != false)
        }

        // Process apps in parallel using chunks
        val result = candidates.chunked(20).flatMap { chunk ->
            chunk.map { resolveInfo ->
                async {
                    runCatching {
                        val pkg = resolveInfo.activityInfo.packageName
                        val label = labelCache[pkg] ?: resolveInfo.loadLabel(pm).toString().also { labelCache[pkg] = it }

                        val bitmap = iconCache.get(pkg) ?: run {
                            val icon = resolveInfo.loadIcon(pm)
                            val bmp = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
                            icon.setBounds(0, 0, 48, 48)
                            icon.draw(Canvas(bmp))
                            iconCache.put(pkg, bmp)
                            bmp
                        }
                        HudApp(pkg, label, bitmap)
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })

        memoryCache = result
        result
    }
}
