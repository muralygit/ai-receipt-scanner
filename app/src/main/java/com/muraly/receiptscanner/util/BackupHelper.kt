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

/**
 * NOTE on nullability: Gson does not invoke the Kotlin constructor when deserializing —
 * it allocates the object via Unsafe and assigns fields by reflection. That means a field
 * declared non-null in Kotlin (e.g. `val items: List<BackupItem>`) can still end up as a
 * real `null` at runtime if the JSON is missing that key, malformed, or from an older
 * backup format. All fields below are therefore declared nullable with safe defaults, and
 * every read in this file uses `?:` fallbacks instead of trusting the Kotlin type alone.
 */
data class BackupItem(
    val name: String? = null,
    val quantity: Int? = null,
    val unitPrice: Double? = null,
    val totalPrice: Double? = null
)

data class BackupReceipt(
    val shopName: String? = null,
    val invoiceNumber: String? = null,
    val date: String? = null,
    val time: String? = null,
    val subtotal: Double? = null,
    val gst: Double? = null,
    val total: Double? = null,
    val paymentMethod: String? = null,
    val imageUri: String? = null,
    val rawOcrText: String? = null,
    val category: String? = null, // added later; old backups won't have this, defaults to "General"
    val items: List<BackupItem>? = null
)

data class BackupFile(
    val formatVersion: Int? = null,
    val exportedAt: String? = null,
    val receiptCount: Int? = null,
    val receipts: List<BackupReceipt>? = null
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
                category = rw.receipt.category,
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
        val jsonText = try {
            context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            null
        }
        if (jsonText.isNullOrBlank()) {
            throw BackupException("Could not read the selected file.")
        }

        val backup = try {
            gson.fromJson(jsonText, BackupFile::class.java)
        } catch (e: Exception) {
            throw BackupException("This doesn't look like a valid AI Receipt Scanner backup file.")
        }

        val receiptsList = backup?.receipts
        if (backup == null || receiptsList.isNullOrEmpty()) {
            throw BackupException("The backup file contains no receipts.")
        }
        val version = backup.formatVersion ?: 1
        if (version > FORMAT_VERSION) {
            throw BackupException("This backup was created by a newer version of the app.")
        }
        return backup
    }

    /** Number of receipts in a parsed backup, safe against a null list. */
    fun receiptCountOf(backup: BackupFile): Int = backup.receipts?.size ?: 0

    /** Converts a parsed backup receipt into fresh Room entities ready to insert (ids left at 0). */
    fun toEntities(item: BackupReceipt): Pair<ReceiptEntity, List<ReceiptItemEntity>> {
        val receipt = ReceiptEntity(
            id = 0,
            shopName = item.shopName ?: "",
            invoiceNumber = item.invoiceNumber ?: "",
            date = item.date ?: "",
            time = item.time ?: "",
            subtotal = item.subtotal ?: 0.0,
            gst = item.gst ?: 0.0,
            total = item.total ?: 0.0,
            paymentMethod = item.paymentMethod ?: "",
            imageUri = item.imageUri ?: "",
            rawOcrText = item.rawOcrText ?: "",
            category = item.category ?: "General"
        )
        val items = (item.items ?: emptyList()).map {
            ReceiptItemEntity(
                receiptId = 0,
                name = it.name ?: "",
                quantity = it.quantity ?: 1,
                unitPrice = it.unitPrice ?: 0.0,
                totalPrice = it.totalPrice ?: 0.0
            )
        }
        return receipt to items
    }
}
