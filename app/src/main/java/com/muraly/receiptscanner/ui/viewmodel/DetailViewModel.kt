package com.muraly.receiptscanner.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import kotlinx.coroutines.launch

class DetailViewModel(private val repository: ReceiptRepository) : ViewModel() {

    private val _receipt = MutableLiveData<ReceiptWithItems?>()
    val receipt: LiveData<ReceiptWithItems?> = _receipt

    private val _deleted = MutableLiveData<Boolean>()
    val deleted: LiveData<Boolean> = _deleted

    fun loadReceipt(id: Long) {
        viewModelScope.launch {
            _receipt.value = repository.getReceiptById(id)
        }
    }

    fun deleteReceipt() {
        val current = _receipt.value?.receipt ?: return
        viewModelScope.launch {
            repository.deleteReceipt(current)
            _deleted.value = true
        }
    }
}
