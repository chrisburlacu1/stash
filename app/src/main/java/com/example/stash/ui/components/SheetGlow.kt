package com.example.stash.ui.components

import android.graphics.RuntimeShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The chooser sheet's light: an LED strip along the bottom edge, breathing toward and away from the
 * surface it lights.
 *
 * ## Why it moves at all
 *
 * The first version was the card's radial glow mirrored under the bottom edge, and deliberately
 * inert — the reasoning being that this app's moving light ([summarizingMesh], [chatAura]) means
 * "the model is working", and a chooser is not working.
 *
 * That conflated two things. The model is not computing, true, but the user is mid-decision and the
 * sheet is a *held moment inside a transition that has not landed*: the final states are the Nano
 * chat or the Gemini app, and the open sheet is the space between them. There is something to
 * express. It just is not computation — it is suspension.
 *
 * ## Why a strip and not a shape
 *
 * Several versions failed the same way before this one worked, and all for one reason: **anything
 * with a moving outline reads as a creature, not as light.** A crest line built from travelling
 * sines is literally the swimming-animal equation and looked like one; making the crest stand still
 * and only change height helped, but a silhouette that swells is still a silhouette.
 *
 * What finally worked was removing the outline entirely. The strip has no visible form — only its
 * *distance* from the surface varies, and distance is invisible from the viewing angle. What the
 * eye gets is brightness swelling and thinning in place, with nothing to track as an object.
 *
 * The depth cue is that standoff drives two things in opposite directions: held away, the light
 * throws further up the surface but arrives weaker; held close, it is tight and bright. Tie
 * brightness to distance alone and the whole thing flattens into a gradient that lightens and
 * darkens.
 *
 * ## Why AGSL
 *
 * None of this is expressible in colour stops — `Brush.radialGradient` cannot vary its falloff
 * along its own length. `RuntimeShader` is already how the other two lit surfaces here are drawn.
 *
 * ## Why it does not repeat
 *
 * The three z-waves drift at rates with no common multiple, so the strip never returns to a
 * previous configuration. A loop of any visible length would turn suspension into a countdown.
 *
 * One hue, not the spectrum: several hues at once already means "the model has not decided what
 * this link *is*" ([summarizingMesh]), and that question is answered long before this sheet opens.
 */
private const val SHEET_RIBBON_SHADER = """
uniform float2 uSize;
uniform float  uTime;
uniform half3  uHue;
uniform float  uAlpha;

// The model: an LED strip lying along the bottom of the sheet, its emitting face toward the
// surface, held a little off it and rippling *toward and away* from it — along z, into the screen.
// We are looking at this from perhaps 45 degrees above, so what is visible is not the strip itself
// but the light it throws on the surface behind it.
//
// **z is a distance, never an occlusion.** The strip never touches down: light escapes everywhere
// along its length, just unevenly. A version that let it make contact broke the line into separate
// pools and read as light through a grille rather than a continuous run.
//
// **z is also not a height.** Earlier versions drew a crest whose *silhouette* rose and fell, and a
// moving silhouette is an object — every one of them read as a creature swimming (the "tadpole").
// Nothing here has an outline. The ripple travels along the strip, but travel in z is invisible
// from this angle: the eye only sees brightness swell and thin in place, never anything sliding.
// That is what makes a travelling wave safe to use here when it was fatal as a shape.

// How far the strip stands off the surface at position x, normalised to roughly 0..1.
float standoff(float x, float t) {
    // Travelling components along the rod. The travel is in z, which is invisible from this angle —
    // the eye only sees brightness rise and fall in place, never anything sliding sideways. That is
    // what makes a travelling wave safe to use here when it was fatal as a silhouette.
    float z = 0.0;
    z += sin(x * 5.3 - t * 0.62);
    z += 0.75 * sin(x * 8.9 + t * 0.44 + 1.9);
    z += 0.45 * sin(x * 13.7 - t * 0.81 + 3.7);

    // Map the ±2.2 swing into a standoff that stays comfortably above zero. The floor is what keeps
    // the strip continuous; the range above it is what gives it life.
    return 0.34 + 0.30 * (z / 2.2);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;

    // Height above the bottom edge, so the maths reads in the direction the light travels.
    float y = 1.0 - uv.y;

    // The strip lies at a fixed height, just clear of the gesture bar. It never rises or falls:
    // a line whose position moves is a shape, and a shape that moves is a creature.
    float line = 0.13;

    // Signed distance from the strip, in height units.
    float d = y - line;

    // Standoff, sampled at three offsets that widen with height. Light leaving the strip spreads as
    // it travels, so a tight source at the strip is a broad pool by the top of its throw; without
    // this, every bright stretch stays a vertical column all the way up and reads as a grille.
    float spread = 0.04 + 0.40 * clamp(max(d, 0.0) / 0.6, 0.0, 1.0);
    float z =
        0.40 * standoff(uv.x, uTime) +
        0.30 * standoff(uv.x - spread, uTime) +
        0.30 * standoff(uv.x + spread, uTime);

    // The depth cue, and the reason this reads as three-dimensional rather than as a brightness
    // map: standoff drives throw and intensity in *opposite* directions. Held away, the light
    // reaches further up the surface but arrives spread thinner; held close, it is tight and
    // bright. Same strip, same power — only its distance changed.
    //
    // Falloff rates are bounded well above the 0.75 first tried, where light was still at a tenth
    // of its strength three sheet-heights up: it filled the sheet like a plain gradient and then
    // met the top edge with real alpha still in it, which is what put a hard line across the top.
    // The throw has to run out on its own, inside the sheet — the top fade only tidies a residue.
    float reach = mix(4.8, 2.9, clamp(z, 0.0, 1.0));
    float above = exp(-max(d, 0.0) * reach);

    // Downward spill is tighter than the upward throw, but only mildly. A steep lower falloff gives
    // the light a hard bottom boundary, and any hard boundary turns it back into a shape.
    float below = exp(-max(-d, 0.0) * 4.0);
    float band = d >= 0.0 ? above : below;

    // The other half of the depth cue. Gentle, not true inverse-square: the physical law over this
    // range swings brightness far too hard and blows out the near stretches.
    float intensity = mix(1.0, 0.55, clamp(z, 0.0, 1.0));

    float a = band * intensity * uAlpha;

    // Ease the horizontal ends out, or the ribbon terminates in two hard vertical cuts at the
    // sheet's edges. The falloff is wide because a narrow one reads as a vignette.
    a *= smoothstep(0.0, 0.22, uv.x) * smoothstep(0.0, 0.22, 1.0 - uv.x);

    // Take the light to zero before the sheet's top edge rather than letting the edge clip it.
    // The exponential falloff never actually reaches zero, so without this the glow meets the top
    // boundary at some small but non-zero alpha and terminates in a straight horizontal line —
    // the same hard-cut failure the bottom inset had, mirrored. Fading over the last stretch lets
    // it disappear because it ran out, which is the only way a light is supposed to end.
    //
    // Starts at the very top and only bites in the last tenth. An earlier version began fading at
    // 0.62, which is where the light is still at roughly half strength — that quietly truncated
    // the reach and made the glow look short when the falloff itself was generous. This must clean
    // up the residual tail, not shape the visible light.
    a *= smoothstep(1.0, 0.90, y);

    // Premultiplied, matching the other shaders in this package.
    return half4(uHue * half(a), half(a));
}
"""

/**
 * Paints [SHEET_RIBBON_SHADER] behind the chooser sheet's content.
 *
 * @param color the item's category colour. Saturated before use for the same reason
 *   `categoryGlow` saturates: the palette is tuned for legible mid-tone text, and spread thin as
 *   light those hues wash out toward grey.
 * @param time monotonically increasing seconds, from `rememberMeshClock`. Read through a lambda so
 *   the animation lives in the draw phase and never recomposes the sheet — the same discipline as
 *   the card glow and both meshes.
 */
fun Modifier.sheetGlow(
    color: Color,
    time: () -> Float,
): Modifier = this.drawWithCache {
    val shader = RuntimeShader(SHEET_RIBBON_SHADER)
    val brush = ShaderBrush(shader)
    val lit = color.saturatedForLight(SHEET_GLOW_SATURATION)
    shader.setFloatUniform("uHue", lit.red, lit.green, lit.blue)

    onDrawBehind {
        shader.setFloatUniform("uSize", size.width, size.height)
        shader.setFloatUniform("uTime", time())
        shader.setFloatUniform("uAlpha", SHEET_GLOW_ALPHA)
        drawRect(brush = brush)
    }
}

/**
 * Pushes a colour's channels away from mid-grey, raising saturation without shifting hue.
 *
 * Same trick and the same reasoning as the card's `saturated`, duplicated rather than shared: that
 * one is private to `StashCardRow` and hoisting it to a common home would mean a third file that
 * exists only to hold four lines of arithmetic. If a fourth caller appears, hoist it then.
 */
private fun Color.saturatedForLight(amount: Float): Color {
    val mean = (red + green + blue) / 3f
    return Color(
        red = (mean + (red - mean) * amount).coerceIn(0f, 1f),
        green = (mean + (green - mean) * amount).coerceIn(0f, 1f),
        blue = (mean + (blue - mean) * amount).coerceIn(0f, 1f),
    )
}

/**
 * Peak opacity right at the strip.
 *
 * Above `chatAura`'s 0.46 ceiling, which is deliberate and not a violation of it. That ceiling
 * governs a field spread over most of a screen, where anything higher stops reading as light on a
 * surface and starts reading as the surface being coloured in. This falls off several times faster,
 * so the high value applies only to a thin band at the strip and the light is well under 0.46
 * across almost all of the sheet. Peak alpha and perceived brightness are not the same number when
 * the falloff rates differ this much.
 */
private const val SHEET_GLOW_ALPHA = 0.68f

/**
 * Saturation lift before the hue is used as light.
 *
 * Below the card's 1.7. That number exists because the card's glow spreads its hue very thin over a
 * wide pool, where an unsaturated colour washes out to grey haze. The ribbon does the opposite — it
 * concentrates alpha into a narrow band — so the same lift overshoots and the light comes out as
 * saturated violet ink. Concentrated light needs less help staying colourful than diffuse light.
 */
private const val SHEET_GLOW_SATURATION = 1.25f
