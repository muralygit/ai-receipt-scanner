package com.muraly.receiptscanner.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.muraly.receiptscanner.databinding.ActivitySettingsBinding
import com.muraly.receiptscanner.util.GeminiHelper
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val geminiHelper = GeminiHelper()
    private val categoryChips = mutableListOf<Chip>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val app = application as com.muraly.receiptscanner.ReceiptScannerApplication
        binding.etApiKey.setText(app.securePrefs.getGeminiApiKey() ?: "")

        setupCategoryChips(app)
        setupDateFormatPreference(app)
        binding.etCustomInstructions.setText(app.securePrefs.getCustomInstructions())

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
                binding.btnSaveKey.text = "Save Settings"

                if (isValid) {
                    app.securePrefs.saveGeminiApiKey(key)
                    savePreferences(app)
                    Toast.makeText(this@SettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
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

    private fun setupCategoryChips(app: com.muraly.receiptscanner.ReceiptScannerApplication) {
        val savedBias = app.securePrefs.getCategoryBias().split(",").map { it.trim() }.toSet()

        ReviewActivity.CATEGORIES.filter { it != "General" && it != "Other" }.forEach { category ->
            val chip = Chip(this).apply {
                text = category
                isCheckable = true
                isChecked = category in savedBias
            }
            categoryChips.add(chip)
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun setupDateFormatPreference(app: com.muraly.receiptscanner.ReceiptScannerApplication) {
        if (app.securePrefs.getDateFormatPreference() == "MM/DD/YYYY") {
            binding.rbDateMDY.isChecked = true
        } else {
            binding.rbDateDMY.isChecked = true
        }
    }

    private fun savePreferences(app: com.muraly.receiptscanner.ReceiptScannerApplication) {
        val selectedCategories = categoryChips.filter { it.isChecked }.joinToString(",") { it.text.toString() }
        app.securePrefs.saveCategoryBias(selectedCategories)

        val dateFormat = if (binding.rbDateMDY.isChecked) "MM/DD/YYYY" else "DD/MM/YYYY"
        app.securePrefs.saveDateFormatPreference(dateFormat)

        app.securePrefs.saveCustomInstructions(binding.etCustomInstructions.text.toString())
    }
}
