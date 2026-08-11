# Design notes

A running log of things that looked wrong, and what turned out to be causing them.

The point is the pairing: the **symptom** is what you notice, usually as a vague "something's off".
The **cause** is the thing you couldn't name yet. Reading these back should make the next one easier
to spot — most design problems recur in different clothes.

Newest first.

---

## The keyboard made the whole screen move, and the fix was not in the layout

> "everything like bounces in like flex about" … "the things from the top and the bottom all bounce,
> which I wouldn't expect. I would expect the keyboard to come up and the input box to animate with
> it. and that's about it."

**Symptom: opening the chat composer moved the header, the transcript, and the welcome text.** Then,
after a first fix attempt, worse — the composer sat stranded in the *middle* of the screen with a
gap beneath it, nowhere near the keyboard it was supposed to dock above.

**Cause: the activity had no `android:windowSoftInputMode`, so it defaulted to `adjustResize` —
the window itself was shrinking.** Every layout inside it was being measured against a canvas that
had already lost its bottom third. The composer *was* correctly aligned to `BottomCenter`; the
bottom had simply moved up the screen.

Two attempts were spent inside Compose before checking this: moving `imePadding()` off the root
`Box` onto the composer, then restructuring the `Column` so the composer floated over it instead of
sitting in its layout flow. Both are the right shape for the final design, and neither could
possibly have worked on its own — **no arrangement of insets inside a window can give back space
the window no longer has.** The fix was one manifest attribute, `adjustNothing`, which is also the
standard pairing for the `enableEdgeToEdge()` that was already in `MainActivity`.

**Generalises to:** when a symptom is "everything moves", suspect the container before the
contents. Compose insets can only distribute the space a window actually has — if the window is
resizing, that is a platform-level contract and it is set in the manifest, not in a modifier chain.
The handoff document had listed the missing `windowSoftInputMode` as unverified context worth
checking; it went unchecked through two failed attempts, which is its own lesson about reading the
notes you were left.

A consequence worth remembering: `adjustNothing` means *nothing* is resized for the keyboard, so
every other surface with a text field has to consume `WindowInsets.ime` itself or run underneath
the IME. The feed's search results needed `imePadding()` added for exactly this reason.

---

## The feed got slow because chat needed the page body

**Symptom: "the list render is very slow. scroll is slow and skippy. when i select a filter chip
and unselect it, the rerender of the list is jerky and v bad."** Four plausible causes were read
out of the source — oversized thumbnail decodes with no bitmap cache, `categoryGlow` rebuilding its
gradient every frame, the filter flow tearing down and resubscribing the whole list — and ranked.

**Cause: none of the visual suspects. A `content` column added for chat was being dragged through
every feed query by `SELECT *`.** Giving chat the full page body (the extractor caps at 600,000
chars) meant `stash_items` rows got very large. The three list queries selected `*`, so every feed
emission loaded every row's entire article text, allocated a String per row, and discarded it on
the next emission. Nothing in the feed renders `content`; it is read in exactly one place, for one
item at a time.

The fix was a projection — `StashListRow`, every column except `content` — leaving the single-item
query as `SELECT *`. Scroll and filter toggling went from "v bad" to fine. The other three
diagnoses were never needed, and were deliberately left alone rather than fixed on spec: once the
symptom is gone they are speculative optimisation against a problem nobody can feel.

**Generalises to:** a feature that changes how much data a row *holds* changes the cost of every
query that reads rows, even queries that predate the feature and ignore the new column. `SELECT *`
is what makes that coupling invisible — the query does not mention `content`, so nothing about the
feed's code suggests the feed pays for it. Ranking suspects by what is *visually* expensive put
three rendering causes above the one that was actually a data-shape change; the newest change in
the diff deserved to be suspect number one on the grounds of recency alone.

---

## Turning the aura's opacity up to fix a dim, muddy chat screen

The chat aura was polished from a written spec by a different model. Four of the five fixes landed
as specified. The two that did not both failed the same way, and the spec had explicitly forbidden
both — which is the interesting part.

**Symptom: the settled aura read as murk rather than light, and the churn as a wash.** The mesh's
opacity ceiling had gone from `0.46` to `mix(0.44, 0.76, calm)` — up 65% at full churn — with the
comment "vibrant 0.76 during full-spectrum churn". The vertical reach had gone to `0.92` and the
source band to `0.70 ± 0.25` at the same time.

**Cause: reaching for opacity when the real complaint was legibility.** "I can't make out the
hues" feels like "it isn't strong enough", and opacity is the nearest knob. But seven accumulating
sources have no natural ceiling — raising it saturates exactly the regions where sources overlap,
so every hue boundary that made the spectrum readable is the first thing to disappear. You get
*more colour* and *less variety*, which perceives as murk. The card mesh already learned this as
the painted slab; the same mistake arrived in a new coat, one surface over.

The right levers were geometric and were already in the shader: the source band and the vertical
mask, both interpolated on `calm` so they widen for churn and pull in for rest. Widening those
makes the same light show more hues. Opacity cannot.

**Second failure, same shape: a `tween` in among the springs.** The settle spec became
`tween(1200, FastOutSlowInEasing)`, replacing `slowSpatialSpec`, and the entrance hold went to the
spec's stated 900ms ceiling — so the aura was busy for over two seconds and the settle drifted
against the slide-up spring it is supposed to arrive with. This is the motion rule in CLAUDE.md
verbatim ("things animating together must share an animation *spec*, not a duration"), rediscovered
by breaking it.

**Also: an `animateDpAsState` spring laid over the IME inset**, replacing `imePadding()`. The
platform already animates that inset, so this was a spring on top of a spring — and a spatial
spring's overshoot drove the padding negative, which throws from the layout pass. The band-aid was
`.coerceAtLeast(0.dp)`, which hides the crash and keeps the double animation. A spring is the wrong
tool for a value that is already a system-driven animation.

**Generalises to:** when an effect reads as weak, ask what is *occluding* it before turning it up —
the previous entry in this log is the same lesson, and both times the tempting knob (palette,
opacity) was downstream of a geometry problem. And a written rule saying "do not raise the alpha
ceiling to make things more visible" does not survive contact with the symptom it was written for
unless the *mechanism* travels with it. Rules in specs need their reason attached, or they read as
arbitrary at exactly the moment they apply.

---

## The seven-hue mesh only ever showed two hues

> "I'm only getting green and pink in the summary effect. Also it only covers a thin slice at the
> top of the card."

Reported as two problems. They were one bug, plus the fix for a *previous* problem being wrong.

**Cause: a falloff cut clipped the field, and the clipping is what killed the colour.** Sources sat
at `sy = 0.42 ± 0.30`, spanning 0.12–0.72 of card height. The vertical falloff was
`(1 - smoothstep(0, 0.62, uv.y))²` — expired by 0.62, then *squared*. Over half the sources were
drawn below the surviving band. Only the two whose paths crossed that strip were ever visible, and
those two happened to be teal and pink.

So "only two hues" was not a colour bug at all. The mesh was rendering all seven correctly; five of
them were being multiplied to nothing by a geometry mistake.

**The deeper error: I changed two things at once when fixing the painted slab.** That fix added an
alpha ceiling *and* pulled the falloff from 0.92 to 0.62. The ceiling was doing the work; the
falloff change was collateral, and it caused this. Classic — a real fix and a speculative one
shipped together, and the speculative one is the bug.

Second contributor, found while fixing: free-roaming Lissajous paths let sources bunch up, so even
with the band restored, whichever hues clustered near the visible region swamped the rest. Sources
are now anchored to evenly spaced horizontal lanes and drift *around* them, which guarantees every
hue holds territory while still letting them wander into each other's. The vertical squash also
came down from 1.65 to 1.25 — at 1.65 each source was a wide flat ellipse, and lane-spread sources
in wide ellipses smear into their horizontal neighbours.

**Generalises to:** when a component of an effect seems missing, check whether something is
multiplying it to zero before assuming the component itself is wrong. "Only two colours" pointed at
the palette and the blending; the bug was in the alpha mask. And: **fix one thing at a time**, or
the next bug is one you introduced while fixing the last.

---

## The summarizing mesh came out as a painted slab

First run of the mesh gradient on device. The effect worked — seven hues, drifting, bleeding into
each other — but the card read as *coloured in* rather than *lit*, and the title and eyebrow were
fighting the header for legibility.

**Cause: seven accumulating sources have no natural ceiling.** Coverage is summed per source and
squashed with `coverage / (coverage + 1)`, which approaches 1 wherever several sources overlap —
i.e. most of the upper card. The resting glow peaks at `GLOW_ALPHA = 0.44` and had that ceiling
written down; the mesh had nothing equivalent, so it drew at effectively full opacity.

Two fixes, both about matching the register the card already established: a `0.50` peak-alpha
ceiling, and pulling the vertical falloff in from 0.92 to 0.62 of card height so the light dies
before the summary panel instead of washing over the tags.

**Generalises to:** when a new effect joins an existing one, find the number the old effect uses to
stay in bounds and give the new one its equivalent. The mesh wasn't too saturated or too colourful —
it was unbounded where its neighbour was bounded. Also a second sighting of *the falloff is the
effect*: an ambient light that keeps going stops reading as a light.

---

## A spinner next to the mesh would have demoted it

Not a symptom noticed after the fact — caught while wiring up, but the same shape of mistake.

The summarizing card had a `CircularProgressIndicator` beside the "Summarizing…" label. Leaving it
in would have put a stock indeterminate spinner directly on top of a bespoke effect that says the
same thing with far more specificity. The eye reads the spinner as the real progress indicator and
the mesh as decoration behind it — exactly backwards, and it would have undercut the one place the
app's visual language does actual work.

The label stayed. The mesh says *thinking*; it doesn't say *about what*, and text is cheap.

**Generalises to:** when a custom visual takes over a job a stock component was doing, remove the
stock component. Two indicators for one state means the generic one wins, because that is the one
users already know how to read.

---

## The jump between states felt too big

> "The default closed state of lighting needs a bit more intensity — the difference is quite a lot
> when we expand it."

**Cause: the problem was the ratio, not either brightness.** Neither state was wrong alone. But a
dim card jumping to a bright one reads as a *switch being thrown*, where the effect wants to read as
a light being turned **up** — both states lit, the expansion changing the degree.

The fix has two halves, and only doing the first would have failed: raise the resting alpha (0.34 →
0.44) **and** trim the multiplier (2.1 → 1.75) so the lit peak stays where it already looked right.
Raising the floor alone drags the ceiling with it — same gap, everything louder.

**Generalises to:** when a transition feels too dramatic, check whether either end is actually wrong.
Often both are fine and the gap is the problem, which means moving one end *toward* the other rather
than adjusting either in isolation.

---

## The light lagged behind the card on close, but matched on open

> "The expand animation and lighting effect are perfectly in sync but the closing ones aren't…
> it closes and then just like a beat of the animation of the light closing."

**Cause: two animations describing one event were using different specs.** The card body runs on
Material's `defaultSpatialSpec` — a *spring*. The glow was on a hand-picked `tween`: 620ms up,
900ms down. Opening, those happened to land close enough to look synchronised. Closing, the card
settled in ~380ms and the light kept fading for another half-second.

**The fix is not a faster duration — it's the same spec.** Springs are duration-free; they settle
when the physics says so. Any hand-picked duration matches by luck on one edge and drifts on the
other, so the only way two things stay locked across *both* directions is to share the spring.

Actual expressive-scheme values, pulled from the artifact since the docs don't list them:

| Spec | Damping | Stiffness |
|---|---|---|
| defaultSpatial | 0.8 | 380 |
| fastSpatial | 0.6 | 800 |
| slowSpatial | 0.8 | 200 |
| defaultEffects | 1.0 | 1600 |
| fastEffects | 1.0 | 3800 |

Note the pattern: **spatial springs bounce (damping < 1), effects springs don't (damping = 1).**
Things that move overshoot; things that fade shouldn't. And "fast" is 2× the stiffness of default.

**Generalises to:** if two animated things drift apart, check they share a spec before touching any
numbers. Mixing spring and tween guarantees drift.

---

## The depth overshot

> "I think we overshot it with the depth, it's a bit much."

**Cause: fixing "not deep enough" by going most of the way to "as deep as possible".** Spread gain
went 0.45 → 0.9, which flooded the card — the pool swallowed the key points and the falloff stopped
being visible, so it read as a tinted card again rather than a lit one.

The falloff *is* the effect. A light with no visible falloff is just a background colour.

**Generalises to:** when correcting an undershoot, the fix usually sits nearer the middle than the
far end. 0.6 was right.

---

## The lit card felt shallow

> "I feel like there's more there. I don't know if it may be the light spread deeper."

**Cause: brightness increased, reach didn't.** Turning a light up in place makes a *brighter small
pool*, which the eye reads as a highlight sitting on the header — not as the card being lit. Real
light does two things at once when you turn it up: it gets brighter *and* it throws further.

Fixed by growing the gradient's radius nearly in step with its alpha, and flattening the falloff
curve as it brightens so light carries further before fading.

**Generalises to:** any "more of the same, but it still feels flat" moment. Ask what *else* changes
in the physical version of the effect. Scaling one property is usually the tell.

---

## The animation felt stiff even at the right speed

**Cause: the spec was tuned for a different job.** Material's motion scheme is built for UI that
must feel *responsive to touch* — it resolves fast because a delay reads as lag. An ambient effect
is the opposite: it wants to be watched. At scheme speed the light finished before the card had
stopped expanding, so it read as a colour change rather than something switching on.

Also: **rise and fall should rarely match.** Lights surge and then decay. Doors, drawers and sheets
usually open faster than they close. Symmetrical timing is the default and almost always the flat
choice.

---

## The card looked flat and uninteresting

> "The card content looks flat and uninteresting."

**Cause: every element had the same visual weight and the same left edge.** Six blocks — title,
pill, headline, key points, tags, domain — all starting at x=16, all separated by similar gaps, all
equal in priority. The eye had nowhere to land first and no path through.

That's what flatness *is*. It isn't a lack of colour or decoration; it's a lack of hierarchy. Adding
a glow to a flat stack would have made it a flat stack with a glow.

Fixed by giving the summary its own recessed panel (a second surface breaks the single column),
moving the category pill above the title so it opens rather than competes, and tightening the title's
leading so a multi-line headline reads as one block.

**Generalises to:** if a layout feels boring, count the things at the same weight. More than three or
four and that's the problem.

---

## The image fade always had a visible band

> "Still not right but better." … "Still a bit retarded."

**Cause: the two ends were too far apart, and no gradient shape can hide that.** A near-black photo
meeting a near-white card is a huge luminance jump. The visible band *is* that jump — so lengthening
the gradient made a wider band, shortening it made a narrower one, and blurring moved it.

Four attempts failed because they all changed the *shape* of a transition whose problem was its
*endpoints*. The only fixes were closing the gap (tint the card toward the image) or removing the
transition (crop the image smaller).

**Generalises to:** when tuning a transition isn't working, check whether the problem is the
transition at all. Endpoints beat curves.

---

## The tinted card fixed the fade but felt worse

**Cause: a fix aimed at one region was applied globally.** Tinting the whole card closed the
luminance gap, but the title, byline and tag chips then all sat on tinted ground — the chips stopped
separating from their background and the card read as a grey slab rather than a white surface
holding content.

**Generalises to:** when a fix "works" but the result feels worse, check its blast radius. The fix
was right; the scope was wrong.

---

## The category spine looked broken

> "The spine kind of fucks with the elevation of the card and makes it look off."

**Cause: a square-cornered bar inside a rounded container gets clipped into a wedge.** At the top-
and bottom-left it was sliced by the card's corner radius, which reads as a rendering fault. It also
fought the soft silhouette an elevated card is built on.

Fixed by dropping the bar and moving the category colour into a pill that has its own shape.

**Generalises to:** decoration should agree with the container's shape language. Hard edges inside
soft containers almost always look like mistakes.

---

## Grain: invisible, then static, with nothing in between

**Cause: three multiplied variables.** Opacity, spread (how far grains sit from the mean) and grain
size each affect visibility, so tuning one at a time overshoots. At 0.09 opacity with a wide spread
it was television static; at 0.03 with a narrow spread it vanished.

Useful rule discovered here: **if you can see it as grain, it's too strong.** Texture should register
as the surface having tooth, not as visible speckling.

Abandoned — the usable band was too narrow to be worth it.

---

## The swipe panel looked like a separate object

**Cause: fully rounded corners on something meant to be *behind* the card.** A panel with its own
radius on all four sides reads as a second object floating beside the card. Matching the card's
radius on the outer edge and leaving the inner edge square makes the two share one silhouette — the
card slides across, and what appears behind it continues the same shape.

Related, later: at rest a narrow panel's square corners poked out past the card's rounded ones. Fixed
by giving the panel the card's exact footprint and shape.

---

## The swipe panel would not stay open — third attempt, and the last

**Cause: `SwipeToDismissBox` has no half-open state, and the fix was to stop wanting one.** Asked to
put two choices behind a swiped card (on-device chat vs. the Gemini app), the obvious build is a
panel that parks open with two buttons in it. That had already failed twice — `confirmValueChange`
either returns true and the card flies off-screen, or false and it springs back. There is no third
value. Attempts to fake one lost the panel on finger-up or threw the card away.

Parking open genuinely needs `AnchoredDraggable` with three anchors. But both swipe edges share one
state object, so that rewrite puts the tuned delete gesture — bounce, damping, confirm dialog — in
the blast radius of what is really just "pick one of two".

So the swipe kept its existing mechanics, and the choice moved to a modal bottom sheet. The sheet
turned out to be the better surface anyway, and not as a consolation: two icons behind a card cannot
say *private, offline, on-device* versus *stronger, online, leaves your device*, and that distinction
is the entire reason the choice exists. The sheet has room for a line of prose each. A parked panel
would have shipped the feature with its most important information missing.

**Generalises to:** when an interaction fights the component three times, the interaction is probably
wrong, not the component. Ask what the gesture is *for* — here, conveying a trade-off — and pick the
surface that can carry that, rather than the one the first sketch assumed.

---

## The sheet's light was a flat lilac tint

**Cause: the radius came from width, on a surface that is mostly width.** The card glow scales its
radius from card width and that reads correctly, so the sheet copied it. But a card is a wide band
lit from a point, while a bottom sheet is wide *and short* — a width-derived radius overshot the
sheet's height several times over, so every pixel sat near the pool's centre, no part of the falloff
ever landed inside, and the light arrived as one even wash. Scaling from **height** puts the visible
part of the curve on the surface.

Then it was still invisible, and the instinct was to raise the alpha. Wrong lever, and the same one
the mesh already got wrong: most of this pool sits *below* the bottom edge and off-screen, where the
card's sits mostly inside the card, so the sheet only ever catches the tail. The fix was moving the
source closer to the edge (0.30 → 0.06 of radius) so the usable part of the falloff is the part on
screen. Peak alpha stayed at the 0.46 ceiling.

Last, a pale unlit band survived along the very bottom — the sheet reserves a navigation-bar strip
below its content, and that strip is outside anything drawn inside it. So the light died a few dp
short of the exact edge it was supposed to be entering from, which is the one place it cannot afford
to. Zeroing `contentWindowInsets` and re-applying the inset *after* the glow modifier lets the wash
run under the gesture bar while the rows still clear it.

**Generalises to:** *a light's geometry has to come from the dimension it travels along.* Width for a
card lit across its face, height for a surface lit from an edge. And when a light reads as too dim,
check where its centre is before touching its opacity — an off-screen source and a dim source look
identical on device but have opposite fixes.

---

## Recurring themes

- **Check the endpoints before tuning the curve.** (image fade)
- **Ask what else changes in the physical version.** (lighting)
- **Flatness is a hierarchy problem, not a decoration problem.** (card layout)
- **Match the container's shape language.** (spine, swipe panel)
- **Rise and fall shouldn't be symmetrical.** (lighting)
- **When a fix makes things worse, question its scope, not the fix.** (card tint)
- **Motion specs are tuned for a purpose — responsive ≠ ambient.** (lighting)
- **Things animating together must share a spec, not a duration.** (glow sync)
- **Correcting an undershoot usually lands mid-range, not at the far end.** (glow depth)
- **A transition that feels too dramatic is often a ratio problem, not a value problem.** (glow states)
- **A new effect needs the same bounds as the one it sits beside.** (mesh alpha ceiling)
- **If part of an effect seems missing, look for what's multiplying it to zero.** (mesh hues)
- **Fix one thing at a time — a speculative fix shipped alongside a real one becomes the next bug.** (mesh falloff)
- **When a custom visual takes a stock component's job, delete the stock component.** (mesh spinner)
- **When an interaction fights the component three times, the interaction is wrong.** (swipe panel)
- **A light's geometry comes from the dimension it travels along.** (sheet glow)
- **A dim light and an off-screen light look identical; check the centre before the alpha.** (sheet glow)
- **Light means presence; churn means work — don't spend one to decorate the other.** (sheet glow)
