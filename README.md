# Stash — On-Device, Privacy-First Second Brain

Stash is a native Android app for saving, summarizing, and searching links — articles, GitHub repos, tweets, videos, and more. Every step of the pipeline (fetching, summarization, search) runs on-device using Jetpack Compose, Room, and on-device Gemini Nano (ML Kit GenAI / AICore). No page content or user data ever leaves the device.

## Requirements

- Android Studio (latest stable)
- A device or emulator running **Android 14+ (API 34)** — required for AICore/Gemini Nano
- Target SDK 36 (Android 16), Min SDK 34

## Building & Running

```bash
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Build and install on a connected device/emulator
./gradlew test                 # Run local JVM unit tests
./gradlew connectedAndroidTest # Run instrumentation tests on a device/emulator
```

Use `gradlew.bat` on Windows with the same task names. On-device summarization requires a real device/emulator with AICore support — it can't be exercised in a plain JVM unit test.

## Architecture

Single-module app, no DI framework — dependencies are constructed by hand in `MainActivity` and passed down:

```
MainActivity → RoomStashRepository(StashDao, GeminiNanoSummarizer) → StashAdaptiveLayout → StashFeedViewModel → StashMainFeedScreen
```

- **`data/StashRepository.kt`** — repository interface (`observe`, `observeTags`, `observeItem`, `addUrl`, `getModelVersion`).
- **`data/local/RoomStashRepository.kt`** — the real implementation. `addUrl` normalizes the URL, inserts a placeholder row immediately, fetches and strips the page HTML, and (if Gemini Nano is available) summarizes it into a structured `{title, summary, category, tags}` record; otherwise falls back to a truncated extract.
- **`data/local/StashDatabase.kt`** — Room database. `stash_items` is the source of truth, paired with an FTS5 `stash_search` virtual table kept in sync on every write. Tags are stored as a delimited string column, not a join table.
- **`ai/OnDeviceSummarizer.kt`** — `GeminiNanoSummarizer`, wrapping ML Kit GenAI's on-device `Generation` API.
- **`ui/feed/StashFeedViewModel.kt`** — combines search query, tag filter, and repository flows into a single `FeedUiState`.
- **`ui/adaptive/StashAdaptiveLayout.kt`** — Navigation 3 (`NavDisplay` + `ListDetailSceneStrategy`) for adaptive single-pane / list-detail layouts, with `SharedTransitionLayout` for shared-element transitions between feed rows and the detail pane.
- **Share intent entry point** — `MainActivity.handleShareIntent` handles `ACTION_SEND text/plain` from the Android share sheet, the primary way URLs get added besides the in-app Add URL dialog.

### Theme & Design System

- **Framework**: Jetpack Compose with Material 3 Expressive (`MaterialExpressiveTheme`, `MotionScheme.expressive()`).
- **`ui/theme/Color.kt`** — 35-token light/dark `ColorScheme`.
- **`ui/theme/Theme.kt`** — custom `ExpressiveShapes` scale (`extraSmall`–`extraLarge`) and `StashTheme` setup.
- **`ui/theme/Type.kt`** — Material 3 Expressive typography hierarchy.
- **`ui/theme/CategoryStyle.kt`** — color-coded styling for content categories (Article, Blog, Tweet, GitHub Repo, Video, Discussion, Documentation, Website), distinct from freeform user tags.

Experimental Compose APIs in use (opted in at the module level): `ExperimentalMaterial3ExpressiveApi`, `ExperimentalMaterial3AdaptiveApi`, `ExperimentalSharedTransitionApi`.

## Privacy & Security

All summarization and extraction happens on-device — no network calls send page content or user data off-device. See `CLAUDE.md` for contributor guidance on the data pipeline and security review expectations.
