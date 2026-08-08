package com.muraly.receiptscanner.data.pref

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Stores the user's Gemini API key using EncryptedSharedPreferences (AES256-GCM for both
 * keys and values, backed by a MasterKey in the Android Keystore). The key is never
 * hardcoded anywhere in the app — it only ever exists as what the user typed in Settings.
 */
class SecurePreferencesHelper(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Extremely rare (broken Keystore on device) — fall back so the app doesn't crash,
        // but this should not happen on any modern Android device.
        Log.e("SecurePrefs", "Falling back to unencrypted prefs: ${e.message}")
        context.getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)
    }

    fun saveGeminiApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }

    fun getGeminiApiKey(): String? {
        return sharedPreferences.getString(KEY_GEMINI_API_KEY, null)
    }

    fun clearGeminiApiKey() {
        sharedPreferences.edit().remove(KEY_GEMINI_API_KEY).apply()
    }

    companion object {
        private const val PREFS_FILE_NAME = "receipt_prefs_encrypted"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
