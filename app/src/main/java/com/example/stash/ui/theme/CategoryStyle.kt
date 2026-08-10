package com.example.stash.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Tag
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
 */
data class CategoryStyle(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val container: Color,
)

private data class CategoryHue(
    val light: Color,
    val lightContainer: Color,
    val dark: Color,
    val darkContainer: Color,
)

private val ArticleHue = CategoryHue(
    light = Color(0xFF1B6BB5), lightContainer = Color(0xFFDCEBFB),
    dark = Color(0xFF8FC2F5), darkContainer = Color(0xFF14324F),
)
private val DocumentationHue = CategoryHue(
    light = Color(0xFF6D4BB8), lightContainer = Color(0xFFE9E1FA),
    dark = Color(0xFFC0AAF5), darkContainer = Color(0xFF2E2350),
)
private val WebsiteHue = CategoryHue(
    light = Color(0xFF0F7B72), lightContainer = Color(0xFFD3F0EC),
    dark = Color(0xFF6FD5C8), darkContainer = Color(0xFF0D3A36),
)
private val RepoHue = CategoryHue(
    light = Color(0xFF4A5568), lightContainer = Color(0xFFE3E7EC),
    dark = Color(0xFFB4BECC), darkContainer = Color(0xFF2A3140),
)
private val VideoHue = CategoryHue(
    light = Color(0xFFC0392E), lightContainer = Color(0xFFFBE0DD),
    dark = Color(0xFFF5A199), darkContainer = Color(0xFF4E1D18),
)
private val SocialHue = CategoryHue(
    light = Color(0xFFB5591B), lightContainer = Color(0xFFFBE7D8),
    dark = Color(0xFFF3B382), darkContainer = Color(0xFF4C2A11),
)
private val BlogHue = CategoryHue(
    light = Color(0xFFA8317A), lightContainer = Color(0xFFFADCEF),
    dark = Color(0xFFF29BD0), darkContainer = Color(0xFF4B1638),
)

/**
 * Resolves the style for a raw category string. Unknown values fall back to the neutral
 * website styling rather than an error color — the AI occasionally returns something outside
 * the prompt's fixed set, and that shouldn't look like a failure.
 */
@Composable
@ReadOnlyComposable
fun categoryStyle(category: String, darkTheme: Boolean): CategoryStyle {
    // Each known category carries its own display label so proper nouns keep their casing —
    // a blanket capitalise would render "github repo" as "Github repo".
    val (display, iconAndHue) = when (category.lowercase().trim()) {
        "article" -> "Article" to (Icons.AutoMirrored.Filled.Article to ArticleHue)
        "documentation" -> "Documentation" to (Icons.Default.MenuBook to DocumentationHue)
        "blog" -> "Blog" to (Icons.Default.Tag to BlogHue)
        "github repo" -> "GitHub repo" to (Icons.Default.Code to RepoHue)
        "code" -> "Code" to (Icons.Default.Code to RepoHue)
        "video" -> "Video" to (Icons.Default.PlayCircle to VideoHue)
        "tweet" -> "Tweet" to (Icons.Default.Forum to SocialHue)
        "discussion" -> "Discussion" to (Icons.Default.Forum to SocialHue)
        // Unrecognised values are shown as the model returned them, only sentence-cased: better
        // to surface an unexpected category than to relabel it "Website" and hide the drift.
        else -> category.trim().replaceFirstChar(Char::uppercaseChar) to
            (Icons.Default.Language to WebsiteHue)
    }
    val (icon, hue) = iconAndHue
    return CategoryStyle(
        // Sentence case, not the uppercase this used to return: the label now sits in the card's
        // byline next to the relative time ("Article · 2h ago"), where all-caps read as a shout.
        label = display,
        icon = icon,
        color = if (darkTheme) hue.dark else hue.light,
        container = if (darkTheme) hue.darkContainer else hue.lightContainer,
    )
}
