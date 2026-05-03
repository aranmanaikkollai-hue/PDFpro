package com.propdf.editor.ui.viewer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.propdf.editor.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RecyclerView adapter for PDF pages with lazy loading.
 * Supports annotations overlay and zoom/pan gestures.
 */
class PdfPageAdapter(
    private val renderer: PdfRenderer,
    private val scope: CoroutineScope,
    private val screenWidth: Int
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private val pageCache = android.util.LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(8 * 1024)
    ) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount / 1024
    }

    private val pageAnnotations = mutableMapOf<Int, MutableList<AnnotationData>>()
    private val pageScaleMap = mutableMapOf<Int, Float>()

    data class AnnotationData(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: String,
        val points: List<PointF>,
        val color: Int,
        val strokeWidth: Float,
        val text: String? = null
    )

    override fun getItemCount(): Int = renderer.pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pdf_page, parent, false)
        return PageViewHolder(view)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun onViewRecycled(holder: PageViewHolder) {
        holder.recycle()
    }

    fun addAnnotation(pageIndex: Int, annotation: AnnotationData) {
        val list = pageAnnotations.getOrPut(pageIndex) { mutableListOf() }
        list.add(annotation)
        notifyItemChanged(pageIndex)
    }

    fun getAnnotations(pageIndex: Int): List<AnnotationData> {
        return pageAnnotations[pageIndex] ?: emptyList()
    }

    fun clearAnnotations(pageIndex: Int) {
        pageAnnotations.remove(pageIndex)
        notifyItemChanged(pageIndex)
    }

    fun clearAllAnnotations() {
        pageAnnotations.clear()
        notifyDataSetChanged()
    }

    fun getPageScale(pageIndex: Int): Float {
        return pageScaleMap[pageIndex] ?: 1f
    }

    inner class PageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.pageImage)
        private val pageNumberText: TextView = itemView.findViewById(R.id.pageNumber)
        private var currentBitmap: Bitmap? = null
        private var currentPage = -1

        fun bind(pageIndex: Int) {
            currentPage = pageIndex
            pageNumberText.text = "Page ${pageIndex + 1}"

            // Check cache first
            val cached = pageCache.get(pageIndex)
            if (cached != null && !cached.isRecycled) {
                displayBitmap(cached, pageIndex)
                return
            }

            // Load in background
            imageView.setImageBitmap(null)
            imageView.setBackgroundColor(Color.parseColor("#2A2A2A"))

            scope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    renderPage(pageIndex)
                }
                if (bitmap != null && currentPage == pageIndex) {
                    withContext(Dispatchers.Main) {
                        displayBitmap(bitmap, pageIndex)
                    }
                }
            }
        }

        private fun renderPage(pageIndex: Int): Bitmap? {
            return try {
                synchronized(renderer) {
                    val page = renderer.openPage(pageIndex)
                    val scale = screenWidth.toFloat() / page.width.coerceAtLeast(1)
                    val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                    val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    pageCache.put(pageIndex, bmp)
                    pageScaleMap[pageIndex] = scale
                    bmp
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun displayBitmap(bitmap: Bitmap, pageIndex: Int) {
            currentBitmap?.takeIf { it != bitmap && !it.isRecycled }?.recycle()
            currentBitmap = bitmap

            // Draw annotations on top
            val annotatedBitmap = drawAnnotations(bitmap, pageIndex)
            imageView.setImageBitmap(annotatedBitmap)
            imageView.setBackgroundColor(Color.TRANSPARENT)
        }

        private fun drawAnnotations(baseBitmap: Bitmap, pageIndex: Int): Bitmap {
            val annotations = pageAnnotations[pageIndex] ?: return baseBitmap
            val result = baseBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(result)

            annotations.forEach { ann ->
                when (ann.type) {
                    "highlight" -> drawHighlight(canvas, ann)
                    "underline" -> drawUnderline(canvas, ann)
                    "freehand", "rectangle", "circle", "arrow" -> drawStroke(canvas, ann)
                    "text" -> drawText(canvas, ann)
                }
            }

            return result
        }

        private fun drawStroke(canvas: Canvas, ann: AnnotationData) {
            if (ann.points.size < 2) return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ann.color
                strokeWidth = ann.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

            val path = android.graphics.Path()
            path.moveTo(ann.points[0].x, ann.points[0].y)
            for (i in 1 until ann.points.size) {
                path.lineTo(ann.points[i].x, ann.points[i].y)
            }
            canvas.drawPath(path, paint)
        }

        private fun drawHighlight(canvas: Canvas, ann: AnnotationData) {
            if (ann.points.size < 2) return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ann.color
                alpha = 128
                style = Paint.Style.FILL
            }
            val path = android.graphics.Path()
            path.moveTo(ann.points[0].x, ann.points[0].y - 10f)
            for (i in 1 until ann.points.size) {
                path.lineTo(ann.points[i].x, ann.points[i].y - 10f)
            }
            for (i in ann.points.size - 1 downTo 0) {
                path.lineTo(ann.points[i].x, ann.points[i].y + 10f)
            }
            path.close()
            canvas.drawPath(path, paint)
        }

        private fun drawUnderline(canvas: Canvas, ann: AnnotationData) {
            if (ann.points.size < 2) return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ann.color
                strokeWidth = ann.strokeWidth
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
            }
            val path = android.graphics.Path()
            path.moveTo(ann.points[0].x, ann.points[0].y)
            for (i in 1 until ann.points.size) {
                path.lineTo(ann.points[i].x, ann.points[i].y)
            }
            canvas.drawPath(path, paint)
        }

        private fun drawText(canvas: Canvas, ann: AnnotationData) {
            val text = ann.text ?: return
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ann.color
                textSize = ann.strokeWidth * 3
                typeface = Typeface.DEFAULT
            }
            if (ann.points.isNotEmpty()) {
                canvas.drawText(text, ann.points[0].x, ann.points[0].y, paint)
            }
        }

        fun recycle() {
            currentBitmap?.takeIf { !it.isRecycled }?.recycle()
            currentBitmap = null
            imageView.setImageBitmap(null)
        }
    }
}
