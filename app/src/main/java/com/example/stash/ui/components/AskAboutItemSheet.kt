package com.example.stash.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
 * [sheetGlow] puts the item's category hue under the bottom edge, so the sheet is lit from the
 * direction it arrived from — the mirror of the card's lamp above its top edge. Static, not
 * churning: the mesh language ([summarizingMesh], [chatAura]) means the model is *working*, and a
 * chooser is waiting on the user, not thinking. See SheetGlow.kt for why that line matters.
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
    val style = categoryStyle(item.category, isSystemInDarkTheme())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // The sheet keeps its own opaque surface — the glow is light *on* a surface, not a
        // substitute for one. Painting it transparent and letting the wash stand in leaves the
        // feed showing through wherever the light is thin, which is most of the sheet.
        //
        // contentWindowInsets is zeroed so the sheet does not reserve a navigation-bar strip below
        // the content. By default it does, and that strip sits outside anything drawn in here: the
        // glow stopped a few dp short of the screen edge and left a pale unlit band along exactly
        // the edge the light is meant to be entering from. The inset is re-applied as padding on
        // the content below, so the rows still clear the navigation bar — the light now runs under
        // it rather than stopping at it.
        contentWindowInsets = { WindowInsets(0) },
        modifier = modifier,
    ) {
        // Order matters: sheetGlow *before* the padding. Modifiers apply outside-in, so the glow
        // draws across the full bounds and the padding then insets only the content. Reversed, the
        // glow inherits the already-shrunk rect and the bottom strip goes unlit.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sheetGlow(style.color)
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
                        // The on-device option is the one that belongs to this app, so it wears the
                        // item's category colour. The Gemini mark below deliberately does not.
                        tint = style.color,
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
