package com.muraly.receiptscanner.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.databinding.ActivityMainBinding
import com.muraly.receiptscanner.ui.viewmodel.MainViewModel
import com.muraly.receiptscanner.ui.viewmodel.ViewModelFactory

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val app by lazy { application as ReceiptScannerApplication }
    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory(app.repository, app.securePrefs)
    }

    private lateinit var adapter: ReceiptAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        adapter = ReceiptAdapter(
            onClick = { item ->
                val intent = Intent(this, DetailActivity::class.java)
                intent.putExtra(DetailActivity.EXTRA_RECEIPT_ID, item.receipt.id)
                startActivity(intent)
            },
            onLongClick = { item ->
                AlertDialog.Builder(this)
                    .setTitle("Delete receipt?")
                    .setMessage("Delete the receipt from ${item.receipt.shopName}? This cannot be undone.")
                    .setPositiveButton("Delete") { _, _ -> viewModel.deleteReceipt(item.receipt) }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.rvReceipts.layoutManager = LinearLayoutManager(this)
        binding.rvReceipts.adapter = adapter

        viewModel.receipts.observe(this) { receipts ->
            adapter.submitList(receipts)
            binding.tvEmptyState.visibility =
                if (receipts.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.setSearchQuery(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.fabScan.setOnClickListener {
            if (app.securePrefs.getGeminiApiKey().isNullOrBlank()) {
                Toast.makeText(this, "Add your Gemini API key in Settings first", Toast.LENGTH_LONG).show()
                startActivity(Intent(this, SettingsActivity::class.java))
            } else {
                startActivity(Intent(this, ScanActivity::class.java))
            }
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.btnExportCsv.setOnClickListener { exportAllToCsv() }
    }

    private fun exportAllToCsv() {
        val receipts = adapter.currentList
        if (receipts.isEmpty()) {
            Toast.makeText(this, "No receipts to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = com.muraly.receiptscanner.util.ExportHelper.exportToCsv(this, receipts)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share CSV"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
