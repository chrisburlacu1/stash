package dev.cburlacu.stash.ui.adaptive

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.navigation3.ui.NavDisplay
import dev.cburlacu.stash.data.StashRepository
import dev.cburlacu.stash.data.StashSettings
import dev.cburlacu.stash.ui.briefing.StashBriefingScreen
import dev.cburlacu.stash.ui.briefing.StashBriefingViewModel
import dev.cburlacu.stash.ui.chat.StashChatScreen
import dev.cburlacu.stash.ui.chat.StashChatViewModel
import dev.cburlacu.stash.ui.detail.StashDetailPlaceholder
import dev.cburlacu.stash.ui.detail.StashDetailScreen
import dev.cburlacu.stash.ui.feed.StashFeedViewModel
import dev.cburlacu.stash.ui.feed.StashMainFeedScreen
import dev.cburlacu.stash.ui.settings.StashSettingsScreen
import kotlinx.serialization.Serializable

@Serializable
data object FeedRoute : NavKey

/** Dedicated detail route for a saved item. */
@Serializable
data class DetailRoute(val itemId: String) : NavKey

/** Chat about one saved item, reached by swiping its card or tapping "Ask Gemini". */
@Serializable
data class ChatRoute(val itemId: String) : NavKey

/** Dedicated settings route for theme, dynamic color, AI model choice and effort. */
@Serializable
data object SettingsRoute : NavKey

/** Multi-item executive briefing or topic catch-up route. */
@Serializable
data class BriefingRoute(val itemIds: List<String>, val topicTitle: String? = null) : NavKey

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
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun StashAdaptiveLayout(repository: StashRepository) {
    val backStack = rememberNavBackStack(FeedRoute)
    val appContext = LocalContext.current.applicationContext
    val settings = remember(appContext) { StashSettings(appContext) }
    val feedViewModel: StashFeedViewModel =
        viewModel(factory = StashFeedViewModel.Factory(repository, settings))

    val feedListState = rememberLazyListState()
    val feedChipsState = rememberLazyListState()

    val windowAdaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(windowAdaptiveInfo) {
        calculatePaneScaffoldDirective(windowAdaptiveInfo)
            .copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)

    val chatSlideSpec = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()

    NavDisplay(
        
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        sceneStrategies = listOf(listDetailStrategy),
        entryProvider = entryProvider {
            entry<FeedRoute>(
                metadata = ListDetailSceneStrategy.listPane(
                    detailPlaceholder = { StashDetailPlaceholder() }
                )
            ) {
                StashMainFeedScreen(
                    viewModel = feedViewModel,
                    listState = feedListState,
                    chipsState = feedChipsState,
                    onOpenDetail = { item -> backStack.addDetail(DetailRoute(item.id)) },
                    onOpenChat = { item -> backStack.add(ChatRoute(item.id)) },
                    onOpenBriefing = { itemIds, topic -> backStack.add(BriefingRoute(itemIds, topic)) },
                    onOpenSettings = { backStack.add(SettingsRoute) },
                )
            }

            val detailSpatialSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
            val detailEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

            entry<DetailRoute>(
                metadata = ListDetailSceneStrategy.detailPane() + metadata {
                    put(NavDisplay.TransitionKey) {
                        (scaleIn(
                            initialScale = 0.92f,
                            animationSpec = detailSpatialSpec,
                        ) + fadeIn(
                            animationSpec = detailEffectsSpec,
                        )) togetherWith (scaleOut(
                            targetScale = 0.96f,
                            animationSpec = detailSpatialSpec,
                        ) + fadeOut(animationSpec = detailEffectsSpec))
                    }
                    put(NavDisplay.PopTransitionKey) {
                        (scaleIn(
                            initialScale = 0.96f,
                            animationSpec = detailSpatialSpec,
                        ) + fadeIn(
                            animationSpec = detailEffectsSpec,
                        )) togetherWith (scaleOut(
                            targetScale = 0.92f,
                            animationSpec = detailSpatialSpec,
                        ) + fadeOut(animationSpec = detailEffectsSpec))
                    }
                    put(NavDisplay.PredictivePopTransitionKey) {
                        (scaleIn(
                            initialScale = 0.96f,
                            animationSpec = detailSpatialSpec,
                        ) + fadeIn(
                            animationSpec = detailEffectsSpec,
                        )) togetherWith (scaleOut(
                            targetScale = 0.92f,
                            animationSpec = detailSpatialSpec,
                        ) + fadeOut(animationSpec = detailEffectsSpec))
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
                )
            }

                entry<ChatRoute>(
                    metadata = verticalSlideMetadata(chatSlideSpec),
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

                entry<BriefingRoute>(
                    metadata = verticalSlideMetadata(chatSlideSpec),
                ) { route ->
                    val briefingViewModel: StashBriefingViewModel = viewModel(
                        key = "briefing-${route.itemIds.sorted().joinToString(",")}",
                        factory = StashBriefingViewModel.Factory(
                            repository = repository,
                            itemIds = route.itemIds,
                            topic = route.topicTitle,
                        ),
                    )
                    StashBriefingScreen(
                        viewModel = briefingViewModel,
                        onBack = { backStack.removeLastOrNull() },
                        onOpenItem = { item -> backStack.addDetail(DetailRoute(item.id)) },
                    )
                }

                entry<SettingsRoute>(
                    metadata = verticalSlideMetadata(chatSlideSpec),
                ) { _ ->
                    StashSettingsScreen(
                        viewModel = feedViewModel,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
        )
}

private fun verticalSlideMetadata(
    animationSpec: androidx.compose.animation.core.FiniteAnimationSpec<IntOffset>,
): Map<String, Any> = metadata {
    put(NavDisplay.TransitionKey) {
        slideInVertically(
            initialOffsetY = { it },
            animationSpec = animationSpec,
        ) togetherWith ExitTransition.KeepUntilTransitionsFinished
    }
    put(NavDisplay.PopTransitionKey) {
        EnterTransition.None togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = animationSpec,
        )
    }
    put(NavDisplay.PredictivePopTransitionKey) {
        EnterTransition.None togetherWith slideOutVertically(
            targetOffsetY = { it },
            animationSpec = animationSpec,
        )
    }
}
