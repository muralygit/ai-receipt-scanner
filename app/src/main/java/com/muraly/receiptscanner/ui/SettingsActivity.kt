package com.muraly.receiptscanner.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.muraly.receiptscanner.databinding.ActivitySettingsBinding
import com.muraly.receiptscanner.util.GeminiHelper
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val geminiHelper = GeminiHelper()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as com.muraly.receiptscanner.ReceiptScannerApplication
        binding.etApiKey.setText(app.securePrefs.getGeminiApiKey() ?: "")

        binding.btnSaveKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "Enter an API key first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            binding.btnSaveKey.isEnabled = false
            binding.btnSaveKey.text = "Checking key…"

            lifecycleScope.launch {
                val isValid = try {
                    geminiHelper.validateApiKey(key)
                } catch (e: Exception) {
                    false
                }

                binding.btnSaveKey.isEnabled = true
                binding.btnSaveKey.text = "Save API Key"

                if (isValid) {
                    app.securePrefs.saveGeminiApiKey(key)
                    Toast.makeText(this@SettingsActivity, "API key verified and saved", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(
                        this@SettingsActivity,
                        "Could not verify this key. Check that it's correct and that you have an internet connection.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
