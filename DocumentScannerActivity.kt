package com.propdf.editor.ui.scanner

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.propdf.editor.data.repository.ScannerProcessor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors
import javax.inject.Inject

@AndroidEntryPoint
class DocumentScannerActivity : AppCompatActivity() {

    @Inject lateinit var scannerProcessor: ScannerProcessor

    private val pages = mutableListOf<Bitmap>()
    private var colorMode = "auto"
    private var isGridVisible = true
    private var cameraProvider: ProcessCameraProvider? = null
    private val executor = Executors.newSingleThreadExecutor()

    private lateinit var previewView: PreviewView
    private lateinit var captureBtn: FrameLayout
    private lateinit var modeRow: LinearLayout
    private lateinit var bottomTools: LinearLayout
    private lateinit var pageStrip: LinearLayout
    private lateinit var rootFrame: FrameLayout

    companion object {
        private const val TAG = "DocScanner"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = mutableListOf(Manifest.permission.CAMERA).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (allPermissionsGranted()) {
            buildUI()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                buildUI()
                startCamera()
            } else {
                Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun buildUI() {
        rootFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        previewView = PreviewView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = PreviewView.ScaleType.FIT_CENTER
        }
        rootFrame.addView(previewView)

        val gridOverlay = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            setBackgroundColor(Color.TRANSPARENT)
            visibility = if (isGridVisible) View.VISIBLE else View.GONE
            tag = "grid_overlay"
        }
        rootFrame.addView(gridOverlay)

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, dp(56)).apply { gravity = Gravity.TOP }
            setBackgroundColor(Color.argb(160, 0, 0, 0))
            setPadding(dp(16), 0, dp(16), 0)

            addView(ImageButton(this@DocumentScannerActivity).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener { finish() }
            })
            addView(TextView(this@DocumentScannerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }
                text = "Document Scanner"
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
            addView(ImageButton(this@DocumentScannerActivity).apply {
                setImageResource(android.R.drawable.ic_menu_mapmode)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener { toggleGrid() }
            })
        }
        rootFrame.addView(topBar)

        modeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, dp(40)).apply {
                gravity = Gravity.TOP
                topMargin = dp(64)
            }
        }
        listOf("Batch" to "batch", "ID Card" to "id_card", "Book" to "book").forEach { (label, mode) ->
            modeRow.addView(TextView(this).apply {
                text = label
                textSize = 11f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setTextColor(Color.parseColor("#448AFF"))
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.argb(180, 255, 255, 255))
                    cornerRadius = dp(16).toFloat()
                }
            })
        }
        rootFrame.addView(modeRow)

        captureBtn = FrameLayout(this).apply {
            val s = dp(72)
            layoutParams = FrameLayout.LayoutParams(s, s).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(120)
            }
            setBackgroundColor(Color.WHITE)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setOval(0, 0, v.width, v.height)
                }
            }
            clipToOutline = true
            elevation = dp(8).toFloat()
            setOnClickListener { captureDocument() }
        }
        captureBtn.addView(View(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56)).apply { gravity = Gravity.CENTER }
            setBackgroundColor(Color.parseColor("#448AFF"))
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setOval(0, 0, v.width, v.height)
                }
            }
            clipToOutline = true
        })
        rootFrame.addView(captureBtn)

        bottomTools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(-1, dp(56)).apply { gravity = Gravity.BOTTOM }
            setBackgroundColor(Color.argb(180, 0, 0, 0))
            setPadding(dp(8), 0, dp(8), 0)
        }

        val modes = listOf("Auto" to "auto", "Color" to "color", "Gray" to "gray", "B&W" to "black_white")
        modes.forEach { (label, mode) ->
            val tv = TextView(this).apply {
                text = label
                textSize = 11f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    setColor(Color.argb(180, 255, 255, 255))
                    cornerRadius = dp(16).toFloat()
                }
                setOnClickListener {
                    colorMode = mode
                    selectColorMode(mode)
                }
            }
            bottomTools.addView(tv)
        }
        rootFrame.addView(bottomTools)

        pageStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(-1, dp(80)).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(56)
            }
            setBackgroundColor(Color.argb(200, 0, 0, 0))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        rootFrame.addView(pageStrip)

        rootFrame.addView(TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                bottomMargin = dp(140)
                marginEnd = dp(16)
            }
            text = "0"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E53935"))
                cornerRadius = dp(16).toFloat()
            }
            tag = "page_count"
        })

        setContentView(rootFrame)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases()
            } catch (e: Exception) {
                Log.e(TAG, "Camera init failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        provider.unbindAll()
        provider.bindToLifecycle(this, cameraSelector, preview)
    }

    private fun captureDocument() {
        lifecycleScope.launch {
            try {
                val bitmap = previewView.bitmap ?: return@launch
                val processed = withContext(Dispatchers.Default) {
                    val enhanced = scannerProcessor.applyColorMode(bitmap, colorMode)
                    scannerProcessor.detectEdges(enhanced)
                }
                pages.add(processed)
                updatePageStrip()
                updatePageCount()
                Toast.makeText(this@DocumentScannerActivity, "Captured page ${pages.size}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Capture failed", e)
                Toast.makeText(this@DocumentScannerActivity, "Capture failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updatePageStrip() {
        pageStrip.removeAllViews()
        pages.forEachIndexed { index, bitmap ->
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(60), dp(80)).apply { marginEnd = dp(4) }
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener { showPageOptions(index) }
            }
            pageStrip.addView(thumb)
        }
    }

    private fun updatePageCount() {
        val badge = rootFrame.findViewWithTag<TextView>("page_count")
        badge?.text = pages.size.toString()
    }

    private fun showPageOptions(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Page ${index + 1}")
            .setItems(arrayOf("Edit", "Rotate", "Delete", "Reorder")) { _, which ->
                when (which) {
                    0 -> editPage(index)
                    1 -> rotatePage(index)
                    2 -> deletePage(index)
                    3 -> showReorderDialog()
                }
            }
            .show()
    }

    private fun editPage(index: Int) {
        val bitmap = pages[index]
        AlertDialog.Builder(this)
            .setTitle("Edit Page")
            .setItems(arrayOf("Rotate 90", "Rotate -90", "Enhance", "Crop")) { _, which ->
                lifecycleScope.launch {
                    val processed = when (which) {
                        0 -> scannerProcessor.rotate(bitmap, 90f)
                        1 -> scannerProcessor.rotate(bitmap, -90f)
                        2 -> scannerProcessor.applyFilter(bitmap, "document")
                        3 -> bitmap
                        else -> bitmap
                    }
                    pages[index] = processed
                    updatePageStrip()
                }
            }
            .show()
    }

    private fun rotatePage(index: Int) {
        lifecycleScope.launch {
            val rotated = scannerProcessor.rotate(pages[index], 90f)
            pages[index] = rotated
            updatePageStrip()
        }
    }

    private fun deletePage(index: Int) {
        pages.removeAt(index)
        updatePageStrip()
        updatePageCount()
    }

    private fun showReorderDialog() {
        if (pages.size < 2) return
        AlertDialog.Builder(this)
            .setTitle("Arrange Pages")
            .setItems(pages.indices.map { "Page ${it + 1}" }.toTypedArray()) { _, from ->
                AlertDialog.Builder(this)
                    .setTitle("Move to position")
                    .setItems(pages.indices.map { "Position ${it + 1}" }.toTypedArray()) { _, to ->
                        if (from != to) {
                            val page = pages.removeAt(from)
                            pages.add(to, page)
                            updatePageStrip()
                        }
                    }
                    .show()
            }
            .show()
    }

    private fun saveAsPdf() {
        if (pages.isEmpty()) {
            Toast.makeText(this, "No pages to save", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val doc = PdfDocument()
                pages.forEachIndexed { index, bitmap ->
                    val pageInfo = PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, index + 1).create()
                    val page = doc.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    doc.finishPage(page)
                }

                val filename = "scanned_${System.currentTimeMillis()}.pdf"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ProPDF")
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    uri?.let { contentResolver.openOutputStream(it)?.use { out -> doc.writeTo(out) } }
                } else {
                    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "ProPDF")
                    dir.mkdirs()
                    FileOutputStream(File(dir, filename)).use { doc.writeTo(it) }
                }
                doc.close()

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DocumentScannerActivity, "Saved to Downloads/ProPDF", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@DocumentScannerActivity, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun selectColorMode(mode: String) {
        colorMode = mode
        for (i in 0 until bottomTools.childCount) {
            val child = bottomTools.getChildAt(i) as? TextView
            child?.let {
                val isSelected = it.text.toString().equals(when (mode) {
                    "auto" -> "Auto"; "color" -> "Color"; "gray" -> "Gray"; "black_white" -> "B&W"; else -> ""
                }, ignoreCase = true)
                it.setTextColor(if (isSelected) Color.parseColor("#448AFF") else Color.WHITE)
                it.typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
        }
    }

    private fun toggleGrid() {
        isGridVisible = !isGridVisible
        rootFrame.findViewWithTag<View>("grid_overlay")?.visibility = if (isGridVisible) View.VISIBLE else View.GONE
    }

    private fun showSettings() {
        AlertDialog.Builder(this)
            .setTitle("Scanner Settings")
            .setItems(arrayOf("Save as PDF", "Clear All Pages", "Auto-capture: Off", "Resolution: High")) { _, which ->
                when (which) {
                    0 -> saveAsPdf()
                    1 -> { pages.clear(); updatePageStrip(); updatePageCount() }
                }
            }
            .show()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        cameraProvider?.unbindAll()
        pages.forEach { if (!it.isRecycled) it.recycle() }
        pages.clear()
    }
}