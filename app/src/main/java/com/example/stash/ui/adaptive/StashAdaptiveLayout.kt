package com.example.stash.ui.adaptive

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.example.stash.data.StashRepository
import com.example.stash.data.StashSettings
import com.example.stash.ui.feed.StashFeedViewModel
import com.example.stash.ui.feed.StashMainFeedScreen
import kotlinx.serialization.Serializable

@Serializable
data object FeedRoute : NavKey

/**
 * Hosts the feed.
 *
 * This was a list-detail scaffold: tapping a row pushed a `DetailRoute` that rendered the item's
 * summary in a second pane. Cards now expand in place to show their key points, so the detail pane
 * had nothing left to show and was removed along with the compact list layout it served. The
 * NavDisplay is kept for the back stack and as the place future routes (settings) will hang off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StashAdaptiveLayout(repository: StashRepository) {
    val backStack = rememberNavBackStack(FeedRoute)
    val appContext = LocalContext.current.applicationContext
    val settings = remember(appContext) { StashSettings(appContext) }
    val feedViewModel: StashFeedViewModel =
        viewModel(factory = StashFeedViewModel.Factory(repository, settings))

    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sharedTransitionScope = this,
            entryProvider = entryProvider {
                entry<FeedRoute> {
                    StashMainFeedScreen(
                        viewModel = feedViewModel,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    )
                }
            },
        )
    }
}
