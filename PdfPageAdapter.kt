package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.collection.LruCache

/**
 * RecyclerView adapter that renders PDF pages using PdfRenderer.
 * Each page is rendered to a Bitmap with white background (prevents black pages).
 */
class PdfPageAdapter(
    private val context: Context,
    private val pdfUri: Uri
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private var pageCount = 0
    private var pdfRenderer: PdfRenderer? = null

    // LruCache: key = page index, value = Bitmap
    private val bitmapCache: LruCache<Int, Bitmap>

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8
        bitmapCache = LruCache(cacheSize)

        try {
            val fd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            if (fd != null) {
                pdfRenderer = PdfRenderer(fd)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setPageCount(count: Int) {
        pageCount = count
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = pageCount

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -2)
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        return PageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val cached = bitmapCache.get(position)
        if (cached != null) {
            holder.imageView.setImageBitmap(cached)
            return
        }

        val renderer = pdfRenderer ?: return
        if (position >= renderer.pageCount) return

        val page = renderer.openPage(position)
        val width = page.width * 2
        val height = page.height * 2

        // ARGB_8888 + white fill before render = no black pages
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()

        bitmapCache.put(position, bitmap)
        holder.imageView.setImageBitmap(bitmap)
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        pdfRenderer?.close()
        pdfRenderer = null
        bitmapCache.evictAll()
    }

    class PageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)
}
