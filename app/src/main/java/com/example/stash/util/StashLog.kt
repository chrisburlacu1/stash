package com.example.stash.util

import android.util.Log
import com.chrisburlacu.stash.BuildConfig

/**
 * Debug-only logging helper guarded on [BuildConfig.DEBUG] so logs are stripped from release builds.
 */
object StashLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message)
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (throwable != null) {
                Log.w(tag, message, throwable)
            } else {
                Log.w(tag, message)
            }
        }
    }
}
