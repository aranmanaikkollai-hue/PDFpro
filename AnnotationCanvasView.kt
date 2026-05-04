package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom canvas view for PDF annotations.
 * Supports freehand drawing, shapes, highlights, and eraser.
 */
class AnnotationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val path = Path()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#007AFF")
        strokeWidth = 5f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private var currentPath = mutableListOf<PointF>()
    private val allPaths = mutableListOf<StrokeData>()

    // Changed from private to internal so AnnotatedPageView can read it
    internal var currentTool: String? = null
        private set

    private var currentColor = Color.parseColor("#007AFF")
    private var currentStrokeWidth = 5f
    private var isDrawing = false
    private var startPoint: PointF? = null

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null

    data class StrokeData(
        val points: List<PointF>,
        val color: Int,
        val strokeWidth: Float,
        val tool: String,
        val alpha: Int = 255
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setWillNotDraw(false)
    }

    override fun onSizeChanged(w: Int, h: Int, oldW: Int, oldH: Int) {
        super.onSizeChanged(w, h, oldW, oldH)
        if (w > 0 && h > 0) {
            bitmap?.recycle()
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bitmapCanvas = Canvas(bitmap!!)
            bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }
    }

    override fun onDraw(canvas: Canvas) {
        bitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        if (isDrawing && currentPath.isNotEmpty()) {
            drawPath(canvas, currentPath, currentColor, currentStrokeWidth, currentTool ?: "freehand")
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tool = currentTool ?: return false
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                currentPath.clear()
                currentPath.add(PointF(x, y))
                startPoint = PointF(x, y)
                path.moveTo(x, y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (tool in listOf("rectangle", "circle", "arrow")) {
                    currentPath.clear()
                    currentPath.add(startPoint!!)
                    currentPath.add(PointF(x, y))
                } else {
                    currentPath.add(PointF(x, y))
                    path.lineTo(x, y)
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                isDrawing = false
                currentPath.add(PointF(x, y))
                val stroke = StrokeData(
                    points = currentPath.toList(),
                    color = currentColor,
                    strokeWidth = currentStrokeWidth,
                    tool = tool
                )
                allPaths.add(stroke)
                drawStrokeToBitmap(stroke)
                currentPath.clear()
                path.reset()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun drawPath(canvas: Canvas, points: List<PointF>, color: Int, width: Float, tool: String) {
        when (tool) {
            "highlight" -> {
                if (points.size >= 2) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        alpha = 128
                        style = Paint.Style.FILL
                    }
                    val hp = Path()
                    hp.moveTo(points[0].x, points[0].y - 10f)
                    for (i in 1 until points.size) hp.lineTo(points[i].x, points[i].y - 10f)
                    for (i in points.size - 1 downTo 0) hp.lineTo(points[i].x, points[i].y + 10f)
                    hp.close()
                    canvas.drawPath(hp, p)
                }
            }
            "rectangle" -> {
                if (points.size >= 2) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        this.strokeWidth = width
                        style = Paint.Style.STROKE
                    }
                    canvas.drawRect(RectF(points[0].x, points[0].y, points[1].x, points[1].y), p)
                }
            }
            "circle" -> {
                if (points.size >= 2) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        this.strokeWidth = width
                        style = Paint.Style.STROKE
                    }
                    val cx = (points[0].x + points[1].x) / 2f
                    val cy = (points[0].y + points[1].y) / 2f
                    val radius = kotlin.math.hypot(
                        (points[1].x - points[0].x).toDouble(),
                        (points[1].y - points[0].y).toDouble()
                    ).toFloat() / 2f
                    canvas.drawCircle(cx, cy, radius, p)
                }
            }
            "arrow" -> {
                if (points.size >= 2) {
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = color
                        this.strokeWidth = width
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                    }
                    canvas.drawLine(points[0].x, points[0].y, points[1].x, points[1].y, p)
                    val angle = kotlin.math.atan2(
                        (points[1].y - points[0].y).toDouble(),
                        (points[1].x - points[0].x).toDouble()
                    )
                    val arrowLen = 20f
                    val arrowAngle = 0.5
                    val x1 = points[1].x - arrowLen * kotlin.math.cos(angle - arrowAngle).toFloat()
                    val y1 = points[1].y - arrowLen * kotlin.math.sin(angle - arrowAngle).toFloat()
                    val x2 = points[1].x - arrowLen * kotlin.math.cos(angle + arrowAngle).toFloat()
                    val y2 = points[1].y - arrowLen * kotlin.math.sin(angle + arrowAngle).toFloat()
                    canvas.drawLine(points[1].x, points[1].y, x1, y1, p)
                    canvas.drawLine(points[1].x, points[1].y, x2, y2, p)
                }
            }
            "eraser" -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                    this.strokeWidth = width * 3
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                }
                if (points.size >= 2) {
                    val ep = Path()
                    ep.moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) ep.lineTo(points[i].x, points[i].y)
                    canvas.drawPath(ep, p)
                }
            }
            else -> {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    this.strokeWidth = width
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                if (points.size >= 2) {
                    val fp = Path()
                    fp.moveTo(points[0].x, points[0].y)
                    for (i in 1 until points.size) fp.lineTo(points[i].x, points[i].y)
                    canvas.drawPath(fp, p)
                }
            }
        }
    }

    private fun drawStrokeToBitmap(stroke: StrokeData) {
        bitmapCanvas?.let { canvas ->
            drawPath(canvas, stroke.points, stroke.color, stroke.strokeWidth, stroke.tool)
        }
    }

    fun setTool(tool: String) {
        currentTool = tool
    }

    fun clearTool() {
        currentTool = null
    }

    fun setColor(color: Int) {
        currentColor = color
        paint.color = color
    }

    fun setStrokeWidth(width: Float) {
        currentStrokeWidth = width.coerceAtLeast(1f)
        paint.strokeWidth = currentStrokeWidth
    }

    fun clear() {
        allPaths.clear()
        currentPath.clear()
        path.reset()
        bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        invalidate()
    }

    fun undo(): Boolean {
        if (allPaths.isEmpty()) return false
        allPaths.removeAt(allPaths.size - 1)
        redrawAll()
        return true
    }

    fun getStrokes(): List<StrokeData> = allPaths.toList()

    private fun redrawAll() {
        bitmapCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        allPaths.forEach { drawStrokeToBitmap(it) }
        invalidate()
    }

    fun getBitmap(): Bitmap? {
        return bitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }
}
