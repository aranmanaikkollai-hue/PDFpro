package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom view for drawing digital signatures.
 * Supports smooth bezier curves and exports as transparent PNG bitmap.
 */
class SignatureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        strokeWidth = 4f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var lastX = 0f
    private var lastY = 0f
    private var hasSignature = false

    private var cacheBmp: Bitmap? = null
    private var cacheCvs: Canvas? = null

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w > 0 && h > 0) {
            cacheBmp?.recycle()
            cacheBmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            cacheCvs = Canvas(cacheBmp!!)
            cacheCvs?.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        }
    }

    override fun onDraw(canvas: Canvas) {
        cacheBmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                path.moveTo(x, y)
                lastX = x
                lastY = y
                hasSignature = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val mx = (lastX + x) / 2f
                val my = (lastY + y) / 2f
                path.quadTo(lastX, lastY, mx, my)
                lastX = x
                lastY = y
                cacheCvs?.drawPath(path, paint)
                path.reset()
                path.moveTo(mx, my)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                path.lineTo(x, y)
                cacheCvs?.drawPath(path, paint)
                path.reset()
                invalidate()
                return true
            }
        }
        return false
    }

    fun clear() {
        path.reset()
        hasSignature = false
        cacheCvs?.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        invalidate()
    }

    fun hasSignature(): Boolean = hasSignature

    fun getSignatureBitmap(): Bitmap? {
        return cacheBmp?.copy(Bitmap.Config.ARGB_8888, false)
    }

    fun setStrokeColor(color: Int) {
        paint.color = color
    }

    fun setStrokeWidth(width: Float) {
        paint.strokeWidth = width.coerceAtLeast(1f)
    }
}
