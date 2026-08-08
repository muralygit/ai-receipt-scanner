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

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val LEFT = 40f
    private const val RIGHT = 555f
    private const val TOP_MARGIN = 50f
    private const val BOTTOM_MARGIN = 70f // reserved space so an item row never gets cut mid-line
    private const val ROW_HEIGHT = 18f

    private val titlePaint = Paint().apply { textSize = 18f; isFakeBoldText = true }
    private val labelPaint = Paint().apply { textSize = 12f }
    private val boldPaint = Paint().apply { textSize = 13f; isFakeBoldText = true }
    private val footerPaint = Paint().apply { textSize = 10f; color = 0xFF888888.toInt() }

    /**
     * Exports a single receipt to PDF, spanning as many pages as needed to fit every
     * item line. The shop header repeats at the top of each page and a "Page X of Y"
     * footer is stamped once the total page count is known.
     */
    fun exportToPdf(context: Context, receipt: ReceiptWithItems): File {
        val pdf = PdfDocument()
        val r = receipt.receipt
        val pages = mutableListOf<PdfDocument.Page>()

        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = drawReceiptHeader(canvas, r, isFirstPage = true)
        y = drawItemsTableHeader(canvas, y)

        val itemIterator = receipt.items.iterator()
        while (itemIterator.hasNext()) {
            val item = itemIterator.next()

            // Not enough room for another row (plus totals block if this is the last item) -> new page.
            val needsTotalsSpace = !itemIterator.hasNext()
            val requiredSpace = ROW_HEIGHT + (if (needsTotalsSpace) 90f else 0f)
            if (y + requiredSpace > PAGE_HEIGHT - BOTTOM_MARGIN) {
                pdf.finishPage(page)
                pages.add(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = drawReceiptHeader(canvas, r, isFirstPage = false)
                y = drawItemsTableHeader(canvas, y)
            }

            canvas.drawText(item.name.take(40), LEFT, y, labelPaint)
            canvas.drawText("x${item.quantity}", 360f, y, labelPaint)
            canvas.drawText(inrFormat.format(item.totalPrice), 470f, y, labelPaint)
            y += ROW_HEIGHT
        }

        if (receipt.items.isEmpty()) {
            canvas.drawText("(no items recorded)", LEFT, y, labelPaint)
            y += ROW_HEIGHT
        }

        y += 10f
        canvas.drawLine(LEFT, y, RIGHT, y, labelPaint)
        y += 20f
        canvas.drawText("Subtotal: ${inrFormat.format(r.subtotal)}", 380f, y, labelPaint)
        y += 16f
        canvas.drawText("GST: ${inrFormat.format(r.gst)}", 380f, y, labelPaint)
        y += 18f
        canvas.drawText("TOTAL: ${inrFormat.format(r.total)}", 380f, y, boldPaint)

        pdf.finishPage(page)
        pages.add(page)

        // Stamp "Page X of Y" on every page now that the final count is known.
        val totalPages = pages.size
        if (totalPages > 1) {
            pages.forEachIndexed { index, finishedPage ->
                finishedPage.canvas.drawText(
                    "Page ${index + 1} of $totalPages",
                    RIGHT - 90f,
                    (PAGE_HEIGHT - 30).toFloat(),
                    footerPaint
                )
            }
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = r.shopName.ifBlank { "receipt" }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(exportDir, "${safeName}_${r.id}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    /** Draws the shop/invoice header block and returns the Y position to continue drawing from. */
    private fun drawReceiptHeader(canvas: android.graphics.Canvas, r: com.muraly.receiptscanner.data.local.entity.ReceiptEntity, isFirstPage: Boolean): Float {
        var y = TOP_MARGIN
        canvas.drawText(r.shopName.ifBlank { "Receipt" }, LEFT, y, titlePaint)
        y += 22f
        canvas.drawText("Invoice: ${r.invoiceNumber.ifBlank { "-" }}", LEFT, y, labelPaint)
        y += 16f
        canvas.drawText("Date: ${r.date}  ${r.time}", LEFT, y, labelPaint)
        y += 16f
        canvas.drawText("Payment: ${r.paymentMethod.ifBlank { "-" }}", LEFT, y, labelPaint)
        if (!isFirstPage) {
            canvas.drawText("(continued)", 450f, TOP_MARGIN, footerPaint)
        }
        y += 26f
        return y
    }

    private fun drawItemsTableHeader(canvas: android.graphics.Canvas, startY: Float): Float {
        var y = startY
        canvas.drawText("Item", LEFT, y, boldPaint)
        canvas.drawText("Qty", 360f, y, boldPaint)
        canvas.drawText("Amount", 470f, y, boldPaint)
        y += 8f
        canvas.drawLine(LEFT, y, RIGHT, y, labelPaint)
        y += 18f
        return y
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
