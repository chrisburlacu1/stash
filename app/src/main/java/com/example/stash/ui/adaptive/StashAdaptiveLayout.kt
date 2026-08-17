package com.example.stash.ui.adaptive

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
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
import com.example.stash.ui.detail.StashDetailPlaceholder
import com.example.stash.ui.detail.StashDetailScreen
import com.example.stash.ui.feed.StashFeedViewModel
import com.example.stash.ui.feed.StashMainFeedScreen
import kotlinx.serialization.Serializable

@Serializable
data object FeedRoute : NavKey

/** Dedicated detail route for a saved item. */
@Serializable
data class DetailRoute(val itemId: String) : NavKey

/** Chat about one saved item, reached by swiping its card or tapping "Ask Gemini". */
@Serializable
data class ChatRoute(val itemId: String) : NavKey

/**
 * Replaces the current detail route if one is already showing, or pushes a new one.
 */
private fun NavBackStack<NavKey>.addDetail(route: DetailRoute) {
    removeAll { it is DetailRoute }
    add(route)
}

/**
 * Hosts the adaptive M3 List-Detail layout and chat routes.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun StashAdaptiveLayout(repository: StashRepository) {
    val backStack = rememberNavBackStack(FeedRoute)
    val appContext = LocalContext.current.applicationContext
    val settings = remember(appContext) { StashSettings(appContext) }
    val feedViewModel: StashFeedViewModel =
        viewModel(factory = StashFeedViewModel.Factory(repository, settings))

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    val chatSlideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val fadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(listDetailStrategy),
            sharedTransitionScope = this,
            entryProvider = entryProvider {
                entry<FeedRoute>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { StashDetailPlaceholder() }
                    ) + metadata {
                        put(NavDisplay.TransitionKey) {
                            EnterTransition.None togetherWith ExitTransition.KeepUntilTransitionsFinished
                        }
                        put(NavDisplay.PopTransitionKey) {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                        put(NavDisplay.PredictivePopTransitionKey) {
                            EnterTransition.None togetherWith ExitTransition.None
                        }
                    }
                ) {
                    StashMainFeedScreen(
                        viewModel = feedViewModel,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onOpenDetail = { item -> backStack.addDetail(DetailRoute(item.id)) },
                        onOpenChat = { item -> backStack.add(ChatRoute(item.id)) },
                    )
                }

                entry<DetailRoute>(
                    metadata = ListDetailSceneStrategy.detailPane() + metadata {
                        put(NavDisplay.TransitionKey) {
                            fadeIn(animationSpec = fadeSpec) togetherWith ExitTransition.KeepUntilTransitionsFinished
                        }
                        put(NavDisplay.PopTransitionKey) {
                            EnterTransition.None togetherWith fadeOut(animationSpec = fadeSpec)
                        }
                        put(NavDisplay.PredictivePopTransitionKey) {
                            EnterTransition.None togetherWith fadeOut(animationSpec = fadeSpec)
                        }
                    }
                ) { route ->
                    val initialItem = remember(route.itemId) {
                        feedViewModel.uiState.value.items.firstOrNull { it.id == route.itemId }
                            ?: feedViewModel.uiState.value.searchResults.firstOrNull { it.id == route.itemId }
                    }
                    StashDetailScreen(
                        itemId = route.itemId,
                        initialItem = initialItem,
                        repository = repository,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenChat = { item -> backStack.add(ChatRoute(item.id)) },
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    )
                }

                entry<ChatRoute>(
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
