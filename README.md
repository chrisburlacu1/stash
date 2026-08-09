# Stash - On-Device Privacy-First Second Brain

Stash is a native Android application built with Jetpack Compose, Material 3 Expressive, and Android 14+ AICore capabilities for saving, summarizing, and searching links, repositories, articles, and tweets.

## Architecture & Design System

- **UI Framework**: Pure Jetpack Compose with Material 3 Expressive components.
- **Theme**: `StashTheme` utilizing `MaterialTheme` with 35-token Light/Dark ColorSchemes (`Color.kt`), custom `ExpressiveShapes` (`extraSmall` to `extraLarge`), and dynamic typography scale (`Type.kt`).
- **Layout & Motion**:
  - `NavigableListDetailPaneScaffold` (M3 Adaptive) for responsive single-column (compact) and dual-pane (medium/expanded) layouts.
  - `SharedTransitionLayout` with `sharedElement` transitions between feed rows and detail pane.
  - Low-bouncy spring physics (`Spring.DampingRatioLowBouncy`) on filter chip selections and floating search capsule morphing.
- **Target SDK**: 36 (Android 16), Min SDK 34 (Android 14).

## Project Structure & Key Files

- [Color.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/theme/Color.kt) — 35-token light & dark color palettes
- [Theme.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/theme/Theme.kt) — Expressive shapes scale & StashTheme setup
- [Type.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/theme/Type.kt) — Material 3 Expressive typography hierarchy
- [StashItem.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/models/StashItem.kt) — StashItem data class
- [StashListRow.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/components/StashListRow.kt) — Item list row composable with category dots
- [FloatingSearchCapsule.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/components/FloatingSearchCapsule.kt) — Bottom floating search pill & FAB
- [StashMainFeedScreen.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/feed/StashMainFeedScreen.kt) — Main feed screen with filter chips & search overlay
- [StashAdaptiveLayout.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/adaptive/StashAdaptiveLayout.kt) — List-detail canonical adaptive layout
