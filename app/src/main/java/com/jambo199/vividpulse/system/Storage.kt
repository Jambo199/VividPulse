package com.jambo199.vividpulse.system

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager

data class StorageSample(val total: Long, val free: Long) {
    val used: Long get() = (total - free).coerceAtLeast(0)
    val usedFraction: Float get() = if (total > 0) used.toFloat() / total else 0f

    companion object {
        val EMPTY = StorageSample(0, 0)
    }
}

class StorageReader(private val context: Context) {
    fun sample(): StorageSample {
        try {
            val stats = context.getSystemService(StorageStatsManager::class.java)
            val total = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
            val free = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
            if (total > 0) return StorageSample(total, free)
        } catch (ignored: Exception) {
            // Fall back to the data partition's own numbers.
        }
        return try {
            val fs = StatFs(Environment.getDataDirectory().path)
            StorageSample(fs.totalBytes, fs.availableBytes)
        } catch (ignored: Exception) {
            StorageSample.EMPTY
        }
    }
}
