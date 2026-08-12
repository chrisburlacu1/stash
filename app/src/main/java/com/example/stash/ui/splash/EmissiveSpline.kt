package com.example.stash.ui.splash

import android.graphics.RuntimeShader
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import com.example.stash.ui.theme.luminousCategoryHues
import kotlin.math.max
import kotlin.math.min

/**
 * A spectrum light field diffused through a wave-shaped emitter.
 *
 * The same idea the summarizing mesh and the chat aura carry at component scale, but as a single
 * composed image rather than a state signal. Every emitter is an analytic falloff evaluated per
 * pixel, so the diffusion is exact rather than approximated by a blur pass or an offscreen bitmap.
 *
 * **This one does not mean anything, and that is deliberate.** Everywhere else in Stash a gradient is
 * load-bearing: the card mesh *is* the model not having decided, the chat aura *is* the assistant
 * working. This field signals nothing, resolves nothing, and hands off to nothing — which is why its
 * only entry point is [SplineEasterEgg], reachable by tapping the app title on purpose. Do not wire
 * it into a real surface, and do not give it a meaning it does not have; it is expensive enough that
 * a scrolling surface must never run it.
 *
 * It was briefly a launch splash. That was abandoned: nothing at startup is slow enough to hide, so
 * the field was pure dead time on every cold start, and it double-splashed with the system's icon
 * window (~557ms cold to first frame on a Pixel 10 Pro XL, measured with `am start -W`).
 *
 * The composition is anchored to the **bottom edge** and measured in *width units* — x spans 0..1
 * across the view, y counts upward from the bottom in the same scale. Nothing references height, so
 * the light pools at the bottom and fades out at whatever height the view happens to be, and the
 * same wave appears at the same size on any aspect ratio.
 */

// ─── palette ──────────────────────────────────────────────────────────────────

/**
 * Eight emitters, ordered left to right along the wave. Nothing between them is authored — every
 * transition hue is the physical byproduct of two emitters overlapping, so an emitter's neighbours
 * decide what its blend zones look like. That is what makes the ordering below load-bearing.
 *
 * [CORE_INDEX] is the narrow hotspot at the crest and stays near-white.
 */
@Immutable
internal data class SplinePalette(
    val emitters: List<Color>,
    val haze: Color,
    val bounceHill: Color,
    val bounceTrough: Color,
) {
    init { require(emitters.size == 8) { "SplinePalette needs exactly 8 emitters" } }
}

/**
 * The blown-out core's slot: emitter index 4, at x ≈ 0.585 with intensity 1.85 and a tight radius.
 *
 * It must stay in the cream-to-white range. Pushing it to a saturated colour still renders, but the
 * peak stops reading as *overexposed* and starts reading as a lamp — the crest's blown highlight is
 * what makes the whole field look like light rather than like a painted gradient, and it is the same
 * distinction `categoryGlow` is built around ("a lamp, not a gradient" — there the lamp is the
 * goal; here the goal is the thing a lamp is not).
 *
 * So the seven category hues fill the seven *other* slots. Seven hues, eight emitters, one reserved
 * core: the arithmetic works out exactly, with nothing padded and no hue dropped.
 */
private const val CORE_INDEX = 4

private val CoreWhite = Color(0xFFFFEA9E)

/**
 * Where each category hue sits along the wave, as indices into `luminousCategoryHues()`
 * (Article, Documentation, Website, Video, Social, Blog, Repo).
 *
 * Ordered by hue angle so neighbouring emitters blend into a clean transition rather than a muddy
 * one, with the warm end running into the near-white core the way the reference composition puts
 * amber beside its crest. Stash's wheel walks red → orange → teal → blue → violet → magenta, with a
 * wide gap where a yellow or a green would be, so the sweep is:
 *
 * ```
 *  0 slate   1 magenta   2 violet   3 orange  [4 core]  5 red   6 teal   7 blue
 *    Repo      Blog        Doc        Social             Video    Website   Article
 * ```
 *
 * Two deliberate placements:
 *
 *  - **Slate (Repo) is at index 0**, the far edge. It is the least saturated hue by a wide margin
 *    (~18%), and `CategoryStyle.kt` already records that putting it at the head of the mesh's list
 *    dulled the entire field. Index 0's emitter sits at x = 0.0, half off-screen and at a lower
 *    intensity than its neighbours, so a near-grey source does the least damage there — and it still
 *    holds territory rather than being dropped.
 *  - **Orange (Social) and red (Video) flank the core.** The warm pair either side of the hotspot is
 *    what lets the crest's cream read as the top of a continuous warm ramp instead of a white blob
 *    dropped onto cool colours.
 *
 * This deliberately ignores `MeshHueOrder`. That order exists to keep adjacent *categories* looking
 * unalike, so a churning mesh reads as several possibilities in tension — the opposite of what
 * blends cleanly. Two fields, two orderings, for two different jobs.
 */
private val SplineHueOrder = intArrayOf(6, 5, 1, 4, -1, 3, 2, 0)

/**
 * Builds the emitter palette from the app's own hues.
 *
 * The hues arrive luminous (see `luminousCategoryHues`) but they were still authored as *ink*, so
 * two adjustments make them behave as emitters. Both are applied here, next to the shader that
 * needs them, rather than baked into new tokens — the palette stays traceable to the theme, and a
 * change to a category colour follows through to the splash instead of silently diverging.
 */
internal fun splashPalette(): SplinePalette {
    val hues = luminousCategoryHues()
    val emitters = SplineHueOrder.map { index ->
        if (index < 0) CoreWhite else hues[index].asEmitter()
    }
    return SplinePalette(
        emitters = emitters,
        // The ambient volume the emitters sit inside. Violet at low intensity, matching the
        // reference's cool haze: it binds the sources into one contained body of light instead of
        // eight separate blobs. Taken from Documentation's hue so even the haze is the app's.
        haze = hues[1].copy(alpha = 1f).scaleTo(value = 0.62f, saturation = 0.45f),
        // Bounce pools below the crest, where light gathers under the wave. Warm, and drawn from
        // the two hues already flanking the core so the underside stays consistent with the ramp.
        bounceHill = hues[5].scaleTo(value = 0.80f, saturation = 0.42f),
        bounceTrough = hues[4].scaleTo(value = 0.92f, saturation = 0.55f),
    )
}

/**
 * Lifts a hue toward emission: saturation up so the additive blend keeps its chroma instead of
 * washing toward grey, and value up so it has energy to give the tonemap.
 */
private fun Color.asEmitter(): Color = scaleTo(value = 0.98f, saturation = 0.72f)

/**
 * Re-tones a colour to a target value and saturation in HSV, preserving hue.
 *
 * Written out rather than pulled from a colour library because it is three lines and this is the
 * only place in the app that needs it.
 */
private fun Color.scaleTo(value: Float, saturation: Float): Color {
    val r = red
    val g = green
    val b = blue
    val mx = max(r, max(g, b))
    val mn = min(r, min(g, b))
    if (mx <= 0f) return Color(value, value, value)
    // Rebuild at the target value/saturation by scaling the existing chroma ramp: the channel
    // spread carries the hue, so stretching it between (value * (1 - saturation)) and value keeps
    // the hue exact without a full RGB→HSV→RGB round trip.
    val chroma = if (mx > 0f) (mx - mn) / mx else 0f
    val targetChroma = if (chroma <= 0f) 0f else saturation
    val lo = value * (1f - targetChroma)
    val span = value - lo
    fun ch(c: Float): Float = if (mx - mn <= 0f) value else lo + span * ((c - mn) / (mx - mn))
    return Color(ch(r).coerceIn(0f, 1f), ch(g).coerceIn(0f, 1f), ch(b).coerceIn(0f, 1f))
}

// ─── modifier ─────────────────────────────────────────────────────────────────

/**
 * Draws the light field behind whatever this modifier is applied to.
 *
 * All animated inputs are lambdas read in the draw phase, so the drift never recomposes anything —
 * the same discipline as `summarizingMesh` and `chatAura`.
 *
 * @param grain dither/film-grain strength. [GRAIN_DITHER] is the minimum that keeps a wide gradient
 *   from banding; [GRAIN_FILM] is a visible texture. Values above ~0.12 stop reading as grain and
 *   start reading as static.
 */
internal fun Modifier.splineField(
    palette: SplinePalette,
    time: () -> Float,
    reveal: () -> Float,
    grain: Float = GRAIN_FILM,
): Modifier = this.drawWithCache {
    val shader = RuntimeShader(SPLINE_SHADER)
    val brush = ShaderBrush(shader)

    palette.emitters.forEachIndexed { i, c -> shader.setColorUniform("uC$i", c) }
    shader.setColorUniform("uHaze", palette.haze)
    shader.setColorUniform("uBounceA", palette.bounceHill)
    shader.setColorUniform("uBounceB", palette.bounceTrough)

    onDrawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time())
        shader.setFloatUniform("uReveal", reveal())
        shader.setFloatUniform("uGrain", grain)
        drawRect(brush = brush)
    }
}

/**
 * The floor: enough jitter to stop a wide gradient banding, invisible as texture.
 *
 * Note this is *not* simply a small [GRAIN_FILM] — the grain term is multiplicative on exposure, so
 * these are fractions of the local signal, not of full white.
 */
const val GRAIN_DITHER = 0.02f

/**
 * Visible film grain — a physical surface over the analytic falloffs.
 *
 * Tuned on a Pixel 10 Pro XL (~490ppi). It is a *±22% modulation of local exposure*, which sounds
 * enormous and is not: the tonemap compresses it hard in the highlights, and it scales to nothing in
 * the unlit region, so what survives is texture through the mids. Below ~0.12 it stops reading as
 * grain on a dense panel; the earlier 0.055 was invisible.
 */
const val GRAIN_FILM = 0.22f

private fun RuntimeShader.setColorUniform(name: String, c: Color) =
    setFloatUniform(name, c.red, c.green, c.blue)

// ─── shader ───────────────────────────────────────────────────────────────────

/**
 * AGSL compiles at *draw* time, not build time — the source is a string the Kotlin compiler never
 * looks inside, so a typo here is an `IllegalArgumentException` from `View.draw` on the first frame,
 * not a build error. Watch for SkSL reserved words in particular (`cast` has already crashed one
 * surface in this app). A green build proves nothing: open the splash before considering it verified.
 */
private const val SPLINE_SHADER = """
uniform float2 uSize;
uniform float  uTime;
uniform float  uReveal;   // 0 = held, 1 = fully withdrawn below the bottom edge
uniform float  uGrain;    // dither strength; see the note at the bottom of main()

uniform float3 uC0; uniform float3 uC1; uniform float3 uC2; uniform float3 uC3;
uniform float3 uC4; uniform float3 uC5; uniform float3 uC6; uniform float3 uC7;
uniform float3 uHaze;
uniform float3 uBounceA;
uniform float3 uBounceB;

const float BASELINE = 0.31;   // resting height of the wave, in width units above the bottom
const float SINK     = 0.46;   // how far the baseline drops across the exit
const float K        = 3.5;    // falloff steepness — how fast light runs out of energy
const float VSTRETCH = 1.22;   // light climbs further than it spreads
const float GRAIN_PX = 2.4;    // grain cell size in device px; see grainSample()
const float FOG      = 0.018;  // base+fog: the floor grain sits on in the unlit region

float gs(float x, float c, float s) {
    float d = (x - c) / s;
    return exp(-0.5 * d * d);
}

float hash2(float2 p, float seed) {
    return fract(sin(dot(p, float2(127.1, 311.7)) + seed) * 43758.5453123);
}

// Three drifting gaussian bumps: hill left, trough right of centre, rise at the edge. Returns
// height above the bottom edge in width units, sinking as the field withdraws. Y is up.
float waveY(float x) {
    float t = uTime;
    float y = BASELINE - SINK * uReveal;
    y += 0.197 * (1.0 + sin(t * 0.1387 + 0.00) * 0.18)
         * gs(x, 0.300 + sin(t * 0.19 + 0.0) * 0.030, 0.205);
    y -= 0.084 * (1.0 + sin(t * 0.1971 + 3.57) * 0.18)
         * gs(x, 0.665 + sin(t * 0.27 + 2.1) * 0.026, 0.145);
    y += 0.142 * (1.0 + sin(t * 0.1095 + 7.14) * 0.18)
         * gs(x, 1.020 + sin(t * 0.15 + 4.2) * 0.034, 0.235);
    return y;
}

// Exponential decay normalised to reach exactly zero at the rim, so emitters have a finite extent
// without a visible cutoff edge.
float falloff(float p) {
    float e = exp(-K);
    return max(0.0, (exp(-K * clamp(p, 0.0, 1.0)) - e) / (1.0 - e));
}

float3 emit(float2 p, float2 c, float r, float inten, float3 col) {
    float2 d = float2(p.x - c.x, (p.y - c.y) / VSTRETCH);
    return col * falloff(length(d) / r) * inten;
}

// An emitter sitting on the wave at x, lifted so its core clears the crest.
float3 node(float2 p, float x, float r, float inten, float3 col) {
    return emit(p, float2(x, waveY(x) + r * 0.10), r, inten, col);
}

/**
 * One grain sample in 0..1 for a device pixel.
 *
 * Two things matter here beyond "call a hash":
 *
 *  - **Grain has a size.** Sampled 1:1 at device resolution on a ~490ppi panel the noise is finer
 *    than the eye resolves, so it averages back to flat grey and reads as nothing — which is exactly
 *    why the first attempt at this was invisible. Quantising the coordinate to GRAIN_PX-sized cells
 *    gives each grain a footprint big enough to see. Bilinearly interpolating between cells keeps it
 *    from looking like deliberate mosaic blocks.
 *  - **It has to move.** A hash of position alone is a fixed dirt pattern welded to the glass. The
 *    seed advances every frame, so the field shimmers the way projected film does. Not quantised to
 *    a lower frame rate: a 24fps cadence on a 120Hz panel means each pattern persists ~5 frames,
 *    which reads as juddering blocks rather than as grain.
 */
float grainSample(float2 fragCoord) {
    float2 cell = fragCoord / GRAIN_PX;
    float2 i = floor(cell);
    float2 f = fract(cell);
    float2 u = f * f * (3.0 - 2.0 * f);

    // A fresh seed per frame. uTime is seconds, so this is a large multiplier by design — adjacent
    // frames must land on unrelated hash inputs, not neighbouring ones.
    float seed = floor(uTime * 1000.0) * 0.618034;

    float a = hash2(i + float2(0.0, 0.0), seed);
    float b = hash2(i + float2(1.0, 0.0), seed);
    float c = hash2(i + float2(0.0, 1.0), seed);
    float d = hash2(i + float2(1.0, 1.0), seed);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float rimProfile(float x) {
    return min(1.0, 0.16 + 0.92 * gs(x, 0.575, 0.155)
                        + 0.30 * gs(x, 0.240, 0.190)
                        + 0.16 * gs(x, 0.930, 0.140));
}

half4 main(float2 fragCoord) {
    // Width units, bottom-anchored. Height is never referenced, which is what makes the
    // composition survive an arbitrary aspect ratio.
    float2 p = float2(fragCoord.x / uSize.x, (uSize.y - fragCoord.y) / uSize.x);

    // Node positions drift on non-harmonic periods so the palette never repeats.
    float x0 = 0.000 + sin(uTime * 0.13 + 0.3) * 0.020;
    float x1 = 0.190 + sin(uTime * 0.21 + 1.1) * 0.030;
    float x2 = 0.360 + sin(uTime * 0.17 + 2.4) * 0.030;
    float x3 = 0.520 + sin(uTime * 0.23 + 3.0) * 0.020;
    float x4 = 0.585 + sin(uTime * 0.31 + 0.8) * 0.014;
    float x5 = 0.700 + sin(uTime * 0.19 + 4.4) * 0.030;
    float x6 = 0.855 + sin(uTime * 0.25 + 5.2) * 0.030;
    float x7 = 1.000 + sin(uTime * 0.15 + 2.8) * 0.020;

    // 1 — ambient haze, so the field reads as one contained volume
    float3 field = emit(p, float2(0.52, waveY(0.52)), 1.15, 0.22, uHaze);

    // 2 — the emitters, accumulated additively
    field += node(p, x0, 0.72, 0.80, uC0);
    field += node(p, x1, 0.66, 0.98, uC1);
    field += node(p, x2, 0.58, 0.58, uC2);
    field += node(p, x3, 0.46, 1.30, uC3);
    field += node(p, x4, 0.17, 1.85, uC4);
    field += node(p, x5, 0.48, 0.88, uC5);
    field += node(p, x6, 0.54, 0.86, uC6);
    field += node(p, x7, 0.58, 0.68, uC7);

    // Signed perpendicular distance to the crest, positive below. Dividing by the slope keeps the
    // terminator an even width where the wave is steep.
    float h = 0.004;
    float sy = waveY(p.x);
    float slope = (waveY(p.x + h) - waveY(p.x - h)) / (2.0 * h);
    float dist = (sy - p.y) / sqrt(1.0 + slope * slope);

    // 3 — the wave occludes itself
    float3 col = field * (1.0 - 0.90 * smoothstep(0.0, 0.020, dist));

    // 4 — bounce pooling below the crest
    col += emit(p, float2(0.30, waveY(0.30) - 0.16), 0.34, 0.30, uBounceA);
    col += emit(p, float2(0.62, waveY(0.62) - 0.12), 0.26, 0.26, uBounceB);

    // 5 — the rim. Boosting the field itself means the crest picks up whatever colour is above it,
    //     so any palette stays self-consistent.
    float band = exp(-0.5 * (dist / 0.030) * (dist / 0.030));
    col += field * band * rimProfile(p.x) * 1.4;

    // The blown-out core where the wave peaks.
    float core = exp(-0.5 * (dist / 0.020) * (dist / 0.020)) * gs(p.x, 0.575, 0.075);
    col += float3(1.0, 0.965, 0.87) * core * 0.9;

    // Grain, applied to the *exposure* — before the tonemap, not after.
    //
    // This is the whole trick, and getting it wrong is why an earlier cut was invisible. Added after
    // `1 - exp(-col)` the noise is a flat ±n on final pixels: the filmic curve has already crushed
    // the shadows and compressed the highlights, so a uniform post-add is simultaneously too small
    // to see in the mids and clipped away at both ends. Perturbing the exposure instead means the
    // curve does the work — the same jitter is stretched where the response is steep (the mids and
    // the falloff shoulders, which is exactly where grain lives on film) and naturally compressed in
    // the blown-out core, so the crest stays clean without being special-cased.
    //
    // Two terms, because one cannot cover both ends of this composition.
    //
    // A purely *multiplicative* grain is the physically honest one — grain is variation in how much
    // light a spot received, so it scales with the signal — and on its own it fails badly here.
    // Measured: sd 7.0/255 through the mids (good) but sd 0.99 in the dark upper region, which is
    // most of the frame. Scaling by signal means the largest area of the picture gets no grain.
    //
    // So: a multiplicative term on exposure for the lit range, and a signal-independent floor added
    // *after* the tonemap for the shadows.
    //
    // The floor has to come after the curve, not before. Pre-tonemap it has to survive
    // `max(col, 0.0)` — and in the dark region col is already ~0, so the negative half of every grain
    // is clipped straight to black and only the bright half survives. That both halves the grain and
    // biases the whole area lighter. After the curve there is signal on both sides to move.
    float g = grainSample(fragCoord) - 0.5;
    col *= 1.0 + g * uGrain * 1.6;

    // Filmic rolloff: overbright regions converge on white instead of clipping, which is what gives
    // the peak its overexposed look.
    col = float3(1.0) - exp(-max(col, float3(0.0)));

    // The shadow floor. Also the anti-banding dither the long falloffs need, which is why it is not
    // gated on darkness — the mid falloffs band too.
    //
    // Note the small lift. Symmetric noise cannot live on pure black: clipping at zero eats the
    // negative half, so the measured variation collapses (sd 1.2/255 — invisible) and what does
    // survive biases the area brighter. Raising the unlit floor by a hair'sbreadth gives the grain
    // something to modulate in both directions. This is the film analogue too — unexposed stock is
    // never absolutely black, it is base+fog, and that faint floor is where its grain is most
    // visible. Kept small enough that the field still reads as black against the panel.
    col += FOG;
    col += g * uGrain * 0.42;

    col = clamp(col, float3(0.0), float3(1.0));

    // Opaque, over the shader's own dark ground.
    //
    // An earlier cut made alpha track the brightest channel so the field could composite over app
    // content. That is the wrong model for an emissive composition, and it was visibly wrong: the
    // whole upper region is *unlit* by design — that darkness is what the crest is bright against —
    // so letting it go transparent over a light surface turned a light field into a pastel tint
    // smeared across the content behind it, with no ground for the wave to sit on. An emissive field
    // needs something to be emissive against.
    //
    // So the ground is painted here, and fading is the *layer's* job (see the graphicsLayer in
    // SplineEasterEgg) rather than the shader's.
    return half4(col.r, col.g, col.b, 1.0);
}
"""
