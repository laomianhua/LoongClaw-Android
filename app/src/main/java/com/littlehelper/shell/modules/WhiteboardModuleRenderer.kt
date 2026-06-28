package com.littlehelper.shell.modules

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.littlehelper.shell.model.ModulePayload
import com.littlehelper.shell.model.UiComponentDto
import com.littlehelper.ui.theme.AppColors

/**
 * 纯血多模态白板槽位：由 Gateway 下发的 [UiComponentDto] 流驱动，与 MAP/NOTE 平行演进。
 */
@Composable
fun WhiteboardModuleRenderer(
    payload: ModulePayload.Whiteboard,
    modifier: Modifier = Modifier
) {
    if (payload.components.isEmpty()) {
        WhiteboardEmptyState(modifier = modifier)
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(payload.components, key = { it.id }) { component ->
            UiComponentNode(component = component)
        }
    }
}

@Composable
private fun WhiteboardEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.systemBackground),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "白板就绪", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = AppColors.textPrimary)
        Text(
            text = "等待 OpenClaw 下发组件流…",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 13.sp,
            color = AppColors.textHint
        )
    }
}

@Composable
private fun UiComponentNode(
    component: UiComponentDto,
    modifier: Modifier = Modifier
) {
    when (component.type.lowercase()) {
        "text" -> Text(
            text = component.text.orEmpty(),
            modifier = modifier.fillMaxWidth(),
            fontSize = 15.sp,
            color = AppColors.textPrimary,
            lineHeight = 22.sp
        )

        "title" -> Text(
            text = component.text.orEmpty(),
            modifier = modifier.fillMaxWidth(),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.textPrimary
        )

        "icon" -> Text(
            text = component.icon ?: component.text.orEmpty(),
            modifier = modifier,
            fontSize = 28.sp
        )

        "metric" -> MetricCard(component = component, modifier = modifier)

        "divider" -> HorizontalDivider(
            modifier = modifier.padding(vertical = 4.dp),
            color = AppColors.panelBorder
        )

        "row" -> Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            component.children.forEach { child ->
                UiComponentNode(component = child, modifier = Modifier.weight(1f, fill = false))
            }
        }

        else -> Text(
            text = component.text ?: "[${component.type}]",
            modifier = modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(10.dp))
                .padding(12.dp),
            fontSize = 14.sp,
            color = AppColors.textHint
        )
    }
}

@Composable
private fun MetricCard(
    component: UiComponentDto,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        component.attributes["label"]?.let { label ->
            Text(text = label, fontSize = 12.sp, color = AppColors.textHint)
        }
        Text(
            text = component.text.orEmpty(),
            modifier = Modifier.padding(top = 2.dp),
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = AppColors.textPrimary
        )
    }
}
