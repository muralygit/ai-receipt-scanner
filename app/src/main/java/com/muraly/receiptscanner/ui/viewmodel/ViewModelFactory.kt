package com.muraly.receiptscanner.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.muraly.receiptscanner.data.pref.SecurePreferencesHelper
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import com.muraly.receiptscanner.util.GeminiHelper
import com.muraly.receiptscanner.util.OcrHelper

/**
 * Simple factory that knows how to construct each of our ViewModels with the
 * dependencies they need (repository, OCR/Gemini helpers, secure prefs).
 */
class ViewModelFactory(
    private val repository: ReceiptRepository,
    private val securePrefs: SecurePreferencesHelper
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when {
            modelClass.isAssignableFrom(MainViewModel::class.java) ->
                MainViewModel(repository) as T

            modelClass.isAssignableFrom(ScanViewModel::class.java) ->
                ScanViewModel(OcrHelper(), GeminiHelper(), securePrefs) as T

            modelClass.isAssignableFrom(ReviewViewModel::class.java) ->
                ReviewViewModel(repository) as T

            modelClass.isAssignableFrom(DetailViewModel::class.java) ->
                DetailViewModel(repository) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
