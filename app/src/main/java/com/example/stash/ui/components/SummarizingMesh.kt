package com.example.stash.ui.components

import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The mesh gradient shown while Gemini Nano is deciding what a saved link *is*.
 *
 * This is the app's design language doing real work rather than decorating. Every other surface
 * shows one category colour because the category is known. Here it is not known yet — so the card
 * shows all seven at once, drifting through each other, and collapses to the single right one at
 * the moment the model answers. The visual is literally the state: undecided, then decided.
 *
 * That is the distinction the reference material draws between a gradient as ornament and a
 * gradient as a context builder. A spinner says "working"; anything can say that. This says
 * *what* is being worked out, and its ending is the answer arriving.
 *
 * ## Why a shader and not stacked radial brushes
 *
 * The obvious cheap version is seven `Brush.radialGradient` layers animated on offset. It was not
 * built that way for two reasons. Overlapping translucent radials composite toward grey — seven of
 * them average out to exactly the muddy neutral the effect must avoid, and the existing glow
 * already needed [saturated] to fight that at *one* layer. And they cannot interact: real mesh
 * gradients bleed into each other, where stacked layers slide over one another like cut paper.
 *
 * In AGSL the seven fields are resolved per pixel and normalised by their total weight, so the
 * result is always a true blend of contributing hues at full chroma, never a wash toward grey.
 *
 * minSdk is 34 and `RuntimeShader` is API 33, so there is no fallback path to maintain.
 *
 * ## The shape of the motion
 *
 * Three ideas from the reference, each with a specific mechanism here:
 *
 *  - **Sharp leading edge, diffuse tail.** Each source's falloff is asymmetric with respect to its
 *    own direction of travel: the field is compressed ahead of the source and stretched behind it,
 *    so a hue arrives with a defined front and smears out behind. A symmetric radial reads as a
 *    blob that happens to be moving; this reads as something travelling.
 *  - **Inner activity.** The sample point is domain-warped by a slow noise field before the sources
 *    are evaluated, so the boundaries between hues churn and fold rather than merely translating.
 *    Movement mirrors processing instead of being applied to it.
 *  - **Softness.** Field weights use a high exponent on a smooth falloff, which keeps every
 *    boundary blurred. Nothing in the mesh has an edge — the uncertainty is legible as fuzziness,
 *    which is the point.
 *
 * ## Resolution
 *
 * `resolve` runs 0→1 when the answer arrives. Three things happen together, because a light coming
 * up in a single dimension reads as flat — the same lesson the card's glow already learned:
 *
 *  1. Non-winning hues fade out, weighted so the winner takes over rather than everything dimming.
 *  2. The remaining field contracts toward the winner's position and up to the card's top edge,
 *     landing where [categoryGlow] draws its resting pool.
 *  3. The churn slows to nothing, so the card settles rather than freezing mid-motion.
 *
 * By `resolve == 1` the mesh is geometrically the same shape as the resting glow, which is what
 * lets the card hand off between them without a visible cut.
 */

/**
 * Seven drifting field sources, blended by inverse-power weighting.
 *
 * Uniform-per-hue rather than an array: AGSL array uniforms are awkward to set from
 * `RuntimeShader` and the count is fixed by the category set anyway. Seven `half3`s is plainer to
 * read than an indexed loop over a packed buffer, and the loop below unrolls over them by index.
 */
private const val MESH_SHADER = """
uniform float2 uSize;
uniform float  uTime;
uniform float  uResolve;      // 0 = undecided, 1 = settled on the answer
uniform float  uWinner;       // index of the hue the mesh contracts to; always a real index
uniform float  uAlpha;        // master opacity, so the caller can fade the whole layer

uniform half3 uHue0;
uniform half3 uHue1;
uniform half3 uHue2;
uniform half3 uHue3;
uniform half3 uHue4;
uniform half3 uHue5;
uniform half3 uHue6;

// Cheap value noise. Good enough for a domain warp at this scale — the mesh is all low
// frequencies and heavy blur, so gradient noise would cost more and look identical.
float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    // Smoothstep the cell interpolant, or the warp shows the lattice as visible creasing.
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + float2(1.0, 0.0));
    float c = hash(i + float2(0.0, 1.0));
    float d = hash(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

half3 hueAt(int i) {
    if (i == 0) return uHue0;
    if (i == 1) return uHue1;
    if (i == 2) return uHue2;
    if (i == 3) return uHue3;
    if (i == 4) return uHue4;
    if (i == 5) return uHue5;
    return uHue6;
}

// Where source i sits at time t, in normalised card space.
//
// Each source runs its own Lissajous path at a different frequency pair, so the set never returns
// to a common phase and the mesh does not visibly loop. Amplitudes are wider on x than y: the card
// is much wider than the band this draws into, and equal amplitudes made everything crowd the
// centre.
// Each source is anchored to its own horizontal lane and drifts around it, rather than all seven
// sweeping the full width. Free-roaming Lissajous paths let sources bunch up: whichever two
// happened to be near the visible band dominated it and the other five were effectively invisible,
// which is how a seven-hue mesh came out green and pink. Lanes guarantee every hue holds territory
// while still letting them wander into each other's.
float2 sourcePos(int i, float t) {
    float fi = float(i);
    float phase = fi * 2.399963;                 // golden angle, so the drifts stay out of phase
    float lane = (fi + 0.5) / 7.0;               // 0.07 … 0.93, evenly spread across the card
    float sx = lane + 0.17 * sin(t * (0.19 + fi * 0.021) + phase);
    // Centred on the upper-middle of the lit band and swinging across most of it. Sources used to
    // sit at 0.42 ± 0.30 and were then cut by a falloff that had mostly expired by 0.45, so over
    // half of them never showed.
    float sy = 0.34 + 0.26 * cos(t * (0.16 + fi * 0.017) + phase * 1.7);
    return float2(sx, sy);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime;

    // Motion stills as the answer lands, so the card settles instead of freezing mid-churn.
    float calm = 1.0 - uResolve;

    // Domain warp — the "inner activity". Sampling the field at a slowly churning offset makes the
    // boundaries fold through each other rather than sliding past each other rigidly.
    float warpAmount = 0.075 * calm;
    float2 warp = float2(
        noise(uv * 2.6 + float2(t * 0.10, t * 0.07)),
        noise(uv * 2.6 + float2(t * -0.08, t * 0.11) + 19.3)
    ) - 0.5;
    float2 p = uv + warp * warpAmount;

    // Where everything converges once decided: the resting glow's centre, just above the top edge.
    // Matching it here is what lets the mesh hand off to categoryGlow without a visible cut.
    float2 restCenter = float2(0.5, -0.10);

    half3 accum = half3(0.0);
    float weightSum = 0.0;
    float coverage = 0.0;

    for (int i = 0; i < 7; i++) {
        float2 basePos = sourcePos(i, t);

        // Losing hues migrate toward the winner as they fade, so the mesh contracts to one pool
        // rather than seven fading in place. Fading alone reads as the effect being switched off;
        // converging reads as an answer being reached.
        float2 pos = mix(basePos, restCenter, uResolve);

        float2 d = p - pos;
        // Mild vertical squash: the lit band is wider than it is tall, so circular fields read as
        // spots rather than as a field across the header. Was 1.65, which flattened each source
        // into a wide ellipse — combined with lane-spread sources that let horizontally adjacent
        // hues swamp each other and cost the mesh its variety. Enough to shape the field, not
        // enough to make it one-dimensional.
        d.y *= 1.25;

        // Asymmetric falloff — sharp leading edge, diffuse tail.
        //
        // The source's velocity direction comes from sampling its path slightly ahead in time,
        // which is cheaper and steadier than differentiating the Lissajous analytically. Distance
        // is then scaled by how far the sample sits *behind* the source: points in its wake are
        // pulled closer (so the field stretches out behind), points ahead are pushed away (so the
        // front stays tight). This is the "clear visual pointer" the reference describes — the
        // gradient states a direction instead of merely existing.
        float2 ahead = sourcePos(i, t + 0.35) - basePos;
        float2 dir = length(ahead) > 0.0001 ? normalize(ahead) : float2(0.0, 1.0);
        float along = dot(normalize(d + 0.0001), dir);
        // 1 directly ahead of travel, -1 directly behind.
        float stretch = 1.0 - 0.40 * (-along) * calm;
        float dist = length(d) * stretch;

        // Inverse-power weighting. The exponent sets how much hues bleed into each other: low
        // values average everything to a single flat wash, high values give each source a hard
        // territory with visible seams. ~3.2 keeps seven distinguishable regions whose boundaries
        // are all soft, which is the "in-between fuzzy space" the effect is after.
        float w = 1.0 / (pow(dist, 3.2) + 0.0035);

        // The winner keeps its full weight while the rest fall away, so the resolve is a handover
        // rather than a general dimming. There is always a winner — off-list categories resolve to
        // the website hue rather than to nothing, so the mesh always contracts to a pool.
        float isWinner = abs(float(i) - uWinner) < 0.5 ? 1.0 : 0.0;
        float survive = mix(1.0, isWinner, uResolve);
        w *= survive;

        accum += hueAt(i) * half(w);
        weightSum += w;

        // Coverage is tracked separately from the colour blend on purpose. Normalising the colour
        // by weightSum makes the hue correct everywhere, but it also makes it fully opaque
        // everywhere — including far from every source. Coverage carries the falloff so the layer
        // actually fades out at the edges instead of filling the card with flat colour.
        coverage += 1.0 / (pow(dist * 1.5, 2.4) + 1.0) * survive;
    }

    half3 color = accum / half(max(weightSum, 0.0001));

    // Shape the coverage into a pool rather than a rectangle of colour.
    float a = coverage / (coverage + 1.0);
    a = smoothstep(0.06, 0.72, a);

    // Vertical falloff, mirroring the resting glow: light enters from above the top edge and is
    // gone by the lower part of the card. Without this the mesh reads as a coloured card rather
    // than as a card being lit from somewhere.
    // Reaches most of the way down the card, then falls away.
    //
    // This was briefly `smoothstep(0.0, 0.62)` squared, which was a mistake made while fixing the
    // painted-slab problem: two things changed at once and the alpha ceiling was the one doing the
    // work. Squaring a curve that had already expired by 0.62 clipped the mesh into a thin strip
    // across the top — and because only a narrow band survived, only the two hues whose paths
    // crossed it were ever visible.
    //
    // Starting the ramp above zero matters as much as where it ends: beginning the fade at the
    // very top edge dims the brightest part of the field, which is the part that should read as
    // the light's source. Full strength through the title, gone by the tags.
    float vertical = 1.0 - smoothstep(0.30, 0.95, uv.y);
    a *= vertical;

    // Ceiling, and the whole reason this is not just `a`.
    //
    // Seven sources accumulate coverage, so anywhere several overlap — most of the upper card —
    // the pool saturates to fully opaque. First run on device that produced a dense painted slab
    // across the header: the title and eyebrow were fighting it for legibility and the card had
    // stopped reading as *lit* and started reading as *coloured in*. The resting glow peaks at
    // 0.44 and the mesh must live in the same register, or the summarizing state looks like a
    // different app rather than a louder moment of this one.
    return half4(color * half(a * 0.50 * uAlpha), half(a * 0.50 * uAlpha));
}
"""

/**
 * Drives [MESH_SHADER] and paints it behind a card's content.
 *
 * @param hues every colour the category could still be, from `categoryHues`.
 * @param winner index into [hues] the mesh contracts to. Always valid — `categoryHueIndex` maps
 *   unrecognised categories to the article hue, matching what `categoryStyle` resolves them to.
 * @param resolve 0 while thinking, 1 once settled. Animate this — do not step it.
 * @param alpha master opacity for the whole layer.
 *
 * All three animated inputs are lambdas, not values, so they are read in the draw phase. Passing
 * floats directly would recompose the card on every frame of a 60fps animation; this way the
 * composition is untouched and only the draw layer re-runs. Same reason `categoryGlow` takes its
 * intensity as a lambda.
 */
fun Modifier.summarizingMesh(
    hues: List<Color>,
    winner: Int,
    time: () -> Float,
    resolve: () -> Float,
    alpha: () -> Float,
): Modifier = this.drawWithCache {
    val shader = RuntimeShader(MESH_SHADER)
    val brush = ShaderBrush(shader)

    shader.setFloatUniform("uSize", size.width, size.height)
    hues.take(7).forEachIndexed { index, color ->
        shader.setFloatUniform("uHue$index", color.red, color.green, color.blue)
    }

    onDrawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time())
        shader.setFloatUniform("uResolve", resolve())
        shader.setFloatUniform("uWinner", winner.toFloat())
        shader.setFloatUniform("uAlpha", alpha())
        drawRect(brush = brush)
    }
}

/**
 * A monotonically advancing clock in seconds, ticking only while [running].
 *
 * Separate from the shader modifier so the card owns when the animation costs anything. A feed of
 * settled cards must not each be running a frame callback — [running] goes false the moment a card
 * resolves, and the clock stops being read.
 *
 * Time is kept rather than reset when [running] goes false and true again, so a card that resolves
 * while off-screen and re-enters does not restart the mesh from a cold phase.
 */
@Composable
fun rememberMeshClock(running: Boolean): State<Float> {
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                // Clamped: a paused-then-resumed app would otherwise jump the mesh by however
                // long it was backgrounded, which reads as a glitch rather than as motion.
                val delta = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                clock.floatValue += delta
            }
        }
    }
    return clock
}
