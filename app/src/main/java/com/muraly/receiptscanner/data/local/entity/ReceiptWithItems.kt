package com.muraly.receiptscanner.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "receiptId"
    )
    val items: List<ReceiptItemEntity>
)
