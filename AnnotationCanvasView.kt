package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.withSave
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

data class AnnotationStroke(
    val points: MutableList<PointF> = mutableListOf(),
    var color: Int = Color.RED,
    var strokeWidth: Float = 5f,
    var type: AnnotationType = AnnotationType.PEN,
    var text: String = "",
    var rect: RectF = RectF()
)

enum class AnnotationType {
    PEN, HIGHLIGHTER, ERASER, TEXT, RECTANGLE, CIRCLE, ARROW, SIGNATURE
}

class AnnotationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val strokes = mutableListOf<AnnotationStroke>()
    private var currentStroke: AnnotationStroke? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val eraserPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    var currentColor = Color.RED
    var currentStrokeWidth = 5f
    var currentType = AnnotationType.PEN
    var onAnnotationChanged: (() -> Unit)? = null

    private val path = Path()
    private val tempRect = RectF()

    init {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        textPaint.textSize = 40f
        textPaint.color = Color.BLACK

        eraserPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        eraserPaint.strokeWidth = 30f
        eraserPaint.strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.withSave {
            strokes.forEach { stroke ->
                when (stroke.type) {
                    AnnotationType.PEN, AnnotationType.SIGNATURE -> drawPenStroke(canvas, stroke)
                    AnnotationType.HIGHLIGHTER -> drawHighlighterStroke(canvas, stroke)
                    AnnotationType.ERASER -> {} // Eraser modifies other strokes, doesn't draw
                    AnnotationType.TEXT -> drawTextAnnotation(canvas, stroke)
                    AnnotationType.RECTANGLE -> drawRectangle(canvas, stroke)
                    AnnotationType.CIRCLE -> drawCircle(canvas, stroke)
                    AnnotationType.ARROW -> drawArrow(canvas, stroke)
                }
            }
            currentStroke?.let {
                when (it.type) {
                    AnnotationType.PEN, AnnotationType.SIGNATURE -> drawPenStroke(canvas, it)
                    AnnotationType.HIGHLIGHTER -> drawHighlighterStroke(canvas, it)
                    AnnotationType.RECTANGLE -> drawRectangle(canvas, it)
                    AnnotationType.CIRCLE -> drawCircle(canvas, it)
                    AnnotationType.ARROW -> drawArrow(canvas, it)
                    else -> {}
                }
            }
        }
    }

    private fun drawPenStroke(canvas: Canvas, stroke: AnnotationStroke) {
        if (stroke.points.size < 2) return
        paint.apply {
            color = stroke.color
            strokeWidth = stroke.strokeWidth
            alpha = 255
            style = Paint.Style.STROKE
        }
        path.reset()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            val prev = stroke.points[i - 1]
            val curr = stroke.points[i]
            val midX = (prev.x + curr.x) / 2f
            val midY = (prev.y + curr.y) / 2f
            path.quadTo(prev.x, prev.y, midX, midY)
        }
        if (stroke.points.size > 1) {
            val last = stroke.points.last()
            path.lineTo(last.x, last.y)
        }
        canvas.drawPath(path, paint)
    }

    private fun drawHighlighterStroke(canvas: Canvas, stroke: AnnotationStroke) {
        if (stroke.points.size < 2) return
        paint.apply {
            color = stroke.color
            strokeWidth = stroke.strokeWidth * 3
            alpha = 80
            style = Paint.Style.STROKE
        }
        path.reset()
        path.moveTo(stroke.points[0].x, stroke.points[0].y)
        for (i in 1 until stroke.points.size) {
            path.lineTo(stroke.points[i].x, stroke.points[i].y)
        }
        canvas.drawPath(path, paint)
        paint.alpha = 255
    }

    private fun drawTextAnnotation(canvas: Canvas, stroke: AnnotationStroke) {
        textPaint.apply {
            color = stroke.color
            textSize = stroke.strokeWidth * 8
        }
        val x = stroke.rect.left
        val y = stroke.rect.top + textPaint.textSize
        canvas.drawText(stroke.text, x, y, textPaint)

        // Draw text box border
        paint.apply {
            color = Color.GRAY
            strokeWidth = 1f
            style = Paint.Style.STROKE
            alpha = 100
        }
        canvas.drawRect(stroke.rect, paint)
        paint.alpha = 255
    }

    private fun drawRectangle(canvas: Canvas, stroke: AnnotationStroke) {
        if (stroke.points.size < 2) return
        val start = stroke.points.first()
        val end = stroke.points.last()
        tempRect.set(start.x, start.y, end.x, end.y)
        paint.apply {
            color = stroke.color
            strokeWidth = stroke.strokeWidth
            style = Paint.Style.STROKE
        }
        canvas.drawRect(tempRect, paint)
    }

    private fun drawCircle(canvas: Canvas, stroke: AnnotationStroke) {
        if (stroke.points.size < 2) return
        val start = stroke.points.first()
        val end = stroke.points.last()
        val centerX = (start.x + end.x) / 2f
        val centerY = (start.y + end.y) / 2f
        val radius = sqrt((end.x - start.x) * (end.x - start.x) + (end.y - start.y) * (end.y - start.y)) / 2f
        paint.apply {
            color = stroke.color
            strokeWidth = stroke.strokeWidth
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(centerX, centerY, radius, paint)
    }

    private fun drawArrow(canvas: Canvas, stroke: AnnotationStroke) {
        if (stroke.points.size < 2) return
        val start = stroke.points.first()
        val end = stroke.points.last()

        paint.apply {
            color = stroke.color
            strokeWidth = stroke.strokeWidth
            style = Paint.Style.STROKE
        }
        canvas.drawLine(start.x, start.y, end.x, end.y, paint)

        // Arrow head
        val angle = atan2(end.y - start.y, end.x - start.x)
        val arrowLength = 30f
        val arrowAngle = Math.PI / 6

        val x1 = end.x - arrowLength * kotlin.math.cos(angle - arrowAngle).toFloat()
        val y1 = end.y - arrowLength * kotlin.math.sin(angle - arrowAngle).toFloat()
        val x2 = end.x - arrowLength * kotlin.math.cos(angle + arrowAngle).toFloat()
        val y2 = end.y - arrowLength * kotlin.math.sin(angle + arrowAngle).toFloat()

        canvas.drawLine(end.x, end.y, x1, y1, paint)
        canvas.drawLine(end.x, end.y, x2, y2, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                when (currentType) {
                    AnnotationType.ERASER -> eraseAt(event.x, event.y)
                    AnnotationType.TEXT -> {
                        // Text is added via dialog, not touch
                    }
                    else -> {
                        currentStroke = AnnotationStroke(
                            color = currentColor,
                            strokeWidth = currentStrokeWidth,
                            type = currentType
                        ).apply {
                            points.add(PointF(event.x, event.y))
                            rect.set(event.x, event.y, event.x, event.y)
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                when (currentType) {
                    AnnotationType.ERASER -> eraseAt(event.x, event.y)
                    AnnotationType.TEXT -> {}
                    else -> {
                        currentStroke?.let { stroke ->
                            stroke.points.add(PointF(event.x, event.y))
                            stroke.rect.union(event.x, event.y)
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                currentStroke?.let { stroke ->
                    stroke.points.add(PointF(event.x, event.y))
                    strokes.add(stroke)
                    currentStroke = null
                    onAnnotationChanged?.invoke()
                }
            }
        }
        return true
    }

    private fun eraseAt(x: Float, y: Float) {
        val eraseRadius = 30f
        val iterator = strokes.iterator()
        var removed = false
        while (iterator.hasNext()) {
            val stroke = iterator.next()
            val nearPoint = stroke.points.any { point ->
                abs(point.x - x) < eraseRadius && abs(point.y - y) < eraseRadius
            }
            if (nearPoint) {
                iterator.remove()
                removed = true
            }
        }
        if (removed) {
            invalidate()
            onAnnotationChanged?.invoke()
        }
    }

    fun addTextAnnotation(text: String, x: Float, y: Float) {
        val stroke = AnnotationStroke(
            color = currentColor,
            strokeWidth = currentStrokeWidth,
            type = AnnotationType.TEXT,
            text = text
        ).apply {
            rect.set(x, y, x + text.length * textPaint.textSize * 0.6f, y + textPaint.textSize * 1.2f)
        }
        strokes.add(stroke)
        invalidate()
        onAnnotationChanged?.invoke()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            strokes.removeAt(strokes.size - 1)
            invalidate()
            onAnnotationChanged?.invoke()
        }
    }

    fun clear() {
        strokes.clear()
        invalidate()
        onAnnotationChanged?.invoke()
    }

    fun hasAnnotations(): Boolean = strokes.isNotEmpty()

    fun getAnnotationsBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        draw(canvas)
        return bitmap
    }

    fun setTool(type: AnnotationType, color: Int, strokeWidth: Float) {
        currentType = type
        currentColor = color
        currentStrokeWidth = strokeWidth
    }
}
