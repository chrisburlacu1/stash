package com.example.stash.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.stash.ui.theme.CategoryStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.stash.models.AiState
import com.example.stash.models.StashItem
import com.example.stash.models.relativeSavedLabel
import com.example.stash.ui.theme.categoryStyle

/**
 * The feed's card. Title-led: the headline is set large enough to be the card's design, with the
 * category, the source image and the saved time as supporting marks around it.
 *
 * The layout is built around what this app has that a bookmark list does not — discrete key points
 * per item. The card advertises how many there are; tapping opens [ItemDetailSheet], which renders
 * them as a numbered briefing. That is the reason to open a card rather than the article.
 *
 * The card used to *expand in place* to show them, and this file still carries the shape of that:
 * a fixed-height row is what is left after the second state was removed. See [ItemDetailSheet] for
 * why a sheet replaced it — briefly, an expanding row moves the thing the user just tapped and
 * pushes the rest of the feed down under their finger.
 *
 * The source image is a full-width header — the shape every feed the user already knows is built on,
 * which is what makes a list of these read as a feed rather than as a settings list.
 *
 * It was a 64dp thumbnail beside the title before that, because a first attempt at a 200dp hero
 * failed: the photo faded into the card surface, and a near-black OG image meeting a near-white card
 * is a luminance jump no gradient shape can hide (four attempts are recorded in DESIGN-NOTES).
 * This version does not reintroduce that seam, because it has no fade at all — the image ends at a
 * defined edge. The failure was the transition between image and card, not the image's size.
 *
 * A category-coloured glow used to cross that edge, which is what originally tied the photo to the
 * card. It is gone with the rest of the light layer (see DESIGN-NOTES, "The lighting layer is
 * parked"); the hard edge stands on its own, which is worth re-checking on device against a dark
 * OG image.
 *
 * Cards with no og:image get no header at all rather than a placeholder, and fall back to the
 * category glyph beside the title — a feed of empty grey rectangles is worse than a feed of
 * text cards.
 *
 * Images are read from local disk, never the network: NIA's equivalent card fetches its header as
 * each row scrolls into view, which would leak the user's reading activity to every host they saved
 * from. Here they are downloaded once at save time. See `RoomStashRepository.cacheHeaderImage`.
 *
 * A compact list variant and a list-detail pane both existed here once and were removed rather than
 * kept half-supported. Note that the detail *sheet* is not that pane returning: the pane was a
 * second permanent region of the layout, where the sheet is transient and leaves the feed's
 * geometry untouched.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashCardRow(
    item: StashItem,
    /**
     * Opens the item's detail sheet. Fired for every card on every tap — the card used to branch
     * here between expanding and opening the link, which made one gesture do two things depending
     * on data the user could not see.
     */
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleRead: (() -> Unit)? = null,
    onOpenLink: (() -> Unit)? = null,
    /** Deletes the item, after a swipe and a confirmation. Null disables the swipe gesture. */
    onDelete: (() -> Unit)? = null,
    /** Opens the on-device chat about this item, on a leading-edge swipe. Null disables it. */
    onChat: (() -> Unit)? = null,
    /**
     * Tags currently filtering the feed. The matching chip on each card is highlighted, so it is
     * obvious *why* a row is in a filtered list — otherwise a filtered feed is just a shorter feed
     * with no visible link back to the chip that shortened it.
     */
    activeTags: Set<String> = emptySet(),
    nowMillis: Long = remember(item.id) { System.currentTimeMillis() },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    // Derived from the resolved ColorScheme, not isSystemInDarkTheme(): the latter reads the
    // *device* setting, which disagrees with the app's own ThemeMode whenever the user has pinned
    // Light or Dark against the device's opposite setting (MainActivity resolves ThemeMode.System
    // correctly, but a call here would silently re-read the device instead of trusting that
    // resolution).
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val style = categoryStyle(item.category, darkTheme)
    val isSummarizing = item.aiState == AiState.Summarizing

    val cardInteractionSource = remember { MutableInteractionSource() }

    // Swipe left to delete, confirmed by a dialog.
    //
    // SwipeToDismissBox has exactly two outcomes: confirmValueChange returns true and the content
    // flies off-screen, or it returns false and the content springs back. There is no third value
    // that parks it half-open — that is what several attempts at a swipe-to-reveal-then-tap flow
    // kept running into, and why they either lost the panel on finger-up or threw the card away.
    //
    // So: return false, and let a dialog carry the confirmation the parked panel would have. The
    val haptic = LocalHapticFeedback.current
    var showDeleteConfirm by rememberSaveable(item.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.EndToStart && onDelete != null -> {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDeleteConfirm = true
                }
                // No confirmation dialog on this edge: opening a chat is free to back out of,
                // where a delete is not. The card springs back and the chat rises over it.
                value == SwipeToDismissBoxValue.StartToEnd && onChat != null -> {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onChat()
                }
            }
            false
        }
    )

    val cardModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "card-${item.id}", bounds = true,
    )

    // A Card rather than a hand-rolled Surface: a saved link is exactly the "single coherent piece
    // of content" Card exists for, and it carries the M3 card tokens plus CardColors, which
    // propagates content colour to children instead of each Text naming its own.
    //
    // Elevated, not outlined: shadow separates adjacent cards without drawing a hard line around
    // each one, which suits a feed whose cards are already distinguished by their category colour
    // and their own generous internal spacing.
    //
    // Tapping opens the detail sheet, every time.
    //
    // It used to expand the card in place, and fall through to opening the link for rows with
    // nothing extra to show — so a tap did one of two very different things depending on data the
    // user cannot see. One gesture, one outcome: a card with no key points still has a title,
    // tags, and its actions, which is a thin sheet rather than a wrong one.
    val handleClick = onClick

    // Surface passes `indication = ripple(...)` straight to its clickable and never reads
    // LocalIndication, so overriding that does nothing here. LocalRippleConfiguration is the
    // supported lever — it is documented as hierarchical per-ripple configuration "including
    // disabling ripples", and null disables.
    //
    // The card is a whole-surface tap target that immediately changes size, so a ripple washing
    // over it reads as a flash rather than as feedback; the expansion is the feedback, and the
    // press elevation still animates. Scoped to the card, so the delete button keeps its ripple.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        SwipeToDismissBox(
            state = dismissState,
            // One action per *edge*, per the M3 guidance: trailing swipe deletes, leading swipe
            // opens the item's chat. The two panels are visually distinct enough — error red
            // versus the item's own category tint — that mid-drag there is never doubt about
            // which action a release commits to.
            enableDismissFromStartToEnd = onChat != null,
            enableDismissFromEndToStart = onDelete != null,
            // Gestures stay enabled while revealed so the card can be swiped back to close it,
            // as well as tapped.
            modifier = modifier,
            backgroundContent = {
                // One panel per direction, chosen by where the drag is heading — the box keeps a
                // single background slot, so the slot decides.
                if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    ChatSwipePanel()
                } else {
                    DeleteSwipePanel()
                }
            },
        ) {
            OutlinedCard(
                onClick = handleClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(cardModifier),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                shape = MaterialTheme.shapes.large,
                interactionSource = cardInteractionSource,
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // When summarizing, edge blurred light effect bleeding inwards from the card's perimeter
                    if (isSummarizing) {
                        CardEdgeBlurEffect(
                            modifier = Modifier.matchParentSize(),
                            cornerRadius = 16.dp,
                            strokeWidth = 8.dp,
                            blurRadius = 8.dp,
                        )
                    }

                    Column {
                        // Drawn before the padded content so the image is genuinely full-bleed to the
                        // card's edges. Renders nothing when the page had no og:image.
                        CardHeaderImage(
                            path = item.imagePath,
                            style = style,
                            onOpenLink = onOpenLink,
                        )

                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 14.dp,
                            bottom = 12.dp
                        )
                    ) {
                        // The eyebrow leads the text block now that the image leads the card. It keeps its
                        // marks — category, when, where — because they are what places a card before it is
                        // read; the image says which *article*, not which kind of thing or how old.


                        Text(
                            text = item.title,
                            // headlineSmall, down from Medium: at Medium most real titles ran past
                            // three lines and ended in an ellipsis, which loses the end of the
                            // headline — the most information-dense part of a card. Still large
                            // enough to be the card's design, but now titles mostly fit.
                            style = MaterialTheme.typography.headlineSmall,
                            // Tighter than the default for this style. Display-size type set at
                            // body leading looks like a paragraph that happens to be large; pulling
                            // the lines together is what makes a multi-line title read as one
                            // typographic block.
                            lineHeight = 30.sp,
                            // Now that the title has the full card width rather than sharing a row with a
                            // 64dp thumbnail, three lines holds more than four did before.
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // The glyph tile stands in for the header on cards with no og:image, so a
                            // text-only row still opens with a category mark rather than starting cold.
                            if (item.imagePath.isNullOrBlank()) {
                                // Ink, not light: category colour appears only as the card's glow
                                // and the summarizing mesh (see DESIGN-NOTES, "M3 owns ink, Stash
                                // owns light"). The pill's container and text/icon are plain M3
                                // roles rather than style.color/style.container, which now exist
                                // only for the light layer to read.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(style.color.copy(alpha = 0.12f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = style.icon,
                                        contentDescription = null,
                                        tint = style.color,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = style.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = style.color,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                MetaDot()
                            }
                            // Time and source, separated by a dot. The category pill moves onto the image
                            // when there is one — see CardHeaderImage — so it is not stated twice.
                            Text(
                                text = relativeSavedLabel(item.savedAtEpochMillis, nowMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = style.color,
                                fontWeight = FontWeight.Medium,
                            )
                            MetaDot()
                            Text(
                                text = item.domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = style.color,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }

                        // One body, no crossfade. This was an AnimatedContent switching between
                        // the standfirst and the key points as the card expanded in place; the key
                        // points live in ItemDetailSheet now, so there is one state and nothing to
                        // animate between. The card is a fixed-height row again.
                        //
                        // The headline is written to fit one line; hidden while summarizing to avoid duplicate "Summarizing" text.
                        if (!isSummarizing && item.headline.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = item.headline,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 21.sp,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        CardMetaRow(
                            item = item,
                            isSummarizing = isSummarizing,
                            accent = style.color,
                            onToggleRead = onToggleRead,
                        )

                        // Tags close the card. They are the app's main way back to a saved item, so they
                        // earn the last line — where the domain used to sit as a stray footer. All of them,
                        // not just the leading one: showing one made the other two invisible, and FlowRow
                        // wraps rather than clipping.
                        if (item.tags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item.tags.forEach { tag ->
                                    TagChip(
                                        tag = tag,
                                        active = tag in activeTags,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

    // Deletion is irreversible and a swipe is easy to trigger while scrolling, so it is confirmed
    // rather than acted on directly.
    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from Stash?") },
            text = { Text("\"${item.title}\" will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * The card's full-width header image — the shape a feed is expected to have.
 *
 * Loads from a local file rather than a URL: images are downloaded once when the link is saved
 * (see `RoomStashRepository.cacheHeaderImage`), so scrolling the feed issues no network requests
 * and the feed renders offline. Nothing here can reach the network even if [path] were hostile.
 *
 * **Renders nothing when there is no cached image.** Not a placeholder tile, not a category-tinted
 * rectangle: an empty 168dp block on every text-only card is a bigger hole in the feed than a card
 * that simply starts at its title. Those cards keep the category pill in the eyebrow instead.
 *
 * ## Why this does not bring back the luminance band
 *
 * The previous 200dp hero was abandoned because a near-black OG image meeting a near-white card
 * produced a visible band, and four attempts at fading between them failed — the endpoints were the
 * problem, not the curve (DESIGN-NOTES, "The image fade always had a visible band"). So there is no
 * fade here. The image ends at a hard, deliberate edge, and three things carry it:
 *
 *  - **A bottom scrim inside the image**, dark at the foot and clear by mid-height. It is not a
 *    transition to the card colour — it is a shadow *in the photograph*, which is why it works on
 *    both a dark and a light image where a fade toward the card surface could only work on one.
 *  - **The category pill sits on that scrim**, so the bottom edge carries content. An edge with
 *    something on it reads as a deliberate boundary; a bare edge reads as a seam.
 *  - **The card's glow crosses the boundary**, because [categoryGlow] is hung above this composable
 *    rather than below it. Light spanning both regions ties them together.
 *
 * The scrim is drawn unconditionally rather than sampling the image's luminance: a scrim on an
 * already-dark photo is invisible, and one on a bright photo is the whole point.
 */
@Composable
private fun CardHeaderImage(
    path: String?,
    style: CategoryStyle,
    onOpenLink: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (path.isNullOrBlank()) return

    // Decoding is file I/O plus a bitmap allocation, so it happens off the composition thread and
    // is keyed to the path — recomposition from unrelated state must not re-decode.
    val bitmap by produceState<ImageBitmap?>(initialValue = path.let(com.example.stash.ui.util.ImageBitmapCache::get), key1 = path) {
        value = com.example.stash.ui.util.ImageBitmapCache.load(path, HEADER_IMAGE_TARGET_PX)
    }

    val scrimColor = MaterialTheme.colorScheme.scrim

    // Clipped to its own bounds — this is load-bearing, not tidiness.
    //
    // `ContentScale.Crop` scales the bitmap to *cover* the box and lets the overflow draw outside
    // the bounds; it does not clip by itself. The old 64dp thumbnail never showed this because it
    // carried a `clip(shapes.medium)` of its own, and a clip on an ancestor does not help: the
    // overflow escapes this Box before the parent's clip applies.
    //
    // Squared at the bottom because the text continues below, rounded at the top to the card's own
    // radius so the photo follows the card's silhouette rather than cutting across its corners.
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HEADER_IMAGE_HEIGHT)

            // Category tint under the image: it holds the slot while the bitmap decodes, so a card
            // scrolling into view never flashes an empty rectangle, and it fills the letterbox on
            // images narrower than the crop.

            .then(
                if (onOpenLink != null) {
                    Modifier.clickable(onClick = onOpenLink, onClickLabel = "Open link")
                } else Modifier
            ),
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null, // Decorative: the title carries the meaning.
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .height(HEADER_IMAGE_HEIGHT),
            )
        }

        // The scrim described above, confined to the bottom quarter.
        //
        // It started at half the image's height and much stronger, on the reasoning that a scrim
        // over an already-dark photo is invisible. That was wrong in the obvious direction: it does
        // not disappear, it compounds — a dark illustration went to near-black across its lower
        // half and lost its subject entirely. The scrim's only job is to put ground under the pill,
        // so it now starts low and stays weak, and the photograph keeps three quarters of its
        // height untouched.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.74f to Color.Transparent,
                            1.00f to scrimColor.copy(alpha = HEADER_SCRIM_ALPHA),
                        ),
                    ),
                ),
        )

        // The category pill moves onto the image, which is what gives the bottom edge its content.
        // Solid rather than the eyebrow's tinted container: over an unpredictable photo a
        // translucent chip is legible on some images and not others.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelSmall,
                color = style.color,
                fontWeight = FontWeight.Bold,
            )
        }

        // Marks the header as the way out to the source. Top-trailing rather than bottom, now that
        // the pill holds the bottom edge, and still on a scrim disc so it survives whatever the
        // image happens to be behind it.
        if (onOpenLink != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null, // The clickable above carries the label.
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}


/**
 * The expanded card's key points, rendered as a numbered briefing.
 *
 * This is the app's one genuinely distinctive surface. Most read-later apps have only a blob of
 * summary text to show; Stash extracts discrete points, so it can render something a bookmark list
 * cannot — a structured takeaway you read instead of the article. Numbering rather than bullets is
 * what makes that read as a briefing: it implies a finite, ordered set someone worked out, where a
 * bullet list reads as arbitrary fragments.
 *
 * A single point is prose from an older row rather than a real list, so the marker is dropped in
 * that case — one lone "1." reads as a formatting mistake.
 */
@Composable
internal fun KeyPoints(points: List<String>, accent: Color) {
    Column {
        points.forEachIndexed { index, point ->
            Row(modifier = Modifier.padding(bottom = 12.dp)) {
                if (points.size > 1) {
                    // Fixed-width, tabular index column so the point text starts on the same
                    // x-position on every line regardless of the numeral's width.
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(24.dp)
                            .padding(top = 3.dp),
                    )
                }
                Text(
                    text = point,
                    style = MaterialTheme.typography.bodyLarge,
                    // Roomier than the default: these are the thing being read, not a caption.
                    lineHeight = 24.sp,
                )
            }
        }
    }
}

/**
 * Separator between the marks in the card's meta line.
 *
 * A drawn dot rather than a "·" character: the glyph's size and vertical position vary by font, and
 * at label sizes it sits high enough to read as an apostrophe between two lowercase words.
 */
@Composable
internal fun MetaDot() {
    Box(
        modifier = Modifier
            .size(2.5.dp)
            .background(
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                CircleShape,
            ),
    )
}

/**
 * A single tag, drawn flat rather than as an interactive chip.
 *
 * Deliberately not an [androidx.compose.material3.AssistChip]: these are labels, and the card's own
 * tap toggles expansion, so anything that looks pressable here would invite a tap that does nothing
 * or — worse — expands the card when the user meant to filter by the tag.
 *
 * [active] marks the tag the feed is currently filtered by. It fills solid rather than merely
 * darkening, so the chip that put this card in the list is findable at a glance across a whole
 * screen of rows — the point is to connect the filter at the top to the reason each row is here.
 */
@Composable
internal fun TagChip(tag: String, active: Boolean = false) {
    // M3 roles, not the category hue: tags are the app's freeform, user-extracted labels, which
    // is a different axis from `category` — the pill above already carries that distinction as
    // ink, and per DESIGN-NOTES category colour appears only as light (glow/mesh), not as a second
    // ink palette here. Animated so chips resolve into and out of their filled state as the filter
    // changes, rather than the whole feed hard-cutting to a new colour scheme.
    val container by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.80f)
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tagChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSecondaryContainer
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tagChipContent",
    )
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Summarizing progress and read state — what is left of the row's metadata line. */
@Composable
private fun CardMetaRow(
    item: StashItem,
    isSummarizing: Boolean,
    accent: Color,
    onToggleRead: (() -> Unit)?,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // Nothing to show once a ready item is unread: the saved time and category moved to the
    // byline under the title, and the tags to their own row.
    if (!isSummarizing && !item.isRead) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (isSummarizing) {
            // This row deliberately had no progress indicator while the mesh gradient existed: the
            // mesh already said "working" with far more specificity, and a stock indeterminate
            // circle beside it would have read as the real signal and demoted the mesh to
            // decoration. The mesh is gone (see DESIGN-NOTES, "The lighting layer is parked"), so
            // that reasoning inverts — with no light layer, this indicator is the *only* thing
            // saying the model is running.
            //
            // Indeterminate rather than a percentage: ML Kit reports no progress for a generation
            // call, and inference time varies enormously by device and model variant, so any
            // determinate bar would be inventing a number.
            LinearProgressIndicator(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp),
            )
            Text(
                text = "Summarizing…",
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
            return@Row
        }

//        Spacer(Modifier.weight(1f))

//        if (item.isRead && onToggleRead != null) {
//            Row(
//                verticalAlignment = Alignment.CenterVertically,
//                horizontalArrangement = Arrangement.spacedBy(3.dp),
//                modifier = Modifier
//                    .clip(MaterialTheme.shapes.small)
//                    .clickable(onClick = onToggleRead)
//                    .padding(horizontal = 6.dp, vertical = 3.dp),
//            ) {
//                Icon(
//                    imageVector = Icons.Filled.CheckCircle,
//                    contentDescription = "Mark as unread",
//                    tint = accent,
//                    modifier = Modifier.size(14.dp),
//                )
//                Text(
//                    text = "Read",
//                    style = MaterialTheme.typography.labelSmall,
//                    color = accent,
//                    fontWeight = FontWeight.SemiBold,
//                )
//            }
//        }
    }
}


/**
 * The delete button revealed behind a card when it is swiped.
 *
 * [onDelete] is null until the swipe has actually settled open, so the panel does not swallow taps
 * along the trailing edge of a closed card.
 *
 * [revealedFraction] drives the icon's entrance. A static icon sitting in a panel that slides into
 * view is the flat option — the panel arrives and the icon is simply there. Springing it in gives
 * the action its own moment, which is the expressive part: the reveal is two things happening in
 * sequence rather than one rectangle moving.
 */
@Composable
private fun DeleteSwipePanel(modifier: Modifier = Modifier) {
    // Fills the whole row and takes the card's own shape, rather than being a fixed-width box
    // pinned to the trailing edge. As a narrow box its square left corners stuck out past the
    // card's rounded ones at rest — visible as hard corners peeking from under the card. Same
    // footprint, same silhouette: nothing to see until the card actually moves.
    //
    // Display only. The swipe is the action and a dialog confirms it, so there is nothing here to
    // tap — which is just as well, since the card springs back the moment a finger lifts.
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(end = 32.dp),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Icon(
            imageVector = Icons.Outlined.DeleteOutline,
            contentDescription = null, // The dialog that follows names the action.
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * The chat affordance revealed behind a card swiped from its leading edge.
 *
 * Same footprint-and-shape discipline as [DeleteSwipePanel], and the same display-only role: the
 * swipe itself is the action. It used to wear the card's own category tint; per the ink/light
 * split in DESIGN-NOTES ("M3 owns ink, Stash owns light") that became a plain `secondaryContainer`
 * — category colour is now expressed only as light (the card's glow/mesh), never as a second ink
 * palette here. The two swipe directions stay unmistakable mid-drag without it: `secondaryContainer`
 * one way, `errorContainer` the other, which is a real colour distinction on its own.
 */
@Composable
private fun ChatSwipePanel(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 32.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null, // The screen this opens names itself.
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** How far a confirmed delete throws the card, in px. Comfortably past any phone's width. */
private const val SWIPE_EXIT_DISTANCE_PX = 2000f

/**
 * Damping for the swipe settle. Well under 1, so the card overshoots its anchor and rebounds — the
 * bounce. `Spring.DampingRatioMediumBouncy` (0.5) is the reference point; this sits just above it,
 * bouncy enough to read without wobbling.
 */
private const val SWIPE_DAMPING = 0.55f

/** Stiffness for the swipe settle. Low enough that the rebound is visible rather than instant. */
private const val SWIPE_STIFFNESS = 380f


/** Shared-element handling for the card's sub-elements: null scopes opt out entirely. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun cardSharedModifier(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
    bounds: Boolean = false,
    shape: androidx.compose.ui.graphics.Shape? = null,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return Modifier
    val spatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<androidx.compose.ui.geometry.Rect>()
    return with(sharedTransitionScope) {
        val contentState = rememberSharedContentState(key = key)
        if (bounds) {
            Modifier.sharedBounds(
                sharedContentState = contentState,
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spatialSpec },
                clipInOverlayDuringTransition = if (shape != null) OverlayClip(shape) else OverlayClip(MaterialTheme.shapes.large),
            )
        } else {
            Modifier.sharedElement(
                sharedContentState = contentState,
                animatedVisibilityScope = animatedVisibilityScope,
                boundsTransform = { _, _ -> spatialSpec },
            )
        }
    }
}

/**
 * Decode target for header images, in pixels. Roughly 2x the header's height on a typical density,
 * so the bitmap stays sharp without holding a full-resolution photo in memory per visible row.
 *
 * Raised along with the slot: at the old 64dp thumbnail 400px was already generous, but a
 * full-width header shows sampling artefacts this hides.
 */
private const val HEADER_IMAGE_TARGET_PX = 600

/**
 * Height of the full-width header image.
 *
 * Shorter than the 200dp hero that was tried and reverted, and shorter than NIA's 180dp. OG images
 * are unpredictably cropped — logos and faces sit anywhere in the frame — so a shallower band is
 * more forgiving of a bad crop, and it keeps the title above the fold on a card in a scrolling
 * feed. Deep enough to read as a header rather than as a strip.
 */
private val HEADER_IMAGE_HEIGHT = 180.dp

/**
 * Peak opacity of the scrim at the very foot of the header image.
 *
 * Set by what the category pill needs to sit on, not by taste: the pill is a solid surface-coloured
 * chip, and below roughly this value a bright OG image leaves it looking like it is floating with
 * no ground under it. Was 0.55 over half the image's height, which turned dark illustrations to
 * near-black — see the comment at the gradient itself.
 */
private const val HEADER_SCRIM_ALPHA = 0.38f

