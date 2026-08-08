package com.muraly.receiptscanner.util

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.muraly.receiptscanner.data.local.entity.ReceiptEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptItemEntity
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class BackupItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double
)

data class BackupReceipt(
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
    val items: List<BackupItem>
)

data class BackupFile(
    val formatVersion: Int,
    val exportedAt: String,
    val receiptCount: Int,
    val receipts: List<BackupReceipt>
)

class BackupException(message: String) : Exception(message)

/**
 * Exports every saved receipt to a single JSON file, and parses one back in for restore.
 * Uses its own plain data classes rather than the Room entities directly, so the backup
 * format stays stable even if the database schema changes in a future version.
 */
object BackupHelper {
    private const val FORMAT_VERSION = 1
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    fun exportBackup(context: Context, receipts: List<ReceiptWithItems>): File {
        val backupReceipts = receipts.map { rw ->
            BackupReceipt(
                shopName = rw.receipt.shopName,
                invoiceNumber = rw.receipt.invoiceNumber,
                date = rw.receipt.date,
                time = rw.receipt.time,
                subtotal = rw.receipt.subtotal,
                gst = rw.receipt.gst,
                total = rw.receipt.total,
                paymentMethod = rw.receipt.paymentMethod,
                imageUri = rw.receipt.imageUri,
                rawOcrText = rw.receipt.rawOcrText,
                items = rw.items.map {
                    BackupItem(
                        name = it.name,
                        quantity = it.quantity,
                        unitPrice = it.unitPrice,
                        totalPrice = it.totalPrice
                    )
                }
            )
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val backup = BackupFile(
            formatVersion = FORMAT_VERSION,
            exportedAt = timestamp,
            receiptCount = backupReceipts.size,
            receipts = backupReceipts
        )

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "ai_receipt_scanner_backup_$timestamp.json")
        FileOutputStream(file).use { out ->
            out.write(gson.toJson(backup).toByteArray())
        }
        return file
    }

    /** Reads and parses a backup JSON file picked by the user. Does not touch the database itself. */
    fun readBackup(context: Context, uri: Uri): BackupFile {
        val jsonText = context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            ?: throw BackupException("Could not read the selected file.")

        val backup = try {
            gson.fromJson(jsonText, BackupFile::class.java)
        } catch (e: Exception) {
            throw BackupException("This doesn't look like a valid AI Receipt Scanner backup file.")
        }

        if (backup == null || backup.receipts.isEmpty()) {
            throw BackupException("The backup file contains no receipts.")
        }
        if (backup.formatVersion > FORMAT_VERSION) {
            throw BackupException("This backup was created by a newer version of the app.")
        }
        return backup
    }

    /** Converts a parsed backup receipt into fresh Room entities ready to insert (ids left at 0). */
    fun toEntities(item: BackupReceipt): Pair<ReceiptEntity, List<ReceiptItemEntity>> {
        val receipt = ReceiptEntity(
            id = 0,
            shopName = item.shopName,
            invoiceNumber = item.invoiceNumber,
            date = item.date,
            time = item.time,
            subtotal = item.subtotal,
            gst = item.gst,
            total = item.total,
            paymentMethod = item.paymentMethod,
            imageUri = item.imageUri,
            rawOcrText = item.rawOcrText
        )
        val items = item.items.map {
            ReceiptItemEntity(
                receiptId = 0,
                name = it.name,
                quantity = it.quantity,
                unitPrice = it.unitPrice,
                totalPrice = it.totalPrice
            )
        }
        return receipt to items
    }
}
