package com.example.stash.util

import android.util.Log
import com.chrisburlacu.stash.BuildConfig

/**
 * Debug-only logging. `Log.d`/`Log.w` calls sprinkled through the summarization pipeline are
 * diagnostically valuable in debug builds (see CLAUDE.md — this is how the "286 chars of
 * navigation menu" extraction bug was found) but must not ship in release: with R8 minification
 * on, unguarded `android.util.Log` calls are NOT stripped by default, and one of them
 * (`RoomStashRepository`'s extraction log) writes the full URL of every saved link.
 *
 * Guarding on `BuildConfig.DEBUG` rather than `Log.isLoggable` keeps this deterministic across
 * devices and matches how the release build type is distinguished elsewhere in the project.
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
