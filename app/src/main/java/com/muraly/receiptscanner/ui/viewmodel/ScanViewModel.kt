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
                val parsedResult = geminiHelper.extractReceiptData(ocrText, apiKey)
                _uiState.value = ScanUiState.Success(parsedResult, imageUriString, ocrText)
            } catch (e: Exception) {
                _uiState.value = ScanUiState.Error("Failed: ${e.message}")
            }
        }
    }
}
