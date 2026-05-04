package com.propdf.editor.ui.viewer

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import androidx.core.view.*
import com.propdf.editor.utils.dpToPx

class AnnotatedPageView(
    context: Context,
    private val bitmap: Bitmap,
    private val pageNumber: Int
) : FrameLayout(context) {

    val canvasView: AnnotationCanvasView
    private val imageView: ImageView
    private val toolbar: LinearLayout
    private var isToolbarVisible = true

    var onAnnotationToolSelected: ((AnnotationType, Int, Float) -> Unit)? = null
    var onUndoClicked: (() -> Unit)? = null
    var onSaveClicked: (() -> Unit)? = null

    init {
        layoutParams = ViewGroup.LayoutParams(-1, -1)
        setBackgroundColor(Color.DKGRAY)

        // PDF Image
        imageView = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(bitmap)
        }
        addView(imageView)

        // Annotation Canvas overlay
        canvasView = AnnotationCanvasView(context).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        addView(canvasView)

        // Floating Annotation Toolbar
        toolbar = createFloatingToolbar()
        addView(toolbar)

        // Toggle toolbar on tap
        setOnClickListener {
            toggleToolbar()
        }
    }

    private fun createFloatingToolbar(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dpToPx(context, 16)
                marginEnd = dpToPx(context, 16)
            }
            setPadding(dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8), dpToPx(context, 8))
            background = createToolbarBackground()
            elevation = dpToPx(context, 8).toFloat()

            // Tool buttons
            addView(createToolButton("✏️", "Pen", AnnotationType.PEN, Color.RED))
            addView(createToolButton("🖍️", "Highlight", AnnotationType.HIGHLIGHTER, Color.YELLOW))
            addView(createToolButton("🧽", "Eraser", AnnotationType.ERASER, Color.WHITE))
            addView(createToolButton("📝", "Text", AnnotationType.TEXT, Color.BLACK))
            addView(createToolButton("⬜", "Rect", AnnotationType.RECTANGLE, Color.BLUE))
            addView(createToolButton("⭕", "Circle", AnnotationType.CIRCLE, Color.GREEN))
            addView(createToolButton("➡️", "Arrow", AnnotationType.ARROW, Color.RED))
            addView(createDivider())
            addView(createActionButton("↩️", "Undo") { onUndoClicked?.invoke() })
            addView(createActionButton("💾", "Save") { onSaveClicked?.invoke() })
            addView(createActionButton("❌", "Clear") { canvasView.clear() })
        }
    }

    private fun createToolButton(icon: String, label: String, type: AnnotationType, defaultColor: Int): View {
        return Button(context).apply {
            text = "$icon $label"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = createButtonBackground(Color.parseColor("#2A2A2A"))
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(context, 40)).apply {
                bottomMargin = dpToPx(context, 4)
            }
            setOnClickListener {
                // Color picker dialog
                showColorPicker { color ->
                    canvasView.setTool(type, color, when(type) {
                        AnnotationType.HIGHLIGHTER -> 15f
                        AnnotationType.ERASER -> 30f
                        AnnotationType.TEXT -> 5f
                        else -> 5f
                    })
                    onAnnotationToolSelected?.invoke(type, color, canvasView.currentStrokeWidth)
                }
            }
        }
    }

    private fun createActionButton(icon: String, label: String, action: () -> Unit): View {
        return Button(context).apply {
            text = "$icon $label"
            textSize = 12f
            setTextColor(Color.WHITE)
            background = createButtonBackground(Color.parseColor("#1A1A1A"))
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(context, 40)).apply {
                bottomMargin = dpToPx(context, 4)
            }
            setOnClickListener { action() }
        }
    }

    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(-1, dpToPx(context, 1)).apply {
                topMargin = dpToPx(context, 4)
                bottomMargin = dpToPx(context, 4)
            }
            setBackgroundColor(Color.parseColor("#444444"))
        }
    }

    private fun createToolbarBackground(): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.parseColor("#1E1E1E"))
            cornerRadius = dpToPx(context, 12).toFloat()
            setStroke(dpToPx(context, 1), Color.parseColor("#333333"))
        }
    }

    private fun createButtonBackground(color: Int): android.graphics.drawable.Drawable {
        return android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = dpToPx(context, 8).toFloat()
        }
    }

    private fun showColorPicker(onColorSelected: (Int) -> Unit) {
        val colors = listOf(
            Color.RED, Color.BLUE, Color.GREEN, Color.YELLOW,
            Color.BLACK, Color.WHITE, Color.MAGENTA, Color.CYAN,
            Color.parseColor("#FF6B35"), Color.parseColor("#004E89")
        )

        val dialog = android.app.AlertDialog.Builder(context)
            .setTitle("Select Color")
            .create()

        val grid = GridLayout(context).apply {
            columnCount = 5
            setPadding(16, 16, 16, 16)
        }

        colors.forEach { color ->
            val btn = View(context).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dpToPx(context, 48)
                    height = dpToPx(context, 48)
                    setMargins(8, 8, 8, 8)
                }
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(color)
                    cornerRadius = dpToPx(context, 24).toFloat()
                    setStroke(dpToPx(context, 2), Color.WHITE)
                }
                setOnClickListener {
                    onColorSelected(color)
                    dialog.dismiss()
                }
            }
            grid.addView(btn)
        }

        dialog.setView(grid)
        dialog.show()
    }

    private fun toggleToolbar() {
        isToolbarVisible = !isToolbarVisible
        toolbar.visibility = if (isToolbarVisible) View.VISIBLE else View.GONE
    }

    fun showTextInputDialog(x: Float, y: Float) {
        val editText = EditText(context).apply {
            hint = "Enter text..."
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("Add Text Annotation")
            .setView(editText)
            .setPositiveButton("Add") { _, _ ->
                val text = editText.text.toString()
                if (text.isNotBlank()) {
                    canvasView.addTextAnnotation(text, x, y)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
