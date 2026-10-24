package com.noir.lynx.freeform

import android.graphics.*
import android.graphics.drawable.Drawable

internal enum class FloatingGlyph { CLOSE, MINIMIZE, BACK, ROTATE, MAXIMIZE, RESIZE, MORE, TOOLBAR }
internal class FloatingLineIcon(private val glyph: FloatingGlyph, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 2.0f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.FILL
    }
    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
        when (glyph) {
            FloatingGlyph.MORE -> {
                canvas.drawCircle(12f, 5.5f, 1.7f, dotPaint)
                canvas.drawCircle(12f, 12f, 1.7f, dotPaint)
                canvas.drawCircle(12f, 18.5f, 1.7f, dotPaint)
            }
            FloatingGlyph.CLOSE -> { line(6.5f, 6.5f, 17.5f, 17.5f); line(17.5f, 6.5f, 6.5f, 17.5f) }
            FloatingGlyph.MINIMIZE -> line(6f, 15.5f, 18f, 15.5f)
            FloatingGlyph.BACK -> { line(14.5f, 5.5f, 8f, 12f); line(8f, 12f, 14.5f, 18.5f) }
            FloatingGlyph.MAXIMIZE -> canvas.drawRoundRect(4.5f, 5.5f, 19.5f, 18.5f, 3.5f, 3.5f, paint)
            FloatingGlyph.RESIZE -> {
                line(19f, 10f, 19f, 19f)
                line(10f, 19f, 19f, 19f)
                line(14f, 14f, 18.5f, 18.5f)
            }
            FloatingGlyph.ROTATE -> {
                canvas.drawArc(4.5f, 4.5f, 19.5f, 19.5f, 35f, 270f, false, paint)
                line(18f, 3.5f, 18f, 8.5f); line(18f, 8.5f, 13f, 8.5f)
            }
            FloatingGlyph.TOOLBAR -> {
                canvas.drawRoundRect(4f, 5f, 20f, 19f, 3.5f, 3.5f, paint)
                line(4.5f, 14.5f, 19.5f, 14.5f)
            }
        }
        canvas.restoreToCount(checkpoint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; dotPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; dotPaint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Drawable contract") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
}
