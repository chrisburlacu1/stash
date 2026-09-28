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

Single-module app with clean, decoupled data and UI layers (`dev.cburlacu.stash`):

```
StashApplication (container) → RoomStashRepository → StashAdaptiveLayout → Screen ViewModels (Feed / Settings / Briefing / Chat)
```

- **`StashApplication.kt`** — Application container holding singletons (`StashDatabase`, `RoomStashRepository`, `LibrarianAgent`) without static state leaks.
- **`data/StashRepository.kt`** — repository interface (`observe`, `observeTags`, `observeItem`, `addUrl`, `getModelVersion`).
- **`data/local/RoomStashRepository.kt`** — orchestrates storage and background jobs. Delegates extraction, image analysis, and prompts to focused modules.
- **`data/extract/`** — `TwitterExtractor.kt` (FxTwitter/VxTwitter API, video thumbnails, banners) and `WebPageExtractor.kt` (HTML stripping, meta tags).
- **`data/image/`** — `ImageAnalyzer.kt` (perceptual color quantization and crop bias analysis) and `ImageFallbackRenderer.kt` (native Obsidian X banner generation).
- **`data/prompt/`** — `PromptContextBuilders.kt` (clean prompt formatting for single-item and multi-item briefings).
- **`data/local/TagNormalizer.kt`** — pure Kotlin morphological lemmatizer and canonicalizer for open-domain tag standardization, invariant noun protection, and noise filtering.
- **`data/local/StashDatabase.kt`** — Room database. `stash_items` is the source of truth, paired with an FTS5 `stash_search` virtual table kept in sync on every write.
- **`ai/`** — `OnDeviceSummarizer.kt` (interface & models), `GeminiNanoSummarizer.kt` (concrete ML Kit GenAI implementation), `Prompts.kt` (isolated prompt templates), `CleanTitle.kt` (domain category mapping and title sanitization), and `LibrarianAgent.kt` (on-device topic curation agent).
- **`ui/feed/StashFeedViewModel.kt`** — handles feed item stream, debounced search, topic tag filtering, sort order, and URL additions.
- **`ui/settings/StashSettingsViewModel.kt`** — dedicated ViewModel for theme preference, AI model options, summary effort, and manual librarian topic curation.
- **`ui/adaptive/StashAdaptiveLayout.kt`** — Navigation 3 (`NavDisplay` + `ListDetailSceneStrategy`) for adaptive single-pane / list-detail layouts, with Material 3 Expressive elevation scale transitions between the feed and detail pane.
- **Share intent entry point** — `MainActivity.handleShareIntent` handles `ACTION_SEND text/plain` from the Android share sheet.

### Theme & Design System

- **Framework**: Jetpack Compose with Material 3 Expressive (`MaterialExpressiveTheme`, `MotionScheme.expressive()`).
- **`ui/theme/Color.kt`** — 35-token light/dark `ColorScheme`.
- **`ui/theme/Theme.kt`** — custom `ExpressiveShapes` scale (`extraSmall`–`extraLarge`) and `StashTheme` setup.
- **`ui/theme/Type.kt`** — Material 3 Expressive typography hierarchy.
- **`ui/theme/CardSeed.kt`** — Dynamic card seed extraction and tinting powered by Google Material Color Utilities HCT quantization.
- **`ui/theme/CategoryStyle.kt`** — Semantic badges (labels and iconography) for content categories (Article, Blog, Tweet, GitHub Repo, Video, Discussion, Documentation, Website). Hardcoded category color palettes are purged in favor of dynamic Material 3 tokens.

Experimental Compose APIs in use (opted in at the module level): `ExperimentalMaterial3ExpressiveApi`, `ExperimentalMaterial3AdaptiveApi`.

## Privacy & Security

All summarization and extraction happens on-device — no network calls send page content or user data off-device. See `CLAUDE.md` for contributor guidance on the data pipeline and security review expectations.
