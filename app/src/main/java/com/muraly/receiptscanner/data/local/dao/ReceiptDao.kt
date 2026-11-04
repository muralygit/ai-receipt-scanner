package com.muraly.receiptscanner.data.local.dao

import androidx.room.*
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptItemEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import kotlinx.coroutines.flow.Flow

data class ImagePathRow(val id: Long, val imageUri: String)

/** One purchase of an item, joined with the receipt it came from (used by the Item prices screen). */
data class ItemPriceRow(
    val receiptId: Long,
    val itemName: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val shopName: String,
    val date: String,
    val invoiceNumber: String,
    val createdAt: Long
)

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

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsForReceipt(receiptId: Long)

    @Transaction
    suspend fun updateReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>) {
        updateReceipt(receipt)
        deleteItemsForReceipt(receipt.id)
        insertReceiptItems(items.map { it.copy(receiptId = receipt.id) })
    }

    @Query("SELECT id, imageUri FROM receipts WHERE imageUri != ''")
    suspend fun getAllImagePaths(): List<ImagePathRow>

    @Query("UPDATE receipts SET imageUri = :imageUri WHERE id = :id")
    suspend fun updateImageUri(id: Long, imageUri: String)

    /** Every saved purchase whose item name contains [query], newest first. Read-only; no schema change. */
    @Query(
        """
        SELECT r.id AS receiptId, i.name AS itemName, i.quantity AS quantity, i.unitPrice AS unitPrice,
               i.totalPrice AS totalPrice, r.shopName AS shopName, r.date AS date,
               r.invoiceNumber AS invoiceNumber, r.createdAt AS createdAt
        FROM receipt_items i
        JOIN receipts r ON r.id = i.receiptId
        WHERE i.name LIKE '%' || :query || '%'
        ORDER BY r.date DESC, r.createdAt DESC
        LIMIT 500
        """
    )
    fun searchItemPrices(query: String): Flow<List<ItemPriceRow>>

    @Delete
    suspend fun deleteReceipt(receipt: ReceiptEntity)

    /**
     * Looks for a likely duplicate of a receipt about to be saved: either a matching
     * non-blank invoice number, or the same shop + date + total (small epsilon for
     * floating point). Used to warn the user before they accidentally save the same
     * receipt twice.
     */
    @Query(
        """
        SELECT * FROM receipts
        WHERE (:invoiceNumber != '' AND invoiceNumber = :invoiceNumber)
           OR (shopName = :shopName AND date = :date AND ABS(total - :total) < 0.01)
        LIMIT 1
        """
    )
    suspend fun findPotentialDuplicate(
        shopName: String,
        invoiceNumber: String,
        date: String,
        total: Double
    ): ReceiptEntity?

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
        OR r.rawOcrText LIKE '%' || :query || '%'
        OR r.date LIKE '%' || :query || '%'
        OR r.category LIKE '%' || :query || '%'
        OR r.paymentMethod LIKE '%' || :query || '%'
        ORDER BY r.createdAt DESC
    """)
    fun searchReceipts(query: String): Flow<List<ReceiptWithItems>>
}
