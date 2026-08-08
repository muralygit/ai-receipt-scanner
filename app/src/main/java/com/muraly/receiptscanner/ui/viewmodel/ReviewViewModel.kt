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

    /** Fires when a likely-duplicate receipt is found so the Activity can confirm with the user. */
    private val _duplicateDetected = MutableLiveData<ReceiptEntity?>()
    val duplicateDetected: LiveData<ReceiptEntity?> = _duplicateDetected

    /** Holds the pending save args while we wait for the user's decision on a duplicate warning. */
    private var pendingSave: (() -> Unit)? = null

    /**
     * Saves a receipt. If [existingReceiptId] is null (or 0) this inserts a new receipt
     * (the normal path after a fresh scan). If it's a real id, this updates that receipt
     * in place instead, replacing its line items (the edit path from Detail screen).
     *
     * For new receipts (not edits), this first checks for a likely duplicate — same
     * invoice number, or same shop + date + total. If one is found and [forceSave] is
     * false, [duplicateDetected] fires instead of saving; call this again with
     * [forceSave] = true to save anyway.
     */
    fun saveReceipt(
        shopName: String, invoiceNumber: String, date: String, time: String,
        subtotal: Double, gst: Double, total: Double, paymentMethod: String,
        imageUri: String, rawOcrText: String, items: List<ParsedReceiptItem>,
        existingReceiptId: Long? = null,
        forceSave: Boolean = false
    ) {
        val isNewReceipt = existingReceiptId == null || existingReceiptId == 0L

        val doSave: () -> Unit = {
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

                if (!isNewReceipt) {
                    repository.updateReceiptWithItems(receipt, itemEntities)
                } else {
                    repository.insertReceiptWithItems(receipt, itemEntities)
                }
                _saveSuccess.value = true
            }
        }

        if (isNewReceipt && !forceSave) {
            viewModelScope.launch {
                val duplicate = repository.findPotentialDuplicate(shopName, invoiceNumber, date, total)
                if (duplicate != null) {
                    pendingSave = doSave
                    _duplicateDetected.value = duplicate
                } else {
                    doSave()
                }
            }
        } else {
            doSave()
        }
    }

    /** Call after the user confirms they want to save despite the duplicate warning. */
    fun confirmSaveDespiteDuplicate() {
        pendingSave?.invoke()
        pendingSave = null
        _duplicateDetected.value = null
    }

    /** Call if the user cancels after seeing the duplicate warning. */
    fun dismissDuplicateWarning() {
        pendingSave = null
        _duplicateDetected.value = null
    }
}
