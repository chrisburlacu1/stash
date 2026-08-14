package com.example.stash.ui.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarScrollBehavior
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.example.stash.ai.ModelOption
import com.example.stash.data.ModelChoice
import com.example.stash.data.SummaryEffort

/**
 * The feed's actions, floating over the content at the bottom of the screen.
 *
 * Belongs in the [Scaffold]'s FAB slot rather than free-floating in a Box: that keeps a future
 * Snackbar stacking above the toolbar instead of behind it, and the slot already clears the
 * navigation bar.
 *
 * @param scrollBehavior optional hide-on-scroll. Null keeps the toolbar pinned, which is how the
 *   feed uses it — these are the app's only actions, so they should not disappear while scrolling.
 *   When non-null, pair it with a matching `Modifier.nestedScroll(...)` on the Scaffold.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FeedToolbar(
    effort: SummaryEffort,
    modelChoice: ModelChoice,
    modelOptions: List<ModelOption>,
    isProbingModels: Boolean,
    onAddUrl: () -> Unit,
    onSearch: () -> Unit,
    onOpenModelMenu: () -> Unit,
    onSelectEffort: (SummaryEffort) -> Unit,
    onSelectModel: (ModelChoice) -> Unit,
    /** Re-runs the availability probe after it failed and left the menu empty. */
    onRetryProbe: () -> Unit = {},
    modifier: Modifier = Modifier,
    scrollBehavior: FloatingToolbarScrollBehavior? = null,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    HorizontalFloatingToolbar(
        // Always expanded. Bottom-centre placement means the whole toolbar slides off on scroll
        // rather than collapsing to just its FAB — collapsing would strand the FAB off-centre.
        // So there is no second collapse state to drive here.
        expanded = true,
        scrollBehavior = scrollBehavior,
        colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
        floatingActionButton = {
            ToolbarTooltip("Add URL") {
                // The vibrant FAB is the one that matches vibrantFloatingToolbarColors; a plain
                // FloatingActionButton would not pick up the toolbar's container colour, nor the
                // sizing the toolbar animates it through.
                FloatingToolbarDefaults.VibrantFloatingActionButton(onClick = onAddUrl) {
                    Icon(Icons.Default.Add, contentDescription = "Add URL")
                }
            }
        },
        // ScreenOffset is the toolbar's own spec'd gap from the screen edge. Do NOT add
        // navigationBarsPadding() as well — the Scaffold's FAB slot already clears the navigation
        // bar, and stacking both insets floats the toolbar well above where it belongs.
        modifier = modifier.offset(y = -FloatingToolbarDefaults.ScreenOffset),
    ) {
        ToolbarTooltip("Search") {
            IconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }
        }
        // The menu anchors to this Box, not the toolbar, so it opens over the button rather than
        // at the toolbar's leading edge.

            ToolbarTooltip("Summarization settings") {
                IconButton(
                    onClick = {
                        menuOpen = true
                        // Probe on open, not at startup: each variant costs a checkStatus() IPC.
                        onOpenModelMenu()
                    }
                ) {
                    Icon(Icons.Default.Tune, contentDescription = "Summarization settings")
                }
            }
            ModelMenu(

                expanded = menuOpen,
                effort = effort,
                modelChoice = modelChoice,
                modelOptions = modelOptions,
                isProbing = isProbingModels,
                onDismiss = { menuOpen = false },
                // The menu stays open through an effort change — it is a quick toggle people
                // often compare against the model row right below it.
                onSelectEffort = onSelectEffort,
                // A model switch closes it: it re-resolves and re-warms the client, which is a
                // deliberate, one-at-a-time action rather than something to flick between.
                onSelectModel = {
                    onSelectModel(it)
                    menuOpen = false
                },
                // Stays open: a retry that closed the menu would hide its own result.
                onRetryProbe = onRetryProbe,
            )

    }
}

/**
 * Wraps a toolbar button in a tooltip. Icon-only buttons carry no visible label, so this is the
 * only thing naming the action — for a screen reader as much as for a long-press. Anchored above,
 * since the toolbar sits at the bottom of the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            PlainTooltip(
                // TODO(b/496338253): Remove this modifier once the bug where tooltip text is not
                //  announced by a11y screen readers is resolved. Carried over from the M3 sample.
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Assertive
                    paneTitle = label
                }
            ) { Text(label) }
        },
        state = rememberTooltipState(),
        content = content,
    )
}
