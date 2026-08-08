package com.muraly.receiptscanner.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

/**
 * Handles exporting receipts to PDF and CSV. Files are written to the app's cache
 * directory and must be shared via FileProvider (see AndroidManifest / file_paths.xml) —
 * raw file:// URIs are blocked by modern Android and silently fail to open/share.
 */
object ExportHelper {
    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun exportToPdf(context: Context, receipt: ReceiptWithItems): File {
        val pdf = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas

        val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
        val labelPaint = Paint().apply { textSize = 12f }
        val boldPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }

        var y = 50f
        val left = 40f
        val r = receipt.receipt

        canvas.drawText(r.shopName.ifBlank { "Receipt" }, left, y, titlePaint)
        y += 22f
        canvas.drawText("Invoice: ${r.invoiceNumber.ifBlank { "-" }}", left, y, labelPaint)
        y += 16f
        canvas.drawText("Date: ${r.date}  ${r.time}", left, y, labelPaint)
        y += 16f
        canvas.drawText("Payment: ${r.paymentMethod.ifBlank { "-" }}", left, y, labelPaint)
        y += 26f

        canvas.drawText("Item", left, y, boldPaint)
        canvas.drawText("Qty", 360f, y, boldPaint)
        canvas.drawText("Amount", 470f, y, boldPaint)
        y += 8f
        canvas.drawLine(left, y, 555f, y, labelPaint)
        y += 18f

        receipt.items.forEach { item ->
            if (y > 780f) return@forEach // simple single-page guard
            canvas.drawText(item.name.take(40), left, y, labelPaint)
            canvas.drawText("x${item.quantity}", 360f, y, labelPaint)
            canvas.drawText(inrFormat.format(item.totalPrice), 470f, y, labelPaint)
            y += 18f
        }

        y += 10f
        canvas.drawLine(left, y, 555f, y, labelPaint)
        y += 20f
        canvas.drawText("Subtotal: ${inrFormat.format(r.subtotal)}", 380f, y, labelPaint)
        y += 16f
        canvas.drawText("GST: ${inrFormat.format(r.gst)}", 380f, y, labelPaint)
        y += 18f
        canvas.drawText("TOTAL: ${inrFormat.format(r.total)}", 380f, y, boldPaint)

        pdf.finishPage(page)

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = r.shopName.ifBlank { "receipt" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(exportDir, "${safeName}_${r.id}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    fun exportToCsv(context: Context, receipts: List<ReceiptWithItems>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, "receipts_export.csv")
        file.printWriter().use { out ->
            out.println("Shop Name,Invoice,Date,Time,Item,Qty,Unit Price,Item Total,Subtotal,GST,Total,Payment Method")
            receipts.forEach { rw ->
                val r = rw.receipt
                if (rw.items.isEmpty()) {
                    out.println(
                        "\"${r.shopName}\",\"${r.invoiceNumber}\",${r.date},${r.time},,,,," +
                            "${r.subtotal},${r.gst},${r.total},\"${r.paymentMethod}\""
                    )
                } else {
                    rw.items.forEach { item ->
                        out.println(
                            "\"${r.shopName}\",\"${r.invoiceNumber}\",${r.date},${r.time}," +
                                "\"${item.name}\",${item.quantity},${item.unitPrice},${item.totalPrice}," +
                                "${r.subtotal},${r.gst},${r.total},\"${r.paymentMethod}\""
                        )
                    }
                }
            }
        }
        return file
    }
}
