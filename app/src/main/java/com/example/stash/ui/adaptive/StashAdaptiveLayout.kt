package com.example.stash.ui.adaptive

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.example.stash.data.StashRepository
import com.example.stash.data.StashSettings
import com.example.stash.ui.chat.StashChatScreen
import com.example.stash.ui.chat.StashChatViewModel
import com.example.stash.ui.feed.StashFeedViewModel
import com.example.stash.ui.feed.StashMainFeedScreen
import kotlinx.serialization.Serializable

@Serializable
data object FeedRoute : NavKey

/** Chat about one saved item, reached by swiping its card toward the leading edge. */
@Serializable
data class ChatRoute(val itemId: String) : NavKey

/**
 * Hosts the feed.
 *
 * This was a list-detail scaffold: tapping a row pushed a `DetailRoute` that rendered the item's
 * summary in a second pane. Cards now expand in place to show their key points, so the detail pane
 * had nothing left to show and was removed along with the compact list layout it served. The
 * NavDisplay is kept for the back stack — which the chat route now actually uses — and as the
 * place future routes (settings) will hang off.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun StashAdaptiveLayout(repository: StashRepository) {
    val backStack = rememberNavBackStack(FeedRoute)
    val appContext = LocalContext.current.applicationContext
    val settings = remember(appContext) { StashSettings(appContext) }
    val feedViewModel: StashFeedViewModel =
        viewModel(factory = StashFeedViewModel.Factory(repository, settings))

    // Read at composition — the transition lambdas below are not composable scopes, the same
    // constraint the card hit with its AnimatedContent specs. A spatial spring, because the
    // screen physically travels; its slight overshoot is what makes the sheet-like entrance
    // read as expressive motion rather than a linear slide.
    val chatSlideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

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
                        onOpenChat = { item -> backStack.add(ChatRoute(item.id)) },
                    )
                }
                entry<ChatRoute>(
                    // The chat rises from the bottom edge over the feed, and leaves the same
                    // way — the same axis its aura light enters on, so the surface and its
                    // light arrive as one thing. The feed stays put underneath rather than
                    // sliding sideways: this is a layer over the stash, not the next page of it.
                    metadata = metadata {
                        put(NavDisplay.TransitionKey) {
                            slideInVertically(
                                initialOffsetY = { it },
                                animationSpec = chatSlideSpec,
                            ) togetherWith ExitTransition.KeepUntilTransitionsFinished
                        }
                        put(NavDisplay.PopTransitionKey) {
                            EnterTransition.None togetherWith slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = chatSlideSpec,
                            )
                        }
                        put(NavDisplay.PredictivePopTransitionKey) {
                            EnterTransition.None togetherWith slideOutVertically(
                                targetOffsetY = { it },
                                animationSpec = chatSlideSpec,
                            )
                        }
                    },
                ) { route ->
                    // Keyed per item so each conversation keeps its own transcript for the
                    // process lifetime — reopening a chat resumes it rather than starting over.
                    val chatViewModel: StashChatViewModel = viewModel(
                        key = "chat-${route.itemId}",
                        factory = StashChatViewModel.Factory(repository, route.itemId),
                    )
                    StashChatScreen(
                        viewModel = chatViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
    }
}
