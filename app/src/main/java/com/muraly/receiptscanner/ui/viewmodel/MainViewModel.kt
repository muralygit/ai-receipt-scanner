package com.muraly.receiptscanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

class MainViewModel(private val repository: ReceiptRepository) : ViewModel() {
    private val searchQuery = MutableStateFlow("")

    val receipts = searchQuery.flatMapLatest { query ->
        if (query.isBlank()) repository.allReceipts else repository.searchReceipts(query)
    }.asLiveData()

    fun setSearchQuery(query: String) { searchQuery.value = query }

    fun deleteReceipt(receipt: ReceiptEntity) {
        viewModelScope.launch { repository.deleteReceipt(receipt) }
    }
}
