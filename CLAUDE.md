# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Stash is a privacy-first, on-device "second brain" Android app for saving, summarizing, and searching links (articles, GitHub repos, tweets, videos, etc.). It uses Jetpack Compose, Material 3 Expressive, Room, and on-device Gemini Nano (ML Kit GenAI / AICore) for summarization — no data leaves the device.

Single-module app. Target SDK 36 (Android 16), min SDK 34 (Android 14, required for AICore).

## Build & Test Commands

Use the Gradle wrapper from the repo root (`gradlew.bat` on Windows, same task names):

```
./gradlew assembleDebug        # Build debug APK
./gradlew installDebug         # Build and install on a connected device/emulator
./gradlew test                 # Run local JVM unit tests (app/src/test)
./gradlew connectedAndroidTest # Run instrumentation tests on a device/emulator (app/src/androidTest)
```

To run a single test class/method, use the standard Gradle test filter, e.g.:
```
./gradlew test --tests "com.example.stash.SomeClassTest.someMethod"
```

There is no linter/formatter configured. Match existing Kotlin style (4-space indent) and clean up imports manually.

Note: on-device summarization requires a real device/emulator with AICore support (Android 14+); it cannot be exercised in a plain JVM unit test.

## Architecture

**Data flow:** `MainActivity` constructs a single `RoomStashRepository` (implements `StashRepository`) wired with a `StashDao` (Room) and a `GeminiNanoSummarizer` (implements `OnDeviceSummarizer`), then hands it down through `StashAdaptiveLayout` → `StashFeedViewModel` → `StashMainFeedScreen`. There is no DI framework — dependencies are constructed and passed by hand.

- **`data/StashRepository.kt`** — the interface (`observe`, `observeTags`, `observeItem`, `addUrl`, `getModelVersion`). Swap `RoomStashRepository` for a fake here in tests.
- **`data/local/RoomStashRepository.kt`** — the real implementation. `addUrl` is the core pipeline:
  1. Normalize the URL, derive a domain-based fallback title, insert a `Pending`/`Summarizing` row immediately so the feed updates instantly.
  2. Fetch the page HTML directly (`HttpURLConnection`, no external HTML parser lib), regex-extract OpenGraph/meta tags, strip `<script>/<style>/<header>/<footer>/<nav>`, collapse to plain text, cap at 2,500 chars.
  3. If Gemini Nano is available, call `summarizer.organize(...)` with a prompt capped at 3,000 chars for a JSON `{title, summary, category, tags}` response; otherwise fall back to a truncated extract.
  4. Upsert the final row with `aiState` set to `Ready`/`Failed`/`Unavailable`.
- **`data/local/StashDatabase.kt`** — Room database. `stash_items` is the source of truth; `stash_search` is a parallel FTS5 virtual table (unicode61, prefix `2 3 4`) kept in sync via the `@Transaction upsert()` helper — never write to one table without the other. Tags are stored as a single `" | "`-delimited string column (`TAG_SEPARATOR` in `RoomStashRepository.kt`), not a join table; tag-filter queries use `LIKE` patterns against that delimited string. Search ranks with `bm25(...)` weighted per-column. Migrations 1→3 and 2→3 rebuild the FTS table; bump `version` and add a migration when changing entity shape.
- **`ai/OnDeviceSummarizer.kt`** — `GeminiNanoSummarizer` wraps `com.google.mlkit.genai.prompt.Generation`. Category is a closed enum-like string (must be one of: Article, Blog, Tweet, GitHub Repo, Video, Discussion, Documentation, Website) enforced only by prompt instruction, not validated in code. `getBaseModelName()` surfaces the active model version (e.g. `nano-v2`) for display in the top bar.
- **`ui/feed/StashFeedViewModel.kt`** — combines `query` + `tag` flows via `flatMapLatest` into `repository.observe(...)`, plus `observeTags()` and `modelVersion`, into one `FeedUiState`. Standard `ViewModelProvider.Factory` (no Hilt/Koin).
- **`ui/adaptive/StashAdaptiveLayout.kt`** — navigation and pane structure. Uses Navigation 3 (`androidx.navigation3`) with `NavDisplay` + `ListDetailSceneStrategy` (M3 Adaptive) for responsive single-pane (compact) / list-detail (medium+) layouts, and `SharedTransitionLayout` for shared-element transitions between feed rows and the detail pane. Routes are `@Serializable` `NavKey`s (`FeedRoute`, `DetailRoute(itemId)`).
- **Share intent entry point:** `MainActivity.handleShareIntent` handles `ACTION_SEND text/plain` (Android share sheet) in both `onCreate` and `onNewIntent`, calling `repository.addUrl` directly — this is the primary way URLs get added besides the in-app Add URL FAB/dialog.

**Theme system:** `StashTheme` (`ui/theme/Theme.kt`) sets up `MaterialExpressiveTheme` with `MotionScheme.expressive()`, custom `ExpressiveShapes` (extraSmall–extraLarge), and a 35-token light/dark `ColorScheme` (`Color.kt`). Always reuse existing theme tokens/shapes — do not hardcode colors, spacing, or corner radii. `category` (content type, fixed set above) drives color-coded dots/badges in list rows and is distinct from user-extracted `tags` (freeform, used for filter chips).

Experimental Compose APIs in use (opted in at the module level in `app/build.gradle.kts`): `ExperimentalMaterial3ExpressiveApi`, `ExperimentalMaterial3AdaptiveApi`, `ExperimentalSharedTransitionApi`.

## Reference Documentation

External docs for the libraries this project depends on. Prefer these over guessing at API shapes.

**ML Kit GenAI (on-device Gemini Nano)** — <https://developers.google.com/ml-kit/genai>
Six APIs are offered, each with its own artifact and per-API page at `developers.google.com/ml-kit/genai/<api>/android`:
- **Prompt** (`genai-prompt`) — free-form text/multimodal prompting. **This is what we use** (`Generation.getClient()` in `ai/OnDeviceSummarizer.kt`). Beta; no SLA, may break compatibility. Supports structured output (Alpha), system instructions (Beta), prefix caching (Experimental), thinking mode (Beta).
- **Summarization** (`genai-summarization`) — purpose-built article/conversation summarizer. Constrained: `InputType` ARTICLE/CONVERSATION, `OutputType` ONE/TWO/THREE_BULLET, English/Japanese/Korean only, <4,000 tokens (~3,000 English words), ARTICLE wants >400 chars. Has `setLongInputAutoTruncationEnabled()`.
- **Proofreading** (`genai-proofreading`), **Rewriting** (`genai-rewriting`) — short chat messages.
- **Image Description** (`genai-image-description`), **Speech Recognition** (`genai-speech-recognition`).

Cross-API notes that apply to our Prompt usage:
- Feature status is `UNAVAILABLE` / `DOWNLOADABLE` / `DOWNLOADING` / `AVAILABLE`. If `downloadFeature()` is never called, **the first inference request triggers the model download** — so a first-run inference can be very slow, and `DOWNLOADABLE`/`DOWNLOADING` do not mean ready.
- Clients hold native resources and expose `close()`; release when no longer needed (docs suggest `onCleared()`/`onDestroy()`).
- The ML Kit GenAI Additional Terms of Service apply; we are responsible for output safety.
- Inference is local, so latency depends on device hardware; AICore has no direct internet access (model downloads route through Private Compute Services).

**Navigation 3** — use the `navigation-3` skill, and `android docs search`/`android docs fetch` (the `android` CLI) for authoritative Android KB pages, e.g. `kb://android/guide/navigation/navigation-3/animate-destinations`. Note the Android KB does **not** index the `developers.google.com/ml-kit/*` pages — fetch those from the web.

## Security & Data

Do not commit `local.properties`, signing credentials, API keys, or `build/` output. All summarization/extraction happens on-device — do not introduce network calls that send page content or user data off-device. Review manifest, backup, and data-extraction (`android:allowBackup`, etc.) changes carefully given the app's privacy-first positioning.
