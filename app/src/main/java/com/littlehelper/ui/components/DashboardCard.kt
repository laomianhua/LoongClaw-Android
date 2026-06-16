package com.littlehelper.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.data.MemoryRecord
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardCard(
    record: MemoryRecord,
    onToggleTodo: (MemoryRecord) -> Unit,
    onDeleteRecord: (MemoryRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    val isTodo = record.type == "todo"
    val isDone = record.done
    val importance = record.importanceLevel

    val backgroundColor = when {
        isDone -> Color(0xFFF5F5F5).copy(alpha = 0.75f)
        importance == "critical" -> Color(0xFFFFF0F0).copy(alpha = 0.85f) // 浅粉红色提醒，稍微不透明一点
        else -> Color.White.copy(alpha = 0.75f)
    }
    val borderColor = when {
        isDone -> Color.White.copy(alpha = 0.5f)
        importance == "critical" -> Color(0xFFFFCDD2)
        else -> Color.White.copy(alpha = 0.5f)
    }
    val textColor = if (isDone) Color.Gray else Color.Black
    val textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "提示") },
            text = { Text(text = "确定要删除这条记录吗？") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteRecord(record)
                    showDeleteDialog = false
                }) {
                    Text("删除", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .combinedClickable(
                onClick = {},
                onLongClick = { showDeleteDialog = true }
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor).takeIf { borderColor != Color.Transparent },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TypeBadge(type = record.type, category = record.category)
                    Spacer(modifier = Modifier.width(8.dp))
                    ImportanceBadge(importance = importance)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(record.createdAt),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = record.summary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    textDecoration = textDecoration
                )
                if (!record.person.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "相关人: ${record.person}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            if (isTodo) {
                Checkbox(
                    checked = isDone,
                    onCheckedChange = { onToggleTodo(record) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF4CAF50),
                        uncheckedColor = Color.Gray
                    )
                )
            }
        }
    }
}

@Composable
private fun TypeBadge(type: String, category: String) {
    val (icon, text, bgColor, textColor) = when {
        type == "todo" -> listOf("📌", "待办", Color(0xFFFFF3E0), Color(0xFFE65100))
        category == "birthday" -> listOf("🎂", "生日", Color(0xFFFCE4EC), Color(0xFFC2185B))
        category == "schedule" -> listOf("📅", "事件", Color(0xFFE3F2FD), Color(0xFF1565C0))
        type == "note" -> listOf("📝", "备忘", Color(0xFFF3E5F5), Color(0xFF7B1FA2))
        else -> listOf("💬", "通用", Color(0xFFEEEEEE), Color(0xFF616161))
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = bgColor as Color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon as String, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text as String,
                fontSize = 10.sp,
                color = textColor as Color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ImportanceBadge(importance: String) {
    when (importance) {
        "critical" -> {
            Text(text = "❗️", fontSize = 12.sp)
        }
        "important" -> {
            Text(text = "⭐", fontSize = 12.sp)
        }
        else -> {
            // normal 或其他情况，保持素雅，不显示图标或显示小灰点
            // Text(text = "•", fontSize = 12.sp, color = Color.LightGray)
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
