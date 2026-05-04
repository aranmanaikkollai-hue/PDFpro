package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

class PdfPageAdapter(
    private val context: Context,
    private val pdfUri: Uri,
    private val onPageViewCreated: ((AnnotatedPageView) -> Unit)? = null
) : RecyclerView.Adapter<PdfPageAdapter.PageViewHolder>() {

    private var pageCount = 0
    private var renderer: PdfRenderer? = null

    init {
        try {
            val pfd = context.contentResolver.openFileDescriptor(pdfUri, "r")
            pfd?.let {
                renderer = PdfRenderer(it)
                pageCount = renderer!!.pageCount
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
        val pageView = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(-1, -1)
        }
        return PageViewHolder(pageView)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        holder.bind(position)
    }

    inner class PageViewHolder(private val container: FrameLayout) : RecyclerView.ViewHolder(container) {

        fun bind(pageIndex: Int) {
            container.removeAllViews()

            val bitmap = renderPage(pageIndex) ?: return

            val annotatedView = AnnotatedPageView(context, bitmap, pageIndex).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
            }

            container.addView(annotatedView)
            onPageViewCreated?.invoke(annotatedView)
        }

        private fun renderPage(index: Int): Bitmap? {
            return try {
                renderer?.let { renderer ->
                    val page = renderer.openPage(index)
                    val bitmap = Bitmap.createBitmap(
                        page.width * 2,
                        page.height * 2,
                        Bitmap.Config.ARGB_8888
                    )
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    bitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun cleanup() {
        renderer?.close()
    }
}
