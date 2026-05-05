package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class PdfPageAdapter(
    private val context: Context,
    private val pdfUri: Uri,
    private val onPageViewCreated: (AnnotatedPageView) -> Unit
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private var pageCount = 0
    private val renderer: PdfRenderer?

    init {
        val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
        renderer = pfd?.let { PdfRenderer(it) }
    }

    fun setPageCount(count: Int) {
        pageCount = count
        notifyDataSetChanged()
    }

    fun getPageBitmap(position: Int): Bitmap? {
        return renderer?.let {
            val page = it.openPage(position)
            val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageViewHolder {
        val frame = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return PageViewHolder(frame)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val bitmap = getPageBitmap(position) ?: return
        val pageView = AnnotatedPageView(context, bitmap, position)
        holder.container.removeAllViews()
        holder.container.addView(pageView)
        onPageViewCreated(pageView)
    }

    override fun getItemCount(): Int = pageCount

    class PageViewHolder(val container: FrameLayout) : RecyclerView.ViewHolder(container)
}
