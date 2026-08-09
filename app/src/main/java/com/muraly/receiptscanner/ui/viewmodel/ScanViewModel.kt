package com.muraly.receiptscanner.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.*
import com.muraly.receiptscanner.data.pref.SecurePreferencesHelper
import com.muraly.receiptscanner.util.*
import kotlinx.coroutines.launch

sealed class ScanUiState {
    object Idle : ScanUiState()
    object OcrProcessing : ScanUiState()
    object AiProcessing : ScanUiState()
    data class Success(val parsedResult: ParsedReceiptResult, val imageUri: String, val rawOcrText: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

class ScanViewModel(
    private val ocrHelper: OcrHelper,
    private val geminiHelper: GeminiHelper,
    private val prefsHelper: SecurePreferencesHelper
) : ViewModel() {

    private val _uiState = MutableLiveData<ScanUiState>(ScanUiState.Idle)
    val uiState: LiveData<ScanUiState> = _uiState

    fun processReceiptImage(bitmap: Bitmap, imageUriString: String) {
        val apiKey = prefsHelper.getGeminiApiKey()
        if (apiKey.isNullOrEmpty()) {
            _uiState.value = ScanUiState.Error("Gemini API Key missing. Please configure in Settings.")
            return
        }

        viewModelScope.launch {
            _uiState.value = ScanUiState.OcrProcessing
            try {
                val ocrText = ocrHelper.recognizeText(bitmap)
                _uiState.value = ScanUiState.AiProcessing
                val customInstructions = buildCustomInstructions()
                val parsedResult = geminiHelper.extractReceiptData(ocrText, apiKey, bitmap, customInstructions)
                _uiState.value = ScanUiState.Success(parsedResult, imageUriString, ocrText)
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error("Failed: ${e.message}")
            }
        }
    }

    /** Combines the user's saved Settings preferences into one instruction block for Gemini. */
    private fun buildCustomInstructions(): String = buildString {
        val bias = prefsHelper.getCategoryBias()
        if (bias.isNotBlank()) {
            appendLine("The user most commonly scans these types of receipts: $bias. If the category is genuinely ambiguous, prefer one of these over others — but still pick a different category if the receipt clearly doesn't match any of them.")
        }
        if (prefsHelper.getDateFormatPreference() == "MM/DD/YYYY") {
            appendLine("If a date on this receipt is ambiguous (e.g. 03/04/2026), interpret it as MM/DD/YYYY (US format) rather than the DD/MM/YYYY default.")
        }
        val custom = prefsHelper.getCustomInstructions()
        if (custom.isNotBlank()) {
            appendLine(custom)
        }
    }
}
