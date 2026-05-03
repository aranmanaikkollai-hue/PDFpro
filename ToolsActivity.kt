package com.propdf.editor.ui.tools

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.propdf.editor.data.repository.PdfOperationsManager
import com.propdf.editor.utils.FileHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ToolsActivity : AppCompatActivity() {

    @Inject
    lateinit var pdfOps: PdfOperationsManager

    private var selectedFiles = mutableListOf<File>()
    private var currentTool = ""
    private val isDark = true

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val file = FileHelper.uriToFile(this, uri) ?: return@registerForActivityResult
        selectedFiles.add(file)
        updateSelectedFilesUI()
    }

    private val multiFilePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
        uris?.forEach { uri ->
            FileHelper.uriToFile(this, uri)?.let { selectedFiles.add(it) }
        }
        updateSelectedFilesUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUI()
    }

    private fun buildUI() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // Header
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(56))
            setBackgroundColor(Color.parseColor("#1A1A1A"))
            setPadding(dp(16), 0, dp(16), 0)

            addView(ImageButton(this@ToolsActivity).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(Color.TRANSPARENT)
                setColorFilter(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                setOnClickListener { finish() }
            })
            addView(TextView(this@ToolsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(12) }
                text = "PDF Tools"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
        })

        // Selected files indicator
        val selectedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
        selectedContainer.addView(TextView(this).apply {
            text = "Selected Files: 0"
            textSize = 12f
            setTextColor(Color.parseColor("#A0A0A0"))
            tag = "selected_count"
        })
        root.addView(selectedContainer)

        // Tool grid
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        // Row 1
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(buildToolCard("Merge", "Combine multiple PDFs", android.R.drawable.ic_menu_agenda) { startMerge() })
        row1.addView(buildToolCard("Split", "Extract pages", android.R.drawable.ic_menu_crop) { startSplit() })
        grid.addView(row1)

        // Row 2
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(buildToolCard("Compress", "Reduce file size", android.R.drawable.ic_menu_save) { startCompress() })
        row2.addView(buildToolCard("Protect", "Encrypt with password", android.R.drawable.ic_lock_lock) { startEncrypt() })
        grid.addView(row2)

        // Row 3
        val row3 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row3.addView(buildToolCard("Watermark", "Add text watermark", android.R.drawable.ic_menu_edit) { startWatermark() })
        row3.addView(buildToolCard("Rotate", "Rotate pages", android.R.drawable.ic_menu_rotate) { startRotate() })
        grid.addView(row3)

        // Row 4
        val row4 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row4.addView(buildToolCard("Delete Pages", "Remove pages", android.R.drawable.ic_menu_delete) { startDeletePages() })
        row4.addView(buildToolCard("Page Numbers", "Add numbering", android.R.drawable.ic_menu_sort_by_size) { startPageNumbers() })
        grid.addView(row4)

        // Row 5
        val row5 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row5.addView(buildToolCard("Header/Footer", "Add headers", android.R.drawable.ic_menu_info_details) { startHeaderFooter() })
        row5.addView(buildToolCard("Images to PDF", "Convert images", android.R.drawable.ic_menu_gallery) { startImagesToPdf() })
        grid.addView(row5)

        scroll.addView(grid)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildToolCard(title: String, desc: String, icon: Int, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(120), 1f).apply { marginEnd = dp(8); bottomMargin = dp(8) }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2A2A2A"))
                cornerRadius = dp(12).toFloat()
            }
            setOnClickListener { action() }

            addView(ImageView(this@ToolsActivity).apply {
                setImageResource(icon)
                setColorFilter(Color.parseColor("#448AFF"))
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32))
            })
            addView(TextView(this@ToolsActivity).apply {
                text = title
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
            addView(TextView(this@ToolsActivity).apply {
                text = desc
                textSize = 10f
                setTextColor(Color.parseColor("#A0A0A0"))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun updateSelectedFilesUI() {
        val countView = findViewWithTag<TextView>("selected_count")
        countView?.text = "Selected Files: ${selectedFiles.size}"
    }

    // -------------------------------------------------------
    // TOOL IMPLEMENTATIONS
    // -------------------------------------------------------

    private fun startMerge() {
        currentTool = "merge"
        selectedFiles.clear()
        multiFilePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Merge PDFs") {
            lifecycleScope.launch {
                if (selectedFiles.size < 2) { toast("Select at least 2 PDFs"); return@launch }
                val output = File(cacheDir, "merged_${System.currentTimeMillis()}.pdf")
                val result = pdfOps.mergePdfs(selectedFiles, output)
                result.onSuccess { file ->
                    shareResult(file, "PDFs merged successfully")
                }.onFailure { e ->
                    toast("Merge failed: ${e.message}")
                }
            }
        }
    }

    private fun startSplit() {
        currentTool = "split"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        showSplitDialog()
    }

    private fun showSplitDialog() {
        val et = EditText(this).apply { hint = "e.g. 1-3,5,7-10" }
        AlertDialog.Builder(this)
            .setTitle("Split PDF")
            .setMessage("Enter page ranges to extract")
            .setView(et)
            .setPositiveButton("Split") { _, _ ->
                val input = et.text.toString()
                val ranges = parsePageRanges(input)
                if (ranges.isEmpty()) { toast("Invalid ranges"); return@setPositiveButton }
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val outputDir = cacheDir
                    val result = pdfOps.splitPdf(file, outputDir, ranges)
                    result.onSuccess { files ->
                        toast("Created ${files.size} files")
                        files.forEach { shareResult(it, "Split result") }
                    }.onFailure { e ->
                        toast("Split failed: ${e.message}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCompress() {
        currentTool = "compress"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Compress PDF") {
            lifecycleScope.launch {
                val file = selectedFiles.firstOrNull() ?: return@launch
                val output = File(cacheDir, "compressed_${System.currentTimeMillis()}.pdf")
                val result = pdfOps.compressPdf(file, output, 9)
                result.onSuccess { outFile ->
                    val originalSize = file.length()
                    val newSize = outFile.length()
                    val reduction = ((originalSize - newSize) * 100 / originalSize.toFloat()).toInt()
                    shareResult(outFile, "Compressed: $reduction% smaller")
                }.onFailure { e ->
                    toast("Compress failed: ${e.message}")
                }
            }
        }
    }

    private fun startEncrypt() {
        currentTool = "encrypt"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        showEncryptDialog()
    }

    private fun showEncryptDialog() {
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val userPass = EditText(this).apply { hint = "User password (optional)" }
        val ownerPass = EditText(this).apply { hint = "Owner password (required)" }
        layout.addView(userPass); layout.addView(ownerPass)
        AlertDialog.Builder(this)
            .setTitle("Encrypt PDF")
            .setView(layout)
            .setPositiveButton("Encrypt") { _, _ ->
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "encrypted_${System.currentTimeMillis()}.pdf")
                    val result = pdfOps.encryptPdf(
                        file, output,
                        userPass = userPass.text.toString().takeIf { it.isNotBlank() },
                        ownerPassword = ownerPass.text.toString()
                    )
                    result.onSuccess { shareResult(it, "PDF encrypted") }
                        .onFailure { e -> toast("Encrypt failed: ${e.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startWatermark() {
        currentTool = "watermark"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        val et = EditText(this).apply { hint = "Watermark text" }
        AlertDialog.Builder(this)
            .setTitle("Add Watermark")
            .setView(et)
            .setPositiveButton("Apply") { _, _ ->
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "watermarked_${System.currentTimeMillis()}.pdf")
                    val result = pdfOps.addTextWatermark(file, output, et.text.toString())
                    result.onSuccess { shareResult(it, "Watermark added") }
                        .onFailure { e -> toast("Failed: ${e.message}") }
                }
            }
            .show()
    }

    private fun startRotate() {
        currentTool = "rotate"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        val et = EditText(this).apply { hint = "Page numbers (e.g. 1,3,5)" }
        AlertDialog.Builder(this)
            .setTitle("Rotate Pages")
            .setView(et)
            .setPositiveButton("Rotate 90") { _, _ ->
                rotatePages(et.text.toString(), 90)
            }
            .setNeutralButton("Rotate 180") { _, _ ->
                rotatePages(et.text.toString(), 180)
            }
            .show()
    }

    private fun rotatePages(pagesStr: String, degrees: Int) {
        val pages = pagesStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (pages.isEmpty()) { toast("No valid pages"); return }
        lifecycleScope.launch {
            val file = selectedFiles.firstOrNull() ?: return@launch
            val output = File(cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
            val pageMap = pages.associateWith { degrees }
            val result = pdfOps.rotatePages(file, output, pageMap)
            result.onSuccess { shareResult(it, "Pages rotated") }
                .onFailure { e -> toast("Failed: ${e.message}") }
        }
    }

    private fun startDeletePages() {
        currentTool = "delete"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        val et = EditText(this).apply { hint = "Pages to delete (e.g. 2,4,6-8)" }
        AlertDialog.Builder(this)
            .setTitle("Delete Pages")
            .setView(et)
            .setPositiveButton("Delete") { _, _ ->
                val pages = parsePageList(et.text.toString())
                if (pages.isEmpty()) { toast("No valid pages"); return@setPositiveButton }
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "deleted_${System.currentTimeMillis()}.pdf")
                    val result = pdfOps.deletePages(file, output, pages)
                    result.onSuccess { shareResult(it, "Pages deleted") }
                        .onFailure { e -> toast("Failed: ${e.message}") }
                }
            }
            .show()
    }

    private fun startPageNumbers() {
        currentTool = "pagenums"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        lifecycleScope.launch {
            val file = selectedFiles.firstOrNull() ?: return@launch
            val output = File(cacheDir, "numbered_${System.currentTimeMillis()}.pdf")
            val result = pdfOps.addPageNumbers(file, output)
            result.onSuccess { shareResult(it, "Page numbers added") }
                .onFailure { e -> toast("Failed: ${e.message}") }
        }
    }

    private fun startHeaderFooter() {
        currentTool = "headerfooter"
        selectedFiles.clear()
        filePicker.launch(arrayOf("application/pdf"))
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        val headerEt = EditText(this).apply { hint = "Header text (optional)" }
        val footerEt = EditText(this).apply { hint = "Footer text (optional)" }
        layout.addView(headerEt); layout.addView(footerEt)
        AlertDialog.Builder(this)
            .setTitle("Header / Footer")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "headerfooter_${System.currentTimeMillis()}.pdf")
                    val result = pdfOps.addHeaderFooter(
                        file, output,
                        headerText = headerEt.text.toString().takeIf { it.isNotBlank() },
                        footerText = footerEt.text.toString().takeIf { it.isNotBlank() }
                    )
                    result.onSuccess { shareResult(it, "Header/Footer added") }
                        .onFailure { e -> toast("Failed: ${e.message}") }
                }
            }
            .show()
    }

    private fun startImagesToPdf() {
        currentTool = "imagestopdf"
        selectedFiles.clear()
        multiFilePicker.launch(arrayOf("image/*"))
        showProcessDialog("Images to PDF") {
            lifecycleScope.launch {
                if (selectedFiles.isEmpty()) { toast("No images selected"); return@launch }
                val output = File(cacheDir, "images_${System.currentTimeMillis()}.pdf")
                val result = pdfOps.imagesToPdf(selectedFiles, output)
                result.onSuccess { shareResult(it, "PDF created from images") }
                    .onFailure { e -> toast("Failed: ${e.message}") }
            }
        }
    }

    // -------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------

    private fun showProcessDialog(title: String, action: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage("Select files first, then tap Process.")
            .setPositiveButton("Process") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareResult(file: File, message: String) {
        toast(message)
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Result"))
        } catch (_: Exception) { toast("Cannot share result") }
    }

    private fun parsePageRanges(input: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        input.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (start, end) = trimmed.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (start != null && end != null) result.add(start..end)
            } else {
                trimmed.toIntOrNull()?.let { result.add(it..it) }
            }
        }
        return result
    }

    private fun parsePageList(input: String): List<Int> {
        val result = mutableListOf<Int>()
        input.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val (start, end) = trimmed.split("-").mapNotNull { it.trim().toIntOrNull() }
                if (start != null && end != null) result.addAll(start..end)
            } else {
                trimmed.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
