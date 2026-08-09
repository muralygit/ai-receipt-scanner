package com.muraly.receiptscanner.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.databinding.ActivityMainBinding
import com.muraly.receiptscanner.ui.viewmodel.MainViewModel
import com.muraly.receiptscanner.ui.viewmodel.ViewModelFactory
import com.muraly.receiptscanner.util.BackupException
import com.muraly.receiptscanner.util.BackupHelper
import com.muraly.receiptscanner.util.ExportHelper
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    private val app by lazy { application as ReceiptScannerApplication }
    private val viewModel: MainViewModel by viewModels {
        ViewModelFactory(app.repository, app.securePrefs)
    }

    private lateinit var adapter: ReceiptAdapter
    private val inrFormat = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))

    private val pickBackupFile = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { restoreFromBackup(it) }
    }

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
            if (receipts.isEmpty()) {
                binding.tvEmptyState.visibility = android.view.View.VISIBLE
                binding.tvEmptyState.text = if (binding.etSearch.text.isNullOrBlank()) {
                    getString(com.muraly.receiptscanner.R.string.empty_receipts)
                } else {
                    getString(com.muraly.receiptscanner.R.string.empty_search_results)
                }
            } else {
                binding.tvEmptyState.visibility = android.view.View.GONE
            }

            val grandTotal = receipts.sumOf { it.receipt.total }
            val count = receipts.size
            binding.tvSummary.text = if (count == 0) {
                ""
            } else {
                val label = if (count == 1) "1 receipt" else "$count receipts"
                "$label  •  Total ${inrFormat.format(grandTotal)}"
            }
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

        binding.btnBackupMenu.setOnClickListener { showBackupRestoreMenu() }
    }

    private fun showBackupRestoreMenu() {
        val popup = PopupMenu(this, binding.btnBackupMenu)
        popup.menu.add(0, 1, 0, "Backup all receipts")
        popup.menu.add(0, 2, 1, "Restore from backup")
        popup.menu.add(0, 3, 2, "View spending insights")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> { backupAllReceipts(); true }
                2 -> { pickBackupFile.launch("application/json"); true }
                3 -> { startActivity(Intent(this, InsightsActivity::class.java)); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun backupAllReceipts() {
        val receipts = adapter.currentList
        if (receipts.isEmpty()) {
            Toast.makeText(this, "No receipts to back up yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = BackupHelper.exportBackup(this, receipts)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Save backup to"))
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun restoreFromBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                val backup = BackupHelper.readBackup(this@MainActivity, uri)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Restore backup?")
                    .setMessage(
                        "This backup contains ${BackupHelper.receiptCountOf(backup)} receipt(s) from " +
                            "${backup.exportedAt ?: "an earlier date"}. They'll be added to your existing receipts " +
                            "(nothing currently saved will be deleted). Continue?"
                    )
                    .setPositiveButton("Restore") { _, _ ->
                        lifecycleScope.launch {
                            var imported = 0
                            for (backupReceipt in backup.receipts.orEmpty()) {
                                val (receipt, items) = BackupHelper.toEntities(backupReceipt)
                                app.repository.insertReceiptWithItems(receipt, items)
                                imported++
                            }
                            Toast.makeText(
                                this@MainActivity,
                                "Restored $imported receipt(s)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } catch (e: BackupException) {
                Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun exportAllToCsv() {
        val receipts = adapter.currentList
        if (receipts.isEmpty()) {
            Toast.makeText(this, "No receipts to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = ExportHelper.exportToCsv(this, receipts)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
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
