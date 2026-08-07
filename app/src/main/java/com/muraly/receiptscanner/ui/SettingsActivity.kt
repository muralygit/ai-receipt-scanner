package com.muraly.receiptscanner.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.muraly.receiptscanner.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as com.muraly.receiptscanner.ReceiptScannerApplication
        binding.etApiKey.setText(app.securePrefs.getGeminiApiKey() ?: "")

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString()
            app.securePrefs.saveGeminiApiKey(key)
            finish()
        }
    }
}
