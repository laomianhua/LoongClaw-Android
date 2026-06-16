package com.littlehelper.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object AppFontSize {
    val title = 30.sp
    val body = 24.sp
    val display = 28.sp
    val displayLineHeight = 40.sp
    val button = 28.sp
    val hint = 18.sp
}

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    )
)
