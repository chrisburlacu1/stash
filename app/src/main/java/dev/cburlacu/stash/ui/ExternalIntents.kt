package dev.cburlacu.stash.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.cburlacu.stash.models.StashItem

private const val GEMINI_PACKAGE = "com.google.android.apps.bard"
private const val GEMINI_ENTRY_ACTIVITY = "com.google.android.apps.bard.shellapp.BardEntryPointActivity"

fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(if (url.startsWith("http")) url else "https://$url")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/**
 * Hands a saved link to the Google Gemini app with a pre-filled prompt, falling back
 * to a standard share chooser if the Gemini app is not installed.
 */
fun openInGemini(context: Context, item: StashItem) {
    val url = if (item.url.startsWith("http")) item.url else "https://${item.url}"
    val prompt = "Help me understand this article: $url"

    val explicit = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, prompt)
        setClassName(GEMINI_PACKAGE, GEMINI_ENTRY_ACTIVITY)
    }

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
