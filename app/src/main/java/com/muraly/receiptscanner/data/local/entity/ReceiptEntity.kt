package com.muraly.receiptscanner.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val shopName: String,
    val invoiceNumber: String,
    val date: String,
    val time: String,
    val subtotal: Double,
    val gst: Double,
    val total: Double,
    val paymentMethod: String,
    val imageUri: String,
    val rawOcrText: String,
    val category: String = "General",
    val createdAt: Long = System.currentTimeMillis()
)
