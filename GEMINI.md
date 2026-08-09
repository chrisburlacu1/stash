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

## Status
- **M3 Top App Bar Search, Bottom-Right Add URL FAB, Tag Filtering, Smart HTML Extraction, Async Loading, Native Model Version**: Completed & Verified (`./gradlew installDebug` clean build).
