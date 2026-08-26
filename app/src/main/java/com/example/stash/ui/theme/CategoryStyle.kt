package com.example.stash.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual styling and iconography for content categories.
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
private val RepoHue = CategoryHue(light = Color(0xFF0D8A5B), dark = Color(0xFF4ADE80))
private val VideoHue = CategoryHue(light = Color(0xFFC0392E), dark = Color(0xFFF5A199))
private val SocialHue = CategoryHue(light = Color(0xFFD83A6F), dark = Color(0xFFFF85A1))

private val MeshHueOrder = listOf(
    ArticleHue, DocumentationHue, VideoHue, SocialHue, RepoHue,
)

/**
 * Maps a category name (including legacy variations) to its corresponding canonical hue index.
 */
fun categoryHueIndex(category: String): Int = when (category.lowercase().trim()) {
    "article", "blog", "website" -> 0
    "documentation" -> 1
    "video" -> 2
    "tweet", "discussion" -> 3
    "repo", "github repo", "code" -> 4
    else -> 0
}

/**
 * Resolves the display label, icon, and theme-aware color for a category string.
 */
@Composable
@ReadOnlyComposable
fun categoryStyle(category: String, darkTheme: Boolean): CategoryStyle {
    val (display, iconAndHue) = when (category.lowercase().trim()) {
        "article", "blog", "website" -> "Article" to (Icons.AutoMirrored.Filled.Article to ArticleHue)
        "documentation" -> "Documentation" to (Icons.AutoMirrored.Filled.MenuBook to DocumentationHue)
        "repo", "github repo", "code" -> "Repo" to (Icons.Default.Code to RepoHue)
        "video" -> "Video" to (Icons.Default.PlayCircle to VideoHue)
        "tweet", "discussion" -> "Discussion" to (Icons.Default.Forum to SocialHue)
        else -> category.trim().replaceFirstChar(Char::uppercaseChar) to
            (Icons.Default.Language to ArticleHue)
    }
    val (icon, hue) = iconAndHue
    return CategoryStyle(
        label = display,
        icon = icon,
        color = if (darkTheme) hue.dark else hue.light,
    )
}
