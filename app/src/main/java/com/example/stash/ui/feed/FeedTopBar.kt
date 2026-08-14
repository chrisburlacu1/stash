package com.example.stash.ui.feed

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
        modifier = modifier,
    )
}
