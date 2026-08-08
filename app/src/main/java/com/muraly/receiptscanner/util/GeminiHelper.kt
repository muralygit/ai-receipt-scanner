package com.muraly.receiptscanner.util

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    @SerializedName("payment_method") val paymentMethod: String = "",
    @SerializedName("category") val category: String = "General"
)

/** Thrown for any Gemini-related failure so the UI layer can show a clear message. */
class GeminiException(message: String) : Exception(message)

class GeminiHelper {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Sends OCR text to Gemini and extracts structured receipt data.
     * Handles Gemini's real response envelope (candidates -> content -> parts -> text)
     * and strips any ```json fencing the model adds before parsing the inner JSON.
     */
    suspend fun extractReceiptData(ocrText: String, apiKey: String): ParsedReceiptResult =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) {
                throw GeminiException("Gemini API key is missing. Add it in Settings.")
            }
            if (ocrText.isBlank()) {
                throw GeminiException("No text was detected on the receipt. Try rescanning with better lighting.")
            }

            val endpoint =
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent?key=$apiKey"

            val prompt = """
                You are given raw OCR text from an Indian retail receipt (may include GST/CGST/SGST/IGST).
                Extract the following fields and return ONLY a single valid JSON object, with no markdown
                fences, no explanation, and no extra text before or after it:

                {
                  "shop_name": "",
                  "invoice_number": "",
                  "date": "",
                  "time": "",
                  "items": [ { "name": "", "qty": 1, "price": 0 } ],
                  "subtotal": 0,
                  "gst": 0,
                  "total": 0,
                  "payment_method": "",
                  "category": ""
                }

                Rules:
                - qty and price/subtotal/gst/total must be plain numbers (no currency symbols, no commas).
                - If a field cannot be found, use an empty string ("") or 0.
                - date should be in YYYY-MM-DD format if determinable.
                - category must be exactly one of: Groceries, Medical, Dining, Fuel, Electronics,
                  Household, Clothing, Utilities, Entertainment, Other. Pick the closest match based
                  on the shop name and items; use "Other" only if nothing fits well.

                OCR TEXT:
                $ocrText
            """.trimIndent()

            val requestJson = gson.toJson(
                mapOf(
                    "contents" to listOf(
                        mapOf("parts" to listOf(mapOf("text" to prompt)))
                    ),
                    "generationConfig" to mapOf(
                        "temperature" to 0.1,
                        "responseMimeType" to "application/json"
                    )
                )
            )

            val body = requestJson.toRequestBody("application/json".toMediaType())
            val request = Request.Builder().url(endpoint).post(body).build()

            val rawResponse: String
            try {
                client.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val reason = extractErrorMessage(responseBody) ?: response.message
                        throw GeminiException(
                            when (response.code) {
                                400, 401, 403 -> "Invalid or unauthorized Gemini API key. Check Settings."
                                429 -> "Gemini rate limit reached. Please try again shortly."
                                else -> "Gemini request failed (${response.code}): $reason"
                            }
                        )
                    }
                    rawResponse = responseBody
                }
            } catch (e: IOException) {
                throw GeminiException("No internet connection or Gemini is unreachable. Check your network.")
            }

            val extractedText = extractTextFromCandidates(rawResponse)
                ?: throw GeminiException("Gemini returned an unexpected response format.")

            val cleanJson = stripMarkdownFences(extractedText)

            try {
                gson.fromJson(cleanJson, ParsedReceiptResult::class.java)
                    ?: throw GeminiException("Gemini's response could not be parsed into receipt data.")
            } catch (e: Exception) {
                throw GeminiException("Could not parse the AI's response as JSON. Try rescanning.")
            }
        }

    /** Pulls the model's text output out of Gemini's candidates[0].content.parts[0].text envelope. */
    private fun extractTextFromCandidates(rawJson: String): String? {
        return try {
            val root = JsonParser.parseString(rawJson).asJsonObject
            val candidates = root.getAsJsonArray("candidates") ?: return null
            if (candidates.size() == 0) return null
            val firstCandidate = candidates[0].asJsonObject
            val content = firstCandidate.getAsJsonObject("content") ?: return null
            val parts = content.getAsJsonArray("parts") ?: return null
            if (parts.size() == 0) return null
            parts[0].asJsonObject.get("text")?.asString
        } catch (e: Exception) {
            null
        }
    }

    private fun extractErrorMessage(rawJson: String): String? {
        return try {
            val root = JsonParser.parseString(rawJson).asJsonObject
            root.getAsJsonObject("error")?.get("message")?.asString
        } catch (e: Exception) {
            null
        }
    }

    /** Removes ```json / ``` fences that Gemini sometimes wraps its JSON output in. */
    private fun stripMarkdownFences(text: String): String {
        var cleaned = text.trim()
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.removePrefix("```json").removePrefix("```").trim()
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.removeSuffix("```").trim()
            }
        }
        return cleaned
    }
}
