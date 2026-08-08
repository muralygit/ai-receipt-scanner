package com.muraly.receiptscanner.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.map

data class BreakdownRow(val label: String, val total: Double, val count: Int)

data class InsightsData(
    val byCategory: List<BreakdownRow>,
    val byMonth: List<BreakdownRow>,
    val grandTotal: Double
)

class InsightsViewModel(private val repository: ReceiptRepository) : ViewModel() {

    val insights: LiveData<InsightsData> = repository.allReceipts.map { receipts ->
        val byCategory = receipts
            .groupBy { it.receipt.category.ifBlank { "General" } }
            .map { (category, group) -> BreakdownRow(category, group.sumOf { it.receipt.total }, group.size) }
            .sortedByDescending { it.total }

        val byMonth = receipts
            .groupBy { monthLabelFor(it.receipt.date) }
            .map { (month, group) -> BreakdownRow(month, group.sumOf { it.receipt.total }, group.size) }
            .sortedByDescending { it.label } // "2026-08" sorts correctly as a string, newest first

        InsightsData(
            byCategory = byCategory,
            byMonth = byMonth,
            grandTotal = receipts.sumOf { it.receipt.total }
        )
    }.asLiveData()

    /** Extracts "YYYY-MM" from a "YYYY-MM-DD" date string; falls back to "Unknown" if unparseable. */
    private fun monthLabelFor(date: String): String {
        return if (date.length >= 7 && date[4] == '-') date.substring(0, 7) else "Unknown"
    }
}
