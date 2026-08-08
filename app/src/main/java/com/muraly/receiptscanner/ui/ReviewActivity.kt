package com.muraly.receiptscanner.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.databinding.ActivityReviewBinding
import com.muraly.receiptscanner.databinding.ItemReviewRowBinding
import com.muraly.receiptscanner.ui.viewmodel.ReviewViewModel
import com.muraly.receiptscanner.ui.viewmodel.ViewModelFactory
import com.muraly.receiptscanner.util.ParsedReceiptItem
import com.muraly.receiptscanner.util.ParsedReceiptResult

class ReviewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReviewBinding
    private val app by lazy { application as ReceiptScannerApplication }
    private val viewModel: ReviewViewModel by viewModels {
        ViewModelFactory(app.repository, app.securePrefs)
    }

    /** Holds one editable item row's view bindings so we can read values back out on save. */
    private data class ItemRowHolder(val binding: ItemReviewRowBinding)
    private val itemRows = mutableListOf<ItemRowHolder>()

    private var imageUri: String = ""
    private var rawOcrText: String = ""
    private var existingReceiptId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val categoryAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, CATEGORIES)
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = categoryAdapter

        imageUri = intent.getStringExtra(EXTRA_IMAGE_URI).orEmpty()
        rawOcrText = intent.getStringExtra(EXTRA_RAW_OCR_TEXT).orEmpty()
        val receiptId = intent.getLongExtra(EXTRA_RECEIPT_ID, 0L)
        existingReceiptId = if (receiptId != 0L) receiptId else null

        val resultJson = intent.getStringExtra(EXTRA_PARSED_RESULT_JSON)
        if (resultJson != null) {
            val parsed = Gson().fromJson(resultJson, ParsedReceiptResult::class.java)
            populateForm(parsed)
        }

        binding.btnAddItem.setOnClickListener {
            addItemRow(ParsedReceiptItem(name = "", qty = 1, price = 0.0))
            recalculateTotals()
        }

        binding.etGst.addTextChangedListener(simpleWatcher { recalculateTotal() })

        binding.btnSaveReceipt.setOnClickListener { saveForm() }

        viewModel.saveSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Receipt saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        viewModel.duplicateDetected.observe(this) { duplicate ->
            if (duplicate != null) {
                AlertDialog.Builder(this)
                    .setTitle("Possible duplicate")
                    .setMessage(
                        "A receipt from \"${duplicate.shopName}\" on ${duplicate.date} " +
                            "for the same total already exists. Save this one anyway?"
                    )
                    .setPositiveButton("Save Anyway") { _, _ -> viewModel.confirmSaveDespiteDuplicate() }
                    .setNegativeButton("Cancel") { _, _ -> viewModel.dismissDuplicateWarning() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun populateForm(parsed: ParsedReceiptResult) {
        binding.etShopName.setText(parsed.shopName)
        binding.etInvoiceNumber.setText(parsed.invoiceNumber)
        binding.etDate.setText(parsed.date)
        binding.etTime.setText(parsed.time)
        binding.etSubtotal.setText(formatNumber(parsed.subtotal))
        binding.etGst.setText(formatNumber(parsed.gst))
        binding.etTotal.setText(formatNumber(parsed.total))
        binding.etPaymentMethod.setText(parsed.paymentMethod)

        val categoryIndex = CATEGORIES.indexOf(parsed.category).let { if (it >= 0) it else 0 }
        binding.spinnerCategory.setSelection(categoryIndex)

        binding.itemsContainer.removeAllViews()
        itemRows.clear()
        if (parsed.items.isEmpty()) {
            addItemRow(ParsedReceiptItem(name = "", qty = 1, price = 0.0))
        } else {
            parsed.items.forEach { addItemRow(it) }
        }
    }

    private fun addItemRow(item: ParsedReceiptItem) {
        val rowBinding = ItemReviewRowBinding.inflate(LayoutInflater.from(this), binding.itemsContainer, false)
        rowBinding.etItemName.setText(item.name)
        rowBinding.etItemQty.setText(item.qty.toString())
        rowBinding.etItemPrice.setText(formatNumber(item.price))

        val holder = ItemRowHolder(rowBinding)
        rowBinding.btnRemoveItem.setOnClickListener {
            binding.itemsContainer.removeView(rowBinding.root)
            itemRows.remove(holder)
            recalculateTotals()
        }

        // Keep Subtotal/Total in sync as the user edits quantity or price on this row.
        val watcher = simpleWatcher { recalculateTotals() }
        rowBinding.etItemQty.addTextChangedListener(watcher)
        rowBinding.etItemPrice.addTextChangedListener(watcher)

        itemRows.add(holder)
        binding.itemsContainer.addView(rowBinding.root)
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged() }
        override fun afterTextChanged(s: Editable?) {}
    }

    /** Recomputes Subtotal from the item rows, then recomputes Total from Subtotal + GST. */
    private fun recalculateTotals() {
        val subtotal = itemRows.sumOf { holder ->
            val qty = holder.binding.etItemQty.text.toString().toIntOrNull() ?: 0
            val price = holder.binding.etItemPrice.text.toString().toDoubleOrNull() ?: 0.0
            qty * price
        }
        binding.etSubtotal.setText(formatNumber(subtotal))
        recalculateTotal()
    }

    private fun recalculateTotal() {
        val subtotal = binding.etSubtotal.text.toString().toDoubleOrNull() ?: 0.0
        val gst = binding.etGst.text.toString().toDoubleOrNull() ?: 0.0
        binding.etTotal.setText(formatNumber(subtotal + gst))
    }

    private fun formatNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun saveForm() {
        val shopName = binding.etShopName.text.toString().trim()
        if (shopName.isEmpty()) {
            binding.etShopName.error = "Required"
            return
        }

        val items = itemRows.mapNotNull { holder ->
            val name = holder.binding.etItemName.text.toString().trim()
            if (name.isEmpty()) return@mapNotNull null
            val qty = holder.binding.etItemQty.text.toString().toIntOrNull() ?: 1
            val price = holder.binding.etItemPrice.text.toString().toDoubleOrNull() ?: 0.0
            ParsedReceiptItem(name = name, qty = qty, price = price)
        }

        viewModel.saveReceipt(
            shopName = shopName,
            invoiceNumber = binding.etInvoiceNumber.text.toString().trim(),
            date = binding.etDate.text.toString().trim(),
            time = binding.etTime.text.toString().trim(),
            subtotal = binding.etSubtotal.text.toString().toDoubleOrNull() ?: 0.0,
            gst = binding.etGst.text.toString().toDoubleOrNull() ?: 0.0,
            total = binding.etTotal.text.toString().toDoubleOrNull() ?: 0.0,
            paymentMethod = binding.etPaymentMethod.text.toString().trim(),
            imageUri = imageUri,
            rawOcrText = rawOcrText,
            items = items,
            category = binding.spinnerCategory.selectedItem as String,
            existingReceiptId = existingReceiptId
        )
    }

    companion object {
        const val EXTRA_PARSED_RESULT_JSON = "extra_parsed_result_json"
        const val EXTRA_IMAGE_URI = "extra_image_uri"
        const val EXTRA_RAW_OCR_TEXT = "extra_raw_ocr_text"
        const val EXTRA_RECEIPT_ID = "extra_receipt_id"

        val CATEGORIES = listOf(
            "General", "Groceries", "Medical", "Dining", "Fuel",
            "Electronics", "Household", "Clothing", "Utilities", "Entertainment", "Other"
        )
    }
}
