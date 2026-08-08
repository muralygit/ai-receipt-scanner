package com.muraly.receiptscanner.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptItemEntity
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import com.muraly.receiptscanner.util.ParsedReceiptItem
import kotlinx.coroutines.launch

class ReviewViewModel(private val repository: ReceiptRepository) : ViewModel() {
    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    /**
     * Saves a receipt. If [existingReceiptId] is null (or 0) this inserts a new receipt
     * (the normal path after a fresh scan). If it's a real id, this updates that receipt
     * in place instead, replacing its line items (the edit path from Detail screen).
     */
    fun saveReceipt(
        shopName: String, invoiceNumber: String, date: String, time: String,
        subtotal: Double, gst: Double, total: Double, paymentMethod: String,
        imageUri: String, rawOcrText: String, items: List<ParsedReceiptItem>,
        existingReceiptId: Long? = null
    ) {
        viewModelScope.launch {
            val receipt = ReceiptEntity(
                id = existingReceiptId ?: 0,
                shopName = shopName, invoiceNumber = invoiceNumber, date = date, time = time,
                subtotal = subtotal, gst = gst, total = total, paymentMethod = paymentMethod,
                imageUri = imageUri, rawOcrText = rawOcrText
            )
            val itemEntities = items.map {
                ReceiptItemEntity(
                    receiptId = existingReceiptId ?: 0,
                    name = it.name,
                    quantity = it.qty,
                    unitPrice = it.price,
                    totalPrice = it.qty * it.price
                )
            }

            if (existingReceiptId != null && existingReceiptId != 0L) {
                repository.updateReceiptWithItems(receipt, itemEntities)
            } else {
                repository.insertReceiptWithItems(receipt, itemEntities)
            }
            _saveSuccess.value = true
        }
    }
}
