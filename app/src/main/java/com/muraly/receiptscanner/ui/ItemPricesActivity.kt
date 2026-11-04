package com.muraly.receiptscanner.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.color.MaterialColors
import com.muraly.receiptscanner.ReceiptScannerApplication
import com.muraly.receiptscanner.data.local.dao.ItemPriceRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * "What did I pay for this?" lookup. Type an item name and see, for every matching item, the latest
 * purchase price, the lowest price ever paid, and the full purchase history. An optional margin %
 * shows cost + margin (a quick minimum selling rate). Prices are exactly as printed on the saved bills.
 */
class ItemPricesActivity : AppCompatActivity() {

    private val app by lazy { application as ReceiptScannerApplication }
    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val query = MutableStateFlow("")
    private var rows: List<ItemPriceRow> = emptyList()

    private lateinit var etSearch: EditText
    private lateinit var etMargin: EditText
    private lateinit var results: LinearLayout
    private var accent = 0

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(
            WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        )

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
        accent = MaterialColors.getColor(root, com.google.android.material.R.attr.colorPrimary)

        root.addView(TextView(this).apply {
            text = "Item prices"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "Find what you paid for an item."
            alpha = 0.7f
            setPadding(0, dp(2), 0, dp(8))
        })

        etSearch = EditText(this).apply {
            hint = "Type an item, e.g. wire"
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            addTextChangedListener(simpleWatcher { query.value = text.toString() })
        }
        root.addView(etSearch)

        // Margin row
        val marginRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, 0)
        }
        marginRow.addView(TextView(this).apply { text = "Add margin %" })
        etMargin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            isSingleLine = true
            gravity = Gravity.CENTER
            hint = "0"
            setText(loadMargin())
            addTextChangedListener(simpleWatcher {
                saveMargin(text.toString())
                render()
            })
        }
        marginRow.addView(
            etMargin,
            LinearLayout.LayoutParams(dp(90), ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(12) }
        )
        root.addView(marginRow)

        root.addView(TextView(this).apply {
            text = "Prices are as printed on your saved bills. Tap a purchase to open its bill and check whether GST is included."
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            alpha = 0.65f
            setPadding(0, dp(6), 0, dp(4))
        })

        results = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(24))
        }
        root.addView(
            ScrollView(this).apply { addView(results) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
        etSearch.requestFocus()
        render()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                query.debounce(150).flatMapLatest { q ->
                    if (q.isBlank()) flowOf(emptyList<ItemPriceRow>())
                    else app.repository.searchItemPrices(q.trim())
                }.collect { list ->
                    rows = list
                    render()
                }
            }
        }
    }

    // ---------------------------------------------------------------- rendering

    private fun render() {
        if (!::results.isInitialized) return
        results.removeAllViews()
        val q = query.value.trim()
        if (q.isEmpty()) {
            results.addView(note("Type an item name above to see what you paid for it."))
            return
        }
        if (rows.isEmpty()) {
            results.addView(note("No saved purchases match \"$q\". Try a shorter word."))
            return
        }
        val margin = etMargin.text.toString().toDoubleOrNull() ?: 0.0
        // rows are newest first, so each group's first entry is its latest purchase
        val groups = rows.groupBy { it.itemName.trim().lowercase(Locale.getDefault()) }
        for ((_, group) in groups.entries.take(30)) addGroup(group, margin)
    }

    private fun addGroup(group: List<ItemPriceRow>, margin: Double) {
        val latest = group.first()
        val lowest = group.minByOrNull { unitOf(it) } ?: latest

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(ColorUtils.setAlphaComponent(accent, 24))
                setStroke(dp(1), ColorUtils.setAlphaComponent(accent, 90))
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }

        card.addView(TextView(this).apply {
            text = latest.itemName.trim()
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTypeface(typeface, Typeface.BOLD)
        })
        card.addView(TextView(this).apply {
            text = "Latest price paid"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            alpha = 0.7f
            setPadding(0, dp(6), 0, 0)
        })
        card.addView(TextView(this).apply {
            text = "${money.format(unitOf(latest))} per unit"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(accent)
        })
        card.addView(TextView(this).apply {
            text = "${prettyDate(latest.date)}  •  ${shopOf(latest)}"
            alpha = 0.75f
        })

        if (margin > 0.0) {
            val withMargin = unitOf(latest) * (1 + margin / 100.0)
            card.addView(TextView(this).apply {
                text = "With ${trimZeros(margin)}% margin: ${money.format(withMargin)}"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(8), 0, 0)
            })
        }

        if (group.size > 1) {
            card.addView(TextView(this).apply {
                text = "Lowest paid: ${money.format(unitOf(lowest))}  (${prettyDate(lowest.date)}, ${shopOf(lowest)})"
                setPadding(0, dp(8), 0, 0)
            })
        }

        card.addView(TextView(this).apply {
            text = if (group.size == 1) "1 purchase (tap to open the bill)" else "All ${group.size} purchases (tap to open the bill)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(typeface, Typeface.BOLD)
            alpha = 0.7f
            setPadding(0, dp(12), 0, dp(4))
        })

        val shown = group.take(MAX_ROWS_PER_ITEM)
        shown.forEachIndexed { index, r ->
            card.addView(purchaseRow(r, isLatest = index == 0, isLowest = r === lowest && group.size > 1))
        }
        if (group.size > shown.size) {
            card.addView(TextView(this).apply {
                text = "+ ${group.size - shown.size} older purchases not shown"
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                alpha = 0.65f
                setPadding(0, dp(6), 0, 0)
            })
        }

        results.addView(card)
    }

    private fun purchaseRow(r: ItemPriceRow, isLatest: Boolean, isLowest: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
            // Tap a purchase to open its bill (photo, subtotal, GST, total) and check whether GST is included.
            isClickable = true
            setOnClickListener {
                startActivity(
                    Intent(this@ItemPricesActivity, DetailActivity::class.java)
                        .putExtra(DetailActivity.EXTRA_RECEIPT_ID, r.receiptId)
                )
            }
        }
        val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        left.addView(TextView(this).apply { text = "${prettyDate(r.date)}  •  ${shopOf(r)}" })
        left.addView(TextView(this).apply {
            text = "Qty ${r.quantity}  •  Item total ${money.format(r.totalPrice)}"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            alpha = 0.7f
        })
        row.addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
        }
        right.addView(TextView(this).apply {
            text = money.format(unitOf(r))
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
        })
        if (isLatest) right.addView(chip("LATEST", accent))
        if (isLowest) right.addView(chip("LOWEST", 0xFF2E7D32.toInt()))
        row.addView(right, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return row
    }

    private fun chip(label: String, color: Int) = TextView(this).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFFFFFFFF.toInt())
        setPadding(dp(8), dp(1), dp(8), dp(1))
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2); gravity = Gravity.END }
    }

    private fun note(text: String) = TextView(this).apply {
        this.text = text
        alpha = 0.75f
        setPadding(0, dp(12), 0, 0)
    }

    // ---------------------------------------------------------------- helpers

    /** Price for one unit: the printed unit price, or total / quantity when the unit price is missing. */
    private fun unitOf(r: ItemPriceRow): Double = when {
        r.unitPrice > 0.0 -> r.unitPrice
        r.quantity > 0 -> r.totalPrice / r.quantity
        else -> r.totalPrice
    }

    private fun shopOf(r: ItemPriceRow): String = r.shopName.trim().ifBlank { "Unknown shop" }

    private fun prettyDate(raw: String): String {
        if (raw.isBlank()) return "No date"
        return try {
            val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.trim())
            if (parsed == null) raw else SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(parsed)
        } catch (e: Exception) {
            raw
        }
    }

    private fun trimZeros(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

    private fun loadMargin(): String = try {
        getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_MARGIN, "") ?: ""
    } catch (e: Exception) {
        ""
    }

    private fun saveMargin(value: String) {
        try {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_MARGIN, value).apply()
        } catch (e: Exception) {
            // Remembering the margin is a convenience only.
        }
    }

    private fun simpleWatcher(onChanged: () -> Unit) = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onChanged() }
        override fun afterTextChanged(s: Editable?) {}
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
        ).toInt()

    private companion object {
        const val PREFS = "app_prefs"
        const val KEY_MARGIN = "item_prices_margin"
        const val MAX_ROWS_PER_ITEM = 8
    }
}
