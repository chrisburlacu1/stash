package dev.cburlacu.stash.ui.briefing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cburlacu.stash.models.StashItem
import dev.cburlacu.stash.ui.components.TimelineRailDefaults
import dev.cburlacu.stash.ui.theme.categoryStyle

@Composable
internal fun BriefingNodeRow(
    node: BriefingNode,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.45f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "nodeAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.0f else 0.985f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "nodeScale",
    )
    val eyebrowColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        animationSpec = tween(300),
        label = "eyebrowColor",
    )
    val bulletColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        animationSpec = tween(300),
        label = "bulletColor",
    )

    Column(
        modifier = modifier
            .padding(start = TimelineRailDefaults.contentStart + (TimelineRailDefaults.indent * node.level))
            .scale(scale)
            .alpha(alpha)
            .padding(vertical = 4.dp),
    ) {
        when (node) {
            is BriefingNode.BigPicture -> {
                Text(
                    text = "THE BIG PICTURE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                node.lines.forEach { line ->
                    Text(
                        text = parseMarkdownBold(line),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                    )
                }
            }

            is BriefingNode.KeyTakeaways -> {
                Text(
                    text = "KEY TAKEAWAYS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                node.lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    val isIndented = rawLine.startsWith("  ") || rawLine.startsWith("\t")

                    when {
                        line.isBlank() -> {}
                        line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ") -> {
                            val bulletText = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isIndented) 12.dp else 0.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(if (isIndented) 3.5.dp else 4.5.dp)
                                        .clip(CircleShape)
                                        .background(bulletColor),
                                )
                                Text(
                                    text = parseMarkdownBold(bulletText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = parseMarkdownBold(line),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            is BriefingNode.Comparison -> {
                Text(
                    text = "COMPARISONS & TRADE-OFFS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )

                node.lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    val isIndented = rawLine.startsWith("  ") || rawLine.startsWith("\t")

                    when {
                        line.isBlank() -> {}
                        isSubheader(line) -> {
                            val title = cleanSubheaderText(line)
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        line.startsWith("* ") || line.startsWith("- ") || line.startsWith("• ") -> {
                            val bulletText = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = if (isIndented) 12.dp else 0.dp, bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .size(if (isIndented) 3.5.dp else 4.5.dp)
                                        .clip(CircleShape)
                                        .background(bulletColor),
                                )
                                Text(
                                    text = parseMarkdownBold(bulletText),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 22.sp,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        else -> {
                            Text(
                                text = parseMarkdownBold(line),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                }
            }

            is BriefingNode.BottomLine -> {
                Text(
                    text = "THE BOTTOM LINE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = eyebrowColor,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                node.lines.forEach { line ->
                    Text(
                        text = parseMarkdownBold(line),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 24.sp,
                    )
                }
            }
        }
    }
}

internal fun isSubheader(line: String): Boolean {
    val cleaned = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
    if (cleaned.startsWith("#")) return true
    if (cleaned.startsWith("**") && (cleaned.endsWith("**:") || cleaned.endsWith(":**") || cleaned.endsWith("**:")) && cleaned.length <= 80) {
        return true
    }
    if (cleaned.endsWith(":") && cleaned.length <= 60 && !cleaned.contains(". ")) {
        return true
    }
    return false
}

internal fun cleanSubheaderText(line: String): String {
    val cleaned = line.removePrefix("* ").removePrefix("- ").removePrefix("• ").trim()
    return cleaned.trimStart('#', ' ')
        .removePrefix("**")
        .removeSuffix("**:")
        .removeSuffix(":**")
        .removeSuffix("**")
        .removeSuffix(":")
        .trim()
}

internal fun parseMarkdownBold(text: String) = buildAnnotatedString {
    val regex = Regex("""\*\*(.*?)\*\*""")
    var lastIndex = 0
    for (match in regex.findAll(text)) {
        append(text.substring(lastIndex, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    if (lastIndex < text.length) {
        append(text.substring(lastIndex))
    }
}

@Composable
internal fun SourceItemMiniCard(
    item: StashItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = categoryStyle(item.category)

    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = modifier.height(68.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            ) {
                Icon(
                    imageVector = style.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }

            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.widthIn(max = 180.dp),
            ) {
                Text(
                    text = item.domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
