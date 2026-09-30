package dev.cburlacu.stash.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.cburlacu.stash.R

val GoogleSansFlex = FontFamily(
    Font(R.font.gsf_400, FontWeight.Normal),
    Font(R.font.gsf_500, FontWeight.Medium),
    Font(R.font.gsf_600, FontWeight.SemiBold),
    Font(R.font.gsf_700, FontWeight.Bold),
)

private val Default = Typography()

private fun TextStyle.gsf() = copy(fontFamily = GoogleSansFlex)

val Typography = Typography(
    displayMedium = Default.displayMedium.gsf(),
    displaySmall = Default.displaySmall.gsf(),
    headlineSmall = Default.headlineSmall.gsf(),
    titleLarge = Default.titleLarge.gsf(),
    titleSmall = Default.titleSmall.gsf(),
    labelLarge = Default.labelLarge.gsf(),
    displayLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.25).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.4.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansFlex,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)

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
