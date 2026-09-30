# Stash — On-Device, Privacy-First Second Brain

> Save, summarize, and synthesize your reading queue — 100% on-device. No external AI servers. No analytics. No telemetry.

---

## What is Stash?

**Stash** is an intelligent bookmarking and knowledge synthesis app built for Android 14 and newer. Designed from the ground up around **Google's on-device Gemini Nano (ML Kit GenAI / AICore)**, Stash automatically reads, organizes, and summarizes articles, documentation, repositories, and tweets directly on your phone's NPU.

Unlike traditional read-it-later services that process your private reading history in the cloud, Stash has **zero backend servers**. Every operation — from HTML extraction and color quantization to AI summarization and full-text search — executes strictly in-memory on your device.

---

## Key Features

### ⚡ Instant, Zero-Latency Share Sheet Integration
Share links directly from Chrome, Firefox, YouTube, Twitter/X, GitHub, or Reddit. Stash parses the URL, scrapes OpenGraph metadata, resolves repository titles (`owner/repo`), and kicks off background AI summarization with immediate UI feedback.

### 🧠 On-Device Gemini Nano Intelligence
- **Key Points Extraction**: Instant 3–4 bullet briefing cards capturing the essence of long-form articles in seconds.
- **Canonical Faceted Tagging**: Automatically classifies content into broad domains (*Engineering*, *Film*, *Finance*), topics (*AI Agent*, *Architecture*), and granular concepts, normalized via an on-device morphological lemmatizer.
- **Chat With Your Content**: Ask questions about any saved article in an offline reader view. Stash supplies the local model with the article body without ever re-fetching the web page.

### 📊 Connected Intelligence Briefings ("Catch Me Up" & Compare)
- **Topic Catch-Up**: Filtering by any topic with multiple items presents an executive briefing that synthesizes common themes across your backlog.
- **Multi-Item Compare**: Long-press cards in your feed to select multiple tools, libraries, or articles and generate a 4-stage comparison timeline: *The Big Picture*, *Key Takeaways*, *Comparisons & Trade-offs*, and *The Bottom Line*.
- **Connected Timeline Rail**: S-curve Bezier canvas timeline with scroll-driven focal magnification and animated energy beam.

### 🎨 Material 3 Expressive & Adaptive Design System
- **Stock M3 Search Bar**: Google Keep-style search pill integrating an in-place View Switcher (List / Staggered Gallery), Sort menu, and instant Room FTS5 debounced search.
- **Perceptual Seed Tinting**: Card glows and accent strokes are dynamically quantized using Google Material Color Utilities (HCT Celebi) from header images, ensuring monochrome graphics harmonize with category tones.
- **Adaptive List-Detail (Navigation 3)**:
  - **Phones**: Material 3 Expressive elevation scale transitions (`scaleIn` + `fadeIn` / `scaleOut` + `fadeOut`) with full Predictive Back gesture support.
  - **Foldables & Tablets**: Dual-pane side-by-side feed and detail layout with zero UI compromises.

### 🐦 High-Fidelity Twitter / X Media Pipeline
- Extracts full-resolution photo attachments (`?name=large`), 1080p/4K video thumbnails, and crisp 400x400 author avatars.
- Renders a native obsidian vector fallback banner on disk for deleted, private, or rate-limited tweets.

---

## Privacy & Security by Architecture

| Guarantee | How Stash Implements It |
| :--- | :--- |
| **No External AI APIs** | All summarization runs via Google Play services AICore / Gemini Nano on local silicon. |
| **No Cloud Sync** | Backups are disabled (`android:allowBackup="false"`). Database lives exclusively in local app storage. |
| **Zero Trackers** | No Google Analytics, Firebase Analytics, Crashlytics, or third-party ad networks. |
| **Minimal Permissions** | Requests only `android.permission.INTERNET` strictly to fetch public page content and OpenGraph images. |

---

## Tech Stack & Architecture

```
[System Share Sheet / Add URL]
             │
             ▼
   [UrlExtractor / Scrapers] ──► [Room SQLite + FTS5]
             │                            ▲
             │                            │
   [Gemini Nano (AICore)] ────────────────┘
             │
             ▼
   [Navigation 3 Strategy]
   ├── Compact: Elevating List ──► Reader Detail ──► Offline Chat
   └── Expanded: Dual-Pane Adaptive Feed + Intelligence Rail
```

- **Language & Runtime**: 100% Kotlin with Coroutines & Flow
- **UI Framework**: Jetpack Compose with Material 3 Expressive (`1.5.0-alpha28`)
- **Navigation**: Jetpack Navigation 3 (`androidx.navigation3` + `material3.adaptive.navigation3`)
- **Database**: Room 3 (`androidx.room3`) with bundled SQLite (`androidx.sqlite.bundled`) and FTS5 search
- **On-Device AI**: Google ML Kit GenAI Prompt API (`com.google.mlkit:genai-prompt`) + KSP Schema Compiler
- **Color Science**: Google Material Color Utilities (`com.google.android.material.color.utilities`)
- **Web Extraction**: Jsoup + Custom FxTwitter/VxTwitter resolver

---

## Getting Started & Building

### Requirements
- Android Studio Ladybug (or newer)
- JDK 17 or JDK 21
- A physical device or emulator running **Android 14+ (API 34+)** with Google Play services AICore support (e.g. Pixel 6+, Samsung Galaxy S21+).

### Build Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew test
```

*On Windows, run `gradlew.bat` with the same commands.*

---

## License

This project is licensed under the Apache License 2.0.

