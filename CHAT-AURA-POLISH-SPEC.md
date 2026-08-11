# Chat aura polish — implementation spec

A hand-off spec for making the chat screen's aura feel *emissive, fluid, and alive*, per the
Gemini visual-design reference (<https://design.google/library/gemini-ai-visual-design>). The
feature already works end-to-end; this is motion/visual polish only. **No data-layer, ViewModel
logic, or navigation changes are needed** beyond what is written here.

Read `DESIGN-NOTES.md` and the Design section of `CLAUDE.md` first. The rules that bind every
change below:

- **Things animating together share an animation *spec*, never a hand-picked duration.**
- **Animated values are read through lambdas in the draw phase** (`() -> Float`), so a 60fps
  animation never recomposes the screen. Every existing call site already does this — keep it.
- **No spinners next to the mesh.** The mesh is the progress indicator; text labels only.
- **Match the register**: the aura's alpha ceiling is `0.46` (`a * 0.46 * uAlpha` in the shader).
  Do not raise it while making things "more visible" — fix geometry/motion instead.
- **Fix one thing at a time.** Build and eyeball between steps; do not batch speculative tweaks.

## What was diagnosed (from a device screenshot, Pixel, dark-ish light theme, Blog/pink item)

1. **Entrance settles too early.** The screen is supposed to arrive with a full-palette mesh
   churning up from the bottom, which then settles into a single category-hue pool. In practice
   `auraResolve` starts animating 0 → 1 the instant the screen composes, so the veil has mostly
   resolved before the slide-up navigation transition finishes. The user only ever sees the end
   state.
2. **The settled state is dead.** At `resolve == 1` the shader's `calm` is 0 (no domain warp, no
   drift) *and* the Kotlin side stops the frame clock, so the resting pool is a frozen vertical
   gradient. It reads as a painted wash, not as light. It needs residual inner activity —
   glowing, not static.
3. **Header is cramped.** The category pill + domain eyebrow sits almost on top of the title, and
   the back arrow's centered alignment makes the eyebrow look like a stray row above it.
4. **Streaming replies are static surfaces.** The reply bubble's gradient wash never moves, even
   while the reply is being generated. Per the reference, motion should mirror creation: moving
   while the text is still arriving, still once it exists.

All four fixes are below, in recommended order. Files involved:

| File | Role |
|---|---|
| `app/src/main/java/com/example/stash/ui/components/ChatAuraMesh.kt` | the AGSL shader + `Modifier.chatAura` |
| `app/src/main/java/com/example/stash/ui/chat/StashChatScreen.kt` | drives `auraResolve`, the clock, header, bubbles |
| `app/src/main/java/com/example/stash/ui/components/SummarizingMesh.kt` | reference only — sibling shader; `rememberMeshClock` lives here. **Do not edit.** |

Verify with `./gradlew assembleDebug` (or `installDebug` on a device). AGSL only fails at
*runtime* — a shader typo compiles fine in Kotlin and crashes when the screen opens, so device
verification after any shader edit is mandatory.

---

## Fix 1 — Let the entrance veil live for the length of the transition

**File: `StashChatScreen.kt`**, inside `StashChatScreen`, where `auraResolve` is driven.

Current code:

```kotlin
val auraResolve = remember { Animatable(0f) }
val flareSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val settleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
LaunchedEffect(state.isResponding) {
    if (state.isResponding) {
        auraResolve.animateTo(0f, flareSpec)
    } else {
        auraResolve.animateTo(1f, settleSpec)
    }
}
```

Replace with:

```kotlin
val auraResolve = remember { Animatable(0f) }
val flareSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
val settleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()

// The veil holds full churn while the screen itself is still travelling, and only begins to
// settle once the surface has landed. The hold length is not an aesthetic pause: it is the
// visible travel time of the entry transition (defaultSpatialSpec, damping 0.8 / stiffness 380,
// lands in roughly 600ms), so the light and the surface arrive as one event. If the route's
// transition spec ever changes, revisit this number with it.
var entranceSettled by remember { mutableStateOf(false) }
LaunchedEffect(Unit) {
    delay(ENTRANCE_CHURN_MILLIS)
    entranceSettled = true
}
LaunchedEffect(state.isResponding, entranceSettled) {
    val churn = state.isResponding || !entranceSettled
    if (churn) {
        auraResolve.animateTo(0f, flareSpec)
    } else {
        auraResolve.animateTo(1f, settleSpec)
    }
}
```

And at file bottom, with the other private constants (create the section if absent):

```kotlin
/**
 * How long the entrance veil holds full churn before settling, in ms. Matched to the visible
 * travel of the chat route's slide-up spring (defaultSpatialSpec ≈ 600ms) plus a beat to
 * register — the veil rides the transition, then the settle is its own watchable moment.
 */
private const val ENTRANCE_CHURN_MILLIS = 650L
```

Imports to add: `kotlinx.coroutines.delay` (the `mutableStateOf`/`getValue`/`setValue`/`remember`
imports already exist in this file).

Why this does not violate the "no artificial hold" rule in `CLAUDE.md`: that rule is about
*inference* time (the summarizing mesh's duration is data). The entrance is choreography — its
duration is the navigation transition's duration, which is a design constant, not data. Say so in
a comment if you touch the wording (the snippet above already does).

**Tuning:** if the veil still feels blink-and-miss on device, raise to 800ms. Do not exceed
~900ms — past that the settle starts *blocking* the first interaction instead of accompanying it.

## Fix 2 — A living resting state (the "glow")

Two halves, shader + clock policy. Both must land together or nothing changes visually
(the shader's residual motion is invisible if the clock is frozen, and an always-running clock is
pure battery cost if the shader ignores time at rest). This is the one permitted exception to
"one change at a time" — they are one change.

### 2a. Shader: residual warp and pool drift at rest

**File: `ChatAuraMesh.kt`**, inside `CHAT_AURA_SHADER`.

Change 1 — the domain warp currently dies completely at rest:

```glsl
float warpAmount = 0.075 * calm;
```

becomes:

```glsl
// Never fully still: a floor of residual warp keeps the settled pool folding slowly, so the
// resting state reads as a glow with inner life rather than a printed gradient. The floor is
// deliberately far below the churn level — the pool must feel settled, just not dead.
float warpAmount = mix(0.020, 0.075, calm);
```

Change 2 — the resting pool centre currently sits pinned at one point:

```glsl
float2 restCenter = float2(0.5, 1.10);
```

becomes:

```glsl
// The resting centre breathes on a slow Lissajous of its own, so even a fully settled pool
// drifts a little — a lamp flame, not a screenshot. Amplitudes are tiny relative to the pool
// radius; at these values the drift registers subliminally rather than as movement.
float2 restCenter = float2(
    0.5 + 0.020 * sin(uTime * 0.11),
    1.10 + 0.025 * cos(uTime * 0.07)
);
```

Nothing else in the shader changes for this fix.

### 2b. Kotlin: keep the clock alive while the chat is open

**File: `StashChatScreen.kt`**:

```kotlin
val auraRunning = state.isResponding || auraResolve.value < 1f
val auraClock by rememberMeshClock(running = auraRunning)
```

becomes:

```kotlin
// Unlike a feed of settled cards, this is one foreground surface that exists to be looked at,
// and its resting light is designed to breathe (see the residual warp in the shader). So the
// clock runs for the life of the screen: withFrameNanos stops ticking whenever the window
// stops drawing, so a backgrounded app pays nothing.
val auraClock by rememberMeshClock(running = true)
```

Do **not** copy this policy back to `StashCardRow`/`SummarizingMesh` — a feed multiplies the cost
by visible rows and its meshes are supposed to go inert. That asymmetry is intentional; leave a
comment saying so if it looks inconsistent.

**Tuning:** residual warp floor 0.020 (range 0.012–0.030; above ~0.035 the resting pool looks
like it is still thinking). Drift amplitudes 0.020/0.025 (halve them if the pool visibly slides;
the target is "was that moving?", confirmed only by staring).

## Fix 3 — Make the churn's spectrum legible

The screenshot's settled wash also hints the churn band may be too compressed to show seven
distinct hues (the card mesh had exactly this failure mode — see "The seven-hue mesh only ever
showed two hues" in `DESIGN-NOTES.md`: check what is *multiplying variety to zero* before blaming
the palette).

**File: `ChatAuraMesh.kt`**, in `sourcePos`:

```glsl
float sy = 0.86 + 0.10 * cos(t * (0.16 + fi * 0.017) + phase * 1.7);
```

becomes:

```glsl
// Wider vertical band than the first cut: at ±0.10 the seven sources sat in a strip thin
// enough that horizontally adjacent hues swamped each other (the card mesh's two-hue bug in a
// new coat). ±0.14 gives each source room to hold territory up the screen as well as across it.
float sy = 0.84 + 0.14 * cos(t * (0.16 + fi * 0.017) + phase * 1.7);
```

And in `main`, the vertical mask during churn:

```glsl
float reach = mix(0.40, 0.78, calm);
float base = mix(0.14, 0.28, calm);
```

becomes:

```glsl
// Churn reaches a little higher (0.84) so the full spectrum has somewhere to be seen during
// the entrance and while thinking; the settled pool pulls in slightly (0.34) so the resting
// state is unmistakably a pool at the bottom, not a tint over half the screen.
float reach = mix(0.34, 0.84, calm);
float base = mix(0.12, 0.28, calm);
```

**Acceptance check on device:** during the entrance and while a reply streams, you should be able
to name at least four distinct hues in the veil (blue, violet, teal, red/orange territory). If
you still see only two, the problem is geometric occlusion, not colour — widen the `sy` band
further or reduce the x-wobble (`0.17` in `sx`) before touching hues, weights, or alpha.

## Fix 4 — Motion in the streaming reply

The reply bubble's wash should move while the reply is being generated and freeze once it is
complete — motion mirrors creation. Mechanism: the gradient's span slides on a slow sine
(ping-pong, so there is no wrap seam), driven by the same aura clock, read in the draw phase.

**File: `StashChatScreen.kt`.**

1. `AssistantMessage` gains two parameters and swaps its static second `background` for a
   `drawBehind`:

```kotlin
@Composable
private fun AssistantMessage(
    text: String,
    leadHue: Color,
    tailHue: Color,
    /** True only while this reply is still streaming in; the wash drifts exactly as long. */
    streaming: Boolean,
    /** The aura clock, read in the draw phase so the drift never recomposes the transcript. */
    clock: () -> Float,
) {
    val shape = MaterialTheme.shapes.large.copy(bottomStart = CornerSize(6.dp))
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            lineHeight = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .padding(end = 24.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .drawBehind {
                    // While streaming, the wash's leading edge breathes back and forth on a
                    // slow sine — the text is being written, so its light is in motion. A sine
                    // ping-pong rather than a scroll: a looping translation needs a seam or a
                    // third stop to hide the wrap, and the seam always shows eventually.
                    // At rest (streaming = false) the phase is exactly 0, so a finished reply
                    // is pixel-identical to one that was never animated.
                    val phase = if (streaming) {
                        kotlin.math.sin(clock() * 0.9f) * size.width * 0.22f
                    } else 0f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0.0f to leadHue.copy(alpha = 0.14f),
                            0.55f to tailHue.copy(alpha = 0.06f),
                            1.0f to tailHue.copy(alpha = 0.02f),
                            startX = phase,
                            endX = size.width + phase,
                        ),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
```

2. The call site inside the `LazyColumn`'s `items` block passes the new arguments. The streaming
   message is, by construction in `StashChatViewModel.send`, the **last** message when it is not
   from the user and a response is in flight:

```kotlin
AssistantMessage(
    text = message.text,
    leadHue = style.color,
    tailHue = hues[(winner + 1) % hues.size],
    streaming = state.isResponding && message.id == state.messages.lastOrNull()?.id,
    clock = { auraClock },
)
```

`auraClock` is already in scope (it drives the aura). No ViewModel change: do not add a
"streaming" flag to `ChatMessage` — the position-based check above is sufficient and keeps the
transcript model dumb.

Note `Brush.horizontalGradient`'s stop overload accepts `startX`/`endX` — keep the colour stops
as vararg pairs exactly as above. `kotlin.math.sin` is fine fully qualified or imported.

## Fix 5 — Header breathing room

**File: `StashChatScreen.kt`**, `ChatHeader`. Three changes, all spacing/alignment:

```kotlin
Row(
    verticalAlignment = Alignment.Top,                    // was CenterVertically
    modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),  // was top 4 / bottom 8
) {
    IconButton(onClick = onBack) { /* unchanged */ }
    Column(
        modifier = Modifier
            .weight(1f)
            .padding(top = 10.dp),                        // optically aligns eyebrow with the icon
    ) {
        /* eyebrow Row unchanged */
        Spacer(Modifier.height(6.dp))                     // was 2.dp — the eyebrow needs air
        /* title Text unchanged */
    }
}
```

Rationale: top-aligning the row stops the back arrow floating relative to a two-line title, and
the pill/title gap goes from "touching" to the same 6–10dp rhythm the card uses between its
eyebrow and title. Also give the transcript some initial clearance: in the `LazyColumn`'s
`contentPadding`, `vertical = 12.dp` → `PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp,
bottom = 12.dp)` (remember: `reverseLayout = true`, so the visual top spacing is the `top` value
of contentPadding at the *end* of the reversed list — Compose maps contentPadding logically, top
is still visual top; just set both and check on device).

---

## What NOT to do

- Do not raise the shader's `0.46` alpha ceiling to make anything more visible.
- Do not replace the AGSL shader with stacked `Brush.radialGradient`s (composites to grey —
  documented failure).
- Do not add a spinner, shimmer placeholder, or typing-dots indicator; the aura + "Thinking…"
  label is the complete thinking state.
- Do not animate with `tween` anywhere near the springs. If two things move together, hand them
  the same MotionScheme spec.
- Do not make `SummarizingMesh`/card clocks always-run "for consistency".
- Do not persist chat transcripts while you are in these files. Out of scope, deliberate choice.

## Verification checklist (device required — AGSL errors only surface at runtime)

1. `./gradlew installDebug`, open any summarized item by swiping its card left→right.
2. **Entrance:** the veil rides up with the screen at full multi-hue churn, holds ~0.65s, then
   visibly settles into a single-colour pool at the bottom. You should *see* the settle happen.
3. **Rest:** stare at the settled pool for five seconds. It should breathe — slow folding, slight
   drift. If you can describe its motion, it's too strong; if it's a screenshot, too weak.
4. **Send a question:** the pool flares back to full-spectrum churn (fast), churns while
   "Thinking…" and while text streams, settles (slow) when the reply completes.
5. **Streaming bubble:** the reply's wash drifts sideways while text arrives and is perfectly
   still afterwards; a finished reply looks identical to the pre-change design.
6. **Spectrum:** ≥4 nameable hues during churn.
7. **Header:** eyebrow, title, and back arrow no longer collide; two-line titles keep the arrow
   top-aligned.
8. **Cost:** after everything settles and no reply is in flight, the only ongoing work is the
   single aura draw (intended). Scroll the feed afterwards to confirm cards are still inert.
9. If any step *diagnosed* a symptom/cause pair along the way, append it to `DESIGN-NOTES.md`
   (newest first, matching its format).
