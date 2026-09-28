package dev.cburlacu.stash.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Opens a saved link in the default browser.
 */
fun openUrl(context: Context, url: String) {
    val uri = Uri.parse(if (url.startsWith("http")) url else "https://$url")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}
