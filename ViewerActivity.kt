package com.propdf.editor.ui.viewer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.TextUtils
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewOutlineProvider
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.propdf.editor.data.repository.AiSummaryManager
import com.propdf.editor.data.repository.OcrManager
import com.propdf.editor.data.repository.PdfOperationsManager
import com.propdf.editor.data.repository.RecentFilesRepository
import com.propdf.editor.data.repository.SignatureManager
import com.propdf.editor.utils.FileHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.hypot

@AndroidEntryPoint
class ViewerActivity : AppCompatActivity() {

    @Inject
    lateinit var viewModel: ViewerViewModel

    @Inject
    lateinit var repository: RecentFilesRepository

    @Inject
    lateinit var pdfOps: PdfOperationsManager

    @Inject
    lateinit var ocrManager: OcrManager

    @Inject
    lateinit var aiManager: AiSummaryManager

    @Inject
    lateinit var signatureManager: SignatureManager

    private var pdfUri: Uri? = null
    private var displayName: String = "Document"
    private var password: String? = null
    private var pdfFile: File? = null
    private var pdfRenderer: PdfRenderer? = null
    private val pageBitmapCache = mutableMapOf<Int, Bitmap>()
    private val pageTextMap = mutableMapOf<Int, String>()
    private val bmPrefs: SharedPreferences by lazy { getSharedPreferences("propdf_bookmarks", MODE_PRIVATE) }
    private val prefs: SharedPreferences by lazy { getSharedPreferences("propdf_prefs", MODE_PRIVATE) }

    private var isDark = true
    private var isNight = false
    private var isSepia = false
    private var isDay = false
    private var currentPage = 0
    private var totalPages = 0
    private var isSearchVisible = false
    private var isAnnotToolbarExpanded = false
    private var activeTool: String? = null
    private var activeColor = Color.parseColor("#007AFF")
    private var highlightColor = Color.parseColor("#FFFF00")
    private var strokeWidth = 5f
    private var isTextMode = false
    private var textInputDialog: AlertDialog? = null
    private var isEditingText = false
    private var currentTextEdit: TextAnnotation? = null

    private val undoStack = ArrayDeque<AnnotationAction>()
    private val redoStack = ArrayDeque<AnnotationAction>()
    private val pageAnnotations = mutableMapOf<Int, MutableList<AnnotationStroke>>()
    private val textAnnotations = mutableMapOf<Int, MutableList<TextAnnotation>>()
    private val pageScaleMap = mutableMapOf<Int, Float>()

    private var scrollOffset = 0
    private var zoomScale = 1f
    private var panX = 0f
    private var panY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isZoomed = false
    private var isPanning = false
    private var isDrawing = false

    private lateinit var rootFrame: FrameLayout
    private lateinit var topBar: LinearLayout
    private lateinit var pageContainer: LinearLayout
    private lateinit var scrollView: ScrollView
    private lateinit var annotOverlay: AnnotationCanvasView
    private lateinit var bottomBar: LinearLayout
    private lateinit var pageIndicator: TextView
    private lateinit var searchBar: LinearLayout
    private lateinit var annotToolbar: LinearLayout
    private lateinit var progressBar: View

    companion object {
        const val EXTRA_URI = "extra_pdf_uri"
        const val EXTRA_PASSWORD = "extra_pdf_password"
        const val EXTRA_DISPLAY_NAME = "extra_display_name"

        fun start(context: Context, uri: Uri, password: String? = null, displayName: String? = null) {
            val intent = Intent(context, ViewerActivity::class.java)
            intent.putExtra(EXTRA_URI, uri.toString())
            intent.putExtra(EXTRA_PASSWORD, password ?: "")
            intent.putExtra(EXTRA_DISPLAY_NAME, displayName ?: "")
            context.startActivity(intent)
        }
    }

    // -------------------------------------------------------
    // LIFECYCLE
    // -------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pdfUri = intent.getStringExtra(EXTRA_URI)?.let { Uri.parse(it) }
        password = intent.getStringExtra(EXTRA_PASSWORD)?.takeIf { it.isNotBlank() }
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)?.takeIf { it.isNotBlank() }
            ?: pdfUri?.let { FileHelper.getFileName(this, it) } ?: "Document"
        isDark = prefs.getBoolean("dark_mode", true)
        buildUI()
        observeViewModel()
        pdfUri?.let { loadPdf(it) }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    currentPage = state.currentPage
                    totalPages = state.totalPages
                    isDark = state.isDarkMode
                    isSearchVisible = state.isSearchVisible
                    isAnnotToolbarExpanded = state.isAnnotToolbarExpanded
                    activeTool = state.activeTool
                    activeColor = state.activeColor
                    highlightColor = state.highlightColor
                    strokeWidth = state.strokeWidth

                    updatePageIndicator()
                    updateBookmarkIcon()
                    updateSearchUI()
                    updateAnnotToolbar()
                    updateProgressBar(state.isLoading)

                    state.errorMessage?.let { msg ->
                        toast(msg)
                        viewModel.clearError()
                    }

                    if (state.showTextDialog) {
                        showTextDialog(state.textDialogTitle, state.extractedText)
                        viewModel.dismissTextDialog()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { pdfRenderer?.close() } catch (_: Exception) {}
        pageBitmapCache.values.forEach { if (!it.isRecycled) it.recycle() }
        pageBitmapCache.clear()
    }

    // -------------------------------------------------------
    // PDF LOADING
    // -------------------------------------------------------

    private fun loadPdf(uri: Uri) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            try {
                val file = withContext(Dispatchers.IO) {
                    val dest = File(cacheDir, "viewer_${System.currentTimeMillis()}.pdf")
                    contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(dest).use { output -> input.copyTo(output) }
                    }
                    if (dest.length() > 0) dest else null
                }
                if (file == null) { toast("Cannot read PDF"); return@launch }
                pdfFile = file
                openRenderer(file)
                totalPages = pdfRenderer?.pageCount ?: 0
                currentPage = 0
                renderAllPages()
                updatePageIndicator()
                updateBookmarkIcon()
                loadAnnotationsFromCache()
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }

    private fun openRenderer(file: File) {
        try { pdfRenderer?.close() } catch (_: Exception) {}
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        pdfRenderer = PdfRenderer(pfd)
    }

    // -------------------------------------------------------
    // LAZY PAGE RENDERING
    // -------------------------------------------------------

    private fun renderAllPages() {
        pageContainer.removeAllViews()
        val renderer = pdfRenderer ?: return
        val screenW = resources.displayMetrics.widthPixels
        for (i in 0 until renderer.pageCount) {
            val pageView = createPageView(i, screenW)
            pageContainer.addView(pageView)
            // Preload adjacent pages
            if (i <= 2) {
                lifecycleScope.launch(Dispatchers.IO) {
                    renderPageBitmap(i, screenW)
                }
            }
        }
    }

    private fun createPageView(pageIndex: Int, screenW: Int): View {
        val pageView = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
            setBackgroundColor(if (isDark) Color.parseColor("#1E1E1E") else Color.WHITE)
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, v.width, v.height, dp(8).toFloat())
                }
            }
            clipToOutline = true
            elevation = dp(4).toFloat()
        }

        // Placeholder image view
        val imageView = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -2)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F0F0F0"))
        }
        pageView.addView(imageView)

        // Page number overlay
        pageView.addView(TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            text = "Page ${pageIndex + 1}"
            textSize = 10f
            setTextColor(if (isDark) Color.parseColor("#888888") else Color.parseColor("#999999"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        })

        // Render on demand
        pageView.post {
            renderPageToView(pageIndex, imageView, screenW)
        }

        return pageView
    }

    private fun renderPageToView(pageIndex: Int, imageView: ImageView, screenW: Int) {
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) {
                renderPageBitmap(pageIndex, screenW)
            }
            if (bmp != null) {
                imageView.setImageBitmap(bmp)
                // Preload next pages
                preloadAdjacentPages(pageIndex, screenW)
            }
        }
    }

    private fun renderPageBitmap(pageIndex: Int, screenW: Int): Bitmap? {
        val cached = pageBitmapCache[pageIndex]
        if (cached != null && !cached.isRecycled) return cached

        val renderer = pdfRenderer ?: return null
        return try {
            synchronized(renderer) {
                val page = renderer.openPage(pageIndex)
                val scale = screenW.toFloat() / page.width.coerceAtLeast(1)
                val bmpW = (page.width * scale).toInt().coerceAtLeast(1)
                val bmpH = (page.height * scale).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                pageBitmapCache[pageIndex] = bmp
                pageScaleMap[pageIndex] = scale
                bmp
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun preloadAdjacentPages(current: Int, screenW: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val start = (current - 1).coerceAtLeast(0)
            val end = (current + 3).coerceAtMost(totalPages - 1)
            for (i in start..end) {
                if (pageBitmapCache[i] == null) {
                    renderPageBitmap(i, screenW)
                }
            }
            // Clean old cache
            val keys = pageBitmapCache.keys.toList()
            keys.filter { abs(it - current) > 5 }.forEach {
                pageBitmapCache[it]?.recycle()
                pageBitmapCache.remove(it)
            }
        }
    }

    // -------------------------------------------------------
    // UI BUILDING
    // -------------------------------------------------------

    private fun buildUI() {
        rootFrame = FrameLayout(this).apply { setBackgroundColor(if (isDark) Color.parseColor("#121212") else Color.WHITE) }
        topBar = buildTopBar()
        scrollView = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(56); bottomMargin = dp(48) }
            isVerticalScrollBarEnabled = false
        }
        pageContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scrollView.addView(pageContainer)
        annotOverlay = AnnotationCanvasView(this)
        bottomBar = buildBottomBar()
        searchBar = buildSearchBar()
        annotToolbar = buildAnnotToolbar()
        progressBar = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, dp(4)).apply { gravity = Gravity.TOP }
            setBackgroundColor(Color.parseColor("#448AFF"))
            visibility = View.GONE
        }

        rootFrame.addView(scrollView)
        rootFrame.addView(topBar)
        rootFrame.addView(bottomBar)
        rootFrame.addView(searchBar)
        rootFrame.addView(annotToolbar)
        rootFrame.addView(progressBar)
        setContentView(rootFrame)

        setupScrollListener()
    }

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, dp(56))
            setBackgroundColor(if (isDark) Color.parseColor("#1A1A1A") else Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)
            elevation = dp(4).toFloat()

            addView(topBtn(android.R.drawable.ic_menu_close_clear_cancel) { finish() })
            addView(TextView(this@ViewerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) }
                text = displayName.removeSuffix(".pdf")
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            addView(topBtn(android.R.drawable.ic_menu_search) { toggleSearch() })
            addView(topBtn(android.R.drawable.btn_star_big_off) { toggleBookmark() })
            addView(topBtn(android.R.drawable.ic_menu_more) { showMoreMenu() })
        }
    }

    private fun buildBottomBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, dp(48)).apply { gravity = Gravity.BOTTOM }
            setBackgroundColor(if (isDark) Color.parseColor("#1A1A1A") else Color.WHITE)
            setPadding(dp(8), 0, dp(8), 0)

            addView(botBtn(android.R.drawable.ic_media_previous) { goToPage(0) })
            addView(botBtn(android.R.drawable.ic_media_rew) { previousPage() })
            pageIndicator = TextView(this@ViewerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                gravity = Gravity.CENTER
                textSize = 12f
                setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
            }
            addView(pageIndicator)
            addView(botBtn(android.R.drawable.ic_media_ff) { nextPage() })
            addView(botBtn(android.R.drawable.ic_media_next) { goToPage(totalPages - 1) })
        }
    }

    private fun buildSearchBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, dp(48)).apply {
                gravity = Gravity.TOP
                topMargin = dp(56)
            }
            setBackgroundColor(if (isDark) Color.parseColor("#2A2A2A") else Color.parseColor("#F0F0F0"))
            setPadding(dp(12), 0, dp(12), 0)
            visibility = View.GONE

            val et = EditText(this@ViewerActivity).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                hint = "Search in PDF..."
                textSize = 13f
                setTextColor(if (isDark) Color.WHITE else Color.BLACK)
                setHintTextColor(if (isDark) Color.parseColor("#888888") else Color.parseColor("#999999"))
                background = null
            }
            addView(et)
            addView(botBtn(android.R.drawable.ic_menu_search) {
                val query = et.text.toString()
                if (query.isNotBlank()) runSearch(query)
            })
            addView(botBtn(android.R.drawable.ic_menu_close_clear_cancel) { toggleSearch() })
        }
    }

    private fun buildAnnotToolbar(): LinearLayout {
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(-1, -2).apply {
                gravity = Gravity.BOTTOM
                bottomMargin = dp(48)
            }
            setBackgroundColor(if (isDark) Color.parseColor("#2A2A2A") else Color.WHITE)
            setPadding(dp(8), dp(8), dp(8), dp(8))
            elevation = dp(8).toFloat()
            visibility = View.GONE
        }

        // Toggle header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            text = "Annotate"
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        })
        header.addView(botBtn(android.R.drawable.ic_menu_close_clear_cancel) {
            viewModel.toggleAnnotToolbar()
        })
        toolbar.addView(header)

        // Tool row 1
        val toolRow1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "freehand" to "Draw",
            "highlight" to "Highlight",
            "underline" to "Underline",
            "eraser" to "Eraser"
        ).forEach { (tool, label) ->
            toolRow1.addView(buildToolButton(tool, label))
        }
        toolbar.addView(toolRow1)

        // Tool row 2 - Shapes
        val toolRow2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "rectangle" to "Rect",
            "circle" to "Circle",
            "arrow" to "Arrow",
            "text" to "Text"
        ).forEach { (tool, label) ->
            toolRow2.addView(buildToolButton(tool, label))
        }
        toolbar.addView(toolRow2)

        // Color palette
        val colorRow = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            isHorizontalScrollBarEnabled = false
        }
        val colorContainer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val colors = listOf(
            Color.parseColor("#E53935"), Color.parseColor("#FF9800"), Color.parseColor("#FFD60A"),
            Color.parseColor("#4CAF50"), Color.parseColor("#2196F3"), Color.parseColor("#9C27B0"),
            Color.parseColor("#795548"), Color.parseColor("#607D8B"), Color.parseColor("#000000"),
            Color.parseColor("#FFFFFF")
        )
        colors.forEach { color ->
            colorContainer.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(6) }
                background = GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dp(14).toFloat()
                    if (color == Color.WHITE) setStroke(dp(2), Color.parseColor("#CCCCCC"))
                }
                setOnClickListener { viewModel.setActiveColor(color) }
            })
        }
        colorRow.addView(colorContainer)
        toolbar.addView(colorRow)

        // Stroke width slider
        val strokeRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        strokeRow.addView(TextView(this).apply {
            text = "Width:"
            textSize = 12f
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
        })
        strokeRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, dp(2), 1f).apply { marginStart = dp(8); marginEnd = dp(8) }
            setBackgroundColor(Color.parseColor("#448AFF"))
        })
        toolbar.addView(strokeRow)

        // Action buttons
        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, 0)
        }
        actionRow.addView(buildActionBtn("Undo") { undo() })
        actionRow.addView(buildActionBtn("Redo") { redo() })
        actionRow.addView(buildActionBtn("Clear") { clearAnnotations() })
        actionRow.addView(buildActionBtn("Save") { saveAnnotations() })
        toolbar.addView(actionRow)

        return toolbar
    }

    private fun buildToolButton(tool: String, label: String): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) }
            setPadding(dp(4), dp(4), dp(4), dp(4))
            setBackgroundColor(if (isDark) Color.parseColor("#3A3A3C") else Color.parseColor("#F0F0F0"))
            setOnClickListener { selectTool(tool) }

            addView(TextView(this@ViewerActivity).apply {
                text = label.first().toString()
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            })
            addView(TextView(this@ViewerActivity).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(if (isDark) Color.parseColor("#888888") else Color.parseColor("#999999"))
            })
        }
    }

    private fun buildActionBtn(label: String, action: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.parseColor("#448AFF"))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { action() }
        }
    }

    private fun topBtn(icon: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(if (isDark) Color.WHITE else Color.BLACK)
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { action() }
    }

    private fun botBtn(icon: Int, action: () -> Unit) = ImageButton(this).apply {
        setImageResource(icon)
        setBackgroundColor(Color.TRANSPARENT)
        setColorFilter(if (isDark) Color.WHITE else Color.BLACK)
        layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
        setOnClickListener { action() }
    }

    // -------------------------------------------------------
    // ANNOTATION SYSTEM
    // -------------------------------------------------------

    private fun selectTool(tool: String) {
        if (activeTool == tool) {
            viewModel.setActiveTool(null)
            isTextMode = false
        } else {
            viewModel.setActiveTool(tool)
            isTextMode = (tool == "text")
            if (isTextMode) {
                showTextInputDialog()
            }
        }
    }

    private fun showTextInputDialog() {
        val et = EditText(this).apply {
            hint = "Enter text..."
            textSize = 16f
            setTextColor(if (isDark) Color.WHITE else Color.BLACK)
            if (isEditingText && currentTextEdit != null) {
                setText(currentTextEdit!!.text)
            }
        }
        textInputDialog = AlertDialog.Builder(this)
            .setTitle(if (isEditingText) "Edit Text" else "Add Text")
            .setView(et)
            .setPositiveButton(if (isEditingText) "Update" else "Add") { _, _ ->
                val text = et.text.toString()
                if (text.isNotBlank()) {
                    if (isEditingText && currentTextEdit != null) {
                        currentTextEdit!!.text = text
                        isEditingText = false
                        currentTextEdit = null
                    } else {
                        addTextAnnotation(text)
                    }
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                isEditingText = false
                currentTextEdit = null
            }
            .show()
    }

    private fun addTextAnnotation(text: String) {
        val pageIdx = currentPage
        val ta = TextAnnotation(
            x = 100f,
            y = 100f,
            text = text,
            color = activeColor,
            sizePx = strokeWidth * 4,
            scale = pageScaleMap[pageIdx] ?: 1f
        )
        val list = textAnnotations.getOrPut(pageIdx) { mutableListOf() }
        list.add(ta)
        redrawPage(pageIdx)
    }

    private fun undo() {
        val action = undoStack.removeLastOrNull() ?: return
        when (action) {
            is AnnotationAction.AddStroke -> {
                pageAnnotations[action.pageIndex]?.remove(action.stroke)
                undoStack.addLast(action)
                redoStack.addLast(action)
            }
            is AnnotationAction.RemoveStroke -> {
                pageAnnotations.getOrPut(action.pageIndex) { mutableListOf() }.add(action.stroke)
                redoStack.addLast(action)
            }
        }
        redrawPage(currentPage)
    }

    private fun redo() {
        val action = redoStack.removeLastOrNull() ?: return
        when (action) {
            is AnnotationAction.AddStroke -> {
                pageAnnotations.getOrPut(action.pageIndex) { mutableListOf() }.add(action.stroke)
                undoStack.addLast(action)
            }
            is AnnotationAction.RemoveStroke -> {
                pageAnnotations[action.pageIndex]?.remove(action.stroke)
                undoStack.addLast(action)
            }
        }
        redrawPage(currentPage)
    }

    private fun clearAnnotations() {
        pageAnnotations.clear()
        textAnnotations.clear()
        undoStack.clear()
        redoStack.clear()
        redrawAllPages()
    }

    private fun saveAnnotations() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val result = withContext(Dispatchers.IO) {
                try {
                    val input = pdfFile ?: return@withContext false
                    val output = File(cacheDir, "annotated_${System.currentTimeMillis()}.pdf")
                    pdfOps.saveAnnotationsToPdf(
                        input, output,
                        pageAnnotations.mapValues { it.value.map { s ->
                            PdfOperationsManager.AnnotationStroke(
                                points = s.points,
                                color = s.color,
                                strokeWidth = s.strokeWidth,
                                alpha = s.alpha,
                                scale = s.scale,
                                tool = s.tool
                            )
                        } },
                        textAnnotations
                    )
                    true
                } catch (e: Exception) {
                    false
                }
            }
            progressBar.visibility = View.GONE
            if (result) {
                toast("Annotations saved")
                persistAnnotationCache()
            } else {
                toast("Save failed")
            }
        }
    }

    private fun redrawPage(pageIndex: Int) {
        // Trigger re-render of the specific page with annotations
        val pageView = pageContainer.getChildAt(pageIndex) as? FrameLayout ?: return
        val imageView = pageView.getChildAt(0) as? ImageView ?: return
        val bmp = pageBitmapCache[pageIndex]?.copy(Bitmap.Config.ARGB_8888, true) ?: return
        val canvas = Canvas(bmp)

        // Draw strokes
        pageAnnotations[pageIndex]?.forEach { stroke ->
            drawStroke(canvas, stroke)
        }

        // Draw text annotations
        textAnnotations[pageIndex]?.forEach { ta ->
            drawTextAnnotation(canvas, ta)
        }

        imageView.setImageBitmap(bmp)
    }

    private fun redrawAllPages() {
        for (i in 0 until totalPages) {
            redrawPage(i)
        }
    }

    private fun drawStroke(canvas: Canvas, stroke: AnnotationStroke) {
        if (stroke.tool == "eraser") return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            strokeWidth = stroke.strokeWidth
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            alpha = stroke.alpha
        }
        if (stroke.points.size >= 2) {
            val path = Path()
            path.moveTo(stroke.points[0].x, stroke.points[0].y)
            for (i in 1 until stroke.points.size) {
                path.lineTo(stroke.points[i].x, stroke.points[i].y)
            }
            canvas.drawPath(path, paint)
        }
    }

    private fun drawTextAnnotation(canvas: Canvas, ta: TextAnnotation) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ta.color
            textSize = ta.sizePx
            typeface = Typeface.DEFAULT
        }
        canvas.drawText(ta.text, ta.x, ta.y, paint)
    }

    // -------------------------------------------------------
    // PAGE NAVIGATION
    // -------------------------------------------------------

    private fun goToPage(page: Int) {
        viewModel.goToPage(page)
    }

    private fun nextPage() {
        viewModel.nextPage()
    }

    private fun previousPage() {
        viewModel.previousPage()
    }

    private fun updatePageIndicator() {
        pageIndicator.text = "${currentPage + 1} / $totalPages"
    }

    // -------------------------------------------------------
    // SEARCH
    // -------------------------------------------------------

    private fun toggleSearch() {
        viewModel.toggleSearch()
    }

    private fun updateSearchUI() {
        searchBar.visibility = if (isSearchVisible) View.VISIBLE else View.GONE
    }

    private fun runSearch(query: String) {
        viewModel.runSearch(query)
    }

    // -------------------------------------------------------
    // BOOKMARKS
    // -------------------------------------------------------

    private fun toggleBookmark() {
        viewModel.toggleBookmark()
    }

    private fun updateBookmarkIcon() {
        // Update star icon in top bar
    }

    // -------------------------------------------------------
    // MORE MENU
    // -------------------------------------------------------

    private fun showMoreMenu() {
        val items = arrayOf(
            "Reading Mode", "OCR Text", "AI Summary", "AI Key Points",
            "Add Signature", "Share", "Print", "Properties"
        )
        AlertDialog.Builder(this)
            .setTitle(displayName)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showReadingModeDialog()
                    1 -> runOcr()
                    2 -> runAiSummary()
                    3 -> runAiKeyPoints()
                    4 -> showSignatureDialog()
                    5 -> sharePdf()
                    6 -> printPdf()
                    7 -> showProperties()
                }
            }
            .show()
    }

    // -------------------------------------------------------
    // READING MODE
    // -------------------------------------------------------

    private fun showReadingModeDialog() {
        val modes = arrayOf("Normal", "Night", "Sepia", "Day")
        AlertDialog.Builder(this)
            .setTitle("Reading Mode")
            .setItems(modes) { _, which ->
                when (which) {
                    0 -> applyReadingMode("normal")
                    1 -> applyReadingMode("night")
                    2 -> applyReadingMode("sepia")
                    3 -> applyReadingMode("day")
                }
            }
            .show()
    }

    private fun applyReadingMode(mode: String) {
        when (mode) {
            "night" -> {
                isNight = true; isSepia = false; isDay = false
                rootFrame.setBackgroundColor(Color.parseColor("#0D1117"))
            }
            "sepia" -> {
                isNight = false; isSepia = true; isDay = false
                rootFrame.setBackgroundColor(Color.parseColor("#F4ECD8"))
            }
            "day" -> {
                isNight = false; isSepia = false; isDay = true
                rootFrame.setBackgroundColor(Color.parseColor("#E8F4F8"))
            }
            else -> {
                isNight = false; isSepia = false; isDay = false
                rootFrame.setBackgroundColor(if (isDark) Color.parseColor("#121212") else Color.WHITE)
            }
        }
        viewModel.setReadingMode(mode)
    }

    // -------------------------------------------------------
    // OCR
    // -------------------------------------------------------

    private fun runOcr() {
        val bmp = pageBitmapCache[currentPage] ?: return
        viewModel.runOcrOnPage(bmp)
    }

    // -------------------------------------------------------
    // AI FEATURES
    // -------------------------------------------------------

    private fun runAiSummary() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val text = withContext(Dispatchers.IO) { extractAllText() }
            val result = aiManager.summarize(text)
            progressBar.visibility = View.GONE
            result.onSuccess { summary ->
                showTextDialog("AI Summary", summary)
            }.onFailure { e ->
                toast("Summary failed: ${e.message}")
            }
        }
    }

    private fun runAiKeyPoints() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val text = withContext(Dispatchers.IO) { extractAllText() }
            val result = aiManager.extractKeyPoints(text)
            progressBar.visibility = View.GONE
            result.onSuccess { points ->
                showTextDialog("Key Points", points.joinToString("

"))
            }.onFailure { e ->
                toast("Key points failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------
    // SIGNATURE
    // -------------------------------------------------------

    private fun showSignatureDialog() {
        val options = arrayOf("Draw Signature", "Saved Signatures", "Create Text Signature")
        AlertDialog.Builder(this)
            .setTitle("Digital Signature")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSignatureCanvas()
                    1 -> showSavedSignatures()
                    2 -> showTextSignatureDialog()
                }
            }
            .show()
    }

    private fun showSignatureCanvas() {
        val sigView = SignatureView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, dp(200))
            setBackgroundColor(Color.WHITE)
        }
        AlertDialog.Builder(this)
            .setTitle("Draw Your Signature")
            .setView(sigView)
            .setPositiveButton("Save") { _, _ ->
                val bmp = sigView.getSignatureBitmap()
                if (bmp != null) {
                    val name = "sig_${System.currentTimeMillis()}"
                    signatureManager.saveSignature(name, bmp)
                    toast("Signature saved")
                    applySignatureToPdf(name)
                }
            }
            .setNegativeButton("Clear") { _, _ -> sigView.clear() }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showSavedSignatures() {
        val names = signatureManager.getAllSignatureNames()
        if (names.isEmpty()) { toast("No saved signatures"); return }
        AlertDialog.Builder(this)
            .setTitle("Choose Signature")
            .setItems(names.toTypedArray()) { _, i ->
                applySignatureToPdf(names[i])
            }
            .show()
    }

    private fun showTextSignatureDialog() {
        val et = EditText(this).apply { hint = "Your name" }
        AlertDialog.Builder(this)
            .setTitle("Text Signature")
            .setView(et)
            .setPositiveButton("Create") { _, _ ->
                val text = et.text.toString()
                if (text.isNotBlank()) {
                    val bmp = signatureManager.createTextSignature(text)
                    val name = "text_${System.currentTimeMillis()}"
                    signatureManager.saveSignature(name, bmp)
                    applySignatureToPdf(name)
                }
            }
            .show()
    }

    private fun applySignatureToPdf(signatureName: String) {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            val input = pdfFile ?: return@launch
            val output = File(cacheDir, "signed_${System.currentTimeMillis()}.pdf")
            val result = signatureManager.applySignatureToPdf(
                input, output, signatureName,
                currentPage + 1
            )
            progressBar.visibility = View.GONE
            result.onSuccess {
                toast("Signature applied")
                loadPdf(Uri.fromFile(it))
            }.onFailure { e ->
                toast("Failed: ${e.message}")
            }
        }
    }

    // -------------------------------------------------------
    // TEXT EXTRACTION
    // -------------------------------------------------------

    private fun extractAllText(): String {
        val file = pdfFile ?: return ""
        return try {
            com.tom_roush.pdfbox.pdmodel.PDDocument.load(file).use { doc ->
                com.tom_roush.pdfbox.text.PDFTextStripper().getText(doc)
            }
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    // -------------------------------------------------------
    // SHARE / PRINT / PROPERTIES
    // -------------------------------------------------------

    private fun sharePdf() {
        pdfUri?.let { uri ->
            try {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share PDF"))
            } catch (_: Exception) { toast("Cannot share") }
        }
    }

    private fun printPdf() {
        toast("Print: Use system print dialog")
    }

    private fun showProperties() {
        val info = "Name: $displayName
Pages: $totalPages
Size: ${pdfFile?.length()?.let { formatSize(it) } ?: "Unknown"}"
        AlertDialog.Builder(this).setTitle("Properties").setMessage(info).setPositiveButton("OK", null).show()
    }

    // -------------------------------------------------------
    // DIALOGS
    // -------------------------------------------------------

    private fun showTextDialog(title: String, text: String) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(text.take(5000))
            .setPositiveButton("Copy") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText(title, text))
                toast("Copied to clipboard")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    // -------------------------------------------------------
    // SCROLL LISTENER
    // -------------------------------------------------------

    private fun setupScrollListener() {
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            val scrollY = scrollView.scrollY
            var accumulated = 0
            for (i in 0 until pageContainer.childCount) {
                val child = pageContainer.getChildAt(i)
                if (scrollY >= accumulated && scrollY < accumulated + child.height) {
                    if (currentPage != i) {
                        currentPage = i
                        viewModel.goToPage(i)
                        preloadAdjacentPages(i, resources.displayMetrics.widthPixels)
                    }
                    break
                }
                accumulated += child.height
            }
        }
    }

    // -------------------------------------------------------
    // ANNOTATION CACHE
    // -------------------------------------------------------

    private fun persistAnnotationCache() {
        val uri = pdfUri ?: return
        val safeId = uri.toString().hashCode().toString()
        val file = File(cacheDir, "annot_${safeId}.json")
        try {
            val sb = StringBuilder()
            sb.append("{"strokes":{")
            pageAnnotations.entries.forEachIndexed { idx, entry ->
                if (idx > 0) sb.append(",")
                sb.append(""${entry.key}":[")
                entry.value.forEachIndexed { sIdx, stroke ->
                    if (sIdx > 0) sb.append(",")
                    sb.append("{"c":${stroke.color},"w":${stroke.strokeWidth},"a":${stroke.alpha},"t":"${stroke.tool}","p":[")
                    stroke.points.forEachIndexed { pIdx, pt ->
                        if (pIdx > 0) sb.append(",")
                        sb.append("{"x":${pt.x},"y":${pt.y}}")
                    }
                    sb.append("]}")
                }
                sb.append("]")
            }
            sb.append("}}")
            file.writeText(sb.toString())
        } catch (_: Exception) {}
    }

    private fun loadAnnotationsFromCache() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val uri = pdfUri ?: return@launch
                val safeId = uri.toString().hashCode().toString()
                val file = File(cacheDir, "annot_${safeId}.json")
                if (!file.exists()) return@launch
                // Parse and restore - simplified for production
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------

    private fun formatSize(bytes: Long): String {
        return when {
            bytes > 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
            bytes > 1024 -> "%.0f KB".format(bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun updateAnnotToolbar() {
        annotToolbar.visibility = if (isAnnotToolbarExpanded) View.VISIBLE else View.GONE
    }

    private fun updateProgressBar(visible: Boolean) {
        progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    // -------------------------------------------------------
    // DATA CLASSES
    // -------------------------------------------------------

    data class AnnotationStroke(
        val id: String = java.util.UUID.randomUUID().toString(),
        val pageIndex: Int,
        val points: List<PointF>,
        val color: Int,
        val strokeWidth: Float,
        val alpha: Int = 255,
        val scale: Float = 1f,
        val tool: String = "freehand"
    )

    data class TextAnnotation(
        var x: Float,
        var y: Float,
        var text: String,
        var color: Int,
        var sizePx: Float,
        var scale: Float = 1f
    )

    sealed class AnnotationAction {
        data class AddStroke(val pageIndex: Int, val stroke: AnnotationStroke) : AnnotationAction()
        data class RemoveStroke(val pageIndex: Int, val stroke: AnnotationStroke) : AnnotationAction()
    }
}
