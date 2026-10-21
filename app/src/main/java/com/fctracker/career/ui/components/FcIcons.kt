package com.fctracker.career.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Icons the material set does not have. */
object FcIcons {
    /** A football boot with studs, for assists. */
    val Boot: ImageVector by lazy {
        ImageVector.Builder(name = "Boot", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f).apply {
            path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.8f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, fill = null) {
                // upper and sole
                moveTo(4f, 6f)
                lineTo(9f, 6f)
                lineTo(10.5f, 10f)
                curveTo(13f, 11f, 17f, 12f, 19.5f, 13.2f)
                curveTo(21f, 14f, 21f, 16f, 19.5f, 16.5f)
                lineTo(4f, 16.5f)
                close()
                // laces
                moveTo(10.5f, 10f)
                lineTo(9.6f, 12f)
                moveTo(13f, 11f)
                lineTo(12.2f, 12.8f)
            }
            path(fill = SolidColor(Color.Black)) {
                // studs
                moveTo(5.5f, 17f); lineTo(7.5f, 17f); lineTo(7.2f, 19f); lineTo(5.8f, 19f); close()
                moveTo(11f, 17f); lineTo(13f, 17f); lineTo(12.7f, 19f); lineTo(11.3f, 19f); close()
                moveTo(16.5f, 17f); lineTo(18.5f, 17f); lineTo(18.2f, 19f); lineTo(16.8f, 19f); close()
            }
        }.build()
    }
}

/** A yellow or red card, slightly tilted. */
@Composable
fun BookingCard(red: Boolean, size: Dp = 13.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(width = size * 0.72f, height = size).rotate(8f).clip(RoundedCornerShape(2.dp))
            .background(if (red) Color(0xFFE5484D) else Color(0xFFF5C518))
    )
}
