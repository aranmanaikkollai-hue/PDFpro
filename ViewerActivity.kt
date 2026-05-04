package com.propdf.editor.ui.viewer

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.*
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.propdf.editor.data.repository.PdfOperationsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ViewerActivity : AppCompatActivity() {

    @Inject lateinit var pdfOps: PdfOperationsManager
    private lateinit var viewModel: ViewerViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var pdfUri: Uri
    private lateinit var adapter: PdfPageAdapter
    private var annotationMode = false
    private var currentAnnotatedView: AnnotatedPageView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)

        recyclerView = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        root.addView(recyclerView)

        val bottomBar = createAnnotationBar()
        root.addView(bottomBar)

        setContentView(root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val uriString = intent.getStringExtra("pdf_uri")
        if (uriString == null) {
            Toast.makeText(this, "No PDF specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        pdfUri = Uri.parse(uriString)

        viewModel = ViewModelProvider(this).get(ViewerViewModel::class.java)
        setupRecyclerView()
        observeViewModel()
        viewModel.loadPdf(pdfUri)
    }

    private fun createAnnotationBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(-1, dp(56)).apply {
                gravity = Gravity.BOTTOM
            }
            setBackgroundColor(android.graphics.Color.parseColor("#1E1E1E"))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE

            addView(createBarButton("✏️", "Pen") { setAnnotationTool(AnnotationType.PEN, android.graphics.Color.RED) })
            addView(createBarButton("🖍️", "Highlight") { setAnnotationTool(AnnotationType.HIGHLIGHTER, android.graphics.Color.YELLOW) })
            addView(createBarButton("📝", "Text") { showTextDialog() })
            addView(createBarButton("⬜", "Rect") { setAnnotationTool(AnnotationType.RECTANGLE, android.graphics.Color.BLUE) })
            addView(createBarButton("⭕", "Circle") { setAnnotationTool(AnnotationType.CIRCLE, android.graphics.Color.GREEN) })
            addView(createBarButton("➡️", "Arrow") { setAnnotationTool(AnnotationType.ARROW, android.graphics.Color.RED) })
            addView(createBarButton("🧽", "Erase") { setAnnotationTool(AnnotationType.ERASER, android.graphics.Color.WHITE) })
            addView(createBarButton("↩️", "Undo") { currentAnnotatedView?.canvasView?.undo() })
            addView(createBarButton("💾", "Save") { saveAnnotations() })
        }
    }

    private fun createBarButton(icon: String, label: String, action: () -> Unit): Button {
        return Button(this).apply {
            text = icon
            textSize = 20f
            setTextColor(android.graphics.Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.TRANSPARENT)
            }
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }
            setOnClickListener { action() }
        }
    }

    private fun setAnnotationTool(type: AnnotationType, color: Int) {
        currentAnnotatedView?.canvasView?.setTool(type, color, when(type) {
            AnnotationType.HIGHLIGHTER -> 15f
            AnnotationType.ERASER -> 30f
            AnnotationType.TEXT -> 5f
            else -> 5f
        })
        Toast.makeText(this, "Tool: $type", Toast.LENGTH_SHORT).show()
    }

    private fun showTextDialog() {
        val et = EditText(this).apply {
            hint = "Enter text..."
            setTextColor(android.graphics.Color.BLACK)
        }
        AlertDialog.Builder(this)
            .setTitle("Add Text")
            .setView(et)
            .setPositiveButton("Add") { _, _ ->
                val text = et.text.toString()
                if (text.isNotBlank()) {
                    currentAnnotatedView?.canvasView?.addTextAnnotation(text, 100f, 100f)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun saveAnnotations() {
        currentAnnotatedView?.let { view ->
            if (view.canvasView.hasAnnotations()) {
                val bitmap = view.canvasView.getAnnotationsBitmap(view.width, view.height)
                Toast.makeText(this, "Annotations saved!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = PdfPageAdapter(this, pdfUri) { pageView ->
            currentAnnotatedView = pageView
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.pageCount.observe(this) { count ->
            adapter.setPageCount(count)
            supportActionBar?.title = "PDF Viewer ($count pages)"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "🖊️ Annotate").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Share").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 3, 2, "Print").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 4, 3, "Delete").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            1 -> { toggleAnnotationMode(); true }
            2 -> { sharePdf(); true }
            3 -> { printPdf(); true }
            4 -> { deletePdf(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleAnnotationMode() {
        annotationMode = !annotationMode
        val bottomBar = (findViewById<FrameLayout>(android.R.id.content)).getChildAt(1) as LinearLayout
        bottomBar.visibility = if (annotationMode) View.VISIBLE else View.GONE
        Toast.makeText(this, if (annotationMode) "Annotation mode ON" else "Annotation mode OFF", Toast.LENGTH_SHORT).show()
    }

    private fun sharePdf() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun printPdf() {
        Toast.makeText(this, "Print - Coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun deletePdf() {
        if (pdfUri.scheme == "file") {
            val file = java.io.File(pdfUri.path ?: "")
            if (file.exists()) file.delete()
        }
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
