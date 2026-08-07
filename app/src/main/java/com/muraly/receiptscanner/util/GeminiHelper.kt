package com.muraly.receiptscanner.util

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class ParsedReceiptItem(
    @SerializedName("name") val name: String = "",
    @SerializedName("qty") val qty: Int = 1,
    @SerializedName("price") val price: Double = 0.0
)

data class ParsedReceiptResult(
    @SerializedName("shop_name") val shopName: String = "",
    @SerializedName("invoice_number") val invoiceNumber: String = "",
    @SerializedName("date") val date: String = "",
    @SerializedName("time") val time: String = "",
    @SerializedName("items") val items: List<ParsedReceiptItem> = emptyList(),
    @SerializedName("subtotal") val subtotal: Double = 0.0,
    @SerializedName("gst") val gst: Double = 0.0,
    @SerializedName("total") val total: Double = 0.0,
    @SerializedName("payment_method") val paymentMethod: String = ""
)

class GeminiHelper {
    private val client = OkHttpClient()
    private val gson = Gson()

    suspend fun extractReceiptData(ocrText: String, apiKey: String): ParsedReceiptResult = withContext(Dispatchers.IO) {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
        val prompt = "Extract shop_name, invoice_number, date, time, items, subtotal, gst, total, payment_method in JSON format for Indian GST receipt: $ocrText"

        val body = gson.toJson(mapOf("contents" to listOf(mapOf("parts" to listOf(mapOf("text" to prompt)))))).toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(endpoint).post(body).build()

        client.newCall(req).execute().use { response ->
            val jsonStr = response.body?.string() ?: ""
            return@withContext gson.fromJson(jsonStr, ParsedReceiptResult::class.java)
        }
    }
}
