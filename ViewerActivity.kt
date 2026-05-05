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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.propdf.editor.data.repository.PdfOperationsManager
import com.propdf.editor.data.repository.OcrManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ViewerActivity : AppCompatActivity() {

    @Inject lateinit var pdfOps: PdfOperationsManager
    @Inject lateinit var ocrManager: OcrManager

    private lateinit var recyclerView: RecyclerView
    private lateinit var pdfUri: Uri
    private lateinit var adapter: PdfPageAdapter
    private var annotationMode = false
    private var currentAnnotatedView: AnnotatedPageView? = null
    private var pageCount = 0

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

        setupRecyclerView()
        loadPdf()
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

            addView(createBarButton("✏️") { setAnnotationTool(AnnotationType.PEN, android.graphics.Color.RED) })
            addView(createBarButton("🖍️") { setAnnotationTool(AnnotationType.HIGHLIGHTER, android.graphics.Color.YELLOW) })
            addView(createBarButton("🧽") { setAnnotationTool(AnnotationType.ERASER, android.graphics.Color.WHITE) })
            addView(createBarButton("📝") { showTextDialog() })
            addView(createBarButton("⬜") { setAnnotationTool(AnnotationType.RECTANGLE, android.graphics.Color.BLUE) })
            addView(createBarButton("⭕") { setAnnotationTool(AnnotationType.CIRCLE, android.graphics.Color.GREEN) })
            addView(createBarButton("➡️") { setAnnotationTool(AnnotationType.ARROW, android.graphics.Color.RED) })
            addView(createBarButton("↩️") { currentAnnotatedView?.canvasView?.undo() })
            addView(createBarButton("💾") { saveAnnotations() })
            addView(createBarButton("❌") { currentAnnotatedView?.canvasView?.clear() })
            addView(createBarButton("🔍") { runOcr() })
        }
    }

    private fun createBarButton(icon: String, action: () -> Unit): Button {
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
        Toast.makeText(this, "Tool: ${type.name}", Toast.LENGTH_SHORT).show()
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
                val outputDir = File(cacheDir, "annotated_pages")
                outputDir.mkdirs()
                val outputFile = File(outputDir, "page_annotated_${System.currentTimeMillis()}.png")
                val success = view.canvasView.saveAnnotationsToFile(outputFile)
                if (success) {
                    Toast.makeText(this, "Saved to: ${outputFile.name}", Toast.LENGTH_LONG).show()
                    shareAnnotatedFile(outputFile)
                } else {
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No annotations to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun runOcr() {
        lifecycleScope.launch {
            val pageIndex = (recyclerView.layoutManager as LinearLayoutManager).findFirstVisibleItemPosition()
            val bitmap = adapter.getPageBitmap(pageIndex) ?: return@launch
            val text = ocrManager.recognizeText(bitmap)
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(this@ViewerActivity)
                    .setTitle("OCR Result")
                    .setMessage(text.getOrDefault("No text found"))
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    private fun shareAnnotatedFile(file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "com.propdf.editor.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Annotated Page"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRecyclerView() {
        adapter = PdfPageAdapter(this, pdfUri) { pageView ->
            currentAnnotatedView = pageView
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun loadPdf() {
        try {
            val pfd = contentResolver.openFileDescriptor(pdfUri, "r")
            pfd?.let {
                val renderer = PdfRenderer(it)
                pageCount = renderer.pageCount
                renderer.close()
                adapter.setPageCount(pageCount)
                supportActionBar?.title = "PDF Viewer ($pageCount pages)"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error loading PDF", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "🖊️ Annotate").setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 2, 1, "Share").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 3, 2, "Delete").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            1 -> { toggleAnnotationMode(); true }
            2 -> { sharePdf(); true }
            3 -> { deletePdf(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleAnnotationMode() {
        annotationMode = !annotationMode
        val bottomBar = (findViewById(android.R.id.content) as ViewGroup).getChildAt(1) as LinearLayout
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

    private fun deletePdf() {
        if (pdfUri.scheme == "file") {
            val file = java.io.File(pdfUri.path ?: "")
            if (file.exists()) file.delete()
        }
        finish()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
