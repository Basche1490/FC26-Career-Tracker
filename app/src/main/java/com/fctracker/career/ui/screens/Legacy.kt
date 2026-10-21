package com.fctracker.career.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fctracker.career.ui.theme.FcTheme

/** The card the older screens use, drawn in the final design's style (rounded 22, surface, hairline border). */
@Composable
fun DarkCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = FcTheme.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .border(1.dp, c.border, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column(content = content)
    }
}
