package com.propdf.editor.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.propdf.editor.ui.scanner.DocumentScannerActivity
import com.propdf.editor.ui.tools.ToolsActivity
import com.propdf.editor.ui.viewer.ViewerActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_STORAGE = 1001
        const val REQUEST_PICK_PDF = 1002
    }

    private lateinit var recentFilesContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }
        scroll.addView(root)

        // Header Section
        root.addView(createHeader())

        // Quick Actions Grid
        root.addView(TextView(this).apply {
            text = "⚡ Quick Actions"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(24), dp(20), dp(12))
        })
        root.addView(createQuickActionsGrid())

        // Recent Files Section
        root.addView(TextView(this).apply {
            text = "📁 Recent Files"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(24), dp(20), dp(12))
        })
        recentFilesContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(20))
        }
        root.addView(recentFilesContainer)
        loadRecentFiles()

        // Premium Banner
        root.addView(createPremiumBanner())

        setContentView(scroll)
        checkPermissions()
    }

    private fun createHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(32), dp(20), dp(16))
            setBackgroundColor(Color.parseColor("#1A1A2E"))

            addView(TextView(this@MainActivity).apply {
                text = "ProPDF Editor"
                textSize = 32f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
            })

            addView(TextView(this@MainActivity).apply {
                text = "Professional PDF tools for everyone"
                textSize = 14f
                setTextColor(Color.parseColor("#888888"))
                setPadding(0, dp(4), 0, 0)
            })
        }
    }

    private fun createQuickActionsGrid(): LinearLayout {
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }

        val actions = listOf(
            Triple("📂", "Open PDF", Color.parseColor("#FF6B35")) { openPdf() },
            Triple("✂️", "PDF Tools", Color.parseColor("#004E89")) { startActivity(Intent(this, ToolsActivity::class.java)) },
            Triple("📷", "Scanner", Color.parseColor("#1A936F")) { startActivity(Intent(this, DocumentScannerActivity::class.java)) },
            Triple("📝", "Create PDF", Color.parseColor("#9B59B6")) { createPdf() }
        )

        actions.chunked(2).forEach { row ->
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { (icon, label, color, action) ->
                rowLayout.addView(createActionCard(icon, label, color, action))
            }
            grid.addView(rowLayout)
        }
        return grid
    }

    private fun createActionCard(icon: String, label: String, color: Int, action: () -> Unit): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, dp(120), 1f).apply {
                marginEnd = dp(8)
                bottomMargin = dp(12)
            }
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(16), dp(16), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.argb(40, Color.red(color), Color.green(color), Color.blue(color)))
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { action() }

            addView(TextView(this@MainActivity).apply {
                text = icon
                textSize = 32f
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = label
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            })
        }
    }

    private fun createPremiumBanner(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(20), dp(16), dp(20), dp(20))
            }
            setPadding(dp(20), dp(20), dp(20), dp(20))
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                colors = intArrayOf(Color.parseColor("#FF6B35"), Color.parseColor("#004E89"))
                orientation = GradientDrawable.Orientation.TL_BR
                cornerRadius = dp(16).toFloat()
            }

            addView(TextView(this@MainActivity).apply {
                text = "⭐ ProPDF Premium"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })

            addView(TextView(this@MainActivity).apply {
                text = "Unlock AI summaries, cloud sync, and advanced OCR"
                textSize = 13f
                setTextColor(Color.parseColor("#E0E0E0"))
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(12))
            })

            addView(Button(this@MainActivity).apply {
                text = "Upgrade Now"
                setTextColor(Color.parseColor("#FF6B35"))
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(24).toFloat()
                }
                setOnClickListener {
                    Toast.makeText(context, "Coming soon!", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun loadRecentFiles() {
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                getPdfFilesFromDownloads()
            }
            recentFilesContainer.removeAllViews()
            if (files.isEmpty()) {
                recentFilesContainer.addView(TextView(this@MainActivity).apply {
                    text = "No recent PDFs found"
                    textSize = 14f
                    setTextColor(Color.parseColor("#666666"))
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
            } else {
                files.take(5).forEach { file ->
                    recentFilesContainer.addView(createRecentFileItem(file))
                }
            }
        }
    }

    private fun getPdfFilesFromDownloads(): List<File> {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        return downloadsDir.listFiles { f -> f.isFile && f.extension.equals("pdf", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    private fun createRecentFileItem(file: File): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, dp(64)).apply {
                bottomMargin = dp(8)
            }
            setPadding(dp(16), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1A1A1A"))
                cornerRadius = dp(12).toFloat()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                openPdfFile(Uri.fromFile(file))
            }

            // PDF icon
            addView(FrameLayout(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#FF6B35"))
                    cornerRadius = dp(8).toFloat()
                }
                addView(TextView(this@MainActivity).apply {
                    text = "PDF"
                    textSize = 10f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                })
            })

            // File info
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -1, 1f).apply {
                    marginStart = dp(12)
                }
                addView(TextView(this@MainActivity).apply {
                    text = file.name
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.WHITE)
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                })
                addView(TextView(this@MainActivity).apply {
                    text = "${formatFileSize(file.length())} • ${formatDate(file.lastModified())}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#888888"))
                })
            })
        }
    }

    private fun openPdf() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "application/pdf"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_PICK_PDF)
    }

    private fun openPdfFile(uri: Uri) {
        val intent = Intent(this, ViewerActivity::class.java).apply {
            putExtra("pdf_uri", uri.toString())
        }
        startActivity(intent)
    }

    private fun createPdf() {
        Toast.makeText(this, "Create PDF - Coming soon!", Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), REQUEST_STORAGE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadRecentFiles()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_PDF && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                openPdfFile(uri)
            }
        }
    }

    private fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} KB"
            else -> String.format("%.1f MB", size / (1024.0 * 1024.0))
        }
    }

    private fun formatDate(time: Long): String {
        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(time))
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
