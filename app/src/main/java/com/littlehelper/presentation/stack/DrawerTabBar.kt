package com.littlehelper.presentation.stack

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DrawerTabBar(
    cards: List<DrawerCard>,
    activeCard: DrawerCard,
    onSelect: (DrawerCard) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        cards.forEach { card ->
            val selected = card == activeCard
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (selected) Color.White.copy(alpha = 0.92f)
                        else Color.White.copy(alpha = 0.55f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSelect(card) }
                    .padding(vertical = 10.dp, horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = card.icon, fontSize = 16.sp)
                Text(
                    text = card.label,
                    modifier = Modifier.padding(start = 4.dp),
                    fontSize = 15.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = Color(0xFF212121)
                )
            }
        }
    }
}
