package com.example

import android.app.Application
import com.example.data.local.DailySpendDatabase
import com.example.data.repository.DailySpendRepository
import com.example.notification.NotificationHelper

class DailySpendApplication : Application() {
    val database: DailySpendDatabase by lazy { DailySpendDatabase.getInstance(this) }
    val repository: DailySpendRepository by lazy { DailySpendRepository(database.dao(), this) }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }
}
