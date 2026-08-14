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

- **`data/StashRepository.kt`** — the interface (`observe`, `observeTags`, `observeItem`, `addUrl`, `setRead`, `delete`, `chat`, `getModelVersion`, `probeModels`, `selectModel`). Swap `RoomStashRepository` for a fake here in tests.
- **`data/local/RoomStashRepository.kt`** — the real implementation. `addUrl` is the core pipeline:
  1. Normalize the URL, derive a domain-based fallback title, insert a `Pending`/`Summarizing` row immediately so the feed updates instantly.
  2. Fetch the page HTML (`HttpURLConnection`, capped at 600,000 chars) and parse it with **jsoup**. Chrome is removed by element (`script, style, nav, header, footer, aside, form`, plus comment/related/sidebar/newsletter containers), then a selector cascade (`article, main, [role=main], [class*=prose], [class*=content]`, falling back to `body`) picks whichever candidate has the most paragraph text. **Do not trust a single container tag** — one measured page kept its `<article>` for the header and table of contents while the real prose sat in a sibling `div.prose`. The earlier regex version returned 286 chars of navigation menu on a 218KB article; jsoup returns ~19,000.
  3. If Gemini Nano is available, call `summarizer.organize(...)` for a `{title, takeaway, keyPoints, category, tags}` response; otherwise fall back to a truncated extract. `keyPoints` is stored newline-separated in the `summary` column so FTS indexes each bullet, and the expanded card renders them as a numbered list. How much page text is sent is a user setting (`SummaryEffort`: 2,500/4,000/8,000 chars) — quality climbs steeply to ~2,500 then tapers, while latency rises roughly linearly at ~2.7ms per 100 chars.
  4. Upsert the final row with `aiState` set to `Ready`/`Failed`/`Unavailable`.
- **`data/local/StashDatabase.kt`** — Room database. `stash_items` is the source of truth; `stash_search` is a parallel FTS5 virtual table (unicode61, prefix `2 3 4`) kept in sync via the `@Transaction upsert()` helper — never write to one table without the other. Tags are stored as a single `" | "`-delimited string column (`TAG_SEPARATOR` in `RoomStashRepository.kt`), not a join table; tag-filter queries use `LIKE` patterns against that delimited string. Search ranks with `bm25(...)` weighted per-column. Migrations 1→3 and 2→3 rebuild the FTS table; bump `version` and add a migration when changing entity shape.
- **`ai/OnDeviceSummarizer.kt`** — `GeminiNanoSummarizer` wraps `com.google.mlkit.genai.prompt.Generation`. Selects the `PREVIEW`/`FAST` model variant where available (~3x faster than the default `STABLE`/`FULL`), falling back to the default otherwise, and calls `warmup()` so the first save doesn't pay for model load. `getBaseModelName()` plus the resolved variant is shown in the top bar.
  - **Two output paths.** `structuredOrganize()` uses the typed/structured-output API against the `@Generable`-annotated `OrganizedResponse`, so the closed category set (`enumValues`) and tag/bullet counts (`minItems`/`maxItems`) are enforced by schema rather than requested in prose. It is gated on `isStructuredOutputFeatureAvailable()` because structured output is Alpha inside a Beta artifact; on failure or where unsupported, `promptJsonOrganize()` asks for JSON in the prompt and parses it. Both share `toOrganizedContent()` so they normalise identically.
  - **The schema needs KSP.** `@Generable`/`@Guide` are compile-time generated, not runtime reflection: `ksp("com.google.mlkit:genai-schema-compiler")` emits `OrganizedResponse_GeneratedProvider`. The annotated class **must be public** — the generated provider exposes it, so `private`/`internal` fails to compile.
  - **Never show the model the existing tag vocabulary.** Listing known tags in the prompt biased it into picking from the list instead of reading the page (a Node.js article came back tagged "Android Development"). Tags come from content only; `reconcileTags` deduplicates afterwards.
  - **Chat** (`chatStream`) streams free-form answers about one item via `generateContentStream` (`Flow<GenerateContentResponse>`, chunks as the model decodes). The Prompt API is stateless — no session object — so every request replays the item context plus the last 12 `ChatTurn`s into the prompt. Chat is grounded in *stored* data only (`RoomStashRepository.itemChatContext`): the summary, key points, tags, and up to 4,000 chars of the page body captured at save time into the `content` column. **The page is never re-fetched** — chat works offline and never leaks reading activity. Storing the body is what lets chat answer beyond the extracted bullets; it costs database size, which is the trade that was chosen.
- **`ui/feed/StashFeedViewModel.kt`** — combines `query` + `tag` flows via `flatMapLatest` into `repository.observe(...)`, plus `observeTags()`, `modelVersion`, and the model-picker state, into one `FeedUiState`. Standard `ViewModelProvider.Factory` (no Hilt/Koin).
- **`ui/adaptive/StashAdaptiveLayout.kt`** — navigation. Navigation 3 (`androidx.navigation3`) with `NavDisplay` inside a `SharedTransitionLayout`. Two routes: `FeedRoute` (cards expand in place — the old list-detail scaffold is gone) and `ChatRoute(itemId)`, which slides up over the feed via per-entry `metadata { put(NavDisplay.TransitionKey) ... }` on a `defaultSpatialSpec`. Chat ViewModels are keyed per item (`"chat-$itemId"`) so a reopened chat resumes its transcript for the process lifetime.
- **Share intent entry point:** `MainActivity.handleShareIntent` handles `ACTION_SEND text/plain` (Android share sheet) in both `onCreate` and `onNewIntent`, calling `repository.addUrl` directly — this is the primary way URLs get added besides the in-app Add URL FAB/dialog.
- **Ask-about-an-item** (`ui/components/AskAboutItemSheet.kt`, `ui/util/OpenGemini.kt`): swiping a card toward the leading edge opens a chooser rather than going straight to chat — on-device Nano (private, offline, grounded in the saved summary) or the Gemini app (stronger, reads the live page, sends the link off-device). Wired at `StashItemActions.onChat`, which the feed and the search surface both bind through, so both inherit it. The sheet's second row is the only place in the app where data leaves the device, and its supporting text says so — **do not trim that copy**. The sheet used to carry its own lit treatment (`sheetMesh`); that went with the lighting layer.
  - **`<queries>` is load-bearing.** At `targetSdk 36`, package visibility filtering hides other apps, so `resolveActivity` for Gemini returns null even when it is installed and "Open in Gemini" silently falls back to the system chooser. The manifest declares `com.google.android.apps.bard` narrowly (not `QUERY_ALL_PACKAGES`, which is Play-policy restricted and unjustifiable for one lookup). **ADB cannot reproduce this bug** — the shell is exempt from visibility filtering, so the identical explicit intent succeeds from `adb shell am start` and fails from the app. Verify intent handoffs in the app, not just over ADB.

**Feed UI files** (`ui/feed/`, split out of what was a 400-line `StashMainFeedScreen`):

- **`StashMainFeedScreen.kt`** — state collection, effects, `Scaffold`. Reads as a page outline; the pieces below do the work.
- **`FeedContent.kt`** — `FeedList`, `FeedEmptyState`, the pinned filter chips, and `StashItemActions` (the per-item callback bundle shared by the feed and the search surface).
- **`FeedSearch.kt`** — the full-screen search surface. There is no top app bar; the toolbar's search button opens this directly.
- **`FeedToolbar.kt`** — the pinned `HorizontalFloatingToolbar` (add / search / summarization settings). Deliberately **not** hidden on scroll: it carries the app's only actions.
- **`ModelMenu.kt`** — effort + model picker. Model availability is probed per-variant with `checkStatus()` when the menu opens; ML Kit has no API that enumerates models.
- **`ui/components/StashCardRow.kt`** — the feed card. Swipe end-to-start to delete (confirmed by dialog), start-to-end to open the item chat. See the design section below.

**Chat UI files** (`ui/chat/`):

- **`StashChatViewModel.kt`** — in-memory transcript (`ChatMessage`), streaming `send()`. Transcripts are deliberately not persisted: a chat is *about* an item, not part of it.
- **`StashChatScreen.kt`** — header (item identity in the card's eyebrow-then-title order), reversed `LazyColumn`, composer. Both sides are plain M3 now, distinguished by alignment, container and corner shape; the assistant's hue wash went with the lighting layer.

**Theme system:** `StashTheme` (`ui/theme/Theme.kt`) sets up `MaterialExpressiveTheme` with `MotionScheme.expressive()` and a 35-token light/dark `ColorScheme` (`Color.kt`, seeded from `#E7418F`). **Shapes come from M3 Expressive's own scale** — a bespoke `ExpressiveShapes` override was removed so there is one source of truth. Always reuse theme tokens/shapes; do not hardcode colors, spacing, or corner radii.

**Dynamic colour (Material You) is on by default** and regenerates the whole palette from the user's wallpaper. It is backed by a real setting (`StashSettings.dynamicColor`) that nothing writes yet — the toggle belongs on the settings screen (workstream C), so until then the default *is* the behaviour. Anything hard-coding a colour will look wrong under it.

`category` (content type, fixed set above) is a **label**, not a colour system: it drives the eyebrow pill, the icon and tag chips through plain M3 roles. It is distinct from user-extracted `tags` (freeform, used for filter chips).

Experimental Compose APIs in use (opted in at the module level in `app/build.gradle.kts`): `ExperimentalMaterial3ExpressiveApi`, `ExperimentalMaterial3AdaptiveApi`, `ExperimentalSharedTransitionApi`.

## Design

**Read `DESIGN-NOTES.md` before changing anything visual.** It records what looked wrong and why, in symptom/cause pairs. Several plausible-sounding ideas are recorded there as already-tried failures — a photo-to-card fade, film grain, a category spine — with the reason each one could not work. Append to it whenever a visual problem gets diagnosed; that log is a deliberate learning tool, not incidental notes.

**The card.** Title-led, built around the key points the summarizer extracts, since that is what a bookmark list cannot show. Collapsed it advertises the count; expanded it renders them as a numbered briefing. Order: category pill + time + domain (dot-separated eyebrow) → title → summary panel → tags. The source image is a full-width header, ending at a hard edge — an earlier 200dp hero that *faded* into the card failed (a near-black OG image meeting a near-white card is a luminance jump no gradient can hide), and a 64dp thumbnail was the interim answer before the header returned. The failure was the transition, not the size.

**The lighting layer is parked — `main` is plain M3 Expressive.** The app used to carry a bespoke light system: a category-coloured glow above each card's top edge, an AGSL mesh of every category hue while the model decided, a screen-scale aura in chat, a lit ask-sheet, and an emissive splash easter egg. All of it is on `lighting/agsl-layer`, removed from `main` in one pass.

It was removed because it was a half-defined system that every other visual decision had to be defined against — background, header image, chat, all blocked on "what does light mean here", which had no answer. See DESIGN-NOTES, "The lighting layer is parked", for the full reasoning.

What this means for anyone working here now:

- **Do not reintroduce shader or glow effects** without the definition existing first. Rebuilding a piece because it looked good is the specific failure mode being avoided.
- **Category is a label, not a colour system.** It drives the eyebrow pill, the icon and chips as plain M3 roles. `CategoryStyle.color` still exists but nothing draws with it; `categoryHueIndex` survives for the *taxonomy* (folding pre-collapse strings onto the current five), not for hue.
- **`Summarizing` is an M3 `LinearProgressIndicator`.** The mesh used to be that signal. If you are tempted to remove the indicator as redundant, check what else is telling the user the model is running — currently nothing.
- **The Gemini-style direction is still the aspiration**, not a current description. The reference is <https://design.google/library/gemini-ai-visual-design>: gradients as context builders with sharp leading edges and diffuse tails, visualising thinking rather than decorating, motion with inner activity, softness as strategy. `Pending`/`Summarizing` remain the genuine analogue whenever this is picked back up.
- **AGSL lessons still apply if it returns.** Shaders compile at *draw* time, so a typo is a runtime crash after a green build — always open the surface. `RuntimeShader` needs API 33; minSdk 34 means no fallback path was ever required.

**The chat screen's IME handling is load-bearing and unrelated to the above.** The activity is `android:windowSoftInputMode="adjustNothing"`, so the keyboard never resizes the window — it simply covers the bottom of it. `StashChatScreen`'s root `Box` therefore keeps full height, the header and transcript never move when the IME opens, and `ChatInputBar` is the only composable consuming `WindowInsets.ime` (as `navigationBars ∪ ime`), floated over the content rather than living in its layout flow. **Do not put `imePadding()` back on the root** — that resizes header and transcript along with everything else, which is the exact bug it once caused (see DESIGN-NOTES). Corollary: under `adjustNothing`, any *other* surface with a text field must consume the IME inset itself.

## Tooling

- **The `android-api-lookup` skill** — dump the real public API of a dependency, from the artifact
  this project actually resolves (`.claude/skills/android-api-lookup/scripts/api.sh`). Use it
  instead of guessing at a signature or trusting a doc page: these are alpha/beta artifacts, and
  `gradle/libs.versions.toml` states a *request*, not the resolved version
  (`material3:1.3.1 -> 1.5.0-alpha25`).
- **`codegraph explore "<question>"`** — how the project's own code fits together, with source and
  blast radius. A `SessionStart` hook indexes the repo automatically (~3s), including in fresh
  worktrees.
- **Never run a bare `find /`.** On Windows Git Bash it walks the whole drive; two agents hung for
  25+ minutes doing this. Scope every search, or use the tools above.

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
