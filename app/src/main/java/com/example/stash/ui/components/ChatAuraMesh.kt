package com.example.stash.ui.components

import android.graphics.RuntimeShader
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

/**
 * The chat screen's light: the same mesh language as [summarizingMesh], entering from *below*.
 *
 * On a card the mesh answers "what is this link?" — seven hues churning until the category lands.
 * In a chat the open question is the model's reply, so the same visual carries the same meaning at
 * screen scale: while Gemini Nano is composing, the full palette churns up from the bottom edge;
 * when the reply finishes, the field contracts to a single resting pool of the item's category,
 * sitting under the input bar the way the card's glow sits over its title.
 *
 * The screen arrives *in* the churn state: the entrance runs `resolve` 0 → 1, so the veil the user
 * sees rising with the screen is literally the assistant presence settling into its resting light.
 * Sending a question turns it back up. One mechanism, three moments — entrance, thinking, settled.
 *
 * Differences from the card mesh, and why:
 *
 *  - **Anchored to the bottom.** The assistant "sits" where replies and the input live. Light from
 *    above would read as the feed's card language leaking into a different surface.
 *  - **Aspect-corrected distances.** The card shader works in raw UV because a card is a wide
 *    band; on a tall screen uncorrected UV stretches every pool into a column. Distances here are
 *    measured in height units with x scaled by aspect, so pools stay pools in any orientation.
 *  - **No handoff.** The card fades the mesh into a separate [categoryGlow]. Here the settled
 *    mesh *is* the resting light — at `resolve == 1` the churn is still and the geometry is the
 *    pool, so nothing needs to take over and there is no seam to hide.
 *  - **Reach rides the churn.** While thinking, the field climbs to well up the screen; settled,
 *    it hugs the bottom. The mask's edges interpolate on `calm` so the veil's rise and fall is the
 *    same motion as its churn, not a second animation laid on top.
 */
private const val CHAT_AURA_SHADER = """
uniform float2 uSize;
uniform float  uTime;
uniform float  uResolve;      // 0 = thinking/arriving, 1 = settled on the item's hue
uniform float  uWinner;       // index of the item's category hue; always a real index
uniform float  uAlpha;        // master opacity, so the caller can fade the whole layer

uniform half3 uHue0;
uniform half3 uHue1;
uniform half3 uHue2;
uniform half3 uHue3;
uniform half3 uHue4;
uniform half3 uHue5;
uniform half3 uHue6;

float hash(float2 p) {
    return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453123);
}

float noise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
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

// Same lane discipline as the card mesh — every hue holds bottom-edge territory — but the drift
// band sits just above the bottom edge rather than across a card's header.
float2 sourcePos(int i, float t) {
    float fi = float(i);
    float phase = fi * 2.399963;                 // golden angle, so the drifts stay out of phase
    float lane = (fi + 0.5) / 7.0;
    float sx = lane + 0.17 * sin(t * (0.19 + fi * 0.021) + phase);
    float sy = 0.86 + 0.10 * cos(t * (0.16 + fi * 0.017) + phase * 1.7);
    return float2(sx, sy);
}

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / uSize;
    float t = uTime;
    float aspect = uSize.x / uSize.y;

    float calm = 1.0 - uResolve;

    float warpAmount = 0.075 * calm;
    float2 warp = float2(
        noise(uv * 2.6 + float2(t * 0.10, t * 0.07)),
        noise(uv * 2.6 + float2(t * -0.08, t * 0.11) + 19.3)
    ) - 0.5;
    float2 p = uv + warp * warpAmount;

    // The resting pool's centre: just below the bottom edge, mirroring the card glow sitting just
    // above the top one. The bright middle of the pool lands under the input bar.
    float2 restCenter = float2(0.5, 1.10);

    half3 accum = half3(0.0);
    float weightSum = 0.0;
    float coverage = 0.0;

    for (int i = 0; i < 7; i++) {
        float2 basePos = sourcePos(i, t);
        float2 pos = mix(basePos, restCenter, uResolve);

        float2 d = p - pos;
        // Distances in height units: x is scaled by aspect so a pool is physically round on a
        // tall screen, where the card shader's raw-UV distances would stretch it into a column.
        d.x *= aspect;

        // Sharp leading edge, diffuse tail, exactly as on the card.
        float2 ahead = sourcePos(i, t + 0.35) - basePos;
        float2 dir = length(ahead) > 0.0001 ? normalize(ahead) : float2(0.0, 1.0);
        float along = dot(normalize(d + 0.0001), dir);
        float stretch = 1.0 - 0.40 * (-along) * calm;
        float dist = length(d) * stretch;

        float w = 1.0 / (pow(dist, 3.2) + 0.0035);

        float isWinner = abs(float(i) - uWinner) < 0.5 ? 1.0 : 0.0;
        float survive = mix(1.0, isWinner, uResolve);
        w *= survive;

        accum += hueAt(i) * half(w);
        weightSum += w;

        coverage += 1.0 / (pow(dist * 1.5, 2.4) + 1.0) * survive;
    }

    half3 color = accum / half(max(weightSum, 0.0001));

    float a = coverage / (coverage + 1.0);
    a = smoothstep(0.06, 0.72, a);

    // Light climbs from the bottom edge. The veil reaches well up the screen while churning and
    // pulls back to an ambient pool as it settles — the mask's span is part of the resolve, so
    // the field visibly *withdraws* as the answer lands rather than dimming in place.
    float h = 1.0 - uv.y;
    float reach = mix(0.40, 0.78, calm);
    float base = mix(0.14, 0.28, calm);
    float vertical = 1.0 - smoothstep(base, reach, h);
    a *= vertical;

    // Same register as the card mesh's 0.50 ceiling: this layer sits under conversation text, so
    // it must read as the screen being lit, never as the screen being painted.
    return half4(color * half(a * 0.46 * uAlpha), half(a * 0.46 * uAlpha));
}
"""

/**
 * Paints [CHAT_AURA_SHADER] behind the chat screen's content.
 *
 * @param hues every category colour, from `categoryHues` — the palette the assistant thinks in.
 * @param winner index into [hues] of this item's category; the pool the field settles into.
 * @param resolve 0 while arriving or composing a reply, 1 once settled. Animate it — the contract
 *   is the same as `summarizingMesh`'s: the travel *is* the meaning.
 * @param alpha master opacity for the whole layer.
 *
 * All animated inputs are lambdas read in the draw phase, so a 60fps churn never recomposes the
 * screen — the same discipline as the card mesh and glow.
 */
fun Modifier.chatAura(
    hues: List<Color>,
    winner: Int,
    time: () -> Float,
    resolve: () -> Float,
    alpha: () -> Float = { 1f },
): Modifier = this.drawWithCache {
    val shader = RuntimeShader(CHAT_AURA_SHADER)
    val brush = ShaderBrush(shader)

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
