package com.example.stash.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual identity for a content type. The app's base palette is a single desaturated blue, so
 * category hues are defined here rather than mapped onto primary/secondary/tertiary — those
 * roles are too close together to distinguish an 8dp dot at a glance.
 *
 * Hues are tuned per theme: light mode uses mid-tones that stay legible on a light surface,
 * dark mode lifts them so they don't muddy against a dark one.
 *
 * [color] is consumed only by the light layer now — `categoryGlow`, `sheetMesh`, the assistant
 * message wash — never as ink. See DESIGN-NOTES, "M3 owns ink, Stash owns light": every ink use
 * (the eyebrow/header pills, tag chips, swipe panels, chat header) reads a plain M3 role instead.
 * `CategoryStyle` used to carry a `container` field for exactly that ink use; it is gone now that
 * nothing consumes it, along with the matching `lightContainer`/`darkContainer` values below.
 */
data class CategoryStyle(
    val label: String,
    val icon: ImageVector,
    val color: Color,
)

private data class CategoryHue(
    val light: Color,
    val dark: Color,
)

private val ArticleHue = CategoryHue(light = Color(0xFF1B6BB5), dark = Color(0xFF8FC2F5))
private val DocumentationHue = CategoryHue(light = Color(0xFF6D4BB8), dark = Color(0xFFC0AAF5))
private val RepoHue = CategoryHue(light = Color(0xFF4A5568), dark = Color(0xFFB4BECC))
private val VideoHue = CategoryHue(light = Color(0xFFC0392E), dark = Color(0xFFF5A199))
private val SocialHue = CategoryHue(light = Color(0xFFB5591B), dark = Color(0xFFF3B382))

/**
 * Every category hue at once, in a fixed order.
 *
 * [categoryStyle] answers "what colour is *this* item", which is all the card needs once the model
 * has decided. The summarizing state needs the opposite: every colour the answer could still turn
 * out to be, blended together, because that is what the model not having decided yet looks like.
 * See `SummarizingMesh`.
 *
 * Ordered deliberately rather than by declaration convenience — adjacent hues sit apart on the
 * wheel (blue, violet, red, orange, slate), so the mesh reads as several distinct possibilities in
 * tension. Sorting them into a smooth spectrum would blend into one continuous wash and lose
 * exactly the "undecided between these" meaning the effect exists to carry.
 *
 * Slate is last: it is the least saturated, and at the head of the list it dulled the whole mesh.
 */
@Composable
@ReadOnlyComposable
fun categoryHues(darkTheme: Boolean): List<Color> =
    MeshHueOrder.map { if (darkTheme) it.dark else it.light }

private val MeshHueOrder = listOf(
    ArticleHue, DocumentationHue, VideoHue, SocialHue, RepoHue,
)

/**
 * Every category hue in its *luminous* form, in [MeshHueOrder], regardless of the active theme.
 *
 * [categoryHues] answers "what colour should this hue be *as ink* on the current surface", which is
 * right for a pill, a chip, or a glow tinting a card. An additive light field wants the opposite
 * question. The light-theme values are mid-tones (~40-50% lightness) chosen to stay legible against
 * white; summed into a field that tonemaps with `1 - exp(-col)` they produce a dim, heavy wash
 * rather than light — dark saturated inputs have little energy to give. The dark-theme values are
 * already lifted to read as emission against a dark surface, which is exactly what an emitter is.
 *
 * So the splash uses these in both themes and gets its theme-awareness from the surface it fades
 * over instead. Not a composable: an emitter palette does not depend on the ambient theme.
 */
fun luminousCategoryHues(): List<Color> = MeshHueOrder.map { it.dark }

/**
 * Where [category] lands in [categoryHues].
 *
 * The mesh resolves *toward* this index when the model answers. Anything off-list — including
 * "Unsorted", which is what a row carries before the model has spoken and what it keeps if
 * inference fails — resolves to the article hue, exactly as [categoryStyle] does for the same
 * input. The two must agree: the mesh contracts to this hue and then hands off to `categoryGlow`
 * drawing [CategoryStyle.color], so disagreeing here would swap the colour at the handover.
 *
 * Never returns -1. An earlier version did, leaving unrecognised categories with no winner to
 * converge on, so the mesh dissolved in place instead of contracting to a pool. That made a failed
 * categorization read as a broken animation on top of being a failure — and the glow it handed off
 * to was showing the article hue regardless, so the "unknown" state was never actually colourless.
 *
 * The taxonomy collapsed from seven categories to five: Blog and Website (the old fallback bucket)
 * both fold into Article — nothing in the UI ever distinguished a blog post from an article, and
 * Website was never a real category, just what an unrecognised page fell back to. Tweet folds into
 * Discussion (both already shared [SocialHue]); Code folds into Repo (both already shared
 * [RepoHue]). Old rows saved before the collapse still carry the pre-collapse strings — "Blog",
 * "Website", "Tweet", "Code", "GitHub repo" — and must keep mapping to a sensible hue rather than
 * falling through silently.
 */
fun categoryHueIndex(category: String): Int = when (category.lowercase().trim()) {
    "article", "blog", "website" -> 0
    "documentation" -> 1
    "video" -> 2
    "tweet", "discussion" -> 3
    "repo", "github repo", "code" -> 4
    // "Unsorted", and anything the model invents outside the fixed set.
    else -> 0
}

/**
 * Resolves the style for a raw category string. Unknown values fall back to the neutral
 * article styling rather than an error color — the AI occasionally returns something outside
 * the prompt's fixed set, and that shouldn't look like a failure.
 *
 * The five current categories are Article, Documentation, Repo, Video, Discussion — collapsed
 * from seven. The `when` below also maps the pre-collapse strings ("Blog", "Website", "Tweet",
 * "Code", "GitHub repo", "GitHub Repo") that rows saved before the collapse still carry, so
 * existing history keeps rendering a sensible category instead of falling through to the
 * unknown branch. Must stay in agreement with [categoryHueIndex] — see its doc comment.
 */
@Composable
@ReadOnlyComposable
fun categoryStyle(category: String, darkTheme: Boolean): CategoryStyle {
    // Each known category carries its own display label so proper nouns keep their casing —
    // a blanket capitalise would render "github repo" as "Github repo".
    val (display, iconAndHue) = when (category.lowercase().trim()) {
        // Blog and Website (the old fallback bucket) fold into Article: neither had a reliable
        // signal to distinguish it, and nothing in the UI ever treated them differently.
        "article", "blog", "website" -> "Article" to (Icons.AutoMirrored.Filled.Article to ArticleHue)
        "documentation" -> "Documentation" to (Icons.Default.MenuBook to DocumentationHue)
        // Code folds into Repo: both already shared RepoHue, so this only unifies the label.
        "repo", "github repo", "code" -> "Repo" to (Icons.Default.Code to RepoHue)
        "video" -> "Video" to (Icons.Default.PlayCircle to VideoHue)
        // Tweet folds into Discussion: both already shared SocialHue.
        "tweet", "discussion" -> "Discussion" to (Icons.Default.Forum to SocialHue)
        // Unrecognised values (including "Unsorted") are shown as the model returned them, only
        // sentence-cased: better to surface an unexpected category than hide the drift.
        else -> category.trim().replaceFirstChar(Char::uppercaseChar) to
            (Icons.Default.Language to ArticleHue)
    }
    val (icon, hue) = iconAndHue
    return CategoryStyle(
        // Sentence case, not the uppercase this used to return: the label now sits in the card's
        // byline next to the relative time ("Article · 2h ago"), where all-caps read as a shout.
        label = display,
        icon = icon,
        color = if (darkTheme) hue.dark else hue.light,
    )
}
