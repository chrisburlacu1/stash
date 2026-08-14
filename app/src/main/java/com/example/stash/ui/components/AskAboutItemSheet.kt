package com.example.stash.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.stash.models.StashItem
import com.example.stash.ui.theme.categoryStyle

/**
 * Chooser offered after swiping a feed card toward the leading edge: ask the on-device model, or
 * hand the link to the Gemini app.
 *
 * ## The copy is deliberately thin
 *
 * "Open in Gemini" carries no explanation. Gemini is a product people already know, and describing
 * it as "a stronger model that reads the current page" tells a user something they can infer while
 * making the sheet read like documentation.
 *
 * The on-device row keeps three words. Not as a description of Nano — as the *contrast* that makes
 * the choice a choice: without "Private, works offline" beside it, the two rows look like two ways
 * to do the same thing rather than a trade. It is also the only place in the app that says what the
 * on-device path protects. Trim it and the sheet stops being a decision.
 *
 * ## The light
 *
 * [sheetMesh] puts a three-point mesh gradient under the bottom edge, so the sheet is lit from the
 * direction it arrived from — the mirror of the card's lamp above its top edge. It drifts rather
 * than sitting still: this is a held moment between two destinations, and three sources bleeding
 * through each other never resolve into a settled shape.
 *
 * Note this is *not* the same statement [summarizingMesh] makes. That one's several hues are
 * several categories, meaning "the model has not decided what this link is". These three are one
 * category rendered with internal variety — same technique, different sentence.
 *
 * The caller owns dismissal: the rows only invoke their callback, and the caller's handler closes
 * the sheet. Calling [onDismiss] from here too would double-dismiss.
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
    // Derived from the resolved ColorScheme, not isSystemInDarkTheme() — see the identical note on
    // StashCardRow.kt. This flag is now load-bearing only for sheetMesh's colour (the light
    // layer); the icon tint above reads a plain M3 role instead.
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = categoryStyle(item.category, darkTheme)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // contentWindowInsets is zeroed so the sheet does not reserve a navigation-bar strip below
        // the content; the inset is re-applied as padding on the content below instead. This was
        // originally load-bearing for a mesh that ran under the navigation bar — `sheetMesh` is
        // gone with the rest of the light layer (see DESIGN-NOTES, "The lighting layer is parked")
        // — but it is kept because the rows' own spacing is now tuned against it.
        contentWindowInsets = { WindowInsets(0) },
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        ) {
            // Identifies which card was swiped. The title alone would leave a bare row of text at
            // the top of a lit sheet; the domain under it gives the block a shape.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp, vertical = 12.dp),
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
                supportingContent = { Text("Private, works offline") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Smartphone,
                        contentDescription = null,
                        // Plain M3 ink, not the item's category colour — per the ink/light split
                        // in DESIGN-NOTES ("M3 owns ink, Stash owns light"), category colour is
                        // expressed only as this sheet's light (sheetMesh below), never as ink.
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAskOnDevice),
            ) { Text("Ask on device") }

            ListItem(
                leadingContent = {
                    // Untinted, at the same size as the icon above it. The brand mark is the whole
                    // explanation this row gets — see the copy note in the KDoc.
                    Icon(
                        imageVector = GeminiMark,
                        contentDescription = null,
                        tint = Color.Unspecified,
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
