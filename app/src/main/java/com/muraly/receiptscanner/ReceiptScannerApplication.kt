package com.muraly.receiptscanner

import android.app.Application
import com.muraly.receiptscanner.data.local.AppDatabase
import com.muraly.receiptscanner.data.pref.SecurePreferencesHelper
import com.muraly.receiptscanner.data.repository.ReceiptRepository
import com.muraly.receiptscanner.util.ImageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReceiptScannerApplication : Application() {
    val database by lazy { AppDatabase.getDatabase(this) }
    val repository by lazy { ReceiptRepository(database.receiptDao()) }
    val securePrefs by lazy { SecurePreferencesHelper(this) }

    override fun onCreate() {
        super.onCreate()
        migrateOldPhotosOnce()
    }

    /**
     * One-time: compress photos saved by earlier versions and move any that are still in the
     * cache into permanent storage. Runs in the background; marks itself done only if it
     * finishes, so an interrupted run simply tries again next launch.
     */
    private fun migrateOldPhotosOnce() {
        val prefs = getSharedPreferences("app_migrations", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PHOTOS_V1, false)) return
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val dao = database.receiptDao()
                val rows = dao.getAllImagePaths()
                val changed = ImageHelper.migrateExisting(
                    this@ReceiptScannerApplication,
                    rows.associate { it.id to it.imageUri }
                )
                changed.forEach { (id, path) -> dao.updateImageUri(id, path) }
                prefs.edit().putBoolean(KEY_PHOTOS_V1, true).apply()
            } catch (e: Exception) {
                // Try again next launch.
            }
        }
    }

    private companion object {
        const val KEY_PHOTOS_V1 = "photos_compressed_v1"
    }
}
