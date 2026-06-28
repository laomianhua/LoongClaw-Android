package com.littlehelper.shell.modules.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.littlehelper.ui.theme.AppColors

@Composable
fun TableBlockRenderer(
    data: JsonObject,
    modifier: Modifier = Modifier
) {
    val parsed = parseTableData(data)
    val headers = parsed.headers
    val rows = parsed.rows
    val options = data.getAsJsonObject("options")
    val highlight = options?.get("highlight")?.asString ?: "none"
    val striped = options?.get("striped")?.asBoolean == true

    if (headers.isEmpty() && rows.isEmpty()) {
        TableEmptyState(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(0.5.dp, AppColors.panelBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF7F8FA))
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            headers.forEach { header ->
                Text(
                    text = header.label,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppColors.textHint,
                    textAlign = header.textAlign
                )
            }
        }
        HorizontalDivider(color = AppColors.panelBorder)
        rows.forEachIndexed { index, row ->
            val rowBg = if (striped && index % 2 == 1) Color(0xFFFAFAFA) else Color.White
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(rowBg)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                headers.forEach { header ->
                    val cell = row[header.key].orEmpty()
                    Text(
                        text = cell,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 2.dp),
                        fontSize = 14.sp,
                        color = cellColor(cell, highlight),
                        textAlign = header.textAlign
                    )
                }
            }
            if (index < rows.lastIndex) {
                HorizontalDivider(color = AppColors.panelBorder.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun TableEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .border(0.5.dp, AppColors.panelBorder, RoundedCornerShape(12.dp))
            .padding(vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "表格数据为空（请检查 headers / rows 格式）",
            fontSize = 13.sp,
            color = AppColors.textHint
        )
    }
}

internal data class ParsedTableData(
    val headers: List<TableHeader>,
    val rows: List<Map<String, String>>
)

internal data class TableHeader(
    val key: String,
    val label: String,
    val align: String
) {
    val textAlign: TextAlign = when (align.lowercase()) {
        "center" -> TextAlign.Center
        "right" -> TextAlign.End
        else -> TextAlign.Start
    }
}

/** 兼容 schema 对象格式与 Agent 常犯的字符串/数组简写。 */
internal fun parseTableData(data: JsonObject): ParsedTableData {
    val headers = parseTableHeaders(data)
    val rows = parseTableRows(data, headers)
    return ParsedTableData(headers = headers, rows = rows)
}

private fun parseTableHeaders(data: JsonObject): List<TableHeader> {
    val array = data.getAsJsonArray("headers") ?: return emptyList()
    return buildList {
        array.forEachIndexed { index, element ->
            when {
                element.isJsonObject -> {
                    val obj = element.asJsonObject
                    add(
                        TableHeader(
                            key = obj.get("key")?.asString?.takeIf { it.isNotEmpty() } ?: "col$index",
                            label = obj.get("label")?.asString.orEmpty(),
                            align = obj.get("align")?.asString ?: "left"
                        )
                    )
                }
                element.isJsonPrimitive -> {
                    val label = element.asString.trim()
                    if (label.isNotEmpty()) {
                        add(TableHeader(key = "col$index", label = label, align = "left"))
                    }
                }
            }
        }
    }
}

private fun parseTableRows(
    data: JsonObject,
    headers: List<TableHeader>
): List<Map<String, String>> {
    val array = data.getAsJsonArray("rows") ?: return emptyList()
    return buildList {
        array.forEachIndexed { rowIndex, element ->
            when {
                element.isJsonObject -> {
                    add(
                        element.asJsonObject.entrySet().associate { (k, v) ->
                            k to v.takeIf { !it.isJsonNull }?.asString.orEmpty()
                        }
                    )
                }
                element.isJsonArray -> {
                    val cells = element.asJsonArray
                    val row = linkedMapOf<String, String>()
                    if (headers.isNotEmpty()) {
                        headers.forEachIndexed { colIndex, header ->
                            val cell = if (colIndex < cells.size()) cells.get(colIndex) else null
                            row[header.key] = when {
                                cell == null || cell.isJsonNull -> ""
                                else -> cell.asString
                            }
                        }
                    } else {
                        for (colIndex in 0 until cells.size()) {
                            val cell = cells[colIndex]
                            row["col$colIndex"] = when {
                                cell.isJsonNull -> ""
                                else -> cell.asString
                            }
                        }
                    }
                    add(row)
                }
                element.isJsonPrimitive -> {
                    add(mapOf((headers.firstOrNull()?.key ?: "col0") to element.asString))
                }
            }
        }
    }
}

private fun cellColor(value: String, highlight: String): Color {
    if (highlight != "red_up_green_down") return AppColors.textPrimary
    val trimmed = value.trim()
    if (trimmed.startsWith("+")) return Color(0xFFE53935)
    if (trimmed.startsWith("-")) return Color(0xFF43A047)
    return AppColors.textPrimary
}
