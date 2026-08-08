package com.muraly.receiptscanner.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import com.muraly.receiptscanner.databinding.ActivityDetailBinding
import com.muraly.receiptscanner.databinding.ItemDetailRowBinding
import com.muraly.receiptscanner.ui.viewmodel.DetailViewModel
import com.muraly.receiptscanner.ui.viewmodel.ViewModelFactory
import com.muraly.receiptscanner.util.ExportHelper
import com.muraly.receiptscanner.util.ParsedReceiptItem
import com.muraly.receiptscanner.util.ParsedReceiptResult
import java.text.NumberFormat
import java.util.Locale

class DetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetailBinding
    private val app by lazy { application as ReceiptScannerApplication }
    private val viewModel: DetailViewModel by viewModels {
        ViewModelFactory(app.repository, app.securePrefs)
    }

    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private var currentReceipt: ReceiptWithItems? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val receiptId = intent.getLongExtra(EXTRA_RECEIPT_ID, -1L)
        if (receiptId == -1L) {
            finish()
            return
        }

        viewModel.loadReceipt(receiptId)
        viewModel.receipt.observe(this) { receipt ->
            if (receipt == null) {
                Toast.makeText(this, "Receipt not found", Toast.LENGTH_SHORT).show()
                finish()
                return@observe
            }
            currentReceipt = receipt
            renderReceipt(receipt)
        }
        viewModel.deleted.observe(this) { deleted ->
            if (deleted) {
                Toast.makeText(this, "Receipt deleted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        binding.btnEdit.setOnClickListener { openEditMode() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.btnExportPdf.setOnClickListener { exportPdf() }
        binding.btnShare.setOnClickListener { exportAndShare() }
    }

    private fun renderReceipt(data: ReceiptWithItems) {
        val r = data.receipt
        binding.tvShopName.text = r.shopName.ifBlank { "Unknown Shop" }
        binding.tvInvoiceNumber.text = "Invoice: ${r.invoiceNumber.ifBlank { "-" }}"
        binding.tvDateTime.text = listOf(r.date, r.time).filter { it.isNotBlank() }.joinToString(" • ")
        binding.tvPaymentMethod.text = "Payment: ${r.paymentMethod.ifBlank { "-" }}"
        binding.tvCategory.text = r.category.ifBlank { "General" }
        loadReceiptThumbnail(r.imageUri)

        binding.itemsContainer.removeAllViews()
        data.items.forEach { item ->
            val rowBinding = ItemDetailRowBinding.inflate(LayoutInflater.from(this), binding.itemsContainer, false)
            rowBinding.tvItemName.text = item.name
            rowBinding.tvItemQty.text = "x${item.quantity}"
            rowBinding.tvItemPrice.text = inrFormat.format(item.totalPrice)
            binding.itemsContainer.addView(rowBinding.root)
        }

        binding.tvSubtotal.text = "Subtotal: ${inrFormat.format(r.subtotal)}"
        binding.tvGst.text = "GST: ${inrFormat.format(r.gst)}"
        binding.tvTotal.text = "Total: ${inrFormat.format(r.total)}"
    }

    /**
     * Loads the receipt photo into the thumbnail ImageView if it still exists on disk.
     * Photos live in cacheDir, which Android is allowed to clear under storage pressure,
     * so a missing file here just means the thumbnail silently hides rather than crashing —
     * the saved receipt data itself is unaffected either way.
     */
    private fun loadReceiptThumbnail(imageUri: String) {
        if (imageUri.isBlank()) {
            binding.ivReceiptThumbnail.visibility = android.view.View.GONE
            return
        }
        val file = java.io.File(imageUri)
        if (!file.exists()) {
            binding.ivReceiptThumbnail.visibility = android.view.View.GONE
            return
        }

        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imageUri, options)
        var sampleSize = 1
        while (options.outWidth / sampleSize > 800) sampleSize *= 2
        val finalOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = BitmapFactory.decodeFile(imageUri, finalOptions)

        if (bitmap == null) {
            binding.ivReceiptThumbnail.visibility = android.view.View.GONE
            return
        }

        binding.ivReceiptThumbnail.setImageBitmap(bitmap)
        binding.ivReceiptThumbnail.visibility = android.view.View.VISIBLE
        binding.ivReceiptThumbnail.setOnClickListener {
            try {
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(viewIntent, "View Receipt Photo"))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open photo: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openEditMode() {
        val data = currentReceipt ?: return
        val parsed = ParsedReceiptResult(
            shopName = data.receipt.shopName,
            invoiceNumber = data.receipt.invoiceNumber,
            date = data.receipt.date,
            time = data.receipt.time,
            items = data.items.map { ParsedReceiptItem(name = it.name, qty = it.quantity, price = it.unitPrice) },
            subtotal = data.receipt.subtotal,
            gst = data.receipt.gst,
            total = data.receipt.total,
            paymentMethod = data.receipt.paymentMethod,
            category = data.receipt.category
        )
        val intent = Intent(this, ReviewActivity::class.java).apply {
            putExtra(ReviewActivity.EXTRA_PARSED_RESULT_JSON, Gson().toJson(parsed))
            putExtra(ReviewActivity.EXTRA_IMAGE_URI, data.receipt.imageUri)
            putExtra(ReviewActivity.EXTRA_RAW_OCR_TEXT, data.receipt.rawOcrText)
            putExtra(ReviewActivity.EXTRA_RECEIPT_ID, data.receipt.id)
        }
        startActivity(intent)
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete receipt?")
            .setMessage("This cannot be undone.")
            .setPositiveButton("Delete") { _, _ -> viewModel.deleteReceipt() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportPdf() {
        val data = currentReceipt ?: return
        try {
            val file = ExportHelper.exportToPdf(this, data)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Open PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun exportAndShare() {
        val data = currentReceipt ?: return
        try {
            val file = ExportHelper.exportToPdf(this, data)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Receipt"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val EXTRA_RECEIPT_ID = "extra_receipt_id"
    }
}
