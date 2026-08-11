package com.example.stash.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.stash.ui.components.chatAura
import com.example.stash.ui.components.rememberMeshClock
import com.example.stash.ui.theme.categoryHueIndex
import com.example.stash.ui.theme.categoryHues
import com.example.stash.ui.theme.categoryStyle

/**
 * Chat with Gemini Nano about one saved item, reached by swiping a card toward its leading edge.
 *
 * The screen is built on the same visual grammar as the feed: category colour as *light*, and the
 * full-palette mesh as *thinking*. Here both run at screen scale — see [chatAura] for the shader
 * and the reasoning. The user's side of the conversation is deliberately plain M3 (a filled
 * bubble, a filled send button): the expressive treatment is reserved for the assistant, so the
 * gradient language keeps meaning "the model" rather than becoming the screen's wallpaper.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StashChatScreen(
    viewModel: StashChatViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val item = state.item

    val darkTheme = isSystemInDarkTheme()
    val hues = categoryHues(darkTheme)
    val style = categoryStyle(item?.category ?: "Unsorted", darkTheme)
    val winner = remember(item?.category) { categoryHueIndex(item?.category ?: "Unsorted") }

    // The screen *arrives* unresolved: the entrance plays resolve 0 → 1, so the veil that rises
    // with the navigation transition is the assistant settling into its resting pool. Sending a
    // question turns it back up. An Animatable rather than animateFloatAsState for the same
    // reason as the card mesh — the starting value is part of the design.
    val auraResolve = remember { Animatable(0f) }

    // Asymmetric on purpose: the light comes up eagerly when a question lands (default spatial)
    // and settles slowly once the answer has finished (slow spatial) — the settle is the payload,
    // the flare is a response to touch. Same asymmetry as the card's dimmer.
    val flareSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val settleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(state.isResponding) {
        if (state.isResponding) {
            auraResolve.animateTo(0f, flareSpec)
        } else {
            auraResolve.animateTo(1f, settleSpec)
        }
    }

    // The frame clock only runs while there is motion to show; a settled chat costs nothing.
    val auraRunning = state.isResponding || auraResolve.value < 1f
    val auraClock by rememberMeshClock(running = auraRunning)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            // The aura is drawn inside the ime padding, so the light rides up with the keyboard
            // and keeps sitting under the input bar instead of being buried behind the IME.
            .imePadding()
            .chatAura(
                hues = hues,
                winner = winner,
                time = { auraClock },
                // Spatial springs overshoot; past 1 the shader's survive-mix would push loser
                // weights negative, which shows as colour artifacts rather than bounce.
                resolve = { auraResolve.value.coerceIn(0f, 1f) },
            ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                title = item?.title.orEmpty(),
                domain = item?.domain.orEmpty(),
                categoryLabel = style.label,
                categoryColor = style.color,
                containerColor = style.container,
                onBack = onBack,
            )

            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                // Newest at the visual bottom, and the list stays pinned there as chunks stream
                // in — the standard chat arrangement, which is why it is not the feed's layout.
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
                items(state.messages.asReversed(), key = ChatMessage::id) { message ->
                    if (message.fromUser) {
                        UserMessage(text = message.text)
                    } else {
                        AssistantMessage(
                            text = message.text,
                            leadHue = style.color,
                            tailHue = hues[(winner + 1) % hues.size],
                        )
                    }
                }
            }

            if (state.messages.isEmpty()) {
                ChatEmptyHint(accent = style.color)
            }

            ChatInputBar(
                enabled = item != null,
                sending = state.isResponding,
                onSend = viewModel::send,
            )
        }
    }
}

/**
 * Compact header: a back affordance and the item's identity, in the card's own eyebrow-then-title
 * order so the chat reads as a continuation of the card the user just swiped.
 */
@Composable
private fun ChatHeader(
    title: String,
    domain: String,
    categoryLabel: String,
    categoryColor: Color,
    containerColor: Color,
    onBack: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = categoryLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(containerColor)
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
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
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
 * The assistant's side, carrying the "lighter version" of the AI visual language: the reply sits
 * on a wash of the item's hue that enters saturated at the leading edge and diffuses to almost
 * nothing — the reference's "sharp leading edge, diffuse tail" at whisper opacity, over an
 * ordinary surface so the text never fights its own background.
 */
@Composable
private fun AssistantMessage(text: String, leadHue: Color, tailHue: Color) {
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
                .background(
                    Brush.horizontalGradient(
                        0.0f to leadHue.copy(alpha = 0.14f),
                        0.55f to tailHue.copy(alpha = 0.06f),
                        1.0f to tailHue.copy(alpha = 0.02f),
                    ),
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** What an empty chat offers instead of a blank pane: what this is, and where answers come from. */
@Composable
private fun ChatEmptyHint(accent: Color) {
    Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(
            text = "Ask about this save",
            style = MaterialTheme.typography.titleMedium,
            color = accent,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Gemini Nano answers from the key points Stash extracted. " +
                "Everything stays on this device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The composer. Sits directly on the aura's resting pool — the input is the one piece of chrome
 * that belongs to the assistant's territory, so it gets no opaque bar of its own.
 */
@Composable
private fun ChatInputBar(
    enabled: Boolean,
    sending: Boolean,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val canSend = enabled && !sending && draft.isNotBlank()
    val submit = {
        if (canSend) {
            onSend(draft)
            draft = ""
        }
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
    ) {
        TextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text("Ask about this save…") },
            enabled = enabled,
            shape = MaterialTheme.shapes.large,
            colors = TextFieldDefaults.colors(
                // The pill is the shape; an underline would put a hard edge inside it.
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submit() }),
            maxLines = 4,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(
            onClick = submit,
            enabled = canSend,
            modifier = Modifier.size(56.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
            )
        }
    }
}
