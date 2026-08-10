package com.example.stash.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
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
import kotlin.math.roundToInt

/**
 * The feed's card. Title-led: the headline is set large enough to be the card's design, with the
 * category, the source image and the saved time as supporting marks around it.
 *
 * The layout is built around what this app has that a bookmark list does not — discrete key points
 * per item. Collapsed, the card advertises how many there are; expanded, it renders them as a
 * numbered briefing. That is the reason to open a card rather than the article.
 *
 * The source image is a 64dp thumbnail, not a hero. It was a 200dp full-bleed header, but OG images
 * are inconsistently dark, low-resolution and unpredictably cropped, so at that size they set the
 * tone of a card whose real content is text. Small, it still identifies the source without doing
 * that. Cards with no image show the category glyph on its own tint instead.
 *
 * Images are read from local disk, never the network: NIA's equivalent card fetches its header as
 * each row scrolls into view, which would leak the user's reading activity to every host they saved
 * from. Here they are downloaded once at save time. See `RoomStashRepository.cacheHeaderImage`.
 *
 * A compact list variant existed alongside this as a user-selectable layout, but tapping a card now
 * expands it in place, which is what the compact row and its detail pane were for — both were
 * removed rather than kept in a half-supported state.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashCardRow(
    item: StashItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleRead: (() -> Unit)? = null,
    onOpenLink: (() -> Unit)? = null,
    /** Fired when the card expands to reveal its key points — the feed uses it to mark the item read. */
    onExpand: (() -> Unit)? = null,
    /** Deletes the item. Reached by swiping the card and tapping the revealed panel; null disables
     *  the swipe gesture entirely. */
    onDelete: (() -> Unit)? = null,
    nowMillis: Long = remember(item.id) { System.currentTimeMillis() },
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val style = categoryStyle(item.category, isSystemInDarkTheme())
    val isSummarizing = item.aiState == AiState.Summarizing

    // Expansion is view state, not app state: it belongs to this row and should not survive
    // scrolling out of the viewport, so it is remembered per item id rather than hoisted.
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }

    // Key points are stored newline-separated in the summary column so FTS indexes each bullet.
    // Older rows hold one prose paragraph, which falls through as a single "bullet" — both render.
    val keyPoints = remember(item.summary) {
        item.summary.split('\n').map(String::trim).filter(String::isNotEmpty)
    }
    // Nothing to reveal while the summary is still the placeholder, or when the headline already
    // says everything the summary would.
    val canExpand = !isSummarizing && keyPoints.isNotEmpty() &&
            !(keyPoints.size == 1 && keyPoints.first() == item.headline)

    val cardInteractionSource = remember { MutableInteractionSource() }

    // Swipe reveals a delete panel; tapping that panel deletes. The two-step gesture is the
    // confirmation, so there is no dialog — an accidental swipe costs a tap to dismiss rather than
    // interrupting with a modal. This is the flow the M3 swipe docs demonstrate.
    //
    // Built on anchoredDraggable, not SwipeToDismissBox. SwipeToDismissBox only knows how to send
    // content all the way off-screen: there is no anchor to stop it at, so the card yeets off the
    // side and the panel is left alone in the row. anchoredDraggable's two anchors say exactly
    // where the card may rest — 0 and -88dp — so it physically cannot travel further.
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { SWIPE_DELETE_PANEL_WIDTH.toPx() }
    val dragState = remember(item.id) {
        AnchoredDraggableState(
            initialValue = SwipeState.Closed,
            anchors = DraggableAnchors {
                SwipeState.Closed at 0f
                // Negative: the card moves left, uncovering the panel at the trailing edge.
                SwipeState.Revealed at -revealPx
            },
        )
    }
    val revealed = dragState.targetValue == SwipeState.Revealed
    val closeSwipe: () -> Unit = { scope.launch { dragState.animateTo(SwipeState.Closed) } }


    // Read here rather than inside transitionSpec: that lambda is not a composable scope, so
    // MaterialTheme cannot be touched from it.
    val bodyFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val bodyFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val bodyResize = MaterialTheme.motionScheme.defaultSpatialSpec<IntSize>()

    val titleModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "title-${item.id}", bounds = true,
    )
    val domainModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "domain-${item.id}",
    )
    val dotModifier = cardSharedModifier(
        sharedTransitionScope, animatedVisibilityScope, "category-dot-${item.id}",
    )

    // A Card rather than a hand-rolled Surface: a saved link is exactly the "single coherent piece
    // of content" Card exists for, and it carries the M3 card tokens plus CardColors, which
    // propagates content colour to children instead of each Text naming its own.
    //
    // Elevated, not outlined: shadow separates adjacent cards without drawing a hard line around
    // each one, which suits a feed whose cards are already distinguished by their category colour
    // and their own generous internal spacing.
    //
    // Tapping the card reveals the key points in place rather than navigating: the detail pane
    // held little the expanded card does not. Rows with nothing extra to show fall back to the
    // caller's onClick so they still do something on tap.
    val handleClick = {
        if (revealed) {
            // First tap after a swipe puts the card back rather than acting on it. Without this
            // the only way out of the revealed state is a swipe back, which is fiddly and not
            // discoverable.
            closeSwipe()
        } else if (canExpand) {
            expanded = !expanded
            // Reading the summary counts as reading the item, the same way opening the detail
            // pane did — otherwise nothing would ever mark itself read in this layout.
            if (expanded) onExpand?.invoke()
        } else {
            onClick()
        }
    }

    // Surface passes `indication = ripple(...)` straight to its clickable and never reads
    // LocalIndication, so overriding that does nothing here. LocalRippleConfiguration is the
    // supported lever — it is documented as hierarchical per-ripple configuration "including
    // disabling ripples", and null disables.
    //
    // The card is a whole-surface tap target that immediately changes size, so a ripple washing
    // over it reads as a flash rather than as feedback; the expansion is the feedback, and the
    // press elevation still animates. Scoped to the card, so the delete button keeps its ripple.
    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        // The panel sits underneath; the card slides across it. Height comes from the card, so the
        // Box wraps the content rather than imposing a size on it.
        Box(modifier = modifier) {
            DeleteSwipePanel(
                // Only tappable once revealed: a clickable panel under a closed card would swallow
                // taps along the row's trailing edge.
                onDelete = if (revealed && onDelete != null) {
                    { onDelete(); closeSwipe() }
                } else null,
                modifier = Modifier.matchParentSize(),
            )

        ElevatedCard(
            onClick = handleClick,
            // fillMaxWidth rather than the caller's modifier, which the Box above now carries: the
            // card must fill that box or the panel shows through beside it at rest.
            modifier = Modifier
                .fillMaxWidth()
                // offset via the lambda overload so the drag is read in the layout phase — a swipe
                // then never recomposes the card.
                .offset { IntOffset(dragState.offset.roundToInt(), 0) }
                .then(
                    if (onDelete != null) {
                        Modifier.anchoredDraggable(
                            state = dragState,
                            orientation = Orientation.Horizontal,
                        )
                    } else Modifier
                ),
            // Card feeds this to elevation.shadowElevation(), so the press elevation only animates
            // if the card owns the interaction — which a hand-rolled Modifier.clickable on the
            // plain overload cannot give it. That elevation change is now the only press feedback.
            interactionSource = cardInteractionSource,
            shape = MaterialTheme.shapes.large,
        ) {
            // Card's content lambda is already a ColumnScope — no wrapper Column needed.
            //
            // No category spine: a square-cornered bar down the leading edge got clipped into a
            // wedge by the card's rounded corners and read as a rendering fault, and its hard edge
            // fought the soft silhouette an elevated card is built on. The category colour spills
            // in as light from the top instead — see categoryGlow.
            Column(
                modifier = Modifier
                    .categoryGlow(style.color)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = 14.dp,
                        bottom = 12.dp
                    )
            ) {
                // Title and thumbnail share a row: the image is an identifier, not a hero. At
                // 200dp full-bleed it dominated a card whose actual content is text, and OG images
                // are too inconsistent in quality to carry that much weight. At 64dp it still says
                // "this is that article" without competing with the title.
                Row {
                    Column(modifier = Modifier.weight(1f)) {
                        // Eyebrow above the title, not a byline below it. Below, the pill competed
                        // with the title for the reader's first fixation and left the card as a
                        // stack of same-weight blocks. Above, it is a small opening mark that hands
                        // off to the title — the order the card is actually read in.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(style.container)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Icon(
                                    imageVector = style.icon,
                                    contentDescription = null, // The label beside it says this.
                                    tint = style.color,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = style.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = style.color,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            Text(
                                text = relativeSavedLabel(item.savedAtEpochMillis, nowMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = item.title,
                            // headlineMedium, up from Small: the title is the card's design, not a
                            // list label. Text stops reading as filler once it is big enough to be
                            // the thing you look at.
                            style = MaterialTheme.typography.headlineMedium,
                            // Tighter than the default for this style. Display-size type set at
                            // body leading looks like a paragraph that happens to be large; pulling
                            // the lines together is what makes a multi-line title read as one
                            // typographic block.
                            lineHeight = 34.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = titleModifier,
                        )
                    }

                    // Falls back to a category-coloured tile when the page had no og:image, so a
                    // card without one still balances rather than leaving a ragged gap.
                    Spacer(Modifier.width(12.dp))
                    HeaderThumbnail(
                        path = item.imagePath,
                        style = style,
                        modifier = Modifier.then(dotModifier),
                    )
                }

                // AnimatedContent alone: it crossfades the two bodies *and* animates its own size
                // between them, so an animateContentSize on top was a second size animation fighting
                // the first. A SharedTransitionLayout/sharedBounds pair was also redundant here —
                // both states are text blocks in the same place, so there are no bounds to travel.
                //
                // No caller modifier: passing one down would apply the feed's gutters and animateItem
                // to this inner block as well as to the card.
                AnimatedContent(
                    targetState = expanded && canExpand,
                    label = "card-body",
                    // Defaults to Center, which drifts the narrower collapsed headline toward the
                    // middle of the wider expanded bounds and reads as stray indentation.
                    contentAlignment = Alignment.TopStart,
                    modifier = Modifier.fillMaxWidth(),
                    // AnimatedContent's default transitionSpec is tween(220, delay 90) for the
                    // fade/scale plus a stock spring(StiffnessMediumLow) for the size — the legacy
                    // easing/duration system M3 is retiring. Every spec here comes from MotionScheme
                    // instead: effects springs for the fades (non-spatial), and a spatial spring for
                    // the size change, which is what gives the expansion its bounce.
                    transitionSpec = {
                        fadeIn(animationSpec = bodyFadeIn)
                            .togetherWith(fadeOut(animationSpec = bodyFadeOut))
                            .using(SizeTransform(clip = false) { _, _ -> bodyResize })
                    },
                ) { isExpanded ->
                    if (isExpanded) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            KeyPoints(points = keyPoints, accent = style.color)
                        }
                    } else {
                        Column {
                            // The headline is written to fit one line; fall back to nothing
                            // rather than showing a clipped paragraph while summarizing.
                            if (item.headline.isNotBlank()) {
                                Spacer(Modifier.height(14.dp))
                                // The summary sits in its own recessed panel rather than as another
                                // paragraph in the stack. Everything on this card was the same
                                // weight on the same left edge, which is what made it read as flat —
                                // a second surface gives the eye a place to land and separates
                                // "what the app worked out" from "what the page is called".
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(MaterialTheme.shapes.medium)
                                        // Opaque: the glow now falls off well above this panel, so
                                        // there is no light left here to let through — and at 60%
                                        // the panel lost the tonal step that separates it from the
                                        // card.
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                ) {
                                    Text(
                                        text = item.headline,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 21.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    // Advertises what expanding gets you. A card that just grows on
                                    // tap gives no reason to tap it; naming the count makes the
                                    // briefing the card's offer rather than a hidden feature.
                                    if (canExpand) {
                                        Spacer(Modifier.height(10.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "${keyPoints.size} key points",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = style.color,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.ExpandMore,
                                                contentDescription = null,
                                                tint = style.color,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // All tags, not just the leading one: they are the app's main way back to a saved
                // item, and showing one made the other two invisible. FlowRow wraps rather than
                // clipping or scrolling, so a card with several tags grows a line instead of hiding
                // them. Kept out of the AnimatedContent so they stay visible in both states.
                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        item.tags.forEach { tag ->
                            TagChip(tag = tag, accent = style.color)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
                CardMetaRow(
                    item = item,
                    isSummarizing = isSummarizing,
                    accent = style.color,
                    onToggleRead = onToggleRead,
                )

                // --- Link row ------------------------------------------------------------
                // A quiet line rather than the tonal strip this used to be. That strip carried a
                // 40dp category tile which the thumbnail above now does better, leaving it as a
                // heavy band holding one short domain string.
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (onOpenLink != null) Modifier.clickable(onClick = onOpenLink)
                            else Modifier
                        )
                        .padding(vertical = 4.dp),
                ) {
                    Text(
                        text = item.domain,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).then(domainModifier),
                    )
                    if (onOpenLink != null) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        }
    }
}

/**
 * The card's header image, matching the 180dp hero in NIA's `NewsResourceCardExpanded`.
 *
 * Loads from a local file rather than a URL: images are downloaded once when the link is saved
 * (see `RoomStashRepository.cacheHeaderImage`), so scrolling the feed issues no network requests
 * and the feed renders offline. Nothing here can reach the network even if [path] were hostile.
 *
 * Renders nothing when there is no cached image — most saves have one, but a page without og:image,
 * or whose download failed, degrades to the text-only card rather than showing a placeholder.
 *
 */
@Composable
private fun HeaderThumbnail(path: String?, style: CategoryStyle, modifier: Modifier = Modifier) {
    // Decoding is file I/O plus a bitmap allocation, so it happens off the composition thread and
    // is keyed to the path — recomposition from unrelated state must not re-decode.
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        if (path.isNullOrBlank()) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                val options = BitmapFactory.Options().apply {
                    inSampleSize = maxOf(1, bounds.outHeight / HEADER_IMAGE_TARGET_PX)
                }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }

    Box(
        modifier = modifier
            .size(HEADER_THUMBNAIL_SIZE)
            .clip(MaterialTheme.shapes.medium)
            // Category tint behind the image as well as instead of it: it shows while the bitmap
            // decodes, so the slot never flashes empty, and it fills the letterboxing on images
            // that do not match the square crop.
            .background(style.container),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null, // Decorative: the title carries the meaning.
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No og:image, or it failed to download. The category glyph on its own tint is a
            // deliberate identifier rather than a placeholder for something missing.
            Icon(
                imageVector = style.icon,
                contentDescription = null,
                tint = style.color,
                modifier = Modifier.size(26.dp),
            )
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
private fun KeyPoints(points: List<String>, accent: Color) {
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
 * A single tag, drawn flat rather than as an interactive chip.
 *
 * Deliberately not an [androidx.compose.material3.AssistChip]: these are labels, and the card's own
 * tap toggles expansion, so anything that looks pressable here would invite a tap that does nothing
 * or — worse — expands the card when the user meant to filter by the tag.
 */
@Composable
private fun TagChip(tag: String, accent: Color) {
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = accent,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = accent,
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
 * Category colour spilling into the card as light from a source just above its top edge.
 *
 * Modelled on a ceiling light rather than a gradient: a *small* source close to the surface, whose
 * pool is bright and tight where it lands and gone within the top third. The first version used a
 * radius nearly the card's full width, which spread the colour evenly across the whole card and
 * read as a tinted panel — a wash, not a light.
 *
 * Three things make it read as illumination:
 *  - **Small radius** relative to the card. A light has a source; a gradient does not.
 *  - **Centre just above the top edge**, so the visible part is the bright middle of the pool
 *    rather than a distant arc.
 *  - **A falloff curve, not a linear ramp.** Real light falls off sharply — most of the drop
 *    happens in the first part of the distance. The stops below approximate that: still at 0.55 of
 *    peak a fifth of the way out, but down to 0.08 by two thirds. A two-stop linear gradient
 *    spreads the fade evenly and is what makes a radial look like a coloured blob.
 *
 * Drawn behind the content via [drawBehind] and fading to transparent, so it settles into the
 * card's own surface — there is no second colour for it to meet.
 */
private fun Modifier.categoryGlow(color: Color): Modifier = this.drawBehind {
    val radius = size.width * GLOW_RADIUS_FACTOR
    // Saturated before use. The category palette is tuned for legible mid-tone *text* and icons;
    // spread thin as light those hues wash out to a grey haze. Pushing each channel away from the
    // midpoint restores the hue at the low alphas this draws at, so a Blog card reads pink and an
    // Article card blue rather than both reading "slightly warm grey".
    val lit = color.saturated(GLOW_SATURATION)
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to lit.copy(alpha = GLOW_ALPHA),
                0.20f to lit.copy(alpha = GLOW_ALPHA * 0.55f),
                0.40f to lit.copy(alpha = GLOW_ALPHA * 0.24f),
                0.65f to lit.copy(alpha = GLOW_ALPHA * 0.08f),
                0.85f to lit.copy(alpha = GLOW_ALPHA * 0.02f),
                1.00f to Color.Transparent,
            ),
            // Just above the top edge and horizontally centred: a fixture hanging over the card.
            center = Offset(size.width / 2f, -radius * GLOW_CENTER_LIFT),
            radius = radius,
        ),
    )
}

/**
 * Pushes a colour's channels away from mid-grey, raising saturation without shifting hue.
 *
 * Deliberately not a full HSL conversion: this runs per draw, and scaling the distance from the
 * channel mean is close enough at the alphas the glow uses while costing three multiplies.
 */
private fun Color.saturated(amount: Float): Color {
    val mean = (red + green + blue) / 3f
    return Color(
        red = (mean + (red - mean) * amount).coerceIn(0f, 1f),
        green = (mean + (green - mean) * amount).coerceIn(0f, 1f),
        blue = (mean + (blue - mean) * amount).coerceIn(0f, 1f),
    )
}

/**
 * Radial size of the glow, as a multiple of card width. Well under 1 so the pool is a source of
 * light rather than a tint across the whole surface.
 */
private const val GLOW_RADIUS_FACTOR = 0.55f

/**
 * How far above the card's top edge the light sits, as a fraction of its radius. Close, so the
 * bright centre of the pool lands just inside the card.
 */
private const val GLOW_CENTER_LIFT = 0.12f

/** Peak opacity at the centre of the pool. Higher than the wash version — it covers far less. */
private const val GLOW_ALPHA = 0.34f

/** How far the category hue is pushed from grey before being used as light. 1f leaves it as-is. */
private const val GLOW_SATURATION = 1.7f

/**
 * The delete button revealed behind a card when it is swiped.
 *
 * [onDelete] is null until the swipe has actually settled open, so the panel does not swallow taps
 * along the trailing edge of a closed card.
 */
@Composable
private fun DeleteSwipePanel(onDelete: (() -> Unit)?, modifier: Modifier = Modifier) {
    // Rounded on the trailing side only, square on the leading side. A fully rounded panel reads as
    // a separate object floating beside the card rather than as something uncovered from behind it;
    // matching the card's radius on the outer edge means the two share one silhouette.
    val panelShape = MaterialTheme.shapes.large.copy(
        topStart = CornerSize(0.dp),
        bottomStart = CornerSize(0.dp),
    )
    // The card disables ripples via LocalRippleConfiguration, which would otherwise leave this
    // button with no press feedback. Restored here only: this one really is a button.
    CompositionLocalProvider(LocalRippleConfiguration provides RippleConfiguration()) {
        Box(modifier = modifier, contentAlignment = Alignment.CenterEnd) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(SWIPE_DELETE_PANEL_WIDTH)
                    .clip(panelShape)
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .then(
                        if (onDelete != null) {
                            Modifier.clickable(onClick = onDelete, onClickLabel = "Delete")
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}

/** Resting positions for a card's delete swipe. */
private enum class SwipeState { Closed, Revealed }

/** Width of the revealed delete panel — sized to the action, not to the card. */
private val SWIPE_DELETE_PANEL_WIDTH = 88.dp

/** Shared-element handling for the card's sub-elements: null scopes opt out entirely. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun cardSharedModifier(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    key: String,
    bounds: Boolean = false,
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return Modifier
    return with(sharedTransitionScope) {
        val contentState = rememberSharedContentState(key = key)
        if (bounds) {
            Modifier.sharedBounds(contentState, animatedVisibilityScope = animatedVisibilityScope)
        } else {
            Modifier.sharedElement(contentState, animatedVisibilityScope = animatedVisibilityScope)
        }
    }
}

/**
 * Decode target for header images, in pixels. Roughly 2x the 180dp slot on a typical density, so
 * the bitmap stays sharp without holding a full-resolution hero in memory per visible row.
 */
private const val HEADER_IMAGE_TARGET_PX = 400

/**
 * Size of the square thumbnail beside the title.
 *
 * Deliberately small. As a 200dp full-bleed hero this image dominated a card whose real content is
 * text, and OG images are too inconsistent — often dark, often low quality, never a predictable
 * crop — to carry that weight. At 64dp it still identifies the source at a glance without setting
 * the card's tone.
 */
private val HEADER_THUMBNAIL_SIZE = 64.dp

