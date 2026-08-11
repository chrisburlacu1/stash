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
  - **Chat** (`chatStream`) streams free-form answers about one item via `generateContentStream` (`Flow<GenerateContentResponse>`, chunks as the model decodes). The Prompt API is stateless — no session object — so every request replays the item context plus the last 12 `ChatTurn`s into the prompt. Chat is grounded in the *stored* notes only (`RoomStashRepository.itemChatContext`): the page is never re-fetched, so chat works offline and never leaks reading activity.
- **`ui/feed/StashFeedViewModel.kt`** — combines `query` + `tag` flows via `flatMapLatest` into `repository.observe(...)`, plus `observeTags()`, `modelVersion`, and the model-picker state, into one `FeedUiState`. Standard `ViewModelProvider.Factory` (no Hilt/Koin).
- **`ui/adaptive/StashAdaptiveLayout.kt`** — navigation. Navigation 3 (`androidx.navigation3`) with `NavDisplay` inside a `SharedTransitionLayout`. Two routes: `FeedRoute` (cards expand in place — the old list-detail scaffold is gone) and `ChatRoute(itemId)`, which slides up over the feed via per-entry `metadata { put(NavDisplay.TransitionKey) ... }` on a `defaultSpatialSpec`. Chat ViewModels are keyed per item (`"chat-$itemId"`) so a reopened chat resumes its transcript for the process lifetime.
- **Share intent entry point:** `MainActivity.handleShareIntent` handles `ACTION_SEND text/plain` (Android share sheet) in both `onCreate` and `onNewIntent`, calling `repository.addUrl` directly — this is the primary way URLs get added besides the in-app Add URL FAB/dialog.

**Feed UI files** (`ui/feed/`, split out of what was a 400-line `StashMainFeedScreen`):

- **`StashMainFeedScreen.kt`** — state collection, effects, `Scaffold`. Reads as a page outline; the pieces below do the work.
- **`FeedContent.kt`** — `FeedList`, `FeedEmptyState`, the pinned filter chips, and `StashItemActions` (the per-item callback bundle shared by the feed and the search surface).
- **`FeedSearch.kt`** — the full-screen search surface. There is no top app bar; the toolbar's search button opens this directly.
- **`FeedToolbar.kt`** — the pinned `HorizontalFloatingToolbar` (add / search / summarization settings). Deliberately **not** hidden on scroll: it carries the app's only actions.
- **`ModelMenu.kt`** — effort + model picker. Model availability is probed per-variant with `checkStatus()` when the menu opens; ML Kit has no API that enumerates models.
- **`ui/components/StashCardRow.kt`** — the feed card. Swipe end-to-start to delete (confirmed by dialog), start-to-end to open the item chat. See the design section below.
- **`ui/components/SummarizingMesh.kt`** — the AGSL mesh gradient shown while the model is deciding a category. See the design section below.

**Chat UI files** (`ui/chat/`):

- **`StashChatViewModel.kt`** — in-memory transcript (`ChatMessage`), streaming `send()`. Transcripts are deliberately not persisted: a chat is *about* an item, not part of it.
- **`StashChatScreen.kt`** — header (item identity in the card's eyebrow-then-title order), reversed `LazyColumn`, composer. The user's side is plain M3; the gradient language is reserved for the assistant. See the design section below.
- **`ui/components/ChatAuraMesh.kt`** — the chat's bottom-anchored AGSL mesh. See the design section below.

**Theme system:** `StashTheme` (`ui/theme/Theme.kt`) sets up `MaterialExpressiveTheme` with `MotionScheme.expressive()`, custom `ExpressiveShapes` (extraSmall–extraLarge), and a 35-token light/dark `ColorScheme` (`Color.kt`). Always reuse existing theme tokens/shapes — do not hardcode colors, spacing, or corner radii. `category` (content type, fixed set above) drives the colour of the eyebrow pill, tag chips and the card's glow, and is distinct from user-extracted `tags` (freeform, used for filter chips).

Experimental Compose APIs in use (opted in at the module level in `app/build.gradle.kts`): `ExperimentalMaterial3ExpressiveApi`, `ExperimentalMaterial3AdaptiveApi`, `ExperimentalSharedTransitionApi`.

## Design

**Read `DESIGN-NOTES.md` before changing anything visual.** It records what looked wrong and why, in symptom/cause pairs. Several plausible-sounding ideas are recorded there as already-tried failures — a photo-to-card fade, film grain, a category spine — with the reason each one could not work. Append to it whenever a visual problem gets diagnosed; that log is a deliberate learning tool, not incidental notes.

**The card.** Title-led, built around the key points the summarizer extracts, since that is what a bookmark list cannot show. Collapsed it advertises the count; expanded it renders them as a numbered briefing. Order: category pill + time + domain (dot-separated eyebrow) → title → summary panel → tags. The source image is a **64dp thumbnail that acts as the link**, not a hero — OG images are inconsistently dark and unpredictably cropped, so at hero size they set the tone of a card whose real content is text.

**The glow** (`categoryGlow` in `StashCardRow.kt`) is the app's one non-standard visual idea and the seed of its visual language. Category colour enters as light from a source above the card's top edge: a small radius, a centre just above the edge, and a sharp falloff curve — a lamp, not a gradient. Expanding a card turns the light **up**, brightening *and* spreading, so the effect signals state rather than decorating. Both states are lit; expansion changes the degree.

Motion rule learned the hard way: **things animating together must share an animation spec, not a duration.** The glow rides the same `defaultSpatialSpec` as the card's expansion. Springs are duration-free, so any hand-picked duration matches on one edge and drifts on the other.

**Direction: toward a Gemini-style visual language.** The reference is <https://design.google/library/gemini-ai-visual-design>, which is what motivated this app's design work. What matters from it:

- **Gradients as context builders, not decoration.** They "convey energy and dynamism rather than fixed object-like qualities", with "sharp, almost opaque leading edges that diffuse at the tail, acting as clear visual pointers". Direction and diffusion carry meaning.
- **Visualise thinking.** Gemini uses gradients rather than iconography to show active processing, to "personify the AI assistant rather than rendering it impenetrable". Stash has a genuine analogue: the `Pending`/`Summarizing` states while Gemini Nano works.
- **Motion with inner activity.** "Inner activity within the motion conveys thinking, analysis, and intelligence, making the processing feel more transparent." Movement should mirror what the system is doing, not merely animate.
- **Softness as strategy.** The "ethereal, in-between fuzzy space that reflects our nonlinear process for ideation" — softness acknowledges uncertainty while still signalling confidently.

**The summarizing mesh** (`ui/components/SummarizingMesh.kt`) is the first piece of that direction actually built. While an item is `Summarizing`, a fluid mesh gradient of all seven category hues runs behind the card and collapses to the single chosen colour once the model decides. The visual is literally the model not having decided yet; the resolution is the answer arriving.

- **An AGSL `RuntimeShader`, not stacked radial brushes.** Seven overlapping translucent radials composite toward grey — the exact muddy neutral the effect must avoid, which `saturated()` already fights at one layer — and they slide over each other like cut paper instead of bleeding. In the shader the fields are normalised by total weight per pixel, so the blend is always at full chroma. minSdk 34 vs `RuntimeShader`'s API 33 means no fallback path.
- **The reference's three ideas, each with a mechanism.** Sharp leading edge / diffuse tail: each source's falloff is asymmetric along its own direction of travel. Inner activity: the sample point is domain-warped by a noise field before the sources are evaluated, so boundaries fold rather than translate. Softness: a high exponent on the inverse-power weighting keeps every boundary blurred.
- **Resolution is three things at once**, for the same reason the glow needed both brightness and reach: losing hues fade, the field contracts toward the winner *and* up to where `categoryGlow` draws its resting pool, and the churn stills. By `resolve == 1` the mesh is geometrically the resting glow, which is what lets the two cross without a visible cut.
- **The two lit states never overlap.** Mesh alpha is `1 - resolve`, glow alpha is `resolve`. Drawing both at full strength doubles the light; sequencing them leaves a dark gap.
- **It costs nothing once settled.** The modifier is not attached and the frame clock is not running unless a card is unresolved or still resolving. Measured 2.09% janky frames, 90th percentile 12ms, with the mesh live.

Off-list categories — including `"Unsorted"`, which is what a row carries before the model answers and keeps if inference fails — resolve to the website hue, matching what `categoryStyle` returns for the same input. `categoryHueIndex` never returns -1: the mesh always has a hue to contract to, so a failed categorization still lands as a pool rather than dissolving in place. The two functions must agree, since the mesh hands off to `categoryGlow` drawing `CategoryStyle.color`.

**Do not add an artificial hold to make the resolve more watchable.** How long the mesh is on screen is the inference time, and that varies enormously by device and model variant — a Pixel 10 Pro XL on the `PreviewFast` dev-preview variant resolves in a few seconds where the `STABLE`/`FULL` default takes considerably longer. Padding the animation to suit the fastest hardware adds dead time to exactly the devices that are already slowest. The duration is data, not a design parameter.

**The chat aura** (`ui/components/ChatAuraMesh.kt`) is the second piece of the direction: the card mesh's language at screen scale, entering from *below*. One `resolve` value carries three moments — the screen arrives at 0 (the entrance veil rising with the slide-up transition), settles to 1 (a single category-hue pool under the input bar), and flares back to 0 while a reply streams. The churn is always exactly "the model is working"; there is no state where the mesh runs decoratively.

- **Bottom-anchored, no handoff.** The assistant sits where replies and the composer live, so light enters from the bottom edge — and unlike the card there is no separate `categoryGlow` to hand off to: the settled mesh *is* the resting light, so there is no seam to hide.
- **Aspect-corrected distances.** The card shader works in raw UV because a card is a wide band; on a tall screen that stretches pools into columns. Distances are in height units with x scaled by aspect.
- **Reach rides the churn.** The vertical mask's span interpolates on `calm`: thinking climbs well up the screen, settled hugs the bottom. Withdrawing is part of the resolve, not a second animation.
- **The flare and settle are asymmetric on purpose** (default spatial up, slow spatial down), matching the card dimmer's surge/decay rule.
- **The aura draws inside `imePadding`** so the light rides up with the keyboard and keeps sitting under the composer instead of being buried behind the IME.
- **AI replies carry the "lighter version" of the language**: a whisper-alpha horizontal wash (sharp leading edge at the item's hue, diffusing to a neighbour hue) over `surfaceContainerLow`. The user's side stays plain M3 — the gradient must keep meaning *the model*, not become wallpaper.

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
