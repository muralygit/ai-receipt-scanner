package com.muraly.receiptscanner

import android.app.Application
import com.muraly.receiptscanner.data.local.AppDatabase
import com.muraly.receiptscanner.data.pref.SecurePreferencesHelper
import com.muraly.receiptscanner.data.repository.ReceiptRepository

class ReceiptScannerApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ReceiptRepository(database.receiptDao()) }
    val securePrefs by lazy { SecurePreferencesHelper(this) }
}
