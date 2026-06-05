package com.onyxlauncher.ui.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.onyxlauncher.domain.model.PageIndicatorStyle

@Composable
fun PageIndicator(
    pageCount: Int,
    currentPage: Int,
    style: PageIndicatorStyle,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.35f),
) {
    if (style == PageIndicatorStyle.NONE || pageCount <= 1) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            if (style == PageIndicatorStyle.DOTS) {
                val size by animateDpAsState(if (isActive) 8.dp else 6.dp, label = "dot")
                Box(
                    Modifier
                        .size(size)
                        .clip(CircleShape)
                        .background(if (isActive) activeColor else inactiveColor)
                )
            } else {
                val width by animateDpAsState(if (isActive) 20.dp else 8.dp, label = "line")
                Box(
                    Modifier
                        .height(3.dp)
                        .width(width)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isActive) activeColor else inactiveColor)
                )
            }
        }
    }
}
