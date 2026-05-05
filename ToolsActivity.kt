package com.propdf.editor.ui.tools

import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
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

    @Inject lateinit var pdfOps: PdfOperationsManager
    @Inject lateinit var fileHelper: FileHelper

    private var selectedFiles = mutableListOf<File>()
    private var selectedCountLabel: TextView? = null

    private val filePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { fileHelper.uriToFile(it)?.let { f -> selectedFiles.add(f); updateUI() } }
    }

    private val multiFilePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.forEach { fileHelper.uriToFile(it)?.let { f -> selectedFiles.add(f) } }
        updateUI()
    }

    private val imagePicker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris?.forEach { fileHelper.uriToFile(it)?.let { f -> selectedFiles.add(f) } }
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildPremiumUI()
    }

    private fun buildPremiumUI() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "🔧 PDF Tools"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(16), 0, dp(8))
        })

        root.addView(TextView(this).apply {
            text = "Professional PDF editing tools at your fingertips"
            textSize = 14f
            setTextColor(Color.parseColor("#888888"))
            setPadding(0, 0, 0, dp(24))
        })

        val filesCard = createCard()
        selectedCountLabel = TextView(this).apply {
            text = "📄 Selected: 0 files"
            textSize = 14f
            setTextColor(Color.parseColor("#AAAAAA"))
        }
        filesCard.addView(selectedCountLabel)
        root.addView(filesCard)

        addCategory(root, "✏️ Edit & Modify", listOf(
            Tool("Merge PDFs", "Combine multiple PDFs into one", "#FF6B35", "merge") { startMerge() },
            Tool("Split PDF", "Extract specific pages", "#FF6B35", "split") { startSplit() },
            Tool("Rotate Pages", "Change page orientation", "#FF6B35", "rotate") { startRotate() },
            Tool("Delete Pages", "Remove unwanted pages", "#FF6B35", "delete") { startDeletePages() }
        ))

        addCategory(root, "🔒 Protect & Secure", listOf(
            Tool("Encrypt PDF", "Password protect your PDF", "#004E89", "encrypt") { startEncrypt() },
            Tool("Add Watermark", "Text or image watermark", "#004E89", "watermark") { startWatermark() },
            Tool("Redact Content", "Permanently hide sensitive info", "#004E89", "redact") { startRedact() }
        ))

        addCategory(root, "📝 Annotate & Markup", listOf(
            Tool("Add Page Numbers", "Automatic numbering", "#1A936F", "pagenums") { startPageNumbers() },
            Tool("Header & Footer", "Add custom headers", "#1A936F", "header") { startHeaderFooter() },
            Tool("Flatten Annotations", "Make annotations permanent", "#1A936F", "flatten") { startFlatten() }
        ))

        addCategory(root, "🔄 Convert & Create", listOf(
            Tool("Images to PDF", "Convert photos to PDF", "#9B59B6", "img2pdf") { startImagesToPdf() },
            Tool("Compress PDF", "Reduce file size", "#9B59B6", "compress") { startCompress() },
            Tool("PDF to Images", "Extract pages as images", "#9B59B6", "pdf2img") { startPdfToImages() }
        ))

        addCategory(root, "✍️ Sign & Fill", listOf(
            Tool("Digital Signature", "Sign with handwritten signature", "#E74C3C", "sign") { startSign() },
            Tool("Fill Forms", "Complete PDF forms", "#E74C3C", "forms") { startFillForms() }
        ))

        setContentView(scroll)
    }

    private fun addCategory(parent: LinearLayout, title: String, tools: List<Tool>) {
        parent.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(0, dp(24), 0, dp(12))
        })

        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        tools.chunked(2).forEach { row ->
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { tool ->
                rowLayout.addView(createToolCard(tool))
            }
            if (row.size == 1) {
                rowLayout.addView(Space(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
                })
            }
            grid.addView(rowLayout)
        }
        parent.addView(grid)
    }

    private fun createToolCard(tool: Tool): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(140), 1f).apply {
                marginEnd = dp(8)
                bottomMargin = dp(12)
            }
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.parseColor(tool.color).let { c ->
                    Color.argb(60, Color.red(c), Color.green(c), Color.blue(c))
                })
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { tool.action() }

            addView(FrameLayout(this@ToolsActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(tool.color))
                    cornerRadius = dp(24).toFloat()
                    alpha = 40
                }
            })

            addView(TextView(this@ToolsActivity).apply {
                text = tool.name
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
            })

            addView(TextView(this@ToolsActivity).apply {
                text = tool.desc
                textSize = 11f
                setTextColor(Color.parseColor("#888888"))
                gravity = Gravity.CENTER
            })
        }
    }

    private fun createCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(16)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(12).toFloat()
            }
        }
    }

    private fun updateUI() {
        selectedCountLabel?.text = "📄 Selected: ${selectedFiles.size} file${if (selectedFiles.size != 1) "s" else ""}"
    }

    private fun startMerge() {
        selectedFiles.clear(); updateUI()
        multiFilePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Merge PDFs", "Select 2+ PDFs") {
            if (selectedFiles.size < 2) { toast("Select at least 2 PDFs"); return@showProcessDialog }
            processWithProgress("Merging...") {
                val output = File(cacheDir, "merged_${time()}.pdf")
                pdfOps.mergePdfs(selectedFiles, output)
                    .onSuccess { share(it, "Merged successfully") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startSplit() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        showInputDialog("Split PDF", "Enter page ranges (e.g. 1-3,5,7-10)") { input ->
            val ranges = parseRanges(input)
            if (ranges.isEmpty()) { toast("Invalid ranges"); return@showInputDialog }
            processWithProgress("Splitting...") {
                pdfOps.splitPdf(selectedFiles.first(), cacheDir, ranges)
                    .onSuccess { files -> toast("Created ${files.size} files") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startCompress() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Compress PDF", "Select a PDF") {
            processWithProgress("Compressing...") {
                val file = selectedFiles.first()
                val output = File(cacheDir, "compressed_${time()}.pdf")
                pdfOps.compressPdf(file, output, 9)
                    .onSuccess { out ->
                        val reduction = ((file.length() - out.length()) * 100 / file.length().toFloat()).toInt()
                        share(out, "Compressed: $reduction% smaller")
                    }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startEncrypt() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val userPass = EditText(this).apply { hint = "User password (optional)" }
        val ownerPass = EditText(this).apply { hint = "Owner password (required)" }
        layout.addView(userPass); layout.addView(ownerPass)
        AlertDialog.Builder(this).setTitle("🔒 Encrypt PDF").setView(layout)
            .setPositiveButton("Encrypt") { _, _ ->
                processWithProgress("Encrypting...") {
                    val output = File(cacheDir, "encrypted_${time()}.pdf")
                    pdfOps.encryptPdf(selectedFiles.first(), output,
                        userPass.text.toString().takeIf { it.isNotBlank() },
                        ownerPass.text.toString())
                        .onSuccess { share(it, "Encrypted successfully") }
                        .onFailure { toast("Failed: ${it.message}") }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun startWatermark() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        showInputDialog("Add Watermark", "Enter watermark text") { text ->
            processWithProgress("Adding watermark...") {
                val output = File(cacheDir, "watermarked_${time()}.pdf")
                pdfOps.addTextWatermark(selectedFiles.first(), output, text)
                    .onSuccess { share(it, "Watermark added") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startRotate() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        AlertDialog.Builder(this)
            .setTitle("Rotate Pages")
            .setItems(arrayOf("Rotate 90°", "Rotate 180°", "Rotate 270°")) { _, which ->
                val degrees = when (which) { 0 -> 90; 1 -> 180; else -> 270 }
                showInputDialog("Pages", "Enter page numbers (e.g. 1,3,5) or leave blank for all") { pages ->
                    processWithProgress("Rotating...") {
                        val file = selectedFiles.first()
                        val output = File(cacheDir, "rotated_${time()}.pdf")
                        val pageList = if (pages.isBlank()) {
                            (1..1000).toList()
                        } else parsePageList(pages)
                        pdfOps.rotatePages(file, output, pageList.associateWith { degrees })
                            .onSuccess { share(it, "Rotated successfully") }
                            .onFailure { toast("Failed: ${it.message}") }
                    }
                }
            }.show()
    }

    private fun startDeletePages() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        showInputDialog("Delete Pages", "Enter pages to delete (e.g. 2,4,6-8)") { input ->
            val pages = parsePageList(input)
            if (pages.isEmpty()) { toast("No valid pages"); return@showInputDialog }
            processWithProgress("Deleting pages...") {
                val output = File(cacheDir, "deleted_${time()}.pdf")
                pdfOps.deletePages(selectedFiles.first(), output, pages)
                    .onSuccess { share(it, "Pages deleted") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startPageNumbers() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        showProcessDialog("Add Page Numbers", "Select a PDF") {
            processWithProgress("Adding page numbers...") {
                val output = File(cacheDir, "numbered_${time()}.pdf")
                pdfOps.addPageNumbers(selectedFiles.first(), output)
                    .onSuccess { share(it, "Page numbers added") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startHeaderFooter() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val header = EditText(this).apply { hint = "Header text (optional)" }
        val footer = EditText(this).apply { hint = "Footer text (optional)" }
        layout.addView(header); layout.addView(footer)
        AlertDialog.Builder(this).setTitle("Header / Footer").setView(layout)
            .setPositiveButton("Apply") { _, _ ->
                processWithProgress("Adding header/footer...") {
                    val output = File(cacheDir, "hf_${time()}.pdf")
                    pdfOps.addHeaderFooter(selectedFiles.first(), output,
                        header.text.toString().takeIf { it.isNotBlank() },
                        footer.text.toString().takeIf { it.isNotBlank() })
                        .onSuccess { share(it, "Added successfully") }
                        .onFailure { toast("Failed: ${it.message}") }
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun startImagesToPdf() {
        selectedFiles.clear(); updateUI()
        imagePicker.launch(arrayOf("image/*"))
        showProcessDialog("Images to PDF", "Select images") {
            if (selectedFiles.isEmpty()) { toast("No images selected"); return@showProcessDialog }
            processWithProgress("Converting...") {
                val output = File(cacheDir, "images_${time()}.pdf")
                pdfOps.imagesToPdf(selectedFiles, output)
                    .onSuccess { share(it, "PDF created") }
                    .onFailure { toast("Failed: ${it.message}") }
            }
        }
    }

    private fun startPdfToImages() {
        selectedFiles.clear(); updateUI()
        filePicker.launch(arrayOf("application/pdf"))
        processWithProgress("Converting...") {
            pdfOps.pdfToImages(selectedFiles.first(), cacheDir)
                .onSuccess { toast("Extracted ${it.size} images") }
                .onFailure { toast("Failed: ${it.message}") }
        }
    }

    private fun startSign() {
        toast("Digital Signature - Use annotation tools in viewer!")
    }

    private fun startFillForms() {
        toast("Form filling - Coming in next update!")
    }

    private fun startRedact() {
        toast("Redaction - Use black highlighter in annotation mode!")
    }

    private fun startFlatten() {
        toast("Flatten - Coming in next update!")
    }

    private fun showProcessDialog(title: String, msg: String, action: () -> Unit) {
        AlertDialog.Builder(this).setTitle(title).setMessage(msg)
            .setPositiveButton("Process") { _, _ -> action() }
            .setNegativeButton("Cancel", null).show()
    }

    private fun showInputDialog(title: String, hint: String, action: (String) -> Unit) {
        val et = EditText(this).apply { this.hint = hint }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ -> action(et.text.toString()) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun processWithProgress(msg: String, block: suspend () -> Unit) {
        val dialogLayout = LinearLayout(this).apply {
            setPadding(dp(24), dp(24), dp(24), dp(24))
            gravity = Gravity.CENTER
            addView(ProgressBar(this@ToolsActivity))
            addView(TextView(this@ToolsActivity).apply {
                text = msg
                setPadding(dp(16), 0, 0, 0)
                setTextColor(Color.WHITE)
            })
        }
        val dialog = AlertDialog.Builder(this)
            .setView(dialogLayout)
            .setCancelable(false)
            .create()
        dialog.show()

        lifecycleScope.launch {
            withContext(Dispatchers.IO) { block() }
            dialog.dismiss()
        }
    }

    private fun share(file: File, msg: String) {
        toast(msg)
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "com.propdf.editor.provider", file)
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }, "Share PDF"))
        } catch (e: Exception) { toast("Cannot share") }
    }

    private fun parseRanges(input: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        input.split(",").forEach { part ->
            val t = part.trim()
            if (t.contains("-")) {
                val p = t.split("-")
                val s = p.getOrNull(0)?.trim()?.toIntOrNull()
                val e = p.getOrNull(1)?.trim()?.toIntOrNull()
                if (s != null && e != null) result.add(s..e)
            } else {
                t.toIntOrNull()?.let { result.add(it..it) }
            }
        }
        return result
    }

    private fun parsePageList(input: String): List<Int> {
        val result = mutableListOf<Int>()
        input.split(",").forEach { part ->
            val t = part.trim()
            if (t.contains("-")) {
                val p = t.split("-")
                val s = p.getOrNull(0)?.trim()?.toIntOrNull()
                val e = p.getOrNull(1)?.trim()?.toIntOrNull()
                if (s != null && e != null) result.addAll(s..e)
            } else {
                t.toIntOrNull()?.let { result.add(it) }
            }
        }
        return result
    }

    private fun time() = System.currentTimeMillis()
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    data class Tool(val name: String, val desc: String, val color: String, val id: String, val action: () -> Unit)
}
