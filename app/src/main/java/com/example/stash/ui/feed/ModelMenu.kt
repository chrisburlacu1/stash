package com.example.stash.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuItemColors
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stash.ai.ModelOption
import com.example.stash.ai.ModelStatus
import com.example.stash.data.ModelChoice
import com.example.stash.data.SummaryEffort

/**
 * Summarization settings: how hard the model works, and which model does the work.
 *
 * Both live in one menu because they trade off the same thing — save latency against summary
 * quality — and picking one without seeing the other invites a confusing combination.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ModelMenu(
    expanded: Boolean,
    effort: SummaryEffort,
    modelChoice: ModelChoice,
    modelOptions: List<ModelOption>,
    isProbing: Boolean,
    onDismiss: () -> Unit,
    onSelectEffort: (SummaryEffort) -> Unit,
    onSelectModel: (ModelChoice) -> Unit,
    /** Re-runs the availability probe after a failure. See the empty-state row below. */
    onRetryProbe: () -> Unit = {},
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, shape = MaterialTheme.shapes.large) {
        MenuSectionLabel("Effort")
        SummaryEffort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = { onSelectEffort(option) },
                leadingIcon = { SelectedCheck(selected = option == effort) },

                trailingIcon = {
                    Text(
                        text = "${option.contentChars / 1_000}k chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        MenuSectionLabel("Model")

        if (isProbing && modelOptions.isEmpty()) {
            DropdownMenuItem(
                text = { Text("Checking availability…") },
                onClick = {},
                enabled = false,
                leadingIcon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                },
            )
        } else if (modelOptions.isEmpty()) {
            // Probing finished and produced nothing — every checkStatus() threw. That happens on a
            // fresh install while AICore is still settling, and it used to render as an empty gap
            // under the "Model" heading: the forEach below simply had nothing to iterate, so the
            // menu said neither "here are your models" nor "something went wrong".
            //
            // An explicit row instead, and a retry: the ViewModel's cache guard means a failed
            // probe would otherwise persist for the process lifetime, so reopening the menu could
            // never recover on its own.
            DropdownMenuItem(
                text = {
                    Column {
                        Text("Couldn't reach AICore")
                        Text(
                            text = "Tap to try again",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = onRetryProbe,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        } else {
            modelOptions.forEach { option ->
                // Unavailable variants stay listed but disabled. Hiding them would leave no
                // explanation for why a device offers only one model — the status line is the
                // answer, so it has to be visible.
                val selectable = option.status != ModelStatus.Unavailable
                DropdownMenuItem(

                    text = {
                        Column {
                            Text(
                                text = option.choice.label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = option.statusLine(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = { onSelectModel(option.choice) },
                    enabled = selectable,
                    leadingIcon = { SelectedCheck(selected = option.choice == modelChoice) },
                    trailingIcon = {
                        if (option.status == ModelStatus.Downloadable) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            }
        }
    }
}

/** The human-readable half of a [ModelStatus], plus the choice's own one-line pitch. */
private fun ModelOption.statusLine(): String = when (status) {
    ModelStatus.Ready -> choice.description
    ModelStatus.Downloadable -> "Not downloaded — tap to fetch"
    ModelStatus.Downloading -> "Downloading…"
    ModelStatus.Unavailable -> "Not available on this device"
}

@Composable
private fun MenuSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/**
 * A check for the active option, or an equally-sized blank so rows do not shift horizontally as
 * the selection moves between them.
 */
@Composable
private fun SelectedCheck(selected: Boolean) {
    Box(Modifier.size(24.dp)) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
