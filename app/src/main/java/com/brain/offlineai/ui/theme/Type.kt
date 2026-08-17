package com.brain.offlineai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val BrainTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = BrainTextPrimary),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = BrainTextPrimary),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, color = BrainTextPrimary),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, color = BrainTextSecondary),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 11.sp, color = BrainTextMuted),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, color = BrainTextPrimary),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 10.sp, color = BrainTextSecondary)
)
