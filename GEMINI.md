# GEMINI.md - Stash Project Context

## Project Overview
Stash is a privacy-first "second brain" Android application built with Jetpack Compose, Material 3 Expressive (`1.5.0-alpha25`), Navigation 3 (`androidx.navigation3`), Room Database, ML Kit GenAI (Gemini Nano on-device), and Android System Share Sheet integration.

## Key Design Patterns & Implementation Rules
1. **Material 3 Expressive System & Top App Bar Search**:
   - Theme Entry Point: `MaterialExpressiveTheme(colorScheme = colorScheme, shapes = ExpressiveShapes, typography = Typography, content = content, motionScheme = MotionScheme.expressive())`.
   - **M3 Top App Bar Search**: Following official Material 3 App Bar guidelines, the search bar is integrated directly into the Top App Bar section below the title, with full status-bar insets and real-time query filtering.
   - **Standard M3 FAB**: Centered/BottomEnd M3 `FloatingActionButton` (`CircleShape`, `primary` background) for triggering the Add URL dialog.
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
   - URL additions save an immediate `Pending`/`Summarizing` entity to Room so the feed updates instantly with a `CircularProgressIndicator`.
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
7. **Animated BlurEffect Edge Glow for Newly Added Links**:
   - **`CardEdgeBlurEffect`**: Uses `androidx.compose.ui.graphics.BlurEffect` (`TileMode.Clamp`) on hardware-accelerated Android 14+ (`minSdk = 34`) with rotating sweep gradients along the card's perimeter stroke using Stash's 5 theme-aware category hues (Article Blue `#1B6BB5`/`#8FC2F5`, Documentation Violet `#6D4BB8`/`#C0AAF5`, Video Crimson `#C0392E`/`#F5A199`, Discussion Amber `#B5591B`/`#F3B382`, Repo Slate `#4A5568`/`#B4BECC`).
   - **Inward Edge Bleed & High Falloff**: Clipped to the card silhouette (`RoundedCornerShape(16.dp)`), the light bleeds with high intensity and steep falloff (~8–10dp) inward from the outer edges into the card surface, pulsing naturally while keeping the central card body and text clean and legible.
   - **Add Link Dialog**: `AddUrlDialog` features an animated blurred gradient halo accent with `GeminiMark`.

8. **Core Feed Polish & Organization**:
   - **Frequency-Sorted Tag Filter Chips**: `observeTags()` groups and counts occurrences across all saved items, sorting chips by highest count descending with display counts (`"${tag.name} (${tag.count})"`).
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

## Status
- **Material 3 Expressive List-Detail Migration, M3 MotionScheme Container Transforms, Dedicated StashDetailScreen Intelligence Briefing, Navigation 3 ListDetailSceneStrategy, Category Palette Refresh, Subtle Byline Typography, Dedicated StashSettingsScreen & Single FAB**: Completed & Verified (`./gradlew test` and live install on device).



