package com.example.stash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em

/**
 * Typographic roles for the feed, scaled relative to font size using em tracking and leading.
 */
@Immutable
data class FeedTextStyles(
    val heroTitle: TextStyle,
    val compactTitle: TextStyle,
    val eyebrow: TextStyle,
    val snippet: TextStyle,
    val typeBadge: TextStyle,
    val tag: TextStyle,
    val sectionLabel: TextStyle,
)

val feedTextStyles: FeedTextStyles
    @Composable @ReadOnlyComposable
    get() {
        val type = MaterialTheme.typography
        return FeedTextStyles(
            heroTitle = type.headlineSmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.14.em,
                letterSpacing = (-0.025).em,
                lineBreak = LineBreak.Heading,
            ),
            compactTitle = type.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 1.25.em,
                letterSpacing = (-0.015).em,
                lineBreak = LineBreak.Heading,
            ),
            eyebrow = type.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.09.em,
                lineHeight = 1.3.em,
            ),
            snippet = type.bodyMedium.copy(
                fontWeight = FontWeight.Normal,
                lineHeight = 1.45.em,
                letterSpacing = 0.em,
            ),
            typeBadge = type.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.06.em,
            ),
            tag = type.labelSmall.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.01.em,
            ),
            sectionLabel = type.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.08.em,
            ),
        )
    }
