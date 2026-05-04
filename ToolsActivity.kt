package com.propdf.editor.ui.tools

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
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
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ToolsActivity : AppCompatActivity() {

    @Inject
    lateinit var pdfOps: PdfOperationsManager

    private var selectedFiles = mutableListOf<File>()
    private var currentTool = ""

    // Keep reference to count label so we can update it
    private var selectedCountLabel: TextView? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val file = FileHelper.uriToFile(this, uri) ?: return@registerForActivityResult
        selectedFiles.add(file)
        updateSelectedFilesUI()
    }

    private val multiFilePicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
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
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    marginStart = dp(12)
                }
                text = "PDF Tools"
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })
        })

        // Selected files indicator
        val countLabel = TextView(this).apply {
            text = "Selected Files: 0"
            textSize = 12f
            setTextColor(Color.parseColor("#A0A0A0"))
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        selectedCountLabel = countLabel

        val selectedContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#2A2A2A"))
        }
        selectedContainer.addView(countLabel)
        root.addView(selectedContainer)

        // Tool grid in ScrollView
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(16))
        }

        data class ToolItem(val title: String, val desc: String, val icon: Int, val action: () -> Unit)

        val tools = listOf(
            ToolItem("Merge", "Combine multiple PDFs", android.R.drawable.ic_menu_agenda) { startMerge() },
            ToolItem("Split", "Extract pages", android.R.drawable.ic_menu_crop) { startSplit() },
            ToolItem("Compress", "Reduce file size", android.R.drawable.ic_menu_save) { startCompress() },
            ToolItem("Protect", "Encrypt with password", android.R.drawable.ic_lock_lock) { startEncrypt() },
            ToolItem("Watermark", "Add text watermark", android.R.drawable.ic_menu_edit) { startWatermark() },
            ToolItem("Rotate", "Rotate pages", android.R.drawable.ic_menu_rotate) { startRotate() },
            ToolItem("Delete Pages", "Remove pages", android.R.drawable.ic_menu_delete) { startDeletePages() },
            ToolItem("Page Numbers", "Add numbering", android.R.drawable.ic_menu_sort_by_size) { startPageNumbers() },
            ToolItem("Header/Footer", "Add headers", android.R.drawable.ic_menu_info_details) { startHeaderFooter() },
            ToolItem("Images to PDF", "Convert images", android.R.drawable.ic_menu_gallery) { startImagesToPdf() }
        )

        // Lay out in rows of 2
        tools.chunked(2).forEach { rowItems ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowItems.forEach { tool ->
                row.addView(buildToolCard(tool.title, tool.desc, tool.icon, tool.action))
            }
            // If odd number in last row, add spacer
            if (rowItems.size == 1) {
                row.addView(FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(120), 1f)
                })
            }
            grid.addView(row)
        }

        scroll.addView(grid)
        root.addView(scroll)
        setContentView(root)
    }

    private fun buildToolCard(
        title: String,
        desc: String,
        icon: Int,
        action: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(120), 1f).apply {
                marginEnd = dp(8)
                bottomMargin = dp(8)
            }
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
        selectedCountLabel?.text = "Selected Files: ${selectedFiles.size}"
    }

    // -------------------------------------------------------
    // TOOL IMPLEMENTATIONS
    // -------------------------------------------------------

    private fun startMerge() {
        currentTool = "merge"
        selectedFiles.clear()
        updateSelectedFilesUI()
        multiFilePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Merge PDFs") {
            lifecycleScope.launch {
                if (selectedFiles.size < 2) {
                    toast("Select at least 2 PDFs")
                    return@launch
                }
                val output = File(cacheDir, "merged_${System.currentTimeMillis()}.pdf")
                pdfOps.mergePdfs(selectedFiles, output)
                    .onSuccess { shareResult(it, "PDFs merged successfully") }
                    .onFailure { toast("Merge failed: ${it.message}") }
            }
        }
    }

    private fun startSplit() {
        currentTool = "split"
        selectedFiles.clear()
        updateSelectedFilesUI()
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
                val ranges = parsePageRanges(et.text.toString())
                if (ranges.isEmpty()) { toast("Invalid ranges"); return@setPositiveButton }
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    pdfOps.splitPdf(file, cacheDir, ranges)
                        .onSuccess { files -> toast("Created ${files.size} files") }
                        .onFailure { toast("Split failed: ${it.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startCompress() {
        currentTool = "compress"
        selectedFiles.clear()
        updateSelectedFilesUI()
        filePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Compress PDF") {
            lifecycleScope.launch {
                val file = selectedFiles.firstOrNull() ?: return@launch
                val output = File(cacheDir, "compressed_${System.currentTimeMillis()}.pdf")
                pdfOps.compressPdf(file, output, 9)
                    .onSuccess { outFile ->
                        val reduction =
                            ((file.length() - outFile.length()) * 100 / file.length().toFloat()).toInt()
                        shareResult(outFile, "Compressed: $reduction% smaller")
                    }
                    .onFailure { toast("Compress failed: ${it.message}") }
            }
        }
    }

    private fun startEncrypt() {
        currentTool = "encrypt"
        selectedFiles.clear()
        updateSelectedFilesUI()
        filePicker.launch(arrayOf("application/pdf"))
        showEncryptDialog()
    }

    private fun showEncryptDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val userPass = EditText(this).apply { hint = "User password (optional)" }
        val ownerPass = EditText(this).apply { hint = "Owner password (required)" }
        layout.addView(userPass)
        layout.addView(ownerPass)

        AlertDialog.Builder(this)
            .setTitle("Encrypt PDF")
            .setView(layout)
            .setPositiveButton("Encrypt") { _, _ ->
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "encrypted_${System.currentTimeMillis()}.pdf")
                    pdfOps.encryptPdf(
                        file, output,
                        userPass = userPass.text.toString().takeIf { it.isNotBlank() },
                        ownerPassword = ownerPass.text.toString()
                    )
                        .onSuccess { shareResult(it, "PDF encrypted") }
                        .onFailure { toast("Encrypt failed: ${it.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startWatermark() {
        currentTool = "watermark"
        selectedFiles.clear()
        updateSelectedFilesUI()
        filePicker.launch(arrayOf("application/pdf"))
        val et = EditText(this).apply { hint = "Watermark text" }
        AlertDialog.Builder(this)
            .setTitle("Add Watermark")
            .setView(et)
            .setPositiveButton("Apply") { _, _ ->
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "watermarked_${System.currentTimeMillis()}.pdf")
                    pdfOps.addTextWatermark(file, output, et.text.toString())
                        .onSuccess { shareResult(it, "Watermark added") }
                        .onFailure { toast("Failed: ${it.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startRotate() {
        currentTool = "rotate"
        selectedFiles.clear()
        updateSelectedFilesUI()
        filePicker.launch(arrayOf("application/pdf"))
        val et = EditText(this).apply { hint = "Page numbers (e.g. 1,3,5)" }
        AlertDialog.Builder(this)
            .setTitle("Rotate Pages")
            .setView(et)
            .setPositiveButton("Rotate 90") { _, _ -> rotatePages(et.text.toString(), 90) }
            .setNeutralButton("Rotate 180") { _, _ -> rotatePages(et.text.toString(), 180) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun rotatePages(pagesStr: String, degrees: Int) {
        val pages = pagesStr.split(",").mapNotNull { it.trim().toIntOrNull() }
        if (pages.isEmpty()) { toast("No valid pages"); return }
        lifecycleScope.launch {
            val file = selectedFiles.firstOrNull() ?: return@launch
            val output = File(cacheDir, "rotated_${System.currentTimeMillis()}.pdf")
            pdfOps.rotatePages(file, output, pages.associateWith { degrees })
                .onSuccess { shareResult(it, "Pages rotated") }
                .onFailure { toast("Failed: ${it.message}") }
        }
    }

    private fun startDeletePages() {
        currentTool = "delete"
        selectedFiles.clear()
        updateSelectedFilesUI()
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
                    pdfOps.deletePages(file, output, pages)
                        .onSuccess { shareResult(it, "Pages deleted") }
                        .onFailure { toast("Failed: ${it.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startPageNumbers() {
        currentTool = "pagenums"
        selectedFiles.clear()
        updateSelectedFilesUI()
        filePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Add Page Numbers") {
            lifecycleScope.launch {
                val file = selectedFiles.firstOrNull() ?: return@launch
                val output = File(cacheDir, "numbered_${System.currentTimeMillis()}.pdf")
                pdfOps.addPageNumbers(file, output)
                    .onSuccess { shareResult(it, "Page numbers added") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startHeaderFooter() {
        currentTool = "headerfooter"
        selectedFiles.clear()
        updateSelectedFilesUI()
        filePicker.launch(arrayOf("application/pdf"))
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val headerEt = EditText(this).apply { hint = "Header text (optional)" }
        val footerEt = EditText(this).apply { hint = "Footer text (optional)" }
        layout.addView(headerEt)
        layout.addView(footerEt)

        AlertDialog.Builder(this)
            .setTitle("Header / Footer")
            .setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                lifecycleScope.launch {
                    val file = selectedFiles.firstOrNull() ?: return@launch
                    val output = File(cacheDir, "headerfooter_${System.currentTimeMillis()}.pdf")
                    pdfOps.addHeaderFooter(
                        file, output,
                        headerText = headerEt.text.toString().takeIf { it.isNotBlank() },
                        footerText = footerEt.text.toString().takeIf { it.isNotBlank() }
                    )
                        .onSuccess { shareResult(it, "Header/Footer added") }
                        .onFailure { toast("Failed: ${it.message}") }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startImagesToPdf() {
        currentTool = "imagestopdf"
        selectedFiles.clear()
        updateSelectedFilesUI()
        multiFilePicker.launch(arrayOf("image/*"))
        showProcessDialog("Images to PDF") {
            lifecycleScope.launch {
                if (selectedFiles.isEmpty()) { toast("No images selected"); return@launch }
                val output = File(cacheDir, "images_${System.currentTimeMillis()}.pdf")
                pdfOps.imagesToPdf(selectedFiles, output)
                    .onSuccess { shareResult(it, "PDF created from images") }
                    .onFailure { toast("Failed: ${it.message}") }
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
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "$packageName.provider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Result"))
        } catch (e: Exception) {
            toast("Cannot share result")
        }
    }

    /**
     * Parses "1-3,5,7-10" into list of IntRange.
     * Fixed: guards against split producing fewer than 2 parts.
     */
    private fun parsePageRanges(input: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        input.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val parts = trimmed.split("-")
                val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null) result.add(start..end)
            } else {
                trimmed.toIntOrNull()?.let { result.add(it..it) }
            }
        }
        return result
    }

    /**
     * Parses "2,4,6-8" into flat list of Int page numbers.
     */
    private fun parsePageList(input: String): List<Int> {
        val result = mutableListOf<Int>()
        input.split(",").forEach { part ->
            val trimmed = part.trim()
            if (trimmed.contains("-")) {
                val parts = trimmed.split("-")
                val start = parts.getOrNull(0)?.trim()?.toIntOrNull()
                val end = parts.getOrNull(1)?.trim()?.toIntOrNull()
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
