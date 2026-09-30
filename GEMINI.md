# GEMINI.md - Stash Project Context

## Project Overview
Stash is a privacy-first "second brain" Android application built with Jetpack Compose, Material 3 Expressive (`1.5.0-alpha28`), Navigation 3 (`androidx.navigation3`), Room 3 Database with bundled SQLite, ML Kit GenAI (Gemini Nano on-device), and Android System Share Sheet integration.

## Project Structure & Architecture
Single-module architecture under package `dev.cburlacu.stash`:
- **`StashApplication.kt`**: Process-scoped application container holding dependencies (`StashDatabase`, `RoomStashRepository`, `StashSettings`, `LibrarianAgent`).
- **`data/extract/`**: `WebPageExtractor.kt` (meta tags, stripped HTML), `TwitterExtractor.kt` (high-res media/video thumbnails), `UrlExtractor.kt` (URL regex extraction, repo fallback title derivation).
- **`data/image/`**: `ImageAnalyzer.kt` (Google Material Color Utilities HCT quantization, crop bias), `ImageFallbackRenderer.kt` (native Obsidian X fallback banner).
- **`data/prompt/`**: `PromptContextBuilders.kt` (clean prompt context formatting for single items and multi-item briefings).
- **`data/local/`**: `StashDatabase.kt` (Room SQLite + FTS5 search), `RoomStashRepository.kt` (local data orchestration), `TagNormalizer.kt` (morphological lemmatizer and canonicalizer), `StashSettings.kt` (DataStore preferences).
- **`ai/`**: `OnDeviceSummarizer.kt` (summarizer contract and `@Generable` structured models), `GeminiNanoSummarizer.kt` (ML Kit GenAI Prompt API implementation), `LibrarianAgent.kt` (on-device topic curation agent), `Prompts.kt` (isolated prompt templates), `CleanTitle.kt` (title sanitization).
- **`ui/`**:
  - **`adaptive/`**: `StashAdaptiveLayout.kt` (Navigation 3 adaptive single-pane / dual-pane list-detail layout).
  - **`feed/`**: `StashMainFeedScreen.kt`, `StashFeedViewModel.kt`, `FeedContent.kt`, `FeedGallery.kt`, `FeedTopBar.kt`, `SortBottomSheet.kt`, `AddUrlDialog.kt`.
  - **`detail/`**: `StashDetailScreen.kt`, `StashDetailPlaceholder.kt`.
  - **`briefing/`**: `StashBriefingScreen.kt`, `StashBriefingViewModel.kt`, `BriefingTimeline.kt`, `BriefingChatSheet.kt`.
  - **`chat/`**: `StashChatScreen.kt`, `StashChatViewModel.kt`.
  - **`settings/`**: `StashSettingsScreen.kt`, `StashSettingsViewModel.kt`.
  - **`components/`**: `StashLogo.kt`, `GeminiMark.kt`, `TimelineRail.kt`, `CardHeaderImage.kt`, `CardSwipePanels.kt`, `ImageBitmapCache.kt`.
  - **`theme/`**: `Color.kt`, `Theme.kt`, `Type.kt` (typography hierarchy and `FeedTextStyles`), `CardSeed.kt` (dynamic tinting), `CategoryStyle.kt` (semantic badges).
  - **`ExternalIntents.kt`**: Intent utilities for launching browser and Gemini shortcuts.

## Key Design Patterns & Implementation Rules

1. **Material 3 Expressive Search & Navigation**:
   - **Search Integration**: Google Keep-style collapsed search pill with start Stash brand logo (`26.dp x 24.dp`, taps scroll to top), trailing View Switcher (List / Staggered Gallery), Sort icon, and Settings action. Expands smoothly to full screen with automatic keyboard focus and real-time Room FTS5 debounced results.
   - **Single FAB**: Bottom-end centered M3 `FloatingActionButton` (`CircleShape`) for Add URL dialog.
   - **Predictive Back**: Active Android 14+ Predictive Back callback registration across all navigation transitions.

2. **Dynamic Card Seeding & Content Types**:
   - `category` represents semantic content formats ("Article", "Blog", "Documentation", "Repo", "Video", "Discussion", "Website") defined in `CategoryStyle.kt`. Hardcoded category palettes are purged.
   - Card glows, edge lighting, and accents are dynamically derived via Google Material Color Utilities (`QuantizerCelebi` + `Score.score()`) from header images in `CardSeed.kt`, with HCT filtering (`tone in 10.0..92.0 && chroma >= 8.0`) and category fallback seeds for monochrome images.

3. **Smart Extraction & Gemini Nano Prompt Capping**:
   - Scrapes OpenGraph/Meta tags first, strips noise tags (`<header>`, `<footer>`, `<nav>`, `<script>`, `<style>`), and caps AI prompt payload at 3,000 characters (~600 tokens) for fast on-device inference (1–3 seconds).
   - Title sanitization in `CleanTitle.kt` strips verbose repository taglines and branding (e.g. `"GitHub - owner/repo: description..."` $\rightarrow$ `"owner/repo"`).
   - URL additions save an immediate placeholder entity to Room so the feed updates instantly with a progress capsule.

4. **Emissive Chat Aura & Fluid Motion**:
   - `auraResolve` holds multi-hue churn for 650ms to ride the navigation transition, then settles on `slowSpatialSpec` motion scheme.
   - Residual warp and Lissajous pool drift keep the settled aura breathing on an always-on frame clock.
   - Streaming assistant message bubbles feature an animated horizontal gradient wash that locks still once generation completes.

5. **Full Page Content for Offline Chat**:
   - `StashEntity.content` stores the scraped article body at save time; feeds up to 4,000 characters to Gemini Nano for item chat. Pages are never re-fetched, preserving offline capability and privacy.

6. **Animated Edge Blur Effect**:
   - `CardEdgeBlurEffect` uses `androidx.compose.ui.graphics.BlurEffect` (`TileMode.Clamp`) on hardware-accelerated Android 14+ (`minSdk = 34`) with rotating sweep gradients clipped to `RoundedCornerShape(16.dp)`, bleeding inward without obscuring central text.

7. **Canonical Tagging & On-Device Librarian Agent**:
   - 3-tier canonical tagging extracts domain, topic, and concept nouns.
   - `TagNormalizer.kt` provides morphological lemmatization (plural reduction, invariant noun protection, acronym preservation).
   - `LibrarianAgent.kt` runs on-device inference to curate and snap items to library topics.

8. **Navigation 3 List-Detail Architecture**:
   - Managed via `rememberListDetailSceneStrategy<NavKey>` with `currentWindowAdaptiveInfoV2()`.
   - **Phones**: Elevation scale transitions (`scaleIn` from 0.92f + `fadeIn` on enter, `scaleOut` to 0.92f + `fadeOut` on exit) driven by `MaterialTheme.motionScheme.fastSpatialSpec()`.
   - **Foldables & Tablets**: Dual-pane side-by-side feed and detail layout.

9. **Topic Briefings ("Catch Me Up" & Compare)**:
   - **Topic Catch-Up**: Filtering by a topic with $\ge 2$ items presents an elevated catch-up briefing card.
   - **Multi-Item Compare**: Long-press selection mode provides a batch Compare & Brief action.
   - **`BriefingTimeline.kt`**: Connected Intelligence Rail with S-curve Bezier bends, forward-traveling energy beam, and scroll-driven focal magnification across 4 sections (The Big Picture, Key Takeaways, Comparisons & Trade-offs, The Bottom Line).

10. **High-Res Twitter / X Media Pipeline**:
    - Queries FxTwitter/VxTwitter endpoints to extract full-res photos (`?name=large`), 1080p/4K video thumbnails, and 400x400 avatars.
    - Generates a native obsidian vector X fallback canvas on disk for private, deleted, or rate-limited tweets.
