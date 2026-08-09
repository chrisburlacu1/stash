package com.example.stash.ui.adaptive

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import com.example.stash.data.StashRepository
import com.example.stash.data.StashSettings
import com.example.stash.models.StashItem
import com.example.stash.models.relativeSavedLabel
import com.example.stash.ui.feed.StashFeedViewModel
import com.example.stash.ui.feed.StashMainFeedScreen
import com.example.stash.ui.theme.categoryStyle
import kotlinx.serialization.Serializable

@Serializable data object FeedRoute : NavKey
@Serializable data class DetailRoute(val itemId: String) : NavKey

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun StashAdaptiveLayout(repository: StashRepository) {
    val backStack = rememberNavBackStack(FeedRoute)
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val directive = remember(adaptiveInfo) {
        calculatePaneScaffoldDirective(adaptiveInfo).copy(horizontalPartitionSpacerSize = 0.dp)
    }
    val strategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    val appContext = LocalContext.current.applicationContext
    val settings = remember(appContext) { StashSettings(appContext) }
    val feedViewModel: StashFeedViewModel =
        viewModel(factory = StashFeedViewModel.Factory(repository, settings))

    // The back button and the predictive-back swipe would otherwise animate differently:
    // NavDisplay's default pop is a plain cross-fade, but its default *predictive* pop adds a
    // scaleOut(0.7f) with no animationSpec, so that half fell back to a stock spring while its
    // paired fade ran at stiffness 1600 — one transition on two curves, which the finger-driven
    // gesture exposed as a skip. Both specs are now the same motionScheme-backed cross-fade.
    val popFade = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val crossFade: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
        fadeIn(animationSpec = popFade) togetherWith fadeOut(animationSpec = popFade)
    }

    SharedTransitionLayout {
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(strategy),
            // Required with a scene strategy: NavDisplay renders each entry in at most one scene,
            // and without this the entry jumps when the scene rendering it changes.
            sharedTransitionScope = this,
            popTransitionSpec = crossFade,
            predictivePopTransitionSpec = { crossFade() },
            entryProvider = entryProvider {
                entry<FeedRoute>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { EmptyDetailPane() },
                    ),
                ) {
                    StashMainFeedScreen(
                        viewModel = feedViewModel,
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                        onItemClick = { item ->
                            backStack.removeAll { it is DetailRoute }
                            backStack.add(DetailRoute(item.id))
                        },
                    )
                }
                entry<DetailRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
                    val item by repository.observeItem(route.itemId)
                        .collectAsStateWithLifecycle(initialValue = null)
                    item?.let {
                        DetailPaneContent(
                            item = it,
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                            onBackClick = { backStack.removeLastOrNull() },
                            onDelete = {
                                // Leave the detail pane first: deleting while it is showing
                                // would blank the pane before the back stack unwinds.
                                backStack.removeLastOrNull()
                                feedViewModel.delete(route.itemId)
                            },
                        )
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun DetailPaneContent(
    item: StashItem,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onBackClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val style = categoryStyle(item.category, isSystemInDarkTheme())
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val openWebpage = remember(item.url) {
        {
            val uri = Uri.parse(if (item.url.startsWith("http")) item.url else "https://${item.url}")
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = "Delete",
                        )
                    }
                },
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { openWebpage() },
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Open ${item.domain}",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Hero Title Container Card. Colors come from the item's category so the accent
            // carries over from the feed row instead of reverting to the theme's primary.
            Surface(
                color = style.container,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        with(sharedTransitionScope) {
                            Surface(
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                                shape = CircleShape,
                                modifier = Modifier.sharedElement(
                                    rememberSharedContentState(key = "category-dot-${item.id}"),
                                    animatedVisibilityScope = animatedVisibilityScope,
                                )
                            ) {
                                Icon(
                                    imageVector = style.icon,
                                    contentDescription = item.category,
                                    tint = style.color,
                                    modifier = Modifier
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = style.color
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    with(sharedTransitionScope) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.sharedBounds(
                                rememberSharedContentState(key = "title-${item.id}"),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            with(sharedTransitionScope) {
                                Text(
                                    text = item.domain,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .sharedElement(
                                            rememberSharedContentState(key = "domain-${item.id}"),
                                            animatedVisibilityScope = animatedVisibilityScope,
                                        ),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Text(text = "•", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = relativeSavedLabel(item.savedAtEpochMillis, System.currentTimeMillis()),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            // AI Context Summary Card
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "KEY POINTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = style.color,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // The summarizer stores key points newline-separated. Older rows hold a single
                    // prose paragraph, which falls through this as one "bullet" — so both render.
                    val points = item.summary.split('\n').map(String::trim).filter(String::isNotEmpty)
                    points.forEach { point ->
                        Row(modifier = Modifier.padding(bottom = 8.dp)) {
                            if (points.size > 1) {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = style.color,
                                    modifier = Modifier.width(18.dp),
                                )
                            }
                            Text(
                                text = point,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                            )
                        }
                    }
                }
            }

            // Tags Flow Section
            if (item.tags.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "TAGS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item.tags.forEach { tag ->
                                AssistChip(
                                    onClick = {},
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete from Stash?") },
            text = { Text("\"${item.title}\" will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun EmptyDetailPane() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Choose something from your stash",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
