package com.noir.lynx.freeform

import android.graphics.*
import android.graphics.drawable.Drawable

internal enum class FloatingGlyph { CLOSE, MINIMIZE, BACK, ROTATE, MAXIMIZE, RESIZE, MORE }
internal class FloatingLineIcon(private val glyph: FloatingGlyph, color: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeCap = Paint.Cap.ROUND
    }
    override fun draw(canvas: Canvas) {
        val checkpoint = canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 24f, bounds.height() / 24f)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = canvas.drawLine(x1, y1, x2, y2, paint)
        when (glyph) {
            FloatingGlyph.MORE -> { canvas.drawCircle(12f, 5f, 1f, paint); canvas.drawCircle(12f, 12f, 1f, paint); canvas.drawCircle(12f, 19f, 1f, paint) }
            FloatingGlyph.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            FloatingGlyph.MINIMIZE -> line(5f, 16f, 19f, 16f)
            FloatingGlyph.BACK -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
            FloatingGlyph.MAXIMIZE -> canvas.drawRoundRect(4f, 5f, 20f, 19f, 2f, 2f, paint)
            FloatingGlyph.RESIZE -> { line(6f, 19f, 19f, 6f); line(12f, 19f, 19f, 12f); line(18f, 19f, 19f, 18f) }
            FloatingGlyph.ROTATE -> {
                canvas.drawArc(4f, 4f, 20f, 20f, 40f, 275f, false, paint)
                line(18f, 3f, 18f, 8f); line(18f, 8f, 13f, 8f)
            }
        }
        canvas.restoreToCount(checkpoint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
    @Deprecated("Drawable contract") override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
}
