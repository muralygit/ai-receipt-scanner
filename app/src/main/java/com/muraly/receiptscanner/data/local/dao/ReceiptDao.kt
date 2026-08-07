package com.muraly.receiptscanner.data.local.dao

import androidx.room.*
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptItemEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceiptItems(items: List<ReceiptItemEntity>)

    @Transaction
    suspend fun insertReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Long {
        val receiptId = insertReceipt(receipt)
        val itemsWithId = items.map { it.copy(receiptId = receiptId) }
        insertReceiptItems(itemsWithId)
        return receiptId
    }

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)

    @Transaction
    @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun getAllReceipts(): Flow<List<ReceiptWithItems>>

    @Transaction
    @Query("SELECT * FROM receipts WHERE id = :id LIMIT 1")
    suspend fun getReceiptById(id: Long): ReceiptWithItems?

    @Transaction
    @Query("""
        SELECT DISTINCT r.* FROM receipts r 
        LEFT JOIN receipt_items i ON r.id = i.receiptId 
        WHERE r.shopName LIKE '%' || :query || '%' 
        OR r.invoiceNumber LIKE '%' || :query || '%' 
        OR i.name LIKE '%' || :query || '%' 
        ORDER BY r.createdAt DESC
    """)
    fun searchReceipts(query: String): Flow<List<ReceiptWithItems>>
}
