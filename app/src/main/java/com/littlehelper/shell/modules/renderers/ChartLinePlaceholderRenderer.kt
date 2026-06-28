package com.littlehelper.shell.modules.renderers

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.littlehelper.ui.theme.AppColors

/**
 * Phase 1 折线图占位：简易 Canvas 折线 + 标题；完整 ECharts 集成留待后续。
 */
@Composable
fun ChartLinePlaceholderRenderer(
    title: String?,
    data: JsonObject,
    modifier: Modifier = Modifier
) {
    val series = data.getAsJsonArray("series")?.let { array ->
        if (array.size() == 0) null else array[0].asJsonObject
    }
    val colorHex = series?.get("color")?.asString ?: "#1890FF"
    val lineColor = parseHexColor(colorHex)
    val points = series?.getAsJsonArray("points")?.let { array ->
        buildList {
            array.forEach { element ->
                if (!element.isJsonObject) return@forEach
                val y = element.asJsonObject.get("y")?.asDouble ?: return@forEach
                add(y.toFloat())
            }
        }
    }.orEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        title?.let {
            Text(
                text = it,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.textPrimary
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (points.size >= 2) {
                Canvas(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                    val minY = points.minOrNull() ?: 0f
                    val maxY = points.maxOrNull() ?: 1f
                    val range = (maxY - minY).coerceAtLeast(0.01f)
                    val stepX = size.width / (points.size - 1).coerceAtLeast(1)
                    val path = Path()
                    points.forEachIndexed { index, y ->
                        val x = stepX * index
                        val normalizedY = size.height - ((y - minY) / range) * size.height
                        if (index == 0) path.moveTo(x, normalizedY) else path.lineTo(x, normalizedY)
                    }
                    drawPath(path, color = lineColor, style = Stroke(width = 3f))
                    points.forEachIndexed { index, y ->
                        val x = stepX * index
                        val normalizedY = size.height - ((y - minY) / range) * size.height
                        drawCircle(color = lineColor, radius = 4f, center = Offset(x, normalizedY))
                    }
                }
            } else {
                Text(
                    text = "折线图占位",
                    fontSize = 13.sp,
                    color = AppColors.textHint
                )
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    val cleaned = hex.removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(0xFF000000 or cleaned.toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> Color(0xFF1890FF)
        }
    } catch (_: Exception) {
        Color(0xFF1890FF)
    }
}
