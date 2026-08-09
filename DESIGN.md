# Stash — Design Intent

This is the reference for what Stash is supposed to be. When a change is unsure whether it fits, check it against this file, not against whatever the last edit happened to leave behind.

## Core loop

Share a link into Stash from any app → it's captured instantly and silently → on-device AI (Gemini Nano) extracts a title, summary, category, and tags in the background, no user input required → later, you scroll or search a single feed to rediscover it.

The user should almost never have to manually categorize or tag something. Correcting AI output is out of scope for now — if the AI gets it wrong, that's a quality problem to improve, not a workflow step to add.

## Priority: the feed

The feed (`StashMainFeedScreen` + `StashListRow`) is the screen that matters most. It's where time is actually spent — scanning and rediscovering past saves. It needs to be fast to scroll, legible at a glance (title, category, domain, freshness), and pleasant, even if other screens are rougher.

Everything else (detail view, add-URL dialog) supports the feed; it isn't the point.

## Search: a showcase Expressive moment

Search is meant to demonstrate M3 Expressive motion done well — not just a plain text field. It should use real `MotionScheme` spatial/effects springs for its expand/collapse, and it must, at minimum, reliably accept text input. A broken or non-typable search bar is a regression against this goal, not an acceptable tradeoff for "expressive."

Current implementation: `AppBarWithSearch` (Material3 `1.5.0-alpha25`) docked into the top app bar, collapsed by default, expanding on tap. As of this doc, the wiring was broken — it used a **deprecated** `SearchBarDefaults.InputField(query: String, onQueryChange: ...)` overload that does not reliably accept keyboard input in this alpha build. The fix is to move to the current `TextFieldState`-based `SearchBarState`-driven overload, which is what M3 alpha actually expects going forward.

## Motion system

The app uses M3 Expressive's spring-based `MotionScheme` (`MaterialTheme.motionScheme`, set to `MotionScheme.expressive()` in `StashTheme`). Stock Material components (FAB, FilterChip, AppBarWithSearch, AlertDialog, Button) inherit this automatically — don't hand-roll separate springs for them. Custom animations (e.g. the filter-chip `AnimatedVisibility` in the feed) should pull `defaultSpatialSpec`/`defaultEffectsSpec` (or `fast`/`slow` variants) from `MaterialTheme.motionScheme` rather than using Compose's stock `tween()` defaults or hardcoded `spring()` values.

## Data flow (unchanged, working)

`MainActivity` wires a single `RoomStashRepository` (Room + `GeminiNanoSummarizer`) and hands it down through `StashAdaptiveLayout` → `StashFeedViewModel` → `StashMainFeedScreen`. Share-sheet intents (`ACTION_SEND text/plain`) call `repository.addUrl` directly. `addUrl` inserts a placeholder row immediately, then extracts page text and calls Gemini Nano in the background, then upserts the final row. This part of the app works and matches the intended core loop — leave it alone unless asked.

## What NOT to do

- Don't introduce a second/competing search or capture UI alongside the existing one (this happened before — a whole unused `FloatingSearchCapsule.kt` component sat dead in the codebase).
- Don't leave imports or scaffolding for a feature (e.g. shared element transitions) without actually wiring it up, or remove it if abandoned.
- Don't use deprecated Compose Material3 APIs when a current, non-deprecated overload exists and is only marginally more code — deprecation warnings in this alpha-dependency codebase have already caused a real bug once.
- No git repo currently backs this project. Until one exists, treat every working state as fragile — verify builds AND actually run on-device before considering a UI change done.
