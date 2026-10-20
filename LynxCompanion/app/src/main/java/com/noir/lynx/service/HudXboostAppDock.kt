package com.noir.lynx.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import com.noir.lynx.core.locale.LynxText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuickAppItem(
    val pkg: String,
    val label: String,
    val iconBitmap: Bitmap? = null,
    val fallbackColor: Color = Color(0xFF2196F3)
)

/**
 * HudXboostAppDock — Horizontal floating app strip at the top of Infinix XBOOST.
 * Launches detected messaging, utility, and browser apps in freeform floating window mode over the game.
 */
@Composable
fun HudXboostAppDock(
    modifier: Modifier = Modifier,
    onAppLaunched: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var appList by remember { mutableStateOf<List<QuickAppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val targetPackages = listOf(
                "com.android.chrome" to ("Chrome" to Color(0xFF4CAF50)),
                "com.whatsapp" to ("WhatsApp" to Color(0xFF25D366)),
                "com.facebook.katana" to ("Facebook" to Color(0xFF1877F2)),
                "com.facebook.lite" to ("Facebook" to Color(0xFF1877F2)),
                "com.google.android.youtube" to ("YouTube" to Color(0xFFFF0000)),
                "com.google.android.calculator" to ("Kalkulator" to Color(0xFFFF9800)),
                "com.miui.calculator" to ("Kalkulator" to Color(0xFFFF9800)),
                "com.transsion.calculator" to ("Kalkulator" to Color(0xFFFF9800)),
                "com.android.calculator2" to ("Kalkulator" to Color(0xFFFF9800)),
                "com.zhiliaoapp.musically" to ("TikTok" to Color(0xFF000000)),
                "com.discord" to ("Discord" to Color(0xFF5865F2)),
                "org.telegram.messenger" to ("Telegram" to Color(0xFF0088CC))
            )

            val resolved = mutableListOf<QuickAppItem>()
            val addedKeys = mutableSetOf<String>()

            for ((pkg, info) in targetPackages) {
                val (label, fallbackColor) = info
                if (addedKeys.contains(label)) continue
                try {
                    val appInfo = pm.getApplicationInfo(pkg, 0)
                    val iconDrawable = pm.getApplicationIcon(appInfo)
                    val bmp = drawableToBitmap(iconDrawable)
                    resolved.add(QuickAppItem(pkg, label, bmp, fallbackColor))
                    addedKeys.add(label)
                } catch (_: PackageManager.NameNotFoundException) {}
            }

            // Fallback default list if few or none detected
            if (resolved.isEmpty()) {
                resolved.add(QuickAppItem("com.android.chrome", "Chrome", fallbackColor = Color(0xFF4CAF50)))
                resolved.add(QuickAppItem("com.whatsapp", "WhatsApp", fallbackColor = Color(0xFF25D366)))
                resolved.add(QuickAppItem("com.google.android.youtube", "YouTube", fallbackColor = Color(0xFFFF0000)))
                resolved.add(QuickAppItem("com.google.android.calculator", "Kalkulator", fallbackColor = Color(0xFFFF9800)))
            }
            appList = resolved
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        appList.take(7).forEach { app ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0x33141F2E),
                border = BorderStroke(1.dp, Color(0x22FFFFFF)),
                modifier = Modifier
                    .size(38.dp)
                    .clickable {
                        onAppLaunched()
                        coroutineScope.launch(Dispatchers.IO) {
                            launchInFreeform(context, app.pkg)
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (app.iconBitmap != null) {
                        Image(
                            bitmap = app.iconBitmap.asImageBitmap(),
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(app.fallbackColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = app.label.take(1),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun launchInFreeform(context: Context, packageName: String) {
    try {
        // Attempt root freeform windowing mode (WindowingMode 5 = Freeform)
        val resolveCmd = "cmd package resolve-activity --brief $packageName 2>/dev/null | tail -n 1"
        val resolved = Shell.cmd(resolveCmd).exec().out.joinToString("").trim()
        if (resolved.isNotEmpty() && resolved.contains("/")) {
            val freeformResult = Shell.cmd("am start -n $resolved --windowingMode 5 2>/dev/null").exec()
            if (freeformResult.isSuccess) return
        }

        // Fallback to standard launch intent
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            Shell.cmd("monkey -p $packageName -c android.intent.category.LAUNCHER 1 2>/dev/null").exec()
        }
    } catch (_: Exception) {}
}

private fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val w = drawable.intrinsicWidth.coerceAtLeast(64)
    val h = drawable.intrinsicHeight.coerceAtLeast(64)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bmp
}
