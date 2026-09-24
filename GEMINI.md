# GEMINI.md - Stash Project Context

## Project Overview
Stash is a privacy-first "second brain" Android application built with Jetpack Compose, Material 3 Expressive (`1.5.0-alpha25`), Navigation 3 (`androidx.navigation3`), Room Database, ML Kit GenAI (Gemini Nano on-device), and Android System Share Sheet integration.

## Key Design Patterns & Implementation Rules
1. **Material 3 Expressive System & Top App Bar Search**:
   - Theme Entry Point: `MaterialExpressiveTheme(colorScheme = colorScheme, shapes = ExpressiveShapes, typography = Typography, content = content, motionScheme = MotionScheme.expressive())`.
   - **`AppBarWithSearch` & `ExpandedFullScreenContainedSearchBar`**: Stock Material 3 Expressive search integration. Collapsed state renders the Stash brand logo foreground vector ([StashLogo.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/components/StashLogo.kt)) as `navigationIcon` at the start with true 47:43 launcher icon proportions (`26.dp x 24.dp`, tappable to scroll to top with haptic feedback), a centered pill-shaped search input field, and `[ ⚙ Settings ]` in `actions` at the end. Tapping expands smoothly to full screen with automatic keyboard focus, back arrow, clear button, and real-time Room FTS results.
   - **Top Bar Touch Interception & Scroll-to-Top**: Root top bar container wrapped in `Surface` with `pointerInput(Unit) { detectTapGestures { ... } }` to prevent pointer events from falling through to underlying scrolled cards in the `LazyColumn`, while routing taps near the top-left logo to `onScrollToTop()`.
   - **Feed-Level Controls Below Top Bar**: The Sort order dropdown (`SortOrder.Newest`, `SortOrder.Oldest`, `SortOrder.UnreadFirst`) is embedded as the leading chip (`[ ≡ Newest first ▼ ]`) directly in the horizontal filter chips row alongside topic tags.
   - **Standard M3 FAB**: BottomEnd M3 `FloatingActionButton` (`CircleShape`, `primary` background) for triggering the Add URL dialog.
   - Detail View Aesthetics: Rich M3 Expressive container cards (`primaryContainer` hero card, `surfaceContainerHigh` AI summary card, `surfaceContainerLow` tags card).
2. **Content Types vs Tags & Refined Category Palette**:
   - `category` represents the strict Content Type ("Article", "Documentation", "Repo", "Video", "Discussion") with distinct theme color coding:
     - 📘 **Article**: Sapphire Blue (`#1B6BB5` / `#8FC2F5`)
     - 📖 **Documentation**: Royal Violet (`#6D4BB8` / `#C0AAF5`)
     - 💻 **Repo**: Emerald Green (`#0D8A5B` / `#4ADE80`)
     - 🎬 **Video**: Crimson (`#C0392E` / `#F5A199`)
     - 💬 **Discussion**: Coral Rose (`#D83A6F` / `#FF85A1`)
   - Category color is subtly applied to card metadata bylines (relative timestamp and domain link) with unified `FontWeight.Medium`, keeping card body and summary text clean and neutral (`onSurfaceVariant`).
   - Filter chips at the top of the feed dynamically filter by topic `tags` extracted from SQLite comma-separated tag queries.
3. **Smart HTML Extraction & Fast AI Summarization**:
   - Scrapes OpenGraph/Meta tags first, strips noise tags (`<header>`, `<footer>`, `<nav>`, `<script>`, `<style>`), and caps AI prompt payload at 3,000 characters (~600 tokens) for 4x-5x faster Gemini Nano inference (1-3 seconds).
   - **Title Sanitization (`cleanTitle`)**: Automatically cleans extracted and AI-generated titles to strip verbose repository taglines and site branding (e.g. GitHub repos: `"GitHub - owner/repo: description..."` -> `"owner/repo"`, issue/PR suffixes, GitLab, YouTube, Hacker News branding).
   - URL additions save an immediate `Pending`/`Summarizing` entity to Room with smart repo fallback title (`owner/repo`) so the feed updates instantly with a `CircularProgressIndicator`.
4. **Native Gemini Nano Model Version**:
   - Calls the suspend method `model.getBaseModelName()` from `com.google.mlkit.genai.prompt.GenerativeModel` to dynamically display the active device model version (e.g., `nano-v2`) in the top bar.
5. **Emissive Chat Aura & Fluid Motion**:
   - **Entrance Churn Hold**: `auraResolve` holds full multi-hue churn for `ENTRANCE_CHURN_MILLIS` (650ms) to ride the navigation transition, then settles on `slowSpatialSpec`. Both ends of the animation are MotionScheme specs — never a hand-picked `tween`, which drifts against the route's slide-up spring.
   - **Living Resting Glow**: Residual warp (`mix(0.020, 0.075, calm)`) and Lissajous pool drift (`0.020 * sin(uTime * 0.11)`, `0.025 * cos(uTime * 0.07)`) keep the settled aura breathing, powered by an always-on frame clock (`rememberMeshClock(running = true)`) for the life of the screen. The feed's card meshes deliberately still go inert.
   - **Legible Churn Spectrum**: Vertical drift band (`sy` = `0.84 ± 0.14`) and mask reach (`0.34` → `0.84` on `calm`) make ≥4 distinct hues visible during churn. The opacity ceiling stays at `0.46`; legibility is fixed geometrically, never by raising alpha.
   - **Streaming Bubble Wash Motion**: Assistant message bubbles feature a horizontal gradient wash that breathes via sine offset (`drawBehind` phase) while streaming, locking still once complete.
   - **Header Layout Rhythm**: Top-aligned header row with 10dp padding above column and 10dp spacing between eyebrow and title for proper visual air.
6. **Full Page Content for Chat**:
   - `StashEntity.content` (migration `6→7`) stores the scraped article body at save time; `itemChatContext` feeds up to 4,000 chars of it to Gemini Nano. The page is never re-fetched — chat stays offline-capable and leaks no reading activity.
7. **Animated BlurEffect Inward Edge Glow & Expressive Progress Indicator**:
   - **`CardEdgeBlurEffect`**: Uses `androidx.compose.ui.graphics.BlurEffect` (`TileMode.Clamp`) on hardware-accelerated Android 14+ (`minSdk = 34`) with rotating sweep gradients along the card's perimeter stroke using Stash's theme-aware category hues. Clipped to the card silhouette (`RoundedCornerShape(16.dp)`), the light bleeds with high intensity and steep falloff (~8dp) inward from the outer edges into the card surface, pulsing naturally while keeping the central card body and text clean and legible.
   - **M3 Expressive `CircularWavyProgressIndicator` Capsule**: In `StashCardRow.kt`, the summarizing status row renders a dedicated `surfaceContainerHigh` capsule containing a rotating `CircularWavyProgressIndicator` (16dp, category-tinted) alongside `"Summarizing with on-device AI…"`.

8. **Core Feed Polish & Organization**:
   - **Frequency-Sorted Tag Filter Chips**: `observeTags()` groups and counts occurrences across all saved items, sorting chips by highest count descending with display counts (`"${tag.name} (${tag.count})"`).
    - **Open-Domain Canonical Tagging & Grouping**:
      - **3-Tier AI Guidance**: On-device Gemini Nano extracts 2 to 4 canonical tags spanning: 1) Broad field/domain (*Film & Cinema*, *Culinary*, *Finance*), 2) Core subject/theme (*Screenwriting*, *Fermentation*, *AI Agent*), and 3) Specific concept/tool (*Scene Transitions*, *Sourdough Starter*, *Claude Code*). Enforces singular nouns and excludes media noise words (*Podcast*, *Episode*, *Article*, *Video*, *Newsletter*).
      - **Morphological Lemmatizer (`TagNormalizer`)**: Pure Kotlin normalizer with English plural lemmatization (`-ies` $\rightarrow$ `-y`, `-ves` $\rightarrow$ `-f`/`-fe`, sibilants, silent-e drops), invariant noun protection (*iOS*, *DevOps*, *Economics*, *Physics*, *Series*, *Kubernetes*, *Node.js*), canonical acronym preservation (*AI*, *ML*, *LLM*, *CLI*, *API*, *KMP*), and format stripping.
      - **Stem-Aware Reconciliation & Startup Backfill**: `RoomStashRepository.reconcileTags()` matches incoming tags against existing library tags by morphological stem to converge synonyms without duplicates. `backfillNormalizedTags()` runs seamlessly on app startup to normalize legacy database tags.
   - **Debounced Search**: Search query updates are debounced by 250ms (`query.debounce(250)`) to eliminate redundant FTS database querying on each keystroke.
   - **Feed Sorting**: Backed by DataStore preference (`SortOrder.Newest`, `SortOrder.Oldest`, `SortOrder.UnreadFirst`), selectable via an M3 `DropdownMenu` in `FeedTopBar`.
   - **Tactile Haptics**: Subtle Material 3 haptic feedback (`HapticFeedbackType.TextHandleMove` and `LongPress`) wired to chip toggles, swipe actions, and read/delete confirmations.

9. **Material 3 Expressive List-Detail Architecture (Navigation 3)**:
   - **`ListDetailSceneStrategy`**: Managed via `rememberListDetailSceneStrategy<NavKey>` with `currentWindowAdaptiveInfoV2()`.
   - **Phone Form Factor (Compact Window)**: Tapping a card transitions to a dedicated `StashDetailScreen` with **Material 3 Expressive Container Transform** (`sharedBounds` on card/screen with `MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()` and `OverlayClip`, `sharedElement` on image, title, dot, and domain) and coordinated `defaultEffectsSpec<Float>()` route fade throughput. Full Android Predictive Back gesture support.
   - **Tablet / Foldable (Expanded Window)**: Automatically renders a Two-Pane side-by-side layout with `FeedRoute` on the left (list pane) and `DetailRoute` on the right (detail pane) with `StashDetailPlaceholder` when no item is selected.
   - **Dedicated `StashDetailScreen` Features**:
     - Clean, focused Top App Bar with back navigation, Open in Browser icon, and subtle overflow menu for secondary actions (Read toggle, Delete).
     - Full-bleed header image, category badge, relative saved timestamp, and domain.
     - Scaled editorial title (`titleLarge` / 20sp).
     - Concise, formatted AI Key Points briefing card with category-tinted bullets.
     - Topic tags.
     - Docked "Ask on-device AI" extended FAB for local on-device chat.

10. **Dedicated Settings Screen & Single FAB (Navigation 3)**:
    - **`SettingsRoute`**: Dedicated navigation destination reached via ⚙️ Settings icon in `FeedTopBar`.
    - **Appearance**: Theme mode picker (System, Light, Dark) with segmented buttons and real-time Dynamic Color (Material You) switch.
    - **On-Device AI Controls**: Live active Gemini Nano engine status probe, Model variant selector (`ModelChoice` with `ModelStatus` probing), and Summary detail level (`SummaryEffort`: Low, Medium, High).
    - **Clean Single FAB**: Collapsed the bottom 3-button floating toolbar to a single, centered/bottom-end M3 `FloatingActionButton` (`+` Add URL), reducing bottom list padding to `80.dp`.

11. **Topic Briefings ("Catch Me Up" & Compare)**:
    - **Backlog Guilt & Decision Making Focus**: Provides fast on-device intelligence summaries across multiple items, cutting through reading backlogs and comparing tool trade-offs.
    - **Feed Topic Catch-Up Banner**: When filtering the feed by a tag with ≥2 items, an elevated banner card appears at the top of the feed list introducing `"Catch up on <Topic>"`, leaving the horizontal tag row purely for filtering.
    - **Multi-Item Selection Mode**: Long-pressing any card in the feed enters multi-select mode with haptic feedback. Floating M3 bottom capsule displays count and `[ Compare & Brief ]` action alongside batch Mark Read and Delete.
    - **`BriefingRoute` & `StashBriefingScreen`**: Dedicated screen featuring a horizontal source carousel and **Connected Intelligence Rail** ([TimelineRail.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/components/TimelineRail.kt)) — a canvas-drawn vertical timeline with smooth S-curve Bezier bends, animated forward-traveling gradient beam, radial glow aura, and **scroll-driven focal magnification** across 4 dedicated sections:
      1. **The Big Picture** (Executive overview)
      2. **Key Takeaways** (Primary insights & core findings)
      3. **Comparisons & Trade-offs** (Direct tool differences & pros/cons)
      4. **The Bottom Line** (Verdict & recommendation)

12. **Twitter/X Image Extraction & Fallback**:
    - **High-Res Media & Video Thumbnail Extraction**: Parses `api.fxtwitter.com` and `api.vxtwitter.com` to extract attached tweet photos, 1080p/4K video thumbnails (`media.videos[0].thumbnail_url`, `media_extended[0].thumbnail_url`), and 1500x500 user profile banners. Automatically upgrades attached photo URLs to `?name=large` (preserving `?name=orig`) and upscales author avatar URLs (`_normal.jpg`, `_mini.jpg`, `_bigger.jpg`, `_200x200.jpg`, `_x96.jpg`) to crisp `_400x400.jpg`.
    - **Native Obsidian X Fallback Banner**: When network calls fail, are rate-limited, or tweets are private/deleted, synthesizes a sleek 800x450 dark gradient canvas with the official centered X vector glyph on disk, ensuring 100% of Twitter saves have a rich visual identity.
    - **Startup Backfill & Low-Res Upgrade**: Automatically inspects existing saved Twitter/X URLs in Room upon app launch to backfill missing images and silently upgrade legacy low-res (< 200px) thumbnails to full-res video captures, banners, and 400x400 avatars with immediate in-memory cache eviction.

## Status
- **Navigation & Feed Motion Polish (Back Swipe Gesture, Gallery Container Transform, Sort Crossfade & Dynamic Content Colors)**:
  - Added `BackHandler(onBack = onBack)` in [StashDetailScreen.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/detail/StashDetailScreen.kt) to ensure active Android 14+ Predictive Back callback registration when entering detail from search or feed.
  - Added Material 3 Container Transform shared bounds (`card-${item.id}`) to [FeedGallery.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/feed/FeedGallery.kt) (`GalleryCard`), bringing full container transform motion to the compact gallery feed view.
  - Restored dynamic per-content seed accent colors (`effectiveTones.accent`) for domain text and icons over dark gradient scrims in [FeedGallery.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/feed/FeedGallery.kt), with clean, un-encapsulated byline typography keeping titles as the primary hero element.
  - Implemented smooth Material 3 Expressive `AnimatedContent` crossfade (`fastEffectsSpec()`) and scoped item keys by `${sortOrder.name}-${item.id}` in [FeedContent.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/feed/FeedContent.kt) and [FeedGallery.kt](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/feed/FeedGallery.kt), eliminating the jarring scroll-down-and-snap when sorting.
  - Completed & Verified on physical device (`./gradlew test` passes all unit tests, `assembleDebug` builds clean APK).
- **Top Bar Touch Interception & Non-Transparent Shielding (FeedTopBar, StashSettingsScreen, StashBriefingScreen)**: Fixed touch bleed-through to underlying scrolled cards and enhanced top-left logo scroll-to-top tap area. Completed & Verified (`./gradlew test` passes all unit tests, `assembleDebug` builds clean APK).
- **Twitter/X High-Res Image Extraction & Low-Res Backfill (Video thumbnails 1080p/4K, Profile banners, name=large photo upscaling, _400x400 avatar upscaling, Native Canvas X banner, In-memory cache eviction, Startup Room Backfill)**: Completed & Verified (`./gradlew test` passes all 10 unit test suites, `assembleDebug` builds clean APK).
- **Open-Domain Canonical Tagging & Grouping (3-Tier Prompting, Morphological Lemmatizer TagNormalizer, Stem-Aware Library Snapping, Noise Word Filtering, Startup Room Backfill)**: Completed & Verified (`./gradlew test` passes all 9 unit test suites, `assembleDebug` builds clean APK).
- **Material 3 Expressive List-Detail Migration, M3 MotionScheme Container Transforms, Dedicated StashDetailScreen Intelligence Briefing, Navigation 3 ListDetailSceneStrategy, Category Palette Refresh, Subtle Byline Typography, Dedicated StashSettingsScreen & Single FAB, M3 AppBarWithSearch & Feed-Level Sort Chip, Topic Briefings ('Catch Me Up' & Compare, 4-Node Canvas Intelligence Rail & Section Magnification)**: Completed & Verified.
- **Release Build R8 Minification & ML Kit Reflection (ProGuard Rules & Safe Model Probing)**: Preserved full reflection surfaces for ML Kit, Firebase component registrars, and Google Play services AICore IPC in `app/proguard-rules.pro`. Hardened model option resolution in `OnDeviceSummarizer` to safely handle variant discovery. Release APK size optimized from 83.6 MB down to 10.65 MB (-87%). Completed & Verified on physical device.
- **Codebase Readability & Comment Cleanup**: Completed & Verified. Stripped redundant AI monologues and dead constants while preserving high-signal architecture and API docs.

## Backlog & Tech Debt
- **`SwipeToDismissBox` Migration ([StashCardRow.kt:L121](file:///c:/Users/Chris/projects/android/Stash/app/src/main/java/com/example/stash/ui/components/StashCardRow.kt#L121))**:
  - `rememberSwipeToDismissBoxState(confirmValueChange = ...)` was deprecated in Compose Material 3 `1.5.0-alpha25`.
  - Migrate to dynamic drag anchors using `AnchoredDraggableState` / dynamic anchors sample rather than relying on `confirmValueChange` callbacks to veto state changes.
