package com.muraly.receiptscanner.data.repository

import com.muraly.receiptscanner.data.local.dao.ReceiptDao
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptItemEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class ReceiptRepository(private val receiptDao: ReceiptDao) {
    val allReceipts: Flow<List<ReceiptWithItems>> = receiptDao.getAllReceipts()

    fun searchReceipts(query: String): Flow<List<ReceiptWithItems>> = receiptDao.searchReceipts(query)

    suspend fun getReceiptById(id: Long): ReceiptWithItems? = withContext(Dispatchers.IO) {
        receiptDao.getReceiptById(id)
    }

    suspend fun insertReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Long =
        withContext(Dispatchers.IO) {
            receiptDao.insertReceiptWithItems(receipt, items)
        }

    suspend fun deleteReceipt(receipt: ReceiptEntity) = withContext(Dispatchers.IO) {
        receiptDao.deleteReceipt(receipt)
    }
}
