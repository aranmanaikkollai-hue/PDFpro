package com.propdf.editor.ui.main

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        val title = TextView(this).apply {
            text = "ProPDF Editor"
            textSize = 24f
            setTextColor(android.graphics.Color.WHITE)
            gravity = android.view.Gravity.CENTER
            setPadding(0, 100, 0, 50)
        }
        layout.addView(title)

        val btnOpen = Button(this).apply {
            text = "Open PDF"
            setOnClickListener {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "application/pdf"
                    addCategory(Intent.CATEGORY_OPENABLE)
                }
                startActivityForResult(intent, 1001)
            }
        }
        layout.addView(btnOpen)

        val btnTools = Button(this).apply {
            text = "PDF Tools"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, com.propdf.editor.ui.tools.ToolsActivity::class.java))
            }
        }
        layout.addView(btnTools)

        val btnScanner = Button(this).apply {
            text = "Document Scanner"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, com.propdf.editor.ui.scanner.DocumentScannerActivity::class.java))
            }
        }
        layout.addView(btnScanner)

        setContentView(layout)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            val uri = data?.data
            if (uri != null) {
                val intent = Intent(this, com.propdf.editor.ui.viewer.ViewerActivity::class.java).apply {
                    // FIX: Pass URI as String extra to match ViewerActivity expectation
                    putExtra("pdf_uri", uri.toString())
                }
                startActivity(intent)
            }
        }
    }
}
