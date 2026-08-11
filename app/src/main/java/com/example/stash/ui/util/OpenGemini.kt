package com.example.stash.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.example.stash.models.StashItem

// Undocumented internal Gemini implementation details, verified working against a specific
// build on a Pixel 10 Pro XL (Android API 37) in Aug 2026. There is no public API for handing
// off to the Gemini app, so this targets its entry activity directly. The chooser fallback below
// is what makes a future Gemini update survivable rather than breaking this feature outright.
private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
private const val GEMINI_ENTRY_ACTIVITY = "com.google.android.apps.bard.shellapp.BardEntryPointActivity"

/**
 * Hands a saved link to the Google Gemini app with a pre-filled prompt, so the user can continue
 * the conversation there with the strong cloud model instead of this app's on-device Gemini Nano.
 *
 * The URL leaving the device here is a deliberate, user-initiated exception to this app's
 * privacy-first, on-device-only positioning — not a background or automatic transmission.
 */
fun openInGemini(context: Context, item: StashItem) {
    val url = if (item.url.startsWith("http")) item.url else "https://${item.url}"
    // Deliberately just the URL, not the stored summary/headline/key points: the whole reason to
    // hand off to cloud Gemini is that it fetches the live page itself, and seeding it with this
    // app's weaker stored extract would anchor it to exactly what the user chose to bypass.
    val prompt = "Help me understand this article: $url"

    val explicit = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, prompt)
        setClassName(GEMINI_PACKAGE, GEMINI_ENTRY_ACTIVITY)
    }

    // Belt and braces: resolveActivity can succeed and the start can still throw, so both guards
    // are needed. Gemini forwards into the Google app (com.google.android.googlequicksearchbox),
    // so the launching package is not what ends up foregrounded — success can't be verified by
    // checking the foreground package, so this doesn't attempt to.
    val handled = if (context.packageManager.resolveActivity(explicit, 0) != null) {
        try {
            context.startActivity(explicit)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    } else {
        false
    }

    if (!handled) {
        val fallback = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        runCatching {
            context.startActivity(Intent.createChooser(fallback, "Ask about this link"))
        }
    }
}
