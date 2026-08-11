# Design notes

A running log of things that looked wrong, and what turned out to be causing them.

The point is the pairing: the **symptom** is what you notice, usually as a vague "something's off".
The **cause** is the thing you couldn't name yet. Reading these back should make the next one easier
to spot — most design problems recur in different clothes.

Newest first.

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
