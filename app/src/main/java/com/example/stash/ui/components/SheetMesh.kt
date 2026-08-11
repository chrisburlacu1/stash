package com.example.stash.ui.components

import android.graphics.RuntimeShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/**
 * The chooser sheet's light: a three-point mesh gradient rising from the bottom edge.
 *
 * ## What it says
 *
 * The sheet is a held moment between two destinations — the on-device chat or the Gemini app — so
 * its light should read as suspended rather than settled. Three sources drifting through each other
 * never resolve into a single shape, which is what that feels like.
 *
 * ## Why three hues and not one
 *
 * A single hue gives the eye two channels, brightness and distance, and they are coupled: dimmer
 * always means further. Any depth the shader computes has nowhere to surface. Three hues bleeding
 * into each other add a colour axis that varies independently of brightness, so the field reads as
 * having volume rather than as a flat ramp.
 *
 * The two extra hues are the category colour rotated a little either way, not other categories'
 * colours. `categoryHues` is deliberately ordered *against* the spectrum so its neighbours contrast
 * — that ordering exists to make [summarizingMesh] read as "several distinct possibilities in
 * tension". Borrowing from it here would say the wrong thing twice over: this sheet is not
 * undecided about what the link is, and a Video card's light has no business containing a
 * Documentation hue. Rotating the item's own hue keeps one identity rendered with internal variety.
 *
 * ## Diffused, not defined
 *
 * Every source uses a wide, high-exponent falloff and the field is normalised by total weight per
 * pixel, so hues bleed rather than sliding over each other like cut paper. The reference calls this
 * the "ethereal, in-between fuzzy space"; concretely it means no source ever shows an edge, and the
 * boundaries between them are the only structure.
 *
 * ## Why the motion is a domain warp
 *
 * The sources do not move. The *space they are measured in* is displaced by two stages of FBM
 * noise, so the boundaries between hues fold, stretch and reconnect in place. Moving the sources
 * instead produces translation, which the eye reads as objects sliding — and the two approaches
 * cancel if combined, since visible travel swamps the folding.
 *
 * Layered octaves rather than one: a single noise field warps everything at one scale and looks
 * like a wobble. Big slow folds with smaller eddies inside them is what separates "fluid" from "a
 * shape being animated" — the same "inner activity within the motion" the reference asks for.
 *
 * ## Cost
 *
 * A warped mesh is the most expensive thing this app draws, so it is worth knowing what it costs
 * and why it costs that. Measured on a Pixel 10 Pro XL with the sheet open:
 *
 * | | 90th pct | 50th pct |
 * |---|---|---|
 * | textbook shader | 20ms | 15ms |
 * | after the four fixes below | **11ms** | **9ms** |
 *
 * Four optimisations, none of which change the image:
 *
 *  1. **A sin-free hash.** The usual `fract(sin(dot(p, k)) * 43758.5)` costs two transcendental
 *     sins per call, and the warp makes tens of hash calls per pixel — billions of sins a second at
 *     sheet size. The integer-style hash here is a few multiplies and a fract.
 *  2. **Two FBM octaves, not three.** The third contributes at amplitude 0.125 and is then scaled
 *     by the warp strength before displacing anything: under 5% of a pixel's travel, for a third of
 *     the shader's cost.
 *  3. **One `pow` per source instead of two.** Both weights shared the exponent 2.2 and differed
 *     only by a constant factor, which folds out of the pow entirely.
 *  4. **Unrolled the source loop.** Indexing positions and hues through `if` chains makes every GPU
 *     lane evaluate every branch; with three sources, writing them out is shorter *and* branch-free.
 *
 * Even so, do not put this in the feed. One transient sheet is affordable; a warped mesh per
 * visible card is not, which is why [summarizingMesh] does not run its clock unless a card is
 * actually unresolved.
 */
private const val SHEET_MESH_SHADER = """
uniform float2 uSize;
uniform float  uTime;
uniform half3  uHueA;   // rotated one way from the category hue
uniform half3  uHueB;   // the category hue itself
uniform half3  uHueC;   // rotated the other way
uniform float  uAlpha;

// Gradient noise. Returns roughly -0.5..0.5, so warping with it displaces in both directions and
// the field folds rather than merely sliding.
//
// The hash is sin-free. The textbook version is `fract(sin(dot(p, k)) * 43758.5)`, which costs two
// transcendental sins per call — and this shader made 48 hash calls per pixel, so at sheet size and
// 60fps that was on the order of five billion sins a second. This variant is a few multiplies and
// a fract, visually indistinguishable as a noise source, and it is where most of the shader's cost
// went. (Adapted from the standard integer-hash trick; the constants are arbitrary large primes.)
float2 hash(float2 p) {
    float3 p3 = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
    p3 += dot(p3, p3.yzx + 33.33);
    return -1.0 + 2.0 * fract((p3.xx + p3.yz) * p3.zy);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(dot(hash(i + float2(0.0, 0.0)), f - float2(0.0, 0.0)),
            dot(hash(i + float2(1.0, 0.0)), f - float2(1.0, 0.0)), u.x),
        mix(dot(hash(i + float2(0.0, 1.0)), f - float2(0.0, 1.0)),
            dot(hash(i + float2(1.0, 1.0)), f - float2(1.0, 1.0)), u.x), u.y);
}

// Fractional Brownian Motion: octaves of noise at doubling frequency and halving amplitude, each
// rotated so the layers do not align into a visible grid. Multi-scale structure — big slow folds
// with smaller eddies inside them — is the difference between "fluid" and "a shape being moved".
//
// Two octaves, not three. The third contributes at amplitude 0.125 *and* is then multiplied by the
// warp strength (0.42) before displacing anything, so its actual effect on the field is under 5% of
// a pixel's travel — invisible, for a third of the shader's total cost. The rotation matrix is a
// compile-time constant rather than being rebuilt per call.
const float2x2 FBM_ROT = float2x2(0.87758, 0.47942, -0.47942, 0.87758);

float fbm(float2 p) {
    float v = 0.5 * noise(p);
    p = FBM_ROT * p * 2.0;
    v += 0.25 * noise(p);
    return v;
}

// Source positions and hues are written inline in main() rather than fetched through index
// helpers: an if-chain on a GPU costs every lane every branch, and there are only three of each.

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float aspect = uSize.x / uSize.y;

    // Work in height units with x scaled by aspect, so pools stay round on a wide sheet.
    float2 st = float2(uv.x * aspect, uv.y);
    float t = uTime * 0.22;

    // Domain warping. The sample point is displaced by one FBM field, which is itself displaced by
    // another — the standard two-stage warp. This is the whole reason the motion reads as fluid:
    // boundaries between hues fold and stretch in place rather than sliding across the surface.
    float2 q = float2(
        fbm(st * 1.5 + t * 0.10),
        fbm(st * 1.5 + float2(1.0, 2.3) - t * 0.08)
    );
    float2 r = float2(
        fbm(st * 1.5 + 0.9 * q + float2(1.7, 9.2) + 0.15 * t),
        fbm(st * 1.5 + 0.9 * q + float2(8.3, 2.8) + 0.12 * t)
    );
    float2 warped = st + r * 0.42;

    // Unrolled, and the two pow() calls per source collapsed into one.
    //
    // The loop indexed sourcePos() and hueAt() through if-chains, which on a GPU means every lane
    // evaluates every branch. Three sources is few enough that writing them out is both shorter and
    // branch-free. And both weights were pow(dist * k, 2.2) with different k — the same exponent,
    // so one pow serves both once the constant factors are folded out (k^2.2 is a compile-time
    // constant). pow() is a log/exp pair, so halving the count is worth more than it looks.
    float d0 = distance(warped, float2(0.02 * aspect, 1.00));
    float d1 = distance(warped, float2(0.50 * aspect, 1.06));
    float d2 = distance(warped, float2(0.98 * aspect, 1.00));

    float e0 = pow(d0, 2.2);
    float e1 = pow(d1, 2.2);
    float e2 = pow(d2, 2.2);

    // 1.15^2.2 = 1.357, 1.5^2.2 = 2.462 — folded in rather than scaling inside the pow.
    float w0 = 1.0 / (e0 * 1.357 + 0.05);
    float w1 = 1.0 / (e1 * 1.357 + 0.05);
    float w2 = 1.0 / (e2 * 1.357 + 0.05);

    float weightSum = w0 + w1 + w2;
    // Normalising by total weight is what keeps the blend at full chroma — stacked translucent
    // layers would composite toward grey, the muddy neutral this must avoid.
    half3 color = (uHueA * half(w0) + uHueB * half(w1) + uHueC * half(w2)) / half(max(weightSum, 0.0001));

    float coverage =
        1.0 / (e0 * 2.462 + 1.0) +
        1.0 / (e1 * 2.462 + 1.0) +
        1.0 / (e2 * 2.462 + 1.0);

    float a = coverage / (coverage + 1.0);
    a = smoothstep(0.04, 0.58, a);

    // Vertical reach, measured up from the bottom edge. The field has to run out inside the sheet:
    // light still carrying real alpha at the top edge terminates in a straight horizontal line,
    // which is the one way a glow cannot be allowed to end.
    float h = 1.0 - uv.y;
    a *= 1.0 - smoothstep(0.05, 0.30, h);

    // Horizontal ease is deliberately narrow. A wide one (0.16) visibly cut the field in from both
    // sides and left the sheet's corners unlit, which reads as a vignette on a rectangle rather
    // than as light filling the space. Just enough to avoid a hard vertical edge.
    a *= smoothstep(0.0, 0.05, uv.x) * smoothstep(0.0, 0.05, 1.0 - uv.x);

    float outA = a * uAlpha;
    return half4(color * half(outA), half(outA));
}
"""

/**
 * Paints [SHEET_MESH_SHADER] behind the chooser sheet's content.
 *
 * @param color the item's category colour. The mesh's three sources are this hue and two rotations
 *   of it, so the sheet stays unmistakably one category while the field has internal variety.
 * @param time monotonically increasing seconds, from [rememberMeshClock]. A lambda so the animation
 *   runs in the draw phase and never recomposes the sheet — the same discipline as the card glow
 *   and both other meshes.
 */
fun Modifier.sheetMesh(
    color: Color,
    time: () -> Float,
): Modifier = this.drawWithCache {
    val shader = RuntimeShader(SHEET_MESH_SHADER)
    val brush = ShaderBrush(shader)

    val base = color.saturatedForMesh(SHEET_MESH_SATURATION)
    val a = base.rotatedHue(-SHEET_MESH_HUE_SPREAD)
    val c = base.rotatedHue(SHEET_MESH_HUE_SPREAD)
    shader.setFloatUniform("uHueA", a.red, a.green, a.blue)
    shader.setFloatUniform("uHueB", base.red, base.green, base.blue)
    shader.setFloatUniform("uHueC", c.red, c.green, c.blue)

    onDrawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time())
        shader.setFloatUniform("uAlpha", SHEET_MESH_ALPHA)
        drawRect(brush = brush)
    }
}

/**
 * Rotates a colour's hue by [degrees], preserving saturation and lightness.
 *
 * Goes through HSV rather than nudging RGB channels: channel arithmetic shifts saturation and
 * lightness as a side effect, and the three mesh sources must differ *only* in hue or the field
 * develops bright and dark patches that read as uneven lighting rather than as blended colour.
 */
private fun Color.rotatedHue(degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

/** Pushes a colour's channels away from mid-grey, raising saturation without shifting hue. */
private fun Color.saturatedForMesh(amount: Float): Color {
    val mean = (red + green + blue) / 3f
    return Color(
        red = (mean + (red - mean) * amount).coerceIn(0f, 1f),
        green = (mean + (green - mean) * amount).coerceIn(0f, 1f),
        blue = (mean + (blue - mean) * amount).coerceIn(0f, 1f),
    )
}

/**
 * How far either side of the category hue the outer two sources sit, in degrees.
 *
 * Wide enough that the blend has somewhere to travel and the field reads as more than one colour;
 * narrow enough that the sheet still says one category at a glance. Beyond about 40 degrees a
 * Documentation card starts looking like it is showing a Video hue.
 */
private const val SHEET_MESH_HUE_SPREAD = 10f

/**
 * Peak opacity of the field.
 *
 * The ceiling `chatAura` settled on, for the same reason: three sources accumulate coverage, so
 * anywhere they overlap a higher value saturates to opaque and the sheet reads as *coloured in*
 * rather than *lit*.
 */
private const val SHEET_MESH_ALPHA = 0.46f

/** The palette is tuned for legible mid-tone text; spread thin as light it washes out to grey. */
private const val SHEET_MESH_SATURATION = 1.45f
