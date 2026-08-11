package com.example.stash.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The category's light, entering a bottom sheet from below.
 *
 * `categoryGlow` hangs a lamp above a card because a card expands *downward* — the light and the
 * motion share a source. A bottom sheet arrives from the opposite edge, so its light has to come
 * from underneath or the surface and its illumination disagree about where they came from.
 *
 * ## Why this is not the card glow flipped
 *
 * A card is a wide band lit from a point: a tight pool, a small radius, a visible falloff, and the
 * card's own colour still dominant around it. Copying that upside-down puts a bright blob under the
 * sheet's last row, which reads as an object behind the sheet rather than as the sheet being lit.
 *
 * A sheet is lit *by the edge it entered from*. So the geometry is an edge wash, not a lamp: the
 * source is wide (radius scales with the sheet's width rather than a fraction of it), sits close
 * under the bottom edge, and is flattened horizontally so the light spreads along the edge instead
 * of pooling at its centre. What the eye should read is "light is coming in from down there", which
 * is the same thing the sheet's own entrance is saying.
 *
 * ## Why it does not churn
 *
 * Everything else lit in this app that *moves* — [summarizingMesh], [chatAura] — moves because the
 * on-device model is working, and the motion resolving is the work landing. A chooser is not
 * working; it is waiting for the user. Borrowing the churn here would spend a signal the rest of
 * the app needs to stay honest, to decorate a menu.
 *
 * So this is static: no frame clock, no animation, nothing to stop when the sheet closes. It takes
 * the *lighting* half of the language and leaves the *thinking* half alone. Light means presence
 * and origin; churn means work.
 *
 * Alphas and the saturation lift are deliberately the card's, not new numbers — a new effect needs
 * the bounds of the one it sits beside, or it reads as belonging to a different app.
 */
fun Modifier.sheetGlow(color: Color): Modifier = this.drawBehind {
    // Radius from *height*, not width. Width was the obvious choice and was wrong: a sheet is much
    // wider than it is tall, so a width-derived radius exceeded the sheet's height several times
    // over and the falloff never landed inside it — every pixel sat near the pool's centre and the
    // whole surface came out one flat lilac tint. Scaling from height puts the visible part of the
    // curve inside the sheet, which is what makes it read as light with a direction.
    val radius = size.height * SHEET_GLOW_RADIUS_FACTOR

    // Same reason the card saturates: the category palette is tuned for legible mid-tone text, and
    // spread thin as light those hues wash out to a grey haze.
    val lit = color.saturatedForLight(SHEET_GLOW_SATURATION)

    drawRect(
        brush = Brush.radialGradient(
            // Shallower mid-stops than the card's. The card wants a visible falloff so the pool has
            // an edge; this wants the opposite — light that arrives at the bottom rows and thins out
            // before the title, with no discernible boundary anywhere.
            colorStops = arrayOf(
                0.00f to lit.copy(alpha = SHEET_GLOW_ALPHA),
                0.28f to lit.copy(alpha = SHEET_GLOW_ALPHA * 0.62f),
                0.52f to lit.copy(alpha = SHEET_GLOW_ALPHA * 0.30f),
                0.74f to lit.copy(alpha = SHEET_GLOW_ALPHA * 0.11f),
                0.90f to lit.copy(alpha = SHEET_GLOW_ALPHA * 0.03f),
                1.00f to Color.Transparent,
            ),
            // Just below the bottom edge, horizontally centred — the mirror of the card's fixture
            // above its top edge. Close enough under that the brightest part of the wash lands
            // inside the sheet rather than off-screen.
            //
            // This depends on the modifier being attached to something that fills the sheet's full
            // painted height. On a content-sized wrapper `size.height` is the content's height, the
            // source lands mid-sheet, and the light ends up brightest at the *top* — the opposite
            // of the whole idea.
            center = Offset(size.width / 2f, size.height + radius * SHEET_GLOW_CENTER_DROP),
            radius = radius,
        ),
    )
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
 * Radius as a multiple of sheet *height*. Just over 1, so the wash reaches the top of the sheet
 * with its curve already well into the falloff — light that arrives at the rows and thins out by
 * the title, rather than a tint sitting on everything equally.
 */
private const val SHEET_GLOW_RADIUS_FACTOR = 1.1f

/**
 * How far below the bottom edge the source sits, as a fraction of radius.
 *
 * This is the lever that decides how much light actually lands. At 0.30 the pool's bright core sat
 * well off-screen and the sheet only caught the far tail — the wash was technically present and
 * visually absent. Close in, so the top of the pool clears the bottom edge and the part of the
 * curve with visible falloff is the part on the sheet.
 */
private const val SHEET_GLOW_CENTER_DROP = 0.06f

/**
 * Peak opacity at the centre of the wash.
 *
 * The first cut used 0.34 and was invisible on device. The mistake was reading this number as
 * "how lit does the surface look" — it is not. Most of this pool sits *below* the bottom edge and
 * off-screen, where the card's sits mostly inside the card, so the sheet only ever sees the tail of
 * the curve. The peak is a long way from what lands on the surface.
 *
 * Nudged up rather than doubled: the visible-light fix is [SHEET_GLOW_CENTER_DROP] bringing the
 * source closer, which moves the *usable* part of the falloff onto the sheet. Turning the peak up
 * to compensate for a badly placed source is the "bright murk" failure `chatAura` records.
 */
private const val SHEET_GLOW_ALPHA = 0.46f

/** Matches the card's lift. The palette needs the same push here for the same reason. */
private const val SHEET_GLOW_SATURATION = 1.7f
