package dev.cburlacu.stash.util

import android.util.Log
import dev.cburlacu.stash.BuildConfig

/**
 * Debug-only logging helper guarded on [BuildConfig.DEBUG] so logs are stripped from release builds.
 */
object StashLog {
    fun d(tag: String, message: String) {
        if (BuildConfig.DEBUG) {
            runCatching { Log.d(tag, message) }
        }
    }

    fun w(tag: String, message: String, throwable: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            runCatching {
                if (throwable != null) {
                    Log.w(tag, message, throwable)
                } else {
                    Log.w(tag, message)
                }
            }
        }
    }
}
