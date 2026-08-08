package com.muraly.receiptscanner.ui

import android.os.Bundle
import android.view.LayoutInflater
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.databinding.ActivityInsightsBinding
import com.muraly.receiptscanner.databinding.ItemBreakdownRowBinding
import com.muraly.receiptscanner.ui.viewmodel.BreakdownRow
import com.muraly.receiptscanner.ui.viewmodel.InsightsViewModel
import com.muraly.receiptscanner.ui.viewmodel.ViewModelFactory
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class InsightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsightsBinding
    private val app by lazy { application as ReceiptScannerApplication }
    private val viewModel: InsightsViewModel by viewModels {
        ViewModelFactory(app.repository, app.securePrefs)
    }

    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val monthDisplayFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    private val monthParseFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsightsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener { finish() }

        viewModel.insights.observe(this) { data ->
            binding.tvGrandTotal.text = "Total Spent: ${inrFormat.format(data.grandTotal)}"

            binding.categoryContainer.removeAllViews()
            binding.tvNoCategoryData.visibility =
                if (data.byCategory.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            data.byCategory.forEach { row -> addBreakdownRow(binding.categoryContainer, row) }

            binding.monthContainer.removeAllViews()
            data.byMonth.forEach { row ->
                val displayRow = row.copy(label = formatMonthLabel(row.label))
                addBreakdownRow(binding.monthContainer, displayRow)
            }
        }
    }

    private fun addBreakdownRow(container: android.widget.LinearLayout, row: BreakdownRow) {
        val rowBinding = ItemBreakdownRowBinding.inflate(LayoutInflater.from(this), container, false)
        rowBinding.tvBreakdownLabel.text = row.label
        rowBinding.tvBreakdownCount.text = if (row.count == 1) "1 receipt" else "${row.count} receipts"
        rowBinding.tvBreakdownAmount.text = inrFormat.format(row.total)
        container.addView(rowBinding.root)
    }

    /** Turns "2026-08" into "August 2026"; leaves anything unparseable (e.g. "Unknown") as-is. */
    private fun formatMonthLabel(monthKey: String): String {
        return try {
            val parsed = monthParseFormat.parse(monthKey) ?: return monthKey
            monthDisplayFormat.format(parsed)
        } catch (e: Exception) {
            monthKey
        }
    }
}
