package com.littlehelper.shell.modules.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.littlehelper.ui.theme.AppColors

@Composable
fun MarkdownBlockRenderer(
    data: JsonObject,
    modifier: Modifier = Modifier
) {
    val content = data.get("content")?.asString.orEmpty()
    val annotated = remember(content) { parseBasicMarkdown(content) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = annotated,
            fontSize = 15.sp,
            color = AppColors.textPrimary,
            lineHeight = 22.sp
        )
    }
}

/** Phase 1 轻量 Markdown：标题、加粗、行内代码、列表、引用。 */
internal fun parseBasicMarkdown(source: String): AnnotatedString = buildAnnotatedString {
    source.lines().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        when {
            line.startsWith("# ") -> withStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)) {
                append(line.removePrefix("# ").trim())
            }
            line.startsWith("## ") -> withStyle(SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) {
                append(line.removePrefix("## ").trim())
            }
            line.startsWith("> ") -> withStyle(
                SpanStyle(
                    color = AppColors.textHint,
                    fontStyle = FontStyle.Italic
                )
            ) {
                append(line.removePrefix("> ").trim())
            }
            line.trimStart().startsWith("- ") -> {
                append("• ")
                appendInlineMarkdown(line.trimStart().removePrefix("- ").trim())
            }
            else -> appendInlineMarkdown(line)
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i++
                }
            }
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFFF0F0F0)
                        )
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i++
                }
            }
            else -> {
                append(text[i])
                i++
            }
        }
    }
}
