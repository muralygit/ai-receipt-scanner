package com.muraly.receiptscanner.ui

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors

/**
 * Offline in-app user guide, in English and Malayalam. The chosen language is remembered.
 */
class GuideActivity : AppCompatActivity() {

    private data class Section(
        val titleEn: String, val bodyEn: String,
        val titleMl: String, val bodyMl: String
    )

    private lateinit var content: LinearLayout
    private lateinit var tvTitle: TextView
    private lateinit var btnEn: MaterialButton
    private lateinit var btnMl: MaterialButton
    private var lang = LANG_EN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lang = try {
            getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_LANG, LANG_EN) ?: LANG_EN
        } catch (e: Exception) {
            LANG_EN
        }

        val basePad = dp(16)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(basePad, basePad, basePad, basePad)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(basePad, basePad + bars.top, basePad, basePad + bars.bottom)
            insets
        }

        // Header: title + language buttons
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvTitle = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        header.addView(tvTitle, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        btnEn = languageButton("English") { setLanguage(LANG_EN) }
        btnMl = languageButton("മലയാളം") { setLanguage(LANG_ML) }
        header.addView(btnEn)
        header.addView(btnMl)
        root.addView(header)

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(24))
        }
        root.addView(
            ScrollView(this).apply { addView(content) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        render()
    }

    private fun languageButton(label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(6) }
        }

    private fun setLanguage(newLang: String) {
        lang = newLang
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_LANG, lang).apply()
        } catch (e: Exception) {
            // Remembering the choice is a convenience only.
        }
        render()
    }

    private fun render() {
        val isMl = lang == LANG_ML
        tvTitle.text = if (isMl) "ഉപയോഗ സഹായി" else "User Guide"
        btnEn.alpha = if (isMl) 0.55f else 1f
        btnMl.alpha = if (isMl) 1f else 0.55f

        val accent = MaterialColors.getColor(content, com.google.android.material.R.attr.colorPrimary)
        content.removeAllViews()
        for (section in SECTIONS) {
            content.addView(TextView(this).apply {
                text = if (isMl) section.titleMl else section.titleEn
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(accent)
                setPadding(0, dp(18), 0, dp(6))
            })
            content.addView(TextView(this).apply {
                text = if (isMl) section.bodyMl else section.bodyEn
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setLineSpacing(0f, 1.2f)
            })
        }
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    companion object {
        private const val PREFS = "app_prefs"
        private const val KEY_LANG = "guide_language"
        private const val LANG_EN = "en"
        private const val LANG_ML = "ml"

        private val SECTIONS = listOf(
            Section(
                "1. Set up your Gemini key",
                "The app uses Google Gemini to understand your receipts, so it needs a free API key.\n" +
                    "1. Open aistudio.google.com/apikey in your browser and sign in with a Google account.\n" +
                    "2. Create an API key and copy it.\n" +
                    "3. In this app, tap the spanner icon (top right) to open Settings.\n" +
                    "4. Paste the key and tap Save API Key. The app checks it (internet needed) and saves it.\n" +
                    "You only need to do this once.",
                "1. Gemini key സജ്ജമാക്കുക",
                "രസീതുകൾ മനസ്സിലാക്കാൻ ആപ്പ് Google Gemini ഉപയോഗിക്കുന്നു, അതിനാൽ ഒരു സൗജന്യ API key ആവശ്യമാണ്.\n" +
                    "1. ബ്രൗസറിൽ aistudio.google.com/apikey തുറന്ന് Google അക്കൗണ്ടിൽ സൈൻ ഇൻ ചെയ്യുക.\n" +
                    "2. ഒരു API key ഉണ്ടാക്കി കോപ്പി ചെയ്യുക.\n" +
                    "3. ഈ ആപ്പിൽ മുകളിൽ വലതുവശത്തുള്ള സ്പാനർ ഐക്കൺ തൊട്ട് Settings തുറക്കുക.\n" +
                    "4. key പേസ്റ്റ് ചെയ്ത് Save API Key അമർത്തുക. ആപ്പ് അത് പരിശോധിച്ച് (ഇന്റർനെറ്റ് വേണം) സേവ് ചെയ്യും.\n" +
                    "ഇത് ഒരിക്കൽ മാത്രം ചെയ്താൽ മതി."
            ),
            Section(
                "2. Scan a receipt",
                "1. Tap Scan Receipt.\n" +
                    "2. Take a photo of the receipt (Capture photo), or choose one from your gallery (Gallery).\n" +
                    "3. Wait a few seconds. The app reads the text on your phone, then Gemini picks out the shop, " +
                    "invoice number, date, items, GST and total (internet needed).\n" +
                    "4. The Review screen opens with everything filled in. Correct anything that is wrong, " +
                    "choose a category, and tap Save Receipt.\n" +
                    "If the receipt looks like one you already saved, the app warns you before saving it again.",
                "2. രസീത് സ്കാൻ ചെയ്യുക",
                "1. Scan Receipt അമർത്തുക.\n" +
                    "2. രസീതിന്റെ ഫോട്ടോ എടുക്കുക (Capture photo), അല്ലെങ്കിൽ ഗാലറിയിൽ നിന്ന് തിരഞ്ഞെടുക്കുക (Gallery).\n" +
                    "3. കുറച്ച് സെക്കൻഡ് കാത്തിരിക്കുക. ആപ്പ് ഫോണിൽ തന്നെ രസീതിലെ എഴുത്ത് വായിക്കും; തുടർന്ന് Gemini " +
                    "കടയുടെ പേര്, ഇൻവോയ്സ് നമ്പർ, തീയതി, സാധനങ്ങൾ, GST, ആകെ തുക എന്നിവ കണ്ടെത്തും (ഇന്റർനെറ്റ് വേണം).\n" +
                    "4. എല്ലാം പൂരിപ്പിച്ച Review സ്ക്രീൻ തുറക്കും. തെറ്റുണ്ടെങ്കിൽ തിരുത്തുക, Category തിരഞ്ഞെടുക്കുക, " +
                    "Save Receipt അമർത്തുക.\n" +
                    "ഇതേ രസീത് മുമ്പ് സേവ് ചെയ്തതാണെന്ന് തോന്നിയാൽ, വീണ്ടും സേവ് ചെയ്യുന്നതിന് മുമ്പ് ആപ്പ് മുന്നറിയിപ്പ് തരും."
            ),
            Section(
                "3. Tips for a good scan",
                "• Use good light and avoid shadows.\n" +
                    "• Lay the receipt flat and fill the screen with it.\n" +
                    "• Hold the phone steady until the photo is taken.\n" +
                    "• Check the Review screen every time. AI can make mistakes, especially with faded or crumpled receipts.\n" +
                    "Photos are shrunk automatically when saved, so they take very little space.",
                "3. നല്ല സ്കാനിനുള്ള നിർദേശങ്ങൾ",
                "• നല്ല വെളിച്ചത്തിൽ എടുക്കുക, നിഴൽ വീഴാതെ നോക്കുക.\n" +
                    "• രസീത് നിരപ്പായി വെച്ച്, സ്ക്രീൻ നിറയുന്ന രീതിയിൽ എടുക്കുക.\n" +
                    "• ഫോട്ടോ എടുക്കുന്നതുവരെ ഫോൺ അനങ്ങാതെ പിടിക്കുക.\n" +
                    "• ഓരോ തവണയും Review സ്ക്രീൻ പരിശോധിക്കുക. മങ്ങിയതോ ചുളിഞ്ഞതോ ആയ രസീതുകളിൽ AI-ക്ക് തെറ്റ് പറ്റാം.\n" +
                    "സേവ് ചെയ്യുമ്പോൾ ഫോട്ടോകൾ സ്വയം ചെറുതാക്കുന്നതിനാൽ വളരെ കുറച്ച് സ്ഥലമേ എടുക്കൂ."
            ),
            Section(
                "4. View, edit and delete",
                "• Tap a receipt in the list to open it. Tap the photo to see it bigger.\n" +
                    "• Edit changes the details. Delete removes the receipt. Export PDF makes a PDF of one receipt, " +
                    "and Share sends it to another app.\n" +
                    "• You can also press and hold a receipt in the list to delete it. Deleting cannot be undone.",
                "4. കാണുക, തിരുത്തുക, ഡിലീറ്റ് ചെയ്യുക",
                "• ലിസ്റ്റിലെ രസീത് തൊട്ടാൽ അത് തുറക്കും. ഫോട്ടോ തൊട്ടാൽ വലുതായി കാണാം.\n" +
                    "• Edit വിവരങ്ങൾ മാറ്റാം. Delete രസീത് നീക്കം ചെയ്യും. Export PDF ഒരു രസീതിന്റെ PDF ഉണ്ടാക്കും. " +
                    "Share മറ്റൊരു ആപ്പിലേക്ക് അയയ്ക്കും.\n" +
                    "• ലിസ്റ്റിൽ രസീതിൽ അമർത്തിപ്പിടിച്ചും ഡിലീറ്റ് ചെയ്യാം. ഡിലീറ്റ് ചെയ്തത് തിരികെ കിട്ടില്ല."
            ),
            Section(
                "5. Search",
                "Type in the search bar at the top. It finds receipts by:\n" +
                    "• shop name or invoice number\n" +
                    "• item names\n" +
                    "• any text printed on the receipt\n" +
                    "• category (for example Utilities)\n" +
                    "• payment method (for example UPI)\n" +
                    "• date, written as year-month-day: 2026-10-28 for one day, 2026-10 for a whole month, 2026 for a year.\n" +
                    "Results update as you type.",
                "5. സെർച്ച്",
                "മുകളിലെ സെർച്ച് ബാറിൽ ടൈപ്പ് ചെയ്യുക. ഇവ വെച്ച് രസീത് കണ്ടെത്താം:\n" +
                    "• കടയുടെ പേര് അല്ലെങ്കിൽ ഇൻവോയ്സ് നമ്പർ\n" +
                    "• സാധനങ്ങളുടെ പേര്\n" +
                    "• രസീതിൽ അച്ചടിച്ചിട്ടുള്ള ഏതു വാക്കും\n" +
                    "• Category (ഉദാ: Utilities)\n" +
                    "• പേയ്മെന്റ് രീതി (ഉദാ: UPI)\n" +
                    "• തീയതി, വർഷം-മാസം-ദിവസം രൂപത്തിൽ: ഒരു ദിവസത്തിന് 2026-10-28, ഒരു മാസത്തിന് 2026-10, " +
                    "ഒരു വർഷത്തിന് 2026.\n" +
                    "ടൈപ്പ് ചെയ്യുമ്പോൾ തന്നെ ഫലങ്ങൾ മാറും."
            ),
            Section(
                "6. Backup and restore",
                "Backup: tap the upload-arrow icon (the middle icon at the top), then Backup all receipts. " +
                    "Save the zip file to Google Drive, Telegram, WhatsApp or Files. It contains all your receipts and their photos.\n\n" +
                    "Restore: tap the same icon, choose Restore from backup, and pick the zip file. Restored receipts are " +
                    "added to the ones you already have; nothing is deleted. Older .json backups still work but have no photos.\n\n" +
                    "Tip: make a backup regularly and keep the zip somewhere safe. If you uninstall the app or lose the " +
                    "phone without a backup, the receipts are gone.",
                "6. ബാക്കപ്പും റീസ്റ്റോറും",
                "ബാക്കപ്പ്: മുകളിലെ അപ്‌ലോഡ് ആരോ ഐക്കൺ (നടുവിലെ ഐക്കൺ) തൊട്ട് Backup all receipts തിരഞ്ഞെടുക്കുക. " +
                    "ലഭിക്കുന്ന zip ഫയൽ Google Drive, Telegram, WhatsApp അല്ലെങ്കിൽ Files-ൽ സേവ് ചെയ്യുക. " +
                    "അതിൽ നിങ്ങളുടെ എല്ലാ രസീതുകളും ഫോട്ടോകളും ഉണ്ടാകും.\n\n" +
                    "റീസ്റ്റോർ: അതേ ഐക്കൺ തൊട്ട് Restore from backup തിരഞ്ഞെടുത്ത് zip ഫയൽ എടുക്കുക. " +
                    "റീസ്റ്റോർ ചെയ്യുന്ന രസീതുകൾ ഇപ്പോഴുള്ളവയോടൊപ്പം ചേർക്കപ്പെടും; ഒന്നും ഡിലീറ്റ് ആകില്ല. " +
                    "പഴയ .json ബാക്കപ്പുകളും പ്രവർത്തിക്കും, പക്ഷേ അവയിൽ ഫോട്ടോകൾ ഉണ്ടാകില്ല.\n\n" +
                    "നിർദേശം: ഇടയ്ക്കിടെ ബാക്കപ്പ് എടുത്ത് zip ഫയൽ സുരക്ഷിതമായി സൂക്ഷിക്കുക. " +
                    "ബാക്കപ്പ് ഇല്ലാതെ ആപ്പ് അൺഇൻസ്റ്റാൾ ചെയ്താലോ ഫോൺ നഷ്ടപ്പെട്ടാലോ രസീതുകൾ നഷ്ടപ്പെടും."
            ),
            Section(
                "7. Export and insights",
                "• Export All (CSV): the floppy-disk icon at the top makes a spreadsheet of every receipt that you can " +
                    "open in Excel or Google Sheets.\n" +
                    "• Spending insights: tap the upload-arrow icon, then View spending insights, to see your spending " +
                    "by category and by month.",
                "7. എക്സ്പോർട്ടും ചെലവ് വിവരങ്ങളും",
                "• Export All (CSV): മുകളിലെ ഫ്ലോപ്പി ഡിസ്ക് ഐക്കൺ എല്ലാ രസീതുകളുടെയും ഒരു സ്പ്രെഡ്ഷീറ്റ് ഉണ്ടാക്കും; " +
                    "അത് Excel-ലോ Google Sheets-ലോ തുറക്കാം.\n" +
                    "• ചെലവ് വിവരങ്ങൾ: അപ്‌ലോഡ് ആരോ ഐക്കൺ തൊട്ട് View spending insights തിരഞ്ഞെടുത്താൽ " +
                    "Category അനുസരിച്ചും മാസം അനുസരിച്ചും ചെലവ് കാണാം."
            ),
            Section(
                "8. Good to know",
                "• Receipts and photos are stored only on your phone. They are not uploaded anywhere unless you share a backup yourself.\n" +
                    "• When you scan, the receipt text and a small copy of the photo are sent to Google Gemini to read the details. " +
                    "That needs internet.\n" +
                    "• Everything else works offline: viewing, searching, editing and exporting.",
                "8. അറിഞ്ഞിരിക്കേണ്ടവ",
                "• രസീതുകളും ഫോട്ടോകളും നിങ്ങളുടെ ഫോണിൽ മാത്രമാണ് സൂക്ഷിക്കുന്നത്. നിങ്ങൾ ബാക്കപ്പ് സ്വയം ഷെയർ ചെയ്താലല്ലാതെ " +
                    "അവ എവിടെയും അപ്‌ലോഡ് ചെയ്യപ്പെടില്ല.\n" +
                    "• സ്കാൻ ചെയ്യുമ്പോൾ വിവരങ്ങൾ വായിക്കാനായി രസീതിലെ എഴുത്തും ഫോട്ടോയുടെ ചെറിയൊരു പകർപ്പും Google Gemini-ക്ക് " +
                    "അയയ്ക്കും. അതിന് ഇന്റർനെറ്റ് വേണം.\n" +
                    "• മറ്റെല്ലാം ഇന്റർനെറ്റ് ഇല്ലാതെ പ്രവർത്തിക്കും: കാണൽ, സെർച്ച്, തിരുത്തൽ, എക്സ്പോർട്ട്."
            )
        )
    }
}
