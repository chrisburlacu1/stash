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
 */
private const val SHEET_MESH_SHADER = """
uniform float2 uSize;
uniform float  uTime;
uniform half3  uHueA;   // rotated one way from the category hue
uniform half3  uHueB;   // the category hue itself
uniform half3  uHueC;   // rotated the other way
uniform float  uAlpha;

// Where each source sits: spread across the width, riding just below the bottom edge so the field
// enters from there. Each drifts on its own slow cycle; the periods share no common multiple, so
// the three never return to a previous arrangement and the field never visibly loops.
float2 sourcePos(int i, float t) {
    if (i == 0) return float2(0.16 + 0.10 * sin(t * 0.21),        0.94 + 0.07 * cos(t * 0.17));
    if (i == 1) return float2(0.50 + 0.13 * sin(t * 0.15 + 2.1),  0.99 + 0.08 * cos(t * 0.23 + 1.1));
    return              float2(0.84 + 0.10 * sin(t * 0.19 + 4.2), 0.94 + 0.07 * cos(t * 0.13 + 3.3));
}

half3 hueAt(int i) {
    if (i == 0) return uHueA;
    if (i == 1) return uHueB;
    return uHueC;
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float aspect = uSize.x / uSize.y;

    half3 accum = half3(0.0);
    float weightSum = 0.0;
    float coverage = 0.0;

    for (int i = 0; i < 3; i++) {
        float2 pos = sourcePos(i, uTime);
        float2 d = uv - pos;
        // Distances in height units, x scaled by aspect: a sheet is far wider than it is tall, and
        // in raw UV every pool stretches into a horizontal band.
        d.x *= aspect;

        float dist = length(d);

        // Inverse power, high exponent. Normalising by the total weight below means the result is
        // always a true blend of the contributing hues at full chroma — where stacked translucent
        // layers would composite toward grey, which is the muddy neutral this must avoid.
        float w = 1.0 / (pow(dist, 2.6) + 0.004);

        accum += hueAt(i) * half(w);
        weightSum += w;

        coverage += 1.0 / (pow(dist * 1.7, 2.2) + 1.0);
    }

    half3 color = accum / half(max(weightSum, 0.0001));

    float a = coverage / (coverage + 1.0);
    a = smoothstep(0.05, 0.62, a);

    // Vertical reach, measured up from the bottom edge. The field has to run out inside the sheet:
    // light still carrying real alpha at the top edge terminates in a straight horizontal line,
    // which is the one way a glow cannot be allowed to end.
    float h = 1.0 - uv.y;
    a *= 1.0 - smoothstep(0.12, 0.66, h);

    // Ease the horizontal ends so the field does not stop at two vertical cuts.
    a *= smoothstep(0.0, 0.16, uv.x) * smoothstep(0.0, 0.16, 1.0 - uv.x);

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
private const val SHEET_MESH_HUE_SPREAD = 28f

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
