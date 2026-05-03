package com.propdf.editor.ui.viewer

import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.propdf.editor.databinding.ActivityViewerBinding
import com.propdf.editor.data.repository.PdfOperationsManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class ViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityViewerBinding
    @Inject lateinit var pdfOperationsManager: PdfOperationsManager

    private lateinit var pdfUri: Uri
    private lateinit var adapter: PdfPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pdfUri = Uri.parse(intent.getStringExtra("pdf_uri"))
        setupRecyclerView()
        loadPdf()
    }

    private fun setupRecyclerView() {
        adapter = PdfPageAdapter()
        binding.recyclerViewPdf.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPdf.adapter = adapter
    }

    private fun loadPdf() {
        lifecycleScope.launch {
            // Use pdfOperationsManager to render pages
            // For brevity, assume pages are loaded
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.viewer_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                sharePdf()
                true
            }
            R.id.action_delete -> {
                deletePdf()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sharePdf() {
        // Share PDF logic
        Toast.makeText(this, "Share clicked", Toast.LENGTH_SHORT).show()
    }

    private fun deletePdf() {
        // Delete file logic
        finish()
    }
}
