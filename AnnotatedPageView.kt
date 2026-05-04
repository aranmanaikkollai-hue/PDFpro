package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * Custom page view that combines PDF page rendering with annotation overlay.
 * Supports zoom, pan, and annotation drawing.
 */
class AnnotatedPageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : FrameLayout(context, attrs, defStyle) {

    private val pageImageView = ImageView(context).apply {
        layoutParams = LayoutParams(-1, -1)
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val annotationCanvas = AnnotationCanvasView(context).apply {
        layoutParams = LayoutParams(-1, -1)
        setBackgroundColor(Color.TRANSPARENT)
    }

    private var scaleGestureDetector: ScaleGestureDetector
    private var gestureDetector: GestureDetector

    private var currentScale = 1f
    private val maxScale = 5f
    private val minScale = 1f
    private var isZoomed = false
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    var onTapListener: (() -> Unit)? = null
    var onScaleChanged: ((Float) -> Unit)? = null

    init {
        addView(pageImageView)
        addView(annotationCanvas)

        scaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    currentScale = (currentScale * detector.scaleFactor).coerceIn(minScale, maxScale)
                    isZoomed = currentScale > 1.1f
                    applyScale()
                    onScaleChanged?.invoke(currentScale)
                    return true
                }
            }
        )

        gestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (isZoomed) {
                        currentScale = 1f
                        panX = 0f
                        panY = 0f
                        isZoomed = false
                    } else {
                        currentScale = 2.5f
                        isZoomed = true
                    }
                    applyScale()
                    onScaleChanged?.invoke(currentScale)
                    return true
                }

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    onTapListener?.invoke()
                    return true
                }
            }
        )
    }

    fun setPageBitmap(bitmap: Bitmap?) {
        pageImageView.setImageBitmap(bitmap)
    }

    fun getAnnotationCanvas(): AnnotationCanvasView = annotationCanvas

    fun setTool(tool: String) {
        annotationCanvas.setTool(tool)
    }

    fun clearTool() {
        annotationCanvas.clearTool()
    }

    fun setAnnotationColor(color: Int) {
        annotationCanvas.setColor(color)
    }

    fun setStrokeWidth(width: Float) {
        annotationCanvas.setStrokeWidth(width)
    }

    fun clearAnnotations() {
        annotationCanvas.clear()
    }

    fun undoAnnotation(): Boolean {
        return annotationCanvas.undo()
    }

    fun getAnnotations(): List<AnnotationCanvasView.StrokeData> {
        return annotationCanvas.getStrokes()
    }

    fun getAnnotationBitmap(): Bitmap? {
        return annotationCanvas.getBitmap()
    }

    fun resetZoom() {
        currentScale = 1f
        panX = 0f
        panY = 0f
        isZoomed = false
        applyScale()
    }

    fun getCurrentScale(): Float = currentScale

    private fun applyScale() {
        pageImageView.scaleX = currentScale
        pageImageView.scaleY = currentScale
        annotationCanvas.scaleX = currentScale
        annotationCanvas.scaleY = currentScale
        pageImageView.translationX = panX
        pageImageView.translationY = panY
        annotationCanvas.translationX = panX
        annotationCanvas.translationY = panY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                if (isZoomed) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (isZoomed && event.pointerCount == 1) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    panX += dx
                    panY += dy
                    lastTouchX = event.x
                    lastTouchY = event.y
                    applyScale()
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        // Forward to annotation canvas only when a tool is active
        if (annotationCanvas.currentTool != null) {
            annotationCanvas.onTouchEvent(event)
        }

        return true
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return isZoomed && ev.pointerCount > 1
    }
}
