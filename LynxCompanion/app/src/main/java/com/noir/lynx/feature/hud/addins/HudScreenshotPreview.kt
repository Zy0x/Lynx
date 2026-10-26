package com.noir.lynx.feature.hud.addins

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.Keep
import com.noir.lynx.R
import com.noir.lynx.core.locale.AppLocale
import kotlin.math.abs

/**
 * Floating screenshot preview card.
 * Appears briefly (3.5s) in a screen corner with thumbnail preview,
 * "Open" action button, and swipe/tap-to-dismiss behavior.
 */
@Keep
class HudScreenshotPreview(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val windows = context.getSystemService(WindowManager::class.java)
    private var activeView: View? = null

    private val dismissRunnable: Runnable = Runnable { dismiss() }

    fun show(uri: Uri) {
        main.post {
            dismissImmediate()

        val density = context.resources.displayMetrics.density
        val cardWidth = (110 * density).toInt()
        val cardHeight = (150 * density).toInt()

        val params = WindowManager.LayoutParams(
            cardWidth,
            cardHeight,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            x = (16 * density).toInt()
            y = (24 * density).toInt()
            setTitle("Lynx screenshot preview")
        }

        // Root container card with Obsidian Cyberpunk styling
        val root = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                setColor(0xF012141C.toInt()) // Deep OLED obsidian
                cornerRadius = 14 * density
                setStroke((1.5f * density).toInt().coerceAtLeast(1), 0xFF00FFAA.toInt()) // Neon Cyan border
            }
            background = bg
            elevation = 12 * density
            clipToOutline = true
        }

        // Thumbnail image
        val thumbnailView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(thumbnailView)

        // Asynchronously decode thumbnail
        loadThumbnailAsync(uri) { bmp ->
            if (bmp != null && activeView == root) {
                thumbnailView.setImageBitmap(bmp)
            }
        }

        // Bottom action bar overlay (semi-transparent gradient)
        val overlayBar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            val barBg = GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xEE0A0A0A.toInt(), 0x88000000.toInt(), 0x00000000)
            )
            background = barBg
            val barParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            layoutParams = barParams
            setPadding((6 * density).toInt(), (14 * density).toInt(), (6 * density).toInt(), (6 * density).toInt())
        }

        // Saved label
        val label = TextView(context).apply {
            text = AppLocale.getString(context, R.string.hud_screenshot_saved)
            textSize = 10f
            setTextColor(0xFF00FFAA.toInt())
            gravity = Gravity.CENTER
            setSingleLine(true)
        }
        overlayBar.addView(label)

        // Open Button (Material 3 Cyberpunk pill)
        val openBtn = TextView(context).apply {
            text = AppLocale.getString(context, R.string.hud_screenshot_open)
            textSize = 11f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            val btnBg = GradientDrawable().apply {
                setColor(0xCC00553A.toInt())
                cornerRadius = 10 * density
                setStroke((1 * density).toInt(), 0xFF00FFAA.toInt())
            }
            background = btnBg
            val btnParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (28 * density).toInt()
            ).apply {
                topMargin = (4 * density).toInt()
            }
            layoutParams = btnParams
            setOnClickListener {
                openImageInGallery(uri)
                dismiss()
            }
        }
        overlayBar.addView(openBtn)
        root.addView(overlayBar)

        // Gesture handling: Tap or swipe to dismiss
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var moved = false
        root.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    moved = false
                    main.removeCallbacks(dismissRunnable)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) {
                        moved = true
                        root.translationX = dx
                        root.alpha = (1f - (abs(dx) / (cardWidth * 1.5f))).coerceIn(0.2f, 1f)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved && abs(event.rawX - downX) > cardWidth * 0.35f) {
                        dismiss()
                    } else if (!moved) {
                        openImageInGallery(uri)
                        dismiss()
                    } else {
                        root.animate().translationX(0f).alpha(1f).setDuration(150).start()
                        main.postDelayed(dismissRunnable, 2500)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    root.animate().translationX(0f).alpha(1f).setDuration(150).start()
                    main.postDelayed(dismissRunnable, 2500)
                    true
                }
                else -> false
            }
        }

        // Smooth entry animation: slide in from left & fade in
        root.alpha = 0f
        root.translationX = -(cardWidth.toFloat())

        val added = runCatching {
            windows.addView(root, params)
            activeView = root
            root.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(220)
                .start()
            true
        }.getOrDefault(false)

        if (added) {
            main.postDelayed(dismissRunnable, 3500)
        }
    }
}

    private fun openImageInGallery(uri: Uri) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "image/png")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun loadThumbnailAsync(uri: Uri, callback: (Bitmap?) -> Unit) {
        Thread {
            val bmp = runCatching {
                val density = context.resources.displayMetrics.density
                val targetW = (120 * density).toInt()
                val targetH = (160 * density).toInt()
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)
                    var sample = 1
                    while (opts.outWidth / (sample * 2) >= targetW && opts.outHeight / (sample * 2) >= targetH) {
                        sample *= 2
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream2 ->
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                        BitmapFactory.decodeStream(stream2, null, decodeOpts)
                    }
                }
            }.getOrNull()
            main.post { callback(bmp) }
        }.start()
    }

    fun dismiss() {
        main.post {
            val v = activeView ?: return@post
            main.removeCallbacks(dismissRunnable)
            v.animate()
                .alpha(0f)
                .translationX(-(v.width.toFloat()))
                .setDuration(180)
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        dismissImmediate()
                    }
                })
                .start()
        }
    }

    private fun dismissImmediate() {
        main.removeCallbacks(dismissRunnable)
        activeView?.let { v ->
            runCatching { windows.removeView(v) }
        }
        activeView = null
    }

    fun close() {
        main.post { dismissImmediate() }
    }
}
