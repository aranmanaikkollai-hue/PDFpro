package com.propdf.editor.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Build UI programmatically or setContentView(R.layout.activity_main)
        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        val title = android.widget.TextView(this).apply {
            text = "ProPDF Editor"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 100, 0, 50)
        }
        layout.addView(title)

        val btnOpen = android.widget.Button(this).apply {
            text = "Open PDF"
            setOnClickListener {
                // Launch file picker
                val intent = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/pdf"
                    addCategory(android.content.Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, 1001)
            }
        }
        layout.addView(btnOpen)

        val btnTools = android.widget.Button(this).apply {
            text = "PDF Tools"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, com.propdf.editor.ui.tools.ToolsActivity::class.java))
            }
        }
        layout.addView(btnTools)

        val btnScanner = android.widget.Button(this).apply {
            text = "Document Scanner"
            setOnClickListener {
                startActivity(android.content.Intent(this@MainActivity, com.propdf.editor.ui.scanner.DocumentScannerActivity::class.java))
            }
        }
        layout.addView(btnScanner)

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val intent = android.content.Intent(this, com.propdf.editor.ui.viewer.ViewerActivity::class.java).apply {
                    data = uri
                }
                startActivity(intent)
            }
        }
    }
}
