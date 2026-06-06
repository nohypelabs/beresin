package com.aicleaner

import android.app.Application
import android.util.Log

class App : Application() {

    companion object {
        private const val TAG = "AICleaner"

        init {
            Log.i(TAG, "AI Storage Cleaner initializing...")
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "App started. Files dir: ${filesDir.absolutePath}")
    }
}
