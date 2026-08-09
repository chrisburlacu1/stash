package com.example.stash.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.example.stash.R

/**
 * Google Sans Flex, shipped as four static cuts rather than one variable file: the Google Fonts
 * CSS API serves per-weight instances for this family, and a single instance would render every
 * FontWeight identically.
 */
val GoogleSansFlex = FontFamily(
    Font(R.font.gsf_400, FontWeight.Normal),
    Font(R.font.gsf_500, FontWeight.Medium),
    Font(R.font.gsf_600, FontWeight.SemiBold),
    Font(R.font.gsf_700, FontWeight.Bold),
)
