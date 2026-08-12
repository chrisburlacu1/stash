# Stash — MVP workstreams

Everything left before Stash is MVP-complete. Features are done; this is definition and cleanup.

Each workstream below is written to be handed to an agent on its own worktree branch. Read
`CLAUDE.md` and `DESIGN-NOTES.md` first — several of the obvious-looking moves in here are recorded
there as already-tried failures.

## The workstreams

| | Workstream | Why it matters | Blocking? |
|---|---|---|---|
| **I** | Release readiness | No R8, **no signing config**, `com.example` namespace, backup on with stub rules | **Yes — nothing ships without it** |
| **J** | Error states | 4 `AiState` values written, 1 rendered. Nano failure is **invisible** | **Yes — headline feature fails silently** |
| **A** | Colour scheme | Palette was never actually chosen; dynamic colour unwired | Yes |
| **B** | Category taxonomy | Blog/Article redundant; 4 sites must agree | Yes |
| **D** | Search → top bar | Search and tag filtering are two unrelated interactions | Yes |
| **C** | Settings screen + FAB | Settings living in the bottom toolbar; FAB centre → bottom-right | Yes |
| **H** | Card split + rename | 1,170-line file, wrong name, verified dead code, theme bug | Yes |
| **G** | Design tokens | 9 inline shape/type decisions bypassing the theme | Fold into H |
| **K** | Privacy claim | Docs claim "nothing leaves the device"; two services say otherwise | Yes (docs only) |
| **E** | Chat revisit | Built before several redesigns | Diagnose first |
| **F** | Mesh revisit | Card moved underneath it | Diagnose first |
| **L** | Test floor | Zero real tests; 3 high-risk pure functions | Optional |

**If you do nothing else before MVP: I and J.** I is half a day of mechanical work standing
between you and an installable artifact. J is the difference between "this app's summaries are bad"
and "this device can't run the model" — on most hardware, that is the user's first impression.

## Conflict map — read before parallelising

The workstreams are ordered by how much they collide, not by importance. Files touched by more than
one stream are the whole risk of running these in parallel.

| File | A. Colour | B. Categories | C. Settings | D. Search | E. Chat | F. Mesh | G. Tokens | H. Card |
|---|---|---|---|---|---|---|---|---|
| `ui/theme/Color.kt` | **owns** | — | — | — | — | — | — | — |
| `ui/theme/Theme.kt` | **owns** | — | reads | — | — | — | — | reads |
| `ui/theme/CategoryStyle.kt` | — | **owns** | — | — | — | reads | — | reads |
| `ui/theme/Type.kt` | — | — | — | — | — | — | **owns** | — |
| `data/StashSettings.kt` | adds key | — | **owns** | — | — | — | — | — |
| `ui/feed/StashMainFeedScreen.kt` | — | — | edits | **edits** | — | — | — | edits |
| `ui/feed/FeedTopBar.kt` | — | — | edits | **edits** | — | — | — | — |
| `ui/feed/FeedToolbar.kt` | — | — | **edits** | edits | — | — | — | — |
| `ui/feed/ModelMenu.kt` | — | — | **owns** | — | — | — | — | — |
| `ui/feed/FeedContent.kt` | — | — | — | edits | — | — | edits | **edits** |
| `ui/feed/FeedSearch.kt` | — | — | — | **owns** | — | — | — | edits |
| `ui/feed/StashFeedViewModel.kt` | — | — | edits | **edits** | — | — | — | edits |
| `ui/chat/StashChatScreen.kt` | — | — | — | — | **owns** | — | edits | edits |
| `ui/components/StashCardRow.kt` | — | reads | — | — | — | reads | edits | **owns** |
| `ui/components/SummarizingMesh.kt` | — | reads | — | — | — | **owns** | — | reads |
| `ui/components/AskAboutItemSheet.kt` | — | reads | — | — | — | — | — | edits |
| `ai/OnDeviceSummarizer.kt` | — | **owns** | — | — | — | — | — | — |

Workstreams I (release), J (error states), K (privacy docs) and L (tests) sit almost entirely
outside this table:

| File | I. Release | J. Errors | K. Privacy | L. Tests |
|---|---|---|---|---|
| `app/build.gradle.kts` | **owns** | — | — | — |
| `AndroidManifest.xml`, `res/xml/*` | **owns** | — | — | — |
| `ai/OnDeviceSummarizer.kt` | edits (logging) | — | — | — |
| `data/local/RoomStashRepository.kt` | edits (logging) | **edits** (url) | edits (opt 2) | — |
| `ui/components/StashCardRow.kt` | — | **edits** | — | — |
| `CLAUDE.md` | — | — | **owns** | — |
| `app/src/test/**` | — | — | — | **owns** |

**The hard conflict is C and D.** Both restructure the feed's chrome — C pulls settings *out* of
the bottom toolbar and into a top-bar route, D replaces the top bar with `AppBarWithSearch` and
pulls search out of the bottom toolbar. They touch the same three files with the same intent
(dismantle `FeedToolbar` down to a bare FAB) and *will* conflict.

**Recommended sequencing:**

- **Wave 0, start now, parallel with everything:** I (release readiness), K (privacy docs),
  L (tests). None of them contend for a file any design workstream touches, and **I is the one
  that actually blocks shipping** — it should not wait behind design work.
- **Wave 1, fully parallel:** A (colour), B (categories), E (chat), F (mesh).
- **Wave 2, sequential:** D (search) then C (settings). D lands the new top bar; C adds a settings
  action to the bar D created. Running C first means D rewrites C's work.
- **Wave 3:** H (card split), with G (design tokens) folded into it.

**J (error states) is the exception to the wave structure.** It edits `StashCardRow.kt`, so it
collides with H. Either run it in wave 1 and let H absorb it, or fold it into H — but **do not
defer it past H**, because it is the highest-value product fix on the list and H is the last wave.

A and B both concern colour but do not overlap in code: A owns the M3 scheme in `Color.kt`/
`Theme.kt`, B owns the category hues in `CategoryStyle.kt`. Keep that boundary — see A's note on
why category hues stay out of dynamic colour.

E and F are independent of everything, including each other. They are the safest to run first.

**H goes last, and G goes with it.** `StashCardRow.kt` is the most contended file in the project —
B, E, F and G all touch it. H moves most of its contents to new files, so any of those landing
after H would conflict against code that has moved. G's card edits are three `lineHeight` lines
that H is relocating anyway; doing them as separate branches guarantees a conflict for no benefit.
**Fold G into H rather than running it as its own worktree** — or if G runs alone, run it before H
and expect H to rebase.

**Two cross-cutting items to watch:**

- **A and H share the `isSystemInDarkTheme` bug** (H §4). A owns `Theme.kt`; H fixes the three call
  sites that bypass it. Whichever lands second should verify the fix still holds.
- **B changes the category set, which F's hue count depends on.** Same wave — either run B first or
  have F treat the hue count as an input rather than a constant.

---

## A. Colour scheme — define and decide

**Status:** new workstream. Nothing decided yet.

**Why:** the palette is a Material Theme Builder export that was never actually chosen — a
desaturated blue (`#4A5D92`) nobody picked. The app needs a colour identity it commits to.

**Scope:**

1. **Decide the base palette.** Pick a deliberate seed hue for Stash and regenerate light/dark
   schemes from it. This is a design decision, not a mechanical one — bring options before writing
   them in.
2. **Delete what is unused.** `Color.kt` is ~220 lines, of which the four medium/high-contrast
   schemes (`*LightMediumContrast`, `*LightHighContrast`, `*DarkMediumContrast`,
   `*DarkHighContrast`) are referenced by nothing. `res/values/colors.xml` still holds stock
   `purple_200`/`teal_200`/`purple_500` template stubs. Both go.
3. **Wire dynamic colour to a real setting.** `StashTheme` already takes `dynamicColor: Boolean`
   but it is hard-coded `false` at the call site. Add a preference (`StashSettings`, same
   name-keyed pattern as `themeMode`) and surface it — the toggle itself belongs to workstream C,
   so if C has not landed, add the preference and leave the UI to C.
4. **Document the token roles.** A short section in `DESIGN-NOTES.md` recording which token does
   what and why, so the next change does not drift.

**Constraint — do not put category hues into dynamic colour.** The seven category hues in
`CategoryStyle.kt` must stay fixed across both themes and under Material You. They encode *content
type*, not brand, and `SummarizingMesh` depends on all seven being mutually distinguishable. A
wallpaper-derived palette cannot guarantee that. Accept that on some wallpapers a category pill
will sit close to the surface hue, and note the trade in `DESIGN-NOTES.md`.

**Done when:** one committed palette, dead schemes deleted, dynamic colour is a working preference,
token roles documented.

---

## B. Category taxonomy — refine into distinct types

**Status:** new workstream.

**Why:** the current set has redundancy. "Blog" and "Article" are semantically near-identical and
the model has no reliable way to choose between them — so the distinction is noise in both the
prompt and the palette. A category is only worth having if a person can tell at a glance why an
item got it.

**Current set** (`categoryStyle` / `categoryHueIndex` in `CategoryStyle.kt`): Article,
Documentation, Blog, GitHub repo / Code, Video, Tweet / Discussion, Website.

**Scope:**

1. **Redefine the set.** Merge or drop the redundant ones, and make each remaining category
   answer a distinct question about what the thing *is*. Keep it at or under seven — the mesh
   blends every hue at once and more than seven stops reading as distinct possibilities.
2. **Update all three places that must agree.** This is the trap:
   - `categoryStyle()` — label, icon, hue.
   - `categoryHueIndex()` — mesh resolve target. Must never return -1, and must map every input
     to the same hue `categoryStyle` gives it, or the colour changes at mesh→glow handover.
   - `MeshHueOrder` — the fixed order, deliberately *not* spectrum-sorted (adjacent hues sit apart
     on the wheel so the mesh reads as competing possibilities).
   - `ai/OnDeviceSummarizer.kt` — the closed `enumValues` set on the `@Generable`
     `OrganizedResponse`. **Changing this class requires KSP regeneration and it must stay
     public.**
3. **Handle existing rows.** Items already in the database carry old category strings. Decide:
   map them in `categoryStyle`'s `when` (cheap, keeps history readable) or migrate. Do not let an
   old value fall through to the unknown branch silently.

**Done when:** the set is distinct, all four sites agree, and existing saved items still render a
sensible category.

---

## C. Settings screen + FAB reposition

**Depends on D.** Land D first.

**Why:** summarization settings currently live in a dropdown anchored inside the bottom floating
toolbar (`ModelMenu` in `FeedToolbar`). That toolbar is for *acting on the stash*; settings is not
that. `FeedTopBar`'s doc comment already anticipates this move.

**Scope:**

1. **New settings route.** A third Nav3 destination alongside `FeedRoute` and `ChatRoute` in
   `StashAdaptiveLayout`. Follow the existing per-entry transition-metadata pattern.
2. **Move settings content in:** summarization effort + model picker (currently `ModelMenu`),
   theme mode, and the dynamic-colour toggle from workstream A. Model probing must stay lazy —
   each variant costs a `checkStatus()` IPC, so probe when the screen opens, not at startup.
3. **Retire the top bar's theme toggle.** It becomes redundant once theme mode is in settings.
4. **Collapse the bottom toolbar to a plain FAB, bottom-right.** With search gone (D) and settings
   gone (C), the toolbar has one action left — add. Replace `HorizontalFloatingToolbar` with a
   plain FAB and switch `FabPosition.Center` → `FabPosition.End` in `StashMainFeedScreen`.
   `FeedToolbar.kt` and `ModelMenu.kt` likely both disappear.
5. **Check the list's bottom padding.** `FeedList` reserves `bottom = 96.dp` to clear the floating
   toolbar; a plain FAB needs less.

**Done when:** settings is its own screen reachable from the top bar, and the feed's only floating
action is a bottom-right add FAB.

---

## D. Search — move to the top bar

**Blocks C.** Land this first.

**Why:** search currently opens as a full-screen surface launched from the bottom toolbar, with no
visual connection to what launched it. It belongs at the top, and M3 now has the component for it.

**Scope:**

1. **Adopt `AppBarWithSearch`.** Confirmed present in the resolved
   `androidx.compose.material3:material3:1.5.0-alpha25`. It replaces `FeedTopBar` — the app title
   and the search affordance become one bar. Work out the current signature from the artifact;
   it is alpha and the shape has changed release to release.

   **Use the component's defaults.** This is a stock M3 Expressive component and its motion is
   part of it. Do not hand-roll the expand/collapse transition, and do not reach for a custom
   spec — take what it ships with.
2. **Unify filtering and searching.** This is the substantive part. Today they are two independent
   pipelines in `StashFeedViewModel` *by design*: `feedItems` ignores the query, `searchResults`
   ignores tags. Tag chips narrow the feed; the query searches the whole stash and shows results
   somewhere else. That means narrowing your stash is two unrelated interactions with no combined
   state. Make tags and query one narrowing model over one list.
   - Watch the resubscription trap the current split was built to avoid: do not wrap both in a
     `flatMapLatest` over `(query, tags)` or the feed tears down and re-queries on every keystroke.
   - `repository.observe(query, tags)` already accepts both.
3. **Preserve what the current surface got right** (see comments in `FeedSearch.kt`): reset scroll
   on query change, and keep `imePadding()` on any scrolling result list — the activity is
   `adjustNothing`, so under that mode *every* surface with a text field consumes the IME inset
   itself.
4. **Remove search from the bottom toolbar.**

**Done when:** search is a top-bar interaction, tags and query narrow one list together, and the
old full-screen surface is gone.

---

## E. Chat interface — revisit

**Not planned in detail. Diagnose first, then decide.**

**Why:** the chat screen was built before several rounds of card and feed redesign and has not
been updated since. It is likely inconsistent with where the rest of the app landed rather than
wrong in a specific way.

**Scope:** run the chat screen against the current feed and card design and report what is actually
inconsistent — identity treatment, typography, spacing, how the item header relates to the card's
eyebrow-then-title order. Bring findings before changing anything.

**Read `DESIGN-NOTES.md` first — specifically "Turning the aura's opacity up to fix a dim, muddy
chat screen".** This surface has already been polished once by an agent working from a spec, and
it regressed: the alpha ceiling went up, a `tween` landed among the springs, and a spring got laid
over the IME inset. All three were explicitly forbidden and all three happened anyway, because the
rules travelled without their reasons. The constraints that matter:

- Alpha ceiling stays `0.46`. If the mesh reads as weak, the problem is geometric occlusion — the
  source band and the vertical mask — not opacity.
- Things animating together share an animation **spec**, not a duration. No hand-picked `tween`
  among the springs.
- No `imePadding()` on the root, and no spring over the IME inset. The platform already animates
  it. `ChatInputBar` is the only composable that consumes it.

**Done when:** findings reported and agreed, then applied.

---

## F. Summarizing mesh — revisit

**Not planned in detail. Diagnose first, then decide.**

**Why:** same as E — the card redesign moved underneath it, most recently the full-width header
image. The mesh may no longer sit correctly against the card it lights.

**Scope:** look at the mesh against the current card and report what is off. Coordinate with
workstream B: if the category set changes, the mesh's hue count and resolve targets change with it.

**Constraints from `DESIGN-NOTES.md`:**

- **AGSL compiles at draw time.** A shader typo is a runtime crash, not a build error, and SkSL
  reserved words (`cast`) have caused exactly that. A green build proves nothing — open the
  surface.
- **No artificial hold to make the resolve more watchable.** How long the mesh is on screen is
  inference time, which varies enormously by device and model variant. Padding it penalises the
  slowest devices most.
- **Do not give the card mesh an always-on frame clock.** The chat aura has one; the card's stops
  when resolved. A feed multiplies that cost by every visible row. The asymmetry is deliberate.
- **Fix one thing at a time.** The last two mesh bugs were both speculative fixes shipped
  alongside real ones.

**Done when:** findings reported and agreed, then applied.

---

## G. Design tokens — one source of truth

**Status:** new workstream. Small, mechanical, low-conflict — but touches many files, so land it
when the others are quiet.

**Why:** shape and typography decisions are being made inline at call sites instead of coming from
the theme. M3 Expressive already owns these values (`ExpressiveShapes`, `Typography` in
`ui/theme/`); anywhere a call site re-decides them, the theme has stopped being the source of truth
and the next theme change silently misses that spot.

**Measured scope — this is smaller and more specific than it feels:**

1. **Typography — the real violation. 6 sites.** Hardcoded `lineHeight` overriding the type scale:
   - `ui/components/StashCardRow.kt:393` (30.sp), `:524` (21.sp), `:793` (24.sp)
   - `ui/chat/StashChatScreen.kt:389` (22.sp), `:449` (24.sp), `:529` (20.sp)

   These are per-call-site adjustments to styles that already exist. Fold them into `Type.kt` — if
   a style genuinely needs a different line height, it is a different named style, not an inline
   override. Drop the now-unused `androidx.compose.ui.unit.sp` imports.

2. **Shape — 3 sites, all in one place.** `ui/feed/FeedContent.kt:191-193`, the filter chip's
   `shape` / `selectedShape` / `pressedShape` at 12/20/8dp. `ExpressiveShapes` already defines
   `small = 12.dp` and `extraSmall = 8.dp`, so two of the three duplicate existing tokens by
   value. Use the tokens. The 20dp selected shape has no equivalent — either add a token or
   justify it in a comment as a deliberate one-off.

3. **Write the rule down** in `DESIGN-NOTES.md`, because this will drift again otherwise: shape and
   typography always come from `MaterialTheme`; a literal at a call site needs a comment saying why.

**Explicitly not in scope:**

- **The ~116 `.dp` literals.** Nearly all are spacing and padding. **M3 has no spacing scale** —
  there is no `MaterialTheme.spacing`, and Compose does not tokenise this. Converting them would
  mean inventing a system Material does not have, which is a bigger and more debatable change than
  the one being asked for. Leave them.
- **The 5 hardcoded colours**, all legitimate and all should stay:
  - `ui/components/GeminiMark.kt:109-112` — Google's Gemini brand palette. These must *not* follow
    the app theme or they stop being the Gemini mark.
  - `ui/splash/EmissiveSpline.kt:70` — an emitter colour, the same category as
    `luminousCategoryHues()`, which is deliberately theme-independent for the reason documented on
    it: emitters are not ink and do not take the ambient theme.

**Conflicts:** touches `StashCardRow.kt`, `StashChatScreen.kt`, `FeedContent.kt`, `Type.kt`.
Overlaps E (chat) and F (mesh) by file. Run it *after* E and F land, or accept a small merge — the
edits are line-local and shallow, so conflicts will be easy either way.

**Done when:** no inline `lineHeight` or `RoundedCornerShape` outside `ui/theme/`, and the rule is
documented.

---

## H. The card — split up, rename, remove what is dead

**Status:** new workstream. Highest-conflict file in the project — see sequencing note.

**Why:** `ui/components/StashCardRow.kt` is ~1,170 lines holding at least six unrelated concerns,
and the name is wrong. It is not a "row" — the compact list variant it was named for was deleted
when cards started expanding in place. It is the feed's card.

**Scope:**

### 1. Rename

`StashCardRow` → `StashCard`, file to `StashCard.kt`. Four call sites
(`FeedContent.kt:76`, `FeedSearch.kt:137`, plus imports). Two doc comments elsewhere reference the
old name by hand (`StashChatScreen.kt:158`, `SheetStripLight.kt:273`) — update them or they become
lies.

### 2. Split by concern

Suggested split — the agent should confirm the seams before committing to them:

| New file | Contents |
|---|---|
| `StashCard.kt` | The composable itself: state, layout, expansion. |
| `CardHeaderImage.kt` | Header image, bitmap decode, scrim, pill, open-link affordance (~130 lines). |
| `CardGlow.kt` | `Modifier.categoryGlow`, `Color.saturated`, the six `GLOW_*` constants (~120 lines). |
| `CardSwipePanels.kt` | `DeleteSwipePanel`, `ChatSwipePanel`. |
| `CardParts.kt` | `KeyPoints`, `TagChip`, `MetaDot` — small shared pieces. |

`categoryGlow` is the one to think about: 11 references, and it is the app's signature visual. It
is currently private to this file. `SheetStripLight.kt:273` already comments that a related helper
is private here and that hoisting it "would mean a third file" — so this split is partly resolving
a tension that is already written down.

### 3. Delete what is dead — verified, not suspected

- **`SWIPE_EXIT_DISTANCE_PX`, `SWIPE_DAMPING`, `SWIPE_STIFFNESS`** (`:1109`, `:1116`, `:1119`).
  One reference each — their own declaration. Left over from the abandoned swipe-to-reveal
  attempts (three of them, per DESIGN-NOTES). Dead.
- **The commented-out read-state block** (`:888-912`), 25 lines of `//`-prefixed code, plus the
  now-unused `CheckCircle` import at `:38`.
- **`CardMetaRow`'s `accent` parameter** — unused once the block above goes.
- **Then follow the thread.** With that block gone, `CardMetaRow` only renders "Summarizing…".
  `onToggleRead` is plumbed from `StashMainFeedScreen.kt:91` → `StashItemActions` →
  `FeedContent.kt:82` / `FeedSearch.kt:140` → the card, and reaches nothing. Either delete the
  parameter chain or restore a read affordance — but decide, rather than leaving it threaded
  through four files doing nothing. Note `repository.setRead` is still called on expand
  (`onExpand`), so read state itself stays meaningful; it is only the *toggle* that is orphaned.

### 4. Fix the theme bug this surfaced

`StashCardRow.kt:148` calls `isSystemInDarkTheme()` directly to pick category hues. So does
`StashChatScreen.kt:113` and `AskAboutItemSheet.kt:65`. **That bypasses the user's `ThemeMode`
setting** — `MainActivity.kt:56` resolves System/Light/Dark properly and hands the result to
`StashTheme`, but these three read the *device* setting instead. Pin the theme to Dark on a
light-mode device and the card's category hues stay in light-mode form against a dark surface.

Correct source is `MaterialTheme.colorScheme` luminance, or hoist the resolved flag via a
`CompositionLocal`. **Coordinate with workstream A** — under dynamic colour this gets worse, and A
owns `Theme.kt`.

### 5. Architecture

Match the [official Compose layering guidance](https://developer.android.com/develop/ui/compose/architecture):
the card is a stateless composable driven by parameters, which it broadly already is. Keep it that
way — do not introduce a card-level ViewModel. If a UI-state holder emerges from the split, it is a
plain class, not a `ViewModel`.

**Do not treat this as a redesign.** Every non-obvious value in this file has a recorded reason
(scrim alpha, glow constants, header height, `maxLines`, the missing spinner). Moving code between
files must not change a single rendered pixel — **carry the comments with the code they explain.**
Their reasons are the file's real value, and most are load-bearing.

**Conflicts:** `StashCardRow.kt` is touched by E (chat, by reference), F (mesh, via
`summarizingMesh`), G (tokens — 3 of its 6 `lineHeight` fixes are in this file), and B (categories,
via `categoryStyle`). **Run H after B, E and F, and fold G into it** — G's card edits are three
lines that H will be moving anyway, so doing them separately guarantees a conflict.

**Done when:** no file over ~400 lines, name matches what it is, verified-dead code gone, the
`isSystemInDarkTheme` bug fixed, and the app renders identically.

---

## I. Release readiness

**Status:** new workstream. Unglamorous, mostly mechanical, and **nothing ships without it.** No
design decisions here — this is the gap between "runs on my device" and "installable artifact".

Touches build config and the manifest almost exclusively, so it conflicts with nothing. **Can run
in parallel with any other wave, including wave 1.**

### 1. The release build cannot currently be built

`app/build.gradle.kts`:

```kotlin
buildTypes { release { optimization { enable = false } } }
```

No R8, no minification, no resource shrinking, and **no `signingConfig` at all**. Fix:

- Enable R8/minification and resource shrinking for release, add a `proguard-rules.pro`.
- Add a signing config that reads from `local.properties` or environment — **never commit the
  keystore or its credentials** (see `CLAUDE.md`, Security & Data).
- Verify the minified release build actually runs. Room, KSP-generated ML Kit schema classes
  (`OrganizedResponse_GeneratedProvider`) and `kotlinx.serialization` all involve reflection or
  generated code and are the usual first casualties of R8. **The `@Generable` class must survive
  shrinking** — if it is stripped, structured output silently falls back to the prompt-JSON path.
- Size matters here: `material-icons-extended` is a large artifact and the app uses roughly ten
  icons from it. Confirm shrinking removes the rest, or import the individual icons instead.

### 2. `namespace` / `applicationId` is a placeholder

Both are `com.example.stash`. **`com.example` cannot be published to Play.** `applicationId`
cannot be changed after release without breaking every upgrade path, so this must be decided
before the first shipped build, not after.

### 3. Backup is on with stub rules

`android:allowBackup="true"`, and both `res/xml/backup_rules.xml` and
`res/xml/data_extraction_rules.xml` are the **unmodified template comments** — nothing is
configured. The Room database, DataStore preferences and cached header images are therefore all
eligible for Google cloud backup.

For an app positioned as "no data leaves the device" this contradicts the core claim. Either set
`allowBackup="false"` or write real `<exclude>` rules. `CLAUDE.md` already flags this file as
needing careful review.

### 4. Debug logging ships in release

~15 `android.util.Log.d` calls in `ai/OnDeviceSummarizer.kt`, plus
`data/local/RoomStashRepository.kt:283` — which logs the **full URL of every saved link**. None are
guarded by `BuildConfig.DEBUG`, and with R8 off none are stripped. Guard them, or route through a
small logging helper that no-ops in release. Keep them in debug: the extraction log is how the
"286 chars of navigation menu" bug was found.

### 5. Confirm `compileSdk = 37`

`compileSdk` is 37 against `targetSdk = 36` — compiling against a preview SDK. Probably
deliberate; confirm it is, and that it is not what a release build should pin.

**Done when:** a signed, minified release APK builds, installs, and runs — including a save that
exercises the KSP-generated structured-output path.

---

## J. Error states — make failure visible

**Status:** new workstream. The highest-value *product* fix on this list.

**Why:** `AiState` has five values. `RoomStashRepository.kt:139-142` writes four of them
(`Ready`, `Failed`, `Failed`, `Unavailable`). The UI reads **one** —
`StashCardRow.kt:150` checks `== AiState.Summarizing` and nothing else.

So when summarization fails, or Gemini Nano is unavailable on the device, the card renders a
truncated-extract fallback with **no indication anything went wrong**. On any device without
AICore — which is most devices — the app's headline feature fails invisibly and reads as "the
summaries are bad" rather than "the model isn't available here". That is the wrong first
impression, and it is a one-surface fix.

There is currently no error surface anywhere in the app: no Snackbar host, no error state, nowhere
a failure can be shown.

### Scope

1. **Render `Failed` and `Unavailable` on the card.** They are different messages and should read
   differently — `Unavailable` is "this device can't run the model" (permanent, not the user's
   fault, and not retryable), `Failed` is "this one didn't work" (retryable). Distinguish the two
   `Failed` branches at `:140` and `:141` if that proves useful — one is "no content extracted",
   the other is "the model was available but returned nothing".
2. **Decide whether a retry exists.** A failed save currently has no path forward except deleting
   and re-adding the link. `Failed` is genuinely retryable; `Unavailable` is not.
3. **Consider a first-run explanation.** If Nano is unavailable, every save will fail the same
   way. Telling the user once is better than a feed of identical failed cards.
4. **Add a Snackbar host** if one is needed for add-URL failures. `ui/feed/FeedToolbar.kt:40`
   already documents the FAB-slot arrangement that keeps a Snackbar stacking above the toolbar —
   note this interacts with workstream C, which replaces that toolbar with a plain FAB.

### Design constraint

**Do not reach for the gradient/mesh language here.** Per `CLAUDE.md`, the mesh means "the model is
working"; a failure is the opposite, and the `AskAboutItemSheet` precedent is that the visual
language is deliberately absent where nothing is working. A failed card should be quiet and plain —
state, not spectacle. It should also not look like the app is broken: an unavailable model is an
expected condition on most hardware.

### Related, same area

**URL normalization is `startsWith("http")`** (`RoomStashRepository.kt:79`). Two problems:

- `httpfoo.com` passes the check and is treated as already-normalized, producing a malformed URL.
- It silently permits `http://`, but the manifest declares neither `usesCleartextTraffic` nor a
  network security config, so the API-28+ default blocks cleartext — the fetch fails with an
  opaque error the user never sees.

Normalize via `URI.scheme` instead. Decide explicitly whether `http://` links are supported: if
yes, that needs a network security config; if no, say so in the UI rather than failing silently.

**Done when:** every `AiState` the repository can write is visible to the user, and a malformed or
cleartext URL produces a message rather than silence.

---

## K. Reconcile the privacy claim with what the code does

**Status:** small, but do it deliberately. This is a documentation-accuracy problem, not a bug —
and for an app whose entire positioning is privacy, the claim has to be true.

**What the code does:** saving an x.com / twitter.com link contacts **two third-party services**
that are neither the user's chosen host nor on-device:

- `data/local/RoomStashRepository.kt:412` — `api.fxtwitter.com`
- `data/local/RoomStashRepository.kt:425` — `publish.twitter.com/oembed` (fallback)

Both receive the full post URL. The reason is sound and already commented: x.com serves a
JS-rendered shell that returns no content, so without this the model invents a plausible post from
nothing. This is a reasonable engineering trade.

**What the docs claim** — both statements are currently false:

- `CLAUDE.md`, Security & Data: "**All** summarization/extraction happens on-device — do not
  introduce network calls that send page content or user data off-device."
- `CLAUDE.md`, Ask-about-an-item: the sheet's second row is "**the only place in the app** where
  data leaves the device".

**Scope — pick one:**

1. **Narrow the claim.** Update both statements in `CLAUDE.md` to describe what actually happens,
   and make sure any user-facing copy (store listing, onboarding) says "on-device summarization"
   rather than "nothing ever leaves your device". Cheapest, and honest.
2. **Make it opt-in.** A setting for "fetch social posts via a third-party service", default off,
   with tweets degrading to title-only when disabled. More work, and preserves the absolute claim.

Note the ordinary page fetch is **not** a problem and should not be touched — that is the user's
own link being fetched from the host they chose, which is what saving a link means.

**Also worth stating plainly somewhere user-facing:** chat never re-fetches the page (it is
grounded in stored data only), and the feed makes no network requests while scrolling because
header images are cached at save time. Those are genuine, unusual privacy wins that the app
currently does not tell anyone about.

**Done when:** no statement in the repo or in user-facing copy overstates the privacy guarantee.

---

## L. A minimal test floor

**Status:** optional for MVP, cheap enough to be worth it. `app/src/test` and `app/src/androidTest`
currently contain **only the two generated template files** (`ExampleUnitTest`,
`ExampleInstrumentedTest`) — there are no real tests.

Broad coverage is not an MVP goal. But three pure functions carry most of the silent-regression
risk in the app, and all three are testable as plain JVM unit tests with no device:

1. **`articleText()` extraction** (`data/local/RoomStashRepository.kt`). The jsoup selector cascade
   has broken twice already — the 286-chars-of-navigation bug, and the page whose `<article>` held
   the table of contents while the prose sat in a sibling `div.prose`. Both are recorded in
   `CLAUDE.md`. **Check both of those in as fixture HTML with an assertion on extracted length**;
   they are known-failing inputs that currently pass only by manual observation.
2. **`categoryHueIndex` / `categoryStyle` agreement.** These two must return the same hue for the
   same input or the colour changes at the mesh→glow handover, and `categoryHueIndex` must never
   return -1. **Workstream B is about to change both**, which is exactly when a property test over
   every category string plus junk input pays for itself.
3. **`relativeSavedLabel`** (`models/RelativeTime.kt`). Pure, boundary-heavy, trivially testable.

`StashRepository` is an interface and `RoomStashRepository` takes its dao and summarizer as
constructor parameters, so a fake drops straight in — the seam is already there.

Note the summarizer itself **cannot** be unit tested: AICore needs a real device, and per
`CLAUDE.md` it will not run in the background at all.

**Done when:** the two template files are gone and the three above have tests. Roughly 40-60 lines.

---

## Out of scope for MVP

- **Logo / launcher icon.** Being produced separately with a dedicated tool. The current adaptive
  icon is still the stock bugdroid on `#3DDC84`; when the mark arrives, note that `monochrome`
  currently points at the coloured foreground, which is wrong for themed icons.
- **Parked branches:** `morphing-toolbar`, `feature/app-functions`, `feature/ask-about-item`,
  `feature/gemini-chat-polish`, `feed-redesign`. Not MVP blockers, and none of them are a starting
  point for the workstreams above — do not go mining them for reusable work. In particular
  `morphing-toolbar` is *not* related to workstream D: it was hand-rolled motion between bottom
  toolbar states, whereas D adopts a stock component and uses its defaults.
