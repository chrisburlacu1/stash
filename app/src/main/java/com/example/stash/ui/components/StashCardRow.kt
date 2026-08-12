package com.example.stash.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.util.lerp
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
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
import com.example.stash.ui.theme.categoryHueIndex
import com.example.stash.ui.theme.categoryHues
import com.example.stash.ui.theme.categoryStyle

/**
 * The feed's card. Title-led: the headline is set large enough to be the card's design, with the
 * category, the source image and the saved time as supporting marks around it.
 *
 * The layout is built around what this app has that a bookmark list does not — discrete key points
 * per item. Collapsed, the card advertises how many there are; expanded, it renders them as a
 * numbered briefing. That is the reason to open a card rather than the article.
 *
 * The source image is a full-width header — the shape every feed the user already knows is built on,
 * which is what makes a list of these read as a feed rather than as a settings list.
 *
 * It was a 64dp thumbnail beside the title before that, because a first attempt at a 200dp hero
 * failed: the photo faded into the card surface, and a near-black OG image meeting a near-white card
 * is a luminance jump no gradient shape can hide (four attempts are recorded in DESIGN-NOTES).
 * This version does not reintroduce that seam, because it has no fade at all — the image ends at a
 * defined edge, and the card's light is drawn *over* the photo rather than starting below it. The
 * failure was the transition between image and card, not the image's size.
 *
 * Cards with no og:image get no header at all rather than a placeholder, and fall back to the
 * category glyph beside the title — a feed of empty grey rectangles is worse than a feed of
 * text cards.
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
    val darkTheme = isSystemInDarkTheme()
    val style = categoryStyle(item.category, darkTheme)
    val isSummarizing = item.aiState == AiState.Summarizing

    // While Gemini Nano is deciding what this link is, the card shows every category colour at
    // once and collapses to the right one when the answer lands. See SummarizingMesh.
    //
    // The two lit states are mutually exclusive by construction: `meshResolve` runs 0→1 as the
    // model answers, the mesh's own alpha is (1 - meshResolve) and the resting glow's is
    // meshResolve. They cross rather than overlap — drawing both at full strength would double
    // the light on the header, and fading one out before the other in would leave a dark gap in
    // the middle of the handover.
    val meshHues = categoryHues(darkTheme)
    val meshWinner = remember(item.category) { categoryHueIndex(item.category) }
    val meshResolved = !isSummarizing

    // An Animatable rather than animateFloatAsState so a card that was *already* resolved when it
    // first composed starts at 1 and never plays the resolve. Otherwise every card in the feed
    // would run the whole reveal on app launch, which would turn the one moment that means
    // something into ambient noise the user learns to ignore.
    val meshResolve = remember { Animatable(if (meshResolved) 1f else 0f) }

    // Deliberately slow, and a spatial spring rather than an effects one. This is the moment the
    // answer arrives — the payload, not a transition to get past — so it is worth watching. The
    // spatial spring also overshoots very slightly as the pool lands, which reads as light
    // settling into place rather than a value reaching its target.
    val meshResolveSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(meshResolved) {
        meshResolve.animateTo(if (meshResolved) 1f else 0f, animationSpec = meshResolveSpec)
    }

    // Runs while thinking, and keeps running until the resolve has fully played out. Tying the
    // clock to `isSummarizing` alone would stop it the instant the answer arrived, freezing the
    // pattern and then sliding it into place — which reads as a screenshot being moved rather
    // than as something settling. Once resolve reaches 1 nothing here costs a frame again.
    val meshRunning = !meshResolved || meshResolve.value < 1f
    val meshClock by rememberMeshClock(running = meshRunning)

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

    // The dimmer. Expanding turns the card's light up; collapsing takes it back down.
    //
    // Deliberately asymmetric, the way a real light is: it comes up fast and eagerly — a spatial
    // spring, so it overshoots slightly and settles, like a filament surging — and fades down
    // slowly on a longer effects spec. A light switching off decays; it does not snap.
    // The light rides the card's own expansion spring rather than a timing of its own.
    //
    // It had a slow tween on both edges, which looked right opening — the card and the light happen
    // to take about the same time — but on close the card snapped shut and the light kept fading
    // for another beat afterwards. Two animations describing one event must share a spec, or the
    // slower one reads as lag.
    //
    // AnimatedContent below drives the body with defaultSpatialSpec (damping 0.8, stiffness 380 in
    // the expressive scheme), so the glow uses the same spring. Springs are duration-free — they
    // settle when they settle — which is exactly why matching the *spec* works where matching a
    // hand-picked duration cannot.
    val glowIntensity by animateFloatAsState(
        targetValue = if (expanded) GLOW_EXPANDED_INTENSITY else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "glowIntensity",
    )

    // Swipe left to delete, confirmed by a dialog.
    //
    // SwipeToDismissBox has exactly two outcomes: confirmValueChange returns true and the content
    // flies off-screen, or it returns false and the content springs back. There is no third value
    // that parks it half-open — that is what several attempts at a swipe-to-reveal-then-tap flow
    // kept running into, and why they either lost the panel on finger-up or threw the card away.
    //
    // So: return false, and let a dialog carry the confirmation the parked panel would have. The
    // card springs back immediately, the dialog asks, and the row only leaves once the item stops
    // being emitted by the feed.
    var showDeleteConfirm by rememberSaveable(item.id) { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when {
                value == SwipeToDismissBoxValue.EndToStart && onDelete != null ->
                    showDeleteConfirm = true
                // No confirmation dialog on this edge: opening a chat is free to back out of,
                // where a delete is not. The card springs back and the chat rises over it.
                value == SwipeToDismissBoxValue.StartToEnd && onChat != null ->
                    onChat()
            }
            false
        }
    )

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
        if (canExpand) {
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
                    ChatSwipePanel(style)
                } else {
                    DeleteSwipePanel()
                }
            },
        ) {
            OutlinedCard(
                onClick = handleClick,
                // fillMaxWidth rather than the caller's modifier, which the box above now carries: the
                // card must fill that box or the panel shows through beside it at rest.
                modifier = Modifier.fillMaxWidth(),
                // Card feeds this to elevation.shadowElevation(), so the press elevation only animates
                // if the card owns the interaction — which a hand-rolled Modifier.clickable on the
                // plain overload cannot give it. That elevation change is now the only press feedback.
                interactionSource = cardInteractionSource,
                shape = MaterialTheme.shapes.large,
            ) {
                // No category spine: a square-cornered bar down the leading edge got clipped into a
                // wedge by the card's rounded corners and read as a rendering fault. The category
                // colour spills in as light from the top instead — see categoryGlow.
                //
                // The lights live on this outer Column, which spans the header image as well as the
                // text, so they still enter from the *card's* top edge. Hung on the inner content
                // column instead they would start below the photo, and the card's whole lighting idea
                // — a source just above the top edge — would be describing the wrong edge.
                //
                // Light falling across the photograph is also what removes the old hero's seam. There
                // is no fade from image to card to band; the glow crosses the boundary, which ties the
                // two regions together instead of transitioning between them.
                Column(
                    modifier = Modifier
                        // The resting light, faded in by the resolve so it takes over exactly as the
                        // mesh lets go. On an already-settled card meshResolve is 1 from the first
                        // frame, so this is simply the glow as it always was.
                        .categoryGlow(style.color) { glowIntensity * meshResolve.value }
                        // The thinking light, drawn over it and fading out on the same value. Only
                        // costs anything while a card is actually unresolved.
                        .then(
                            if (meshRunning) {
                                Modifier.summarizingMesh(
                                    hues = meshHues,
                                    winner = meshWinner,
                                    time = { meshClock },
                                    resolve = { meshResolve.value },
                                    alpha = { 1f - meshResolve.value },
                                )
                            } else Modifier
                        )
                ) {
                    // Drawn before the padded content so the image is genuinely full-bleed to the
                    // card's edges. Renders nothing when the page had no og:image.
                    CardHeaderImage(
                        path = item.imagePath,
                        style = style,
                        onOpenLink = onOpenLink,
                        modifier = Modifier.then(dotModifier),
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
                            modifier = titleModifier,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // The glyph tile stands in for the header on cards with no og:image, so a
                            // text-only row still opens with a category mark rather than starting cold.
                            if (item.imagePath.isNullOrBlank()) {
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
                                MetaDot()
                            }
                            // Time and source, separated by a dot. The category pill moves onto the image
                            // when there is one — see CardHeaderImage — so it is not stated twice.
                            Text(
                                text = relativeSavedLabel(item.savedAtEpochMillis, nowMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MetaDot()
                            Text(
                                text = item.domain,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .then(domainModifier),
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
                                        Spacer(Modifier.height(10.dp))
                                        // Plain text on the card's own surface, the way a news feed
                                        // sets a standfirst. The summary used to sit in a recessed
                                        // panel, which was there to break up a card where every
                                        // element had the same weight on the same left edge — but
                                        // the header image now does that job, and far better. Two
                                        // devices for one problem left the panel reading as a boxed
                                        // aside rather than as the card's own text.
                                        //
                                        // The directive is part of this paragraph rather than a row
                                        // of its own. It advertises what expanding gets you — a card
                                        // that merely grows on tap gives no reason to tap it — but it
                                        // is a property *of the summary*, and giving it a separate
                                        // line made the card five stacked text rows, which is the
                                        // same equal-weight flatness the recessed panel was once
                                        // added to fix. Inline, it costs no row at all.
                                        Text(
                                            text = buildAnnotatedString {
                                                append(item.headline)
                                                if (canExpand) {
                                                    append(" ")
                                                    withStyle(
                                                        SpanStyle(color = style.color)
                                                    ) {
                                                        // Non-breaking spaces: the directive has to
                                                        // wrap as one unit. Split across two lines
                                                        // ("See 5 key / points") it stops reading as
                                                        // a link and reads as damaged text — worse
                                                        // than the separate row it replaced.
                                                        append(
                                                            "See ${keyPoints.size} key points"
                                                                .replace(' ', ' ')
                                                        )
                                                    }
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 21.sp,
                                            // One more line than the summary alone needs, so the
                                            // directive still lands when the headline runs long
                                            // rather than being the thing that gets ellipsized.
                                            maxLines = 4,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
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

                        // Tags close the card. They are the app's main way back to a saved item, so they
                        // earn the last line — where the domain used to sit as a stray footer. All of them,
                        // not just the leading one: showing one made the other two invisible, and FlowRow
                        // wraps rather than clipping. Kept out of the AnimatedContent so they stay visible
                        // in both states.
                        if (item.tags.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                item.tags.forEach { tag ->
                                    TagChip(
                                        tag = tag,
                                        accent = style.color,
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
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
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
 * Separator between the marks in the card's meta line.
 *
 * A drawn dot rather than a "·" character: the glyph's size and vertical position vary by font, and
 * at label sizes it sits high enough to read as an apostrophe between two lowercase words.
 */
@Composable
private fun MetaDot() {
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
private fun TagChip(tag: String, accent: Color, active: Boolean = false) {
    // Animated so chips resolve into and out of their filled state as the filter changes, rather
    // than the whole feed hard-cutting to a new colour scheme.
    val container by animateColorAsState(
        targetValue = if (active) accent else accent.copy(alpha = 0.10f),
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tagChipContainer",
    )
    val content by animateColorAsState(
        targetValue = if (active) MaterialTheme.colorScheme.surface else accent,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "tagChipContent",
    )
    Text(
        text = tag,
        style = MaterialTheme.typography.labelSmall,
        color = content,
        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .background(container)
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
            // No spinner. The mesh gradient behind this row already says "working", and says it
            // with far more specificity — a stock indeterminate circle next to it reads as the
            // real progress indicator and demotes the mesh to decoration, which is exactly
            // backwards. The label stays, because the mesh says *thinking* but not about what.
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
 * Drawn *over* the content rather than behind it, and fading to transparent, so it settles into
 * whatever it lands on — there is no second colour for it to meet.
 *
 * It was a `drawBehind` while the card began with text, where over and under are indistinguishable
 * because the ground is the card's own surface. With a full-width header image at the top, behind
 * means the photograph covers the light completely: the pool would exist only on the text below the
 * image, which is the one place the card's top-edge light should *not* be. Over the content, the
 * light falls across the photo and continues onto the card, which is what makes the image read as
 * part of the card rather than as a picture glued above it — and it is why the header needs no fade
 * to the card surface. The alphas here are low enough that text under the pool stays legible.
 *
 * @param intensity a dimmer, 0..1+. Expanding a card turns the light up and collapsing it back
 *   down, so the glow is doing something rather than only decorating: the card you opened is
 *   visibly lit while the rest of the feed stays at rest. Read through a lambda so the animation
 *   runs in the draw phase and never recomposes the card.
 */
private fun Modifier.categoryGlow(
    color: Color,
    intensity: () -> Float = { 1f },
): Modifier = this.drawWithContent {
    drawContent()
    val t = intensity()
    // Both the brightness and the spread grow with the dimmer. Scaling alpha alone reads as the
    // colour being turned up; a real light also throws further, and it is the pool widening that
    // sells it as illumination rather than a fade.
    val radius = size.width * GLOW_RADIUS_FACTOR * (1f + (t - 1f) * GLOW_SPREAD_GAIN)
    val peak = GLOW_ALPHA * t
    // Saturated before use. The category palette is tuned for legible mid-tone *text* and icons;
    // spread thin as light those hues wash out to a grey haze. Pushing each channel away from the
    // midpoint restores the hue at the low alphas this draws at, so a Blog card reads pink and an
    // Article card blue rather than both reading "slightly warm grey".
    val lit = color.saturated(GLOW_SATURATION)
    // The falloff flattens as the light comes up. At rest the curve drops away sharply, keeping the
    // pool tight and the card mostly its own colour; lit, the mid-stops lift so the light carries
    // further before fading. Physically this is a lamp being brought closer as well as brighter,
    // and it is what stops the expanded state reading as a brighter version of the same small pool.
    val reach = ((t - 1f) / (GLOW_EXPANDED_INTENSITY - 1f)).coerceIn(0f, 1f)
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to lit.copy(alpha = peak),
                0.20f to lit.copy(alpha = peak * lerp(0.55f, 0.66f, reach)),
                0.40f to lit.copy(alpha = peak * lerp(0.24f, 0.38f, reach)),
                0.65f to lit.copy(alpha = peak * lerp(0.08f, 0.17f, reach)),
                0.85f to lit.copy(alpha = peak * lerp(0.02f, 0.06f, reach)),
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

/**
 * Peak opacity at the centre of the pool, at rest.
 *
 * Raised so the collapsed state is closer to the expanded one. The gap between them was doing too
 * much work: a dim card jumping to a bright one reads as a switch being thrown, where the effect
 * wants to read as a light being turned *up*. Both states should look lit — the expansion changes
 * the degree, not the fact.
 */
private const val GLOW_ALPHA = 0.44f

/** How far the category hue is pushed from grey before being used as light. 1f leaves it as-is. */
private const val GLOW_SATURATION = 1.7f

/**
 * Peak dimmer value when a card is expanded. Above 1, so opening a card genuinely brightens past
 * the resting state rather than merely returning to it.
 */
private const val GLOW_EXPANDED_INTENSITY = 1.75f

/**
 * How much of the dimmer's travel also widens the pool.
 *
 * Enough that the light visibly reaches further when it comes up — brightness alone reads as a
 * highlight on the header rather than the card being lit — but not so much that it floods the
 * whole card. At 0.9 it overshot and the pool swallowed the key points; a little over half the
 * travel keeps the falloff visible, which is what makes it read as light with a source.
 */
private const val GLOW_SPREAD_GAIN = 0.6f

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
 * swipe itself is the action. The panel wears the card's own category tint rather than a fixed
 * accent — the swipe is "talk to the model about *this*", and the category colour is how this app
 * says *this*. It also keeps the two swipe directions unmistakable mid-drag: category tint one
 * way, error red the other.
 */
@Composable
private fun ChatSwipePanel(style: CategoryStyle, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(style.container)
            .padding(start = 32.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Chat,
            contentDescription = null, // The screen this opens names itself.
            tint = style.color,
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

