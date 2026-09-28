package dev.cburlacu.stash.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Semantic iconography and display labels for content categories.
 */
data class CategoryStyle(
    val label: String,
    val icon: ImageVector,
)

/**
 * Resolves the display label and icon for a category string.
 */
fun categoryStyle(category: String): CategoryStyle {
    val (display, icon) = when (category.lowercase().trim()) {
        "article", "blog", "website" -> "Article" to Icons.AutoMirrored.Filled.Article
        "documentation" -> "Documentation" to Icons.AutoMirrored.Filled.MenuBook
        "repo", "github repo", "code" -> "Repo" to Icons.Default.Code
        "video" -> "Video" to Icons.Default.PlayCircle
        "tweet", "discussion" -> "Discussion" to Icons.Default.Forum
        else -> category.trim().replaceFirstChar(Char::uppercaseChar) to Icons.Default.Language
    }
    return CategoryStyle(
        label = display,
        icon = icon,
    )
}
