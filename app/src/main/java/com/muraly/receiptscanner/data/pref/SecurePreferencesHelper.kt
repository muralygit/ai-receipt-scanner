package com.muraly.receiptscanner.data.pref

import android.content.Context
import android.content.SharedPreferences

class SecurePreferencesHelper(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("receipt_prefs", Context.MODE_PRIVATE)

    fun saveGeminiApiKey(apiKey: String) {
        sharedPreferences.edit().putString("gemini_api_key", apiKey.trim()).apply()
    }

    fun getGeminiApiKey(): String? {
        return sharedPreferences.getString("gemini_api_key", null)
    }
}
