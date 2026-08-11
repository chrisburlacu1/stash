package com.example.stash.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stash.models.StashItem

/**
 * Chooser sheet offered after swiping a feed card toward the leading edge: ask about the item
 * on-device (private, grounded in the saved summary) or hand off to the Gemini app (stronger
 * model, live page, but the link leaves the device). Two affordances for the same intent because
 * they carry genuinely different privacy trade-offs — see the row copy below.
 *
 * Plain M3 surfaces only. The gradient/mesh language ([SummarizingMesh], [ChatAuraMesh]) means
 * "the on-device model is actively working" everywhere else in this app; nothing is working while
 * this chooser is open, so borrowing that vocabulary here would misuse a signal the rest of the
 * app relies on being trustworthy.
 *
 * The caller owns dismissal: both rows only invoke their callback, since the caller's handler is
 * what actually closes the sheet (for [onDismiss] as well as after a choice). Calling [onDismiss]
 * from here too would double-dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAboutItemSheet(
    item: StashItem,
    onAskOnDevice: () -> Unit,
    onAskGemini: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            ListItem(
                supportingContent = {
                    Text("Private and offline. Answers from the summary saved on this device.")
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Smartphone,
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAskOnDevice),
            ) { Text("Ask on device") }

            // Required disclosure, not boilerplate: this app's whole positioning is "no data
            // leaves the device", and this row is the one place in the app where that stops being
            // true. Keep this sentence explicit if the copy above it ever gets trimmed — do not
            // let "reads the live page" stand in for "sends the link to the Gemini app".
            ListItem(
                supportingContent = {
                    Text(
                        "A stronger model that reads the current page. Sends the link to the " +
                            "Gemini app.",
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = null,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAskGemini),
            ) { Text("Open in Gemini") }
        }
    }
}
