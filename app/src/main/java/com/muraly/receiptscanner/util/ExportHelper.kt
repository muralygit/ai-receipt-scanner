package com.muraly.receiptscanner.util

import android.content.Context
import android.graphics.pdf.PdfDocument
import com.muraly.receiptscanner.data.local.entity.ReceiptWithItems
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.Locale

object ExportHelper {
    private val inrFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))

    fun exportToPdf(context: Context, receipt: ReceiptWithItems): File {
        val pdf = PdfDocument()
        val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        canvas.drawText(receipt.receipt.shopName, 40f, 50f, android.graphics.Paint())
        canvas.drawText("TOTAL: ${inrFormat.format(receipt.receipt.total)}", 40f, 100f, android.graphics.Paint())
        pdf.finishPage(page)
        val file = File(context.cacheDir, "receipt.pdf")
        pdf.writeTo(FileOutputStream(file))
        pdf.close()
        return file
    }

    fun exportToCsv(context: Context, receipts: List<ReceiptWithItems>): File {
        val file = File(context.cacheDir, "receipts.csv")
        file.printWriter().use { out ->
            out.println("ID,Shop Name,Invoice,Total (INR),Date")
            receipts.forEach { out.println("${it.receipt.id},${it.receipt.shopName},${it.receipt.invoiceNumber},${inrFormat.format(it.receipt.total)},${it.receipt.date}") }
        }
        return file
    }
}
