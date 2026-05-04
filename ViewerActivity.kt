package com.propdf.editor.ui.viewer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.propdf.editor.R
import com.propdf.editor.data.repository.PdfOperationsManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ViewerActivity : AppCompatActivity() {

    @Inject
    lateinit var pdfOperationsManager: PdfOperationsManager

    private lateinit var viewModel: ViewerViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var pdfUri: Uri
    private lateinit var adapter: PdfPageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build layout programmatically - no ViewBinding needed
        val root = FrameLayout(this)
        recyclerView = RecyclerView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        root.addView(recyclerView)
        setContentView(root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val uriString = intent.getStringExtra("pdf_uri")
        if (uriString == null) {
            Toast.makeText(this, "No PDF specified", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        pdfUri = Uri.parse(uriString)

        viewModel = ViewModelProvider(this)[ViewerViewModel::class.java]

        setupRecyclerView()
        observeViewModel()
        viewModel.loadPdf(pdfUri)
    }

    private fun setupRecyclerView() {
        adapter = PdfPageAdapter(this, pdfUri)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun observeViewModel() {
        viewModel.pageCount.observe(this) { count ->
            adapter.setPageCount(count)
            supportActionBar?.title = "PDF Viewer ($count pages)"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Share").setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, 2, 1, "Delete").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> { finish(); true }
            1 -> { sharePdf(); true }
            2 -> { deletePdf(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun sharePdf() {
        try {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, pdfUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot share PDF", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deletePdf() {
        // Only delete if it's a file URI
        if (pdfUri.scheme == "file") {
            val file = java.io.File(pdfUri.path ?: "")
            if (file.exists()) file.delete()
        }
        finish()
    }
}
