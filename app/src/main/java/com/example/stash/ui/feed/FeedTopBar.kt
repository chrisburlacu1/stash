package com.example.stash.ui.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.stash.data.ThemeMode

/**
 * The feed's title bar. Carries the app's identity, and is where the settings action will live.
 *
 * The feed had no top bar for a while: search moved into the floating toolbar, which was the only
 * thing that opened the old one, so it was costing screen height for nothing. It comes back for a
 * different reason — the app had no name on screen anywhere, and a settings entry point needs a home
 * that is not the bottom toolbar (that toolbar is for *acting on the stash*; settings is not that).
 *
 * **Opaque, and deliberately not scroll-reactive.** Two fancier arrangements were tried and both
 * failed, in ways worth recording because each looks like the obvious improvement:
 *
 *  - *Transparent with no scroll behaviour.* Only coherent if something else paints that strip.
 *    Something did — the feed's background treatment — and when that was parked with the lighting
 *    layer the bar became see-through over nothing, so cards appeared in the strip above the title
 *    while scrolling. **Transparent is a promise that another layer is painting there.**
 *  - *Transparent at rest, fading to a surface via `pinnedScrollBehavior`.* The textbook M3 answer,
 *    and it flickered continuously. The behaviour reads the list's scroll position to decide
 *    whether content is at the start, and that decision changes the bar's container colour, which
 *    remeasures the bar, which changes the Scaffold's reported top padding, which was feeding the
 *    list's `contentPadding` — a loop that re-entered every frame. Breaking the padding half of it
 *    still leaves the bar resting exactly on the "at start" boundary, which is its own flicker.
 *
 * So: one colour, no scroll state, nothing to oscillate. Cards scroll under it and are cleanly
 * occluded. If a scroll-reactive bar is wanted later, the prerequisite is that the top inset must
 * not come from anything the bar's own height influences.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedTopBar(
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        // The title was a hidden tap target opening an AGSL splash. That went with the light layer
        // (see DESIGN-NOTES, "The lighting layer is parked"), so it is plain text again.
        title = { Text("Stash") },
        actions = {
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
        // `surface`, matching what the feed sits on, so the bar reads as the top of the same plane
        // rather than as a raised plate. Cards passing under it are hidden by an opaque colour, not
        // by a scroll-driven one.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        modifier = modifier,
    )
}
