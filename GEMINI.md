# GEMINI.md - Stash Project Context

## Project Overview
Stash is a privacy-first "second brain" Android application built with Jetpack Compose, Material 3 Expressive (`1.5.0-alpha25`), Navigation 3 (`androidx.navigation3`), Room Database, ML Kit GenAI (Gemini Nano on-device), and Android System Share Sheet integration.

## Key Design Patterns & Implementation Rules
1. **Material 3 Expressive System & Top App Bar Search**:
   - Theme Entry Point: `MaterialExpressiveTheme(colorScheme = colorScheme, shapes = ExpressiveShapes, typography = Typography, content = content, motionScheme = MotionScheme.expressive())`.
   - **M3 Top App Bar Search**: Following official Material 3 App Bar guidelines, the search bar is integrated directly into the Top App Bar section below the title, with full status-bar insets and real-time query filtering.
   - **Standard M3 FAB**: Centered/BottomEnd M3 `FloatingActionButton` (`CircleShape`, `primary` background) for triggering the Add URL dialog.
   - Detail View Aesthetics: Rich M3 Expressive container cards (`primaryContainer` hero card, `surfaceContainerHigh` AI summary card, `surfaceContainerLow` tags card).
2. **Content Types vs Tags & Filtering**:
   - `category` represents the strict Content Type ("Article", "Blog", "Tweet", "GitHub Repo", "Video", "Discussion", "Documentation", "Website") with distinct theme color coding.
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

## Status
- **Gemini Chat Redesign, 7-Hue Fluid Mesh Shader, M3 Expressive Motion, Gemini Input Pill, Full Article Context Chat**: Completed & Verified (`./gradlew assembleDebug` and `./gradlew test` clean build on `feature/gemini-chat-polish`).

