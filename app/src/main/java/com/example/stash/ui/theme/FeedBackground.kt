package com.example.stash.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.util.lerp

/**
 * Candidate treatments for the feed's background, behind a toggle so they can be compared on a real
 * device with real cards rather than chosen from a description.
 *
 * The app's thesis is that **light is the layer Stash owns** — M3 has no light model, which is the
 * gap `categoryGlow` and the meshes fill. A flat `surface` is the one large area where that idea is
 * not expressed at all, so the feed reads as cards floating on nothing.
 *
 * Every option here is built from `colorScheme` tokens, never hard-coded hex, so all of them follow
 * a dynamic-colour palette as readily as the bespoke one.
 *
 * The bar each must clear: it has to stay *under* the card glow. The glow works because the card is
 * lit and its surroundings are not; a background bright enough to compete flattens exactly the
 * contrast that makes it read as light. When comparing, look at a card's top edge, not at the
 * background on its own.
 */
enum class FeedBackground(val label: String) {
    /** The current state: one flat `surface`. The control to compare against. */
    Flat("Flat"),

    /**
     * A long vertical wash, `surfaceBright` at the top falling to `surface`.
     *
     * Shares the card glow's logic — light enters from above — so the room and the objects in it
     * agree about where the light is. The delta between the two tokens is small by design: this
     * should register as depth rather than as a gradient you can point at.
     */
    Ambient("Ambient"),

    /**
     * A large, very low-alpha `primaryContainer` radial anchored off the top-trailing corner.
     *
     * Puts the brand hue into the room as *light* rather than as ink, which is the same move the
     * category glow makes on a card. The risk is competition: it is a second coloured light source
     * on screen, so watch whether card glows still read as distinct against it.
     */
    BrandGlow("Brand glow"),

    /**
     * [Ambient], but the wash's origin drifts with scroll, giving the background parallax against
     * the cards.
     *
     * Included because it was asked for, but it argues against itself: DESIGN-NOTES is emphatic
     * that motion in this app means *the model is working*. Ambient movement with no state behind
     * it spends that vocabulary on decoration, and makes the mesh's churn less legible as a signal.
     */
    Parallax("Parallax"),
}

/**
 * Peak alpha of the brand radial.
 *
 * Ambient fill, not a light with a source. It has to stay *under* the card glow: the glow works
 * because the card is lit and its surroundings are not, so a background bright enough to compete
 * flattens exactly the contrast that makes it read as light.
 */
private const val BRAND_GLOW_ALPHA = 0.14f

/**
 * How far the parallax wash travels over a full screen of scroll, as a fraction of height.
 *
 * DEBUG VALUE — exaggerated so the effect is unmistakable while comparing. Anything this large
 * would ship as a distraction.
 */
private const val PARALLAX_TRAVEL = 0.8f

/**
 * Paints [background] behind the content.
 *
 * `drawBehind` rather than `background(brush)` so the gradient is recomputed in the draw phase when
 * scroll moves it — a scrolling background must not recompose the feed.
 */
@Composable
fun Modifier.feedBackground(
    background: FeedBackground,
    listState: LazyListState? = null,
): Modifier {
    val scheme = MaterialTheme.colorScheme
    val surface = scheme.surface
    val brand = scheme.primaryContainer
    // DEBUG: the radial uses `primary` rather than `primaryContainer` so the corner source is
    // unmistakably a different effect from the vertical washes while comparing.
    val brandStrong = scheme.primary

    // A light background and a dark one need different mechanisms, not the same one at different
    // strengths — the same lesson DESIGN-NOTES records for the sheet strip ("light on a dark field
    // and light on a light surface are not the same effect").
    //
    // Measured on this palette: surface -> surfaceContainerHighest is a contrast ratio of 1.22 in
    // light mode and 1.50 in dark. Worse, both light-mode endpoints sit at luminance 0.79-0.97,
    // crushed against the ceiling where the eye separates almost nothing. Adding *lightness* to
    // near-white has nowhere to go.
    //
    // So light mode varies CHROMA instead: a tinted wash of the brand hue over white reads clearly
    // where a brighter white cannot. Dark mode keeps the luminance ramp, which is what works there.
    val isLight = surface.luminance() > 0.5f
    val washTop = if (isLight) {
        // The saturated `primary`, not the pale `primaryContainer`. primaryContainer is already a
        // near-white tint, so compositing it over a near-white surface moves almost nothing --
        // measured 1.22 contrast, which is why the washes stayed invisible while the corner radial
        // (which uses primary) read clearly. Chroma is the only axis with room here.
        //
        // DEBUG ALPHA: high enough to compare directions. A shipping value is far lower.
        brandStrong.copy(alpha = 0.11f).compositeOver(surface)
    } else {
        // Dark mode has luminance headroom, so a neutral lift is enough and keeps the wash from
        // reading as a colour cast over every card.
        scheme.surfaceContainerHighest
    }

    return when (background) {
        FeedBackground.Flat -> this.drawBehind { drawRect(surface) }

        FeedBackground.Ambient -> this.drawBehind {
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(washTop, surface),
                    startY = 0f,
                    endY = size.height,
                )
            )
        }

        FeedBackground.BrandGlow -> this.drawBehind {
            drawRect(surface)
            // Radius comfortably larger than the screen: the visible part is the bright middle of
            // the pool, not a ring, which is the same reason categoryGlow keeps its centre off-card.
            val radius = size.height * 0.9f
            drawRect(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to brandStrong.copy(alpha = BRAND_GLOW_ALPHA),
                        0.45f to brandStrong.copy(alpha = BRAND_GLOW_ALPHA * 0.35f),
                        1.0f to Color.Transparent,
                    ),
                    center = Offset(size.width * 0.95f, -size.height * 0.05f),
                    radius = radius,
                )
            )
        }

        FeedBackground.Parallax -> {
            // Read through the lambda inside drawBehind so scrolling re-draws without recomposing.
            val scroll by remember(listState) {
                androidx.compose.runtime.derivedStateOf {
                    val s = listState ?: return@derivedStateOf 0f
                    (s.firstVisibleItemIndex * 400f + s.firstVisibleItemScrollOffset) / 2000f
                }
            }
            this.drawBehind {
                val shift = lerp(0f, size.height * PARALLAX_TRAVEL, scroll.coerceIn(0f, 1f))
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(washTop, surface),
                        startY = -shift,
                        endY = size.height - shift,
                    )
                )
            }
        }
    }
}

/**
 * Renders [content] over [background]. Convenience for surfaces that are not a Scaffold.
 */
@Composable
fun FeedBackgroundBox(
    background: FeedBackground,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().feedBackground(background, listState)) { content() }
}
