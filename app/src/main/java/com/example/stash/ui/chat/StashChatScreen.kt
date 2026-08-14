package com.example.stash.ui.chat

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stash.ui.theme.categoryStyle

/**
 * Chat with Gemini Nano about one saved item, reached by swiping a card toward its leading edge.
 *
 * Plain M3 Expressive throughout. The screen was built on the feed's visual grammar — category
 * colour as *light*, a full-palette mesh as *thinking*, both at screen scale via `chatAura`, with
 * the assistant's replies carrying a hue wash the user's side deliberately lacked. That whole layer
 * is removed; see DESIGN-NOTES, "The lighting layer is parked". The two sides are now distinguished
 * by alignment, container and corner shape alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashChatScreen(
    viewModel: StashChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // The IME outlives the composable if it is not dismissed here: navigating back with the
    // keyboard up leaves it floating over the feed for a frame or two.
    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.item

    // Only `style.label` is read now — the category's display name, which is ink and theme
    // independent. `style.color` fed the aura and the assistant wash, both removed with the light
    // layer, so the theme argument here no longer affects anything this screen draws.
    val style = categoryStyle(
        item?.category ?: "Unsorted",
        darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
    )

    val handleBackWithKeyboard = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onBack()
    }

    // The transcript reserves this much bottom space so the newest message clears the floating
    // composer at its docked (IME-closed) height. Measured rather than hard-coded because the
    // composer's height already varies with a multi-line draft.
    var composerHeight by remember { mutableStateOf(0.dp) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // Deliberately *not* .imePadding() here. That used to sit on this root Box, so the
            // whole Column below — header included — shrank on every frame of the keyboard's
            // animation: the header has nothing to do with the IME and should never move. Now
            // only ChatInputBar carries imePadding(), and it is floated over the Column (see
            // below) rather than living inside its layout flow, so nothing else in the Column
            // resizes when the composer translates. See DESIGN-NOTES for the on-device symptom
            // this fixes, including why floating (not just relocating imePadding) was necessary.
            //
            // This Box also carried `chatAura` — a bottom-anchored AGSL mesh whose churn tracked
            // the model streaming a reply. Removed with the rest of the light layer; see
            // DESIGN-NOTES, "The lighting layer is parked". Streaming state is carried by the
            // transcript itself until light is redefined.
    ) {
        // Header and transcript are one Column that never touches the IME inset — its height is
        // fixed to the screen, full stop. The composer below is a separate sibling, floated over
        // this Column and pinned to the bottom by its own imePadding(), so it is the only thing
        // that translates when the keyboard opens. This replaces an earlier structure where the
        // composer lived inside the Column and pushed everything above it around: with the empty
        // welcome centred in the shrinking transcript space, that read as the composer floating
        // to the middle of the screen with a gap below it, not docking above the keyboard.
        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                title = item?.title.orEmpty(),
                domain = item?.domain.orEmpty(),
                categoryLabel = style.label,
                onBack = handleBackWithKeyboard,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    // Newest at the visual bottom, and the list stays pinned there as chunks
                    // stream in — the standard chat arrangement, which is why it is not the
                    // feed's layout.
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    // Bottom padding reserves room for the floating composer's resting height
                    // (see composerHeight below) so the newest message never sits under it. The
                    // composer's own IME translation is independent of this — it is not sized
                    // into this padding, only its docked height is.
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 20.dp,
                        bottom = 12.dp + composerHeight,
                    ),
                ) {
                    // The thinking label, not a spinner: the aura already says "working" with far
                    // more specificity, and text is what tells the user *what* is being worked on.
                    // Rendered only until the first chunk arrives — once the reply is streaming,
                    // the growing text is its own indicator.
                    if (state.isResponding && state.messages.lastOrNull()?.fromUser == true) {
                        item(key = "thinking") {
                            Text(
                                text = "Thinking…",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .animateItem(
                                        // Fade only — no placement spring. See the note on the
                                        // message rows below.
                                        placementSpec = null,
                                    )
                                    .padding(horizontal = 4.dp),
                            )
                        }
                    }
                    // Deliberately *no* placement animation on transcript rows.
                    //
                    // animateItem's placement spring is for content changes — a row arriving,
                    // leaving, reordering. A chat transcript's rows move for two reasons that are
                    // not content changes, and springing both looked like the screen flexing:
                    //
                    //  - The IME opening. The composer floats over this list and does not resize
                    //    it — but new rows still land under the now-taller reserved bottom padding
                    //    at the same moment, and a per-row spring chasing that would be one more
                    //    animation fighting the system's own inset curve.
                    //  - A reply streaming. The bubble grows by a few characters per chunk, so
                    //    every row above it gets a new position several times a second and springs
                    //    to each one. The text should extend; the transcript should not wobble.
                    //
                    // reverseLayout already pins the newest row to the bottom, so a new message
                    // appears in place rather than shoving the list — there is nothing here that
                    // needs a placement animation to stay legible.
                    items(state.messages.asReversed(), key = ChatMessage::id) { message ->
                        if (message.fromUser) {
                            UserMessage(text = message.text)
                        } else {
                            AssistantMessage(text = message.text)
                        }
                    }
                }

                // What an empty chat offers instead of a blank pane: what this is, and where
                // answers come from. Centred rather than pinned above the composer, so the first
                // thing on screen is the offer, not a gap.
                //
                // Fully qualified because the bare name resolves to the ColumnScope extension,
                // which does not apply inside this Box.
                // Fade, not scale. A scale spring on a block this large reads as the panel
                // inflating, and the spatial spring's overshoot pushes it past its own size — on
                // top of the several-hundred-pixel slide it already makes when the IME halves
                // this Box. The effects spec is duration-based and does not overshoot, which is
                // what a pure opacity change wants.
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.messages.isEmpty(),
                    enter = fadeIn(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec()),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    ChatEmptyWelcome()
                }
            }
        }

        // Floats over the Column above, pinned to the bottom edge and translating only with its
        // own imePadding() — the Column never sees this move. onSizeChanged captures the bar's
        // docked height (IME closed) so the transcript can reserve exactly that much bottom
        // padding; when the IME opens the bar translates up over the transcript's reserved gap
        // rather than resizing anything, which is the "floating, screen otherwise still" behaviour.
        ChatInputBar(
            enabled = item != null,
            sending = state.isResponding,
            onSend = viewModel::send,
            modifier = Modifier.align(Alignment.BottomCenter),
            // Pill height plus the Surface's own vertical padding (10dp top + 10dp bottom) —
            // everything except the IME/nav-bar inset, which must not feed back into this.
            onPillHeightChanged = { pillHeight -> composerHeight = pillHeight + 20.dp },
        )
    }
}

/**
 * Compact header: back button and item identity with 10dp spacing.
 *
 * The category pill is plain M3 ink (`onSurfaceVariant` on `surfaceContainerHigh`), not the item's
 * category hue — per the ink/light split in DESIGN-NOTES ("M3 owns ink, Stash owns light"), category
 * colour appears only as the aura's light, never as a second ink palette in the header.
 */
@Composable
private fun ChatHeader(
    title: String,
    domain: String,
    categoryLabel: String,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The user's side: plain M3, a filled container bubble hugging the trailing edge. */
@Composable
private fun UserMessage(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                // Never full width: a bubble that spans the screen stops reading as one side of
                // an exchange. The start inset is what keeps long messages ragged-left.
                .padding(start = 48.dp)
                .clip(
                    MaterialTheme.shapes.large.copy(bottomEnd = CornerSize(6.dp)),
                )
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * The assistant's side. Plain `surfaceContainerLow`, distinguished from the user's side by
 * alignment and the asymmetric corner rather than by colour.
 *
 * It used to carry the "lighter version" of the AI visual language — a whisper-alpha wash of the
 * item's hue, sharp at the leading edge and diffusing to nothing, drifting while the reply was
 * being written and still once it existed. That went with the rest of the light layer; see
 * DESIGN-NOTES, "The lighting layer is parked".
 */
@Composable
private fun AssistantMessage(text: String) {
    val shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(6.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(end = 24.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/**
 * How long the entrance veil holds full churn before settling, in ms. Matched to the visible
 * travel of the chat route's slide-up spring (`defaultSpatialSpec` ≈ 600ms) plus a beat to
 * register — the veil rides the transition, then the settle is its own watchable moment.
 *
 * Do not push this past ~900ms: the settle then stops accompanying the first interaction and
 * starts blocking it. Paired with `slowSpatialSpec`, 650ms already puts the resting pool a full
 * second out.
 */
private const val ENTRANCE_CHURN_MILLIS = 650L

/**
 * Centered welcome hint displayed in the middle of the chat area on initial load.
 *
 * The icon's accent used to be the item's category hue; per the ink/light split in DESIGN-NOTES
 * ("M3 owns ink, Stash owns light") it is now `primary`/`primaryContainer`, the same M3 role the
 * card's "See N key points" directive and key-point numerals use — category colour is expressed
 * only as the aura's light elsewhere on this screen, never as a second ink palette here.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatEmptyWelcome(
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 32.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Ask about this save",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Gemini Nano answers using the full content of this item. Everything stays on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )
    }
}

/**
 * Taller, narrower Gemini-style composer pill with smooth M3 Expressive Motion response.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ChatInputBar(
    enabled: Boolean,
    sending: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    onPillHeightChanged: (Dp) -> Unit = {},
) {
    val density = LocalDensity.current
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = enabled && !sending && draft.isNotBlank()
    val submit = {
        if (canSend) {
            onSend(draft)
            draft = ""
        }
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            // navigationBars *union* ime, not navigationBarsPadding().
            //
            // The two insets change in opposite directions at the same moment: as the IME rises
            // the nav bar collapses to zero, and the screen's imePadding() grows. Consuming them
            // as two independent paddings meant the composer was pushed up by one while being
            // pulled down by the other, on the system's curve versus the layout pass — so it
            // visibly settled twice. The union is always whichever is larger, so there is exactly
            // one number here and it moves once.
            .windowInsetsPadding(
                WindowInsets.navigationBars.union(WindowInsets.ime),
            )
            .padding(horizontal = 28.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                // Measures only the pill's own content — not the Surface's outer inset padding,
                // which grows as the IME rises. Sizing the transcript's reserved gap off that
                // would grow it every frame of the keyboard animation, feeding straight back into
                // a layout the fix is meant to hold still.
                .onSizeChanged { size ->
                    onPillHeightChanged(density.run { size.height.toDp() })
                }
                .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        ) {
            // BasicTextField, not TextField.
            //
            // A Material TextField carries its own focus choreography: it animates the label from
            // placeholder position up to the top, retargets its internal content padding, and
            // cross-fades indicator and container. Making those colours transparent hides the
            // *appearance* of all that but leaves the *motion* running — an M3 filled field
            // playing its label-and-padding animation inside a custom pill that is not the shape
            // it was designed for. That is the flex on focus: the field's own decoration moving
            // under a container that has no decoration to move.
            //
            // BasicTextField has no such choreography. The pill is the container, the placeholder
            // is a plain sibling that swaps on emptiness, and focusing changes nothing but the
            // cursor. Everything visible here is drawn by this composable, so nothing animates
            // that we did not ask for.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (draft.isEmpty()) {
                    Text(
                        text = "Ask about this save…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // The enabled transition is colour, not scale. A spatial spring here fired on the
            // *first keystroke* — so the button bounced while the keyboard was still opening and
            // the user's eyes were on the field, which read as the composer flexing. Container
            // and tint already say "this is live now", and they cross-fade without moving
            // anything. The effects spec is for non-spatial changes exactly like this one.
            val sendContainer by animateColorAsState(
                targetValue = if (canSend) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    Color.Transparent
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "sendContainer",
            )
            val sendTint by animateColorAsState(
                targetValue = if (canSend) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "sendTint",
            )

            IconButton(
                onClick = submit,
                enabled = canSend,
                modifier = Modifier
                    .size(44.dp)
                    .background(color = sendContainer, shape = CircleShape),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send message",
                    tint = sendTint,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
