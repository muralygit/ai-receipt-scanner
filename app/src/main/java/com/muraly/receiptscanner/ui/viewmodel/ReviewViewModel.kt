package com.muraly.receiptscanner.ui.viewmodel

import androidx.lifecycle.*
import com.muraly.receiptscanner.data.local.entity.*
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import com.muraly.receiptscanner.util.ParsedReceiptItem
import kotlinx.coroutines.launch

class ReviewViewModel(private val repository: ReceiptRepository) : ViewModel() {
    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    fun saveReceipt(
        shopName: String, invoiceNumber: String, date: String, time: String,
        subtotal: Double, gst: Double, total: Double, paymentMethod: String,
        imageUri: String, rawOcrText: String, items: List<ParsedReceiptItem>
    ) {
        viewModelScope.launch {
            val receipt = ReceiptEntity(
                shopName = shopName, invoiceNumber = invoiceNumber, date = date, time = time,
                subtotal = subtotal, gst = gst, total = total, paymentMethod = paymentMethod,
                imageUri = imageUri, rawOcrText = rawOcrText
            )
            val itemEntities = items.map {
                ReceiptItemEntity(receiptId = 0, name = it.name, quantity = it.qty, unitPrice = it.price, totalPrice = it.qty * it.price)
            }
            repository.insertReceiptWithItems(receipt, itemEntities)
            _saveSuccess.value = true
        }
    }
}
