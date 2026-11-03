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
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
 * A parsed backup. [extractedImages] is a temp folder holding the photos when the backup was a
 * .zip (format v2); it is null for old plain-JSON backups, which carry no photos.
 */
class BackupContents(val backup: BackupFile, val extractedImages: File?)

/**
 * Exports every saved receipt (data + photos) to a single .zip file, and reads one back for restore.
 * Zip layout: backup.json, images/img_0.jpg, images/img_1.jpg ... Old plain-JSON backups
 * (format v1, no photos) can still be restored.
 * Uses its own plain data classes rather than the Room entities directly, so the backup
 * format stays stable even if the database schema changes in a future version.
 */
object BackupHelper {
    private const val FORMAT_VERSION = 2
    private const val JSON_ENTRY = "backup.json"
    private const val IMAGES_PREFIX = "images/"
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /** Builds the backup .zip in the cache folder. Call from a background thread. */
    fun exportBackup(context: Context, receipts: List<ReceiptWithItems>): File {
        // imageEntries[i] = photo file to add as images/img_i.jpg (null if the receipt has no photo on disk)
        val imageFiles = mutableListOf<File>()
        val backupReceipts = receipts.map { rw ->
            val photo = rw.receipt.imageUri.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.exists() }
            val entryName = if (photo != null) {
                imageFiles.add(photo)
                "${IMAGES_PREFIX}img_${imageFiles.size - 1}.jpg"
            } else {
                ""
            }
            BackupReceipt(
                shopName = rw.receipt.shopName,
                invoiceNumber = rw.receipt.invoiceNumber,
                date = rw.receipt.date,
                time = rw.receipt.time,
                subtotal = rw.receipt.subtotal,
                gst = rw.receipt.gst,
                total = rw.receipt.total,
                paymentMethod = rw.receipt.paymentMethod,
                imageUri = entryName,
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
        val file = File(exportDir, "ai_receipt_scanner_backup_$timestamp.zip")
        ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(JSON_ENTRY))
            zip.write(gson.toJson(backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            imageFiles.forEachIndexed { index, photo ->
                zip.putNextEntry(ZipEntry("${IMAGES_PREFIX}img_$index.jpg"))
                photo.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return file
    }

    /**
     * Reads a backup picked by the user: either the new .zip (data + photos) or an old plain
     * .json. Photos from a zip are unpacked into a temp folder; call [cleanup] when done.
     * Does not touch the database. Call from a background thread.
     */
    fun readBackup(context: Context, uri: Uri): BackupContents {
        val input = (try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            null
        }) ?: throw BackupException("Could not read the selected file.")

        val bytesHead = ByteArray(2)
        val buffered = input.buffered()
        buffered.mark(4)
        val headRead = try { buffered.read(bytesHead) } catch (e: Exception) { -1 }
        buffered.reset()
        val isZip = headRead == 2 && bytesHead[0] == 'P'.code.toByte() && bytesHead[1] == 'K'.code.toByte()

        var jsonText: String? = null
        var imagesDir: File? = null
        try {
            if (isZip) {
                imagesDir = File(context.cacheDir, "restore_tmp_${System.currentTimeMillis()}").apply { mkdirs() }
                ZipInputStream(buffered).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            if (entry.name == JSON_ENTRY) {
                                jsonText = zip.readBytes().toString(Charsets.UTF_8)
                            } else if (entry.name.startsWith(IMAGES_PREFIX)) {
                                // Only the bare file name is used, so a crafted entry can't escape the folder.
                                val safeName = File(entry.name).name
                                File(imagesDir, safeName).outputStream().use { zip.copyTo(it) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } else {
                jsonText = buffered.use { it.bufferedReader().readText() }
            }
        } catch (e: Exception) {
            imagesDir?.deleteRecursively()
            throw BackupException("Could not read the selected file.")
        }

        if (jsonText.isNullOrBlank()) {
            imagesDir?.deleteRecursively()
            throw BackupException(
                if (isZip) "This zip doesn't contain a valid receipt backup." else "Could not read the selected file."
            )
        }

        val backup = try {
            gson.fromJson(jsonText, BackupFile::class.java)
        } catch (e: Exception) {
            imagesDir?.deleteRecursively()
            throw BackupException("This doesn't look like a valid AI Receipt Scanner backup file.")
        }

        val receiptsList = backup?.receipts
        if (backup == null || receiptsList.isNullOrEmpty()) {
            imagesDir?.deleteRecursively()
            throw BackupException("The backup file contains no receipts.")
        }
        val version = backup.formatVersion ?: 1
        if (version > FORMAT_VERSION) {
            imagesDir?.deleteRecursively()
            throw BackupException("This backup was created by a newer version of the app.")
        }
        return BackupContents(backup, imagesDir)
    }

    /** Deletes the temp folder of unpacked photos. */
    fun cleanup(contents: BackupContents) {
        contents.extractedImages?.deleteRecursively()
    }

    /** Number of receipts in a parsed backup, safe against a null list. */
    fun receiptCountOf(backup: BackupFile): Int = backup.receipts?.size ?: 0

    /** Converts a parsed backup receipt into fresh Room entities ready to insert (ids left at 0). */
    fun toEntities(
        context: Context,
        item: BackupReceipt,
        extractedImages: File?
    ): Pair<ReceiptEntity, List<ReceiptItemEntity>> {
        val imagePath = restoreImage(context, item.imageUri, extractedImages)
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
            imageUri = imagePath,
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

    /**
     * Zip backups: copies the unpacked photo into permanent storage and returns its new path
     * ("" if the photo is missing). Old JSON backups: keeps the stored path, which still works
     * if the file is still on this device.
     */
    private fun restoreImage(context: Context, stored: String?, extractedImages: File?): String {
        if (stored.isNullOrBlank()) return ""
        if (extractedImages == null) return stored
        val src = File(extractedImages, File(stored).name)
        if (!src.exists()) return ""
        return try {
            val dest = File(ImageHelper.imageDir(context), "restored_${System.nanoTime()}.jpg")
            src.copyTo(dest, overwrite = true)
            dest.absolutePath
        } catch (e: Exception) {
            ""
        }
    }
}
