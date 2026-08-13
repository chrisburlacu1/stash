package com.example.stash.ui.feed

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.stash.data.ThemeMode

/**
 * The feed's title bar. Carries the app's identity, and is where the settings action will live.
 *
 * The feed had no top bar for a while: search moved into the floating toolbar, which was the only
 * thing that opened the old one, so it was costing screen height for nothing. It comes back for a
 * different reason — the app had no name on screen anywhere, and a settings entry point needs a home
 * that is not the bottom toolbar (that toolbar is for *acting on the stash*; settings is not that).
 *
 * Note for whoever adds the settings icon: the container is deliberately transparent, so this bar
 * reads as part of the feed rather than as a separate plate. Cards scroll underneath it. If that
 * turns out to be illegible once there is an icon over a scrolling card, give it a
 * `scrollBehavior` and let it pick up its own container colour — do not paint it opaque by hand.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedTopBar(
    onTitleClick: () -> Unit,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    /** TEMPORARY: cycles the feed background treatment. Remove once one is chosen. */
    onCycleBackground: (() -> Unit)? = null,
    /** TEMPORARY: label of the active background treatment; doubles as the cycle button. */
    backgroundLabel: String? = null,
) {
    TopAppBar(
        title = {
            // No ripple and no button affordance: this is an easter egg, so it must look exactly
            // like a title. A visible indication would advertise it and make it a control the user
            // expects to do something useful.
            val interaction = remember { MutableInteractionSource() }
            Text(
                text = "Stash",

                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onTitleClick,
                    )
                    .padding(horizontal = 4.dp, vertical = 2.dp)
                    // Named for a screen reader even though it is a hidden extra — an unlabelled
                    // clickable is worse than a discoverable one.
                    .semantics { contentDescription = "Stash" },
            )
        },
        actions = {
            // TEMPORARY debug control: cycles the background treatment. Deliberately a plain,
            // obvious button — this is scaffolding for an on-device comparison and gets deleted
            // once one option wins, so it does not need to look like part of the app.
            if (onCycleBackground != null && backgroundLabel != null) {
                TextButton(onClick = onCycleBackground) {
                    Text(backgroundLabel)
                }
            }
            IconButton(onClick = onToggleTheme) {
                // Shows the *current* mode; tapping cycles System → Light → Dark → System.
                val (icon, description) = when (themeMode) {
                    ThemeMode.System -> Icons.Outlined.BrightnessAuto to "Theme: System"
                    ThemeMode.Light -> Icons.Filled.LightMode to "Theme: Light"
                    ThemeMode.Dark -> Icons.Filled.DarkMode to "Theme: Dark"
                }
                Icon(imageVector = icon, contentDescription = description)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}
