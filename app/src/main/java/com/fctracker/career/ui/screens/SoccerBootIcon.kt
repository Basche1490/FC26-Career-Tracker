package com.fctracker.career.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * A football boot (studs underneath, toe on the right), used for assists in the match log. The icon set that ships
 * with Compose has a ball but no boot, so it is drawn here; tint it like any other icon.
 */
val SoccerBootIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SoccerBoot",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            // the boot
            moveTo(3f, 6.6f)
            curveTo(3f, 5.8f, 3.6f, 5.2f, 4.4f, 5.2f)
            lineTo(9.2f, 5.2f)
            curveTo(9.8f, 5.2f, 10.3f, 5.6f, 10.4f, 6.2f)
            lineTo(10.9f, 9.1f)
            curveTo(11.1f, 9.9f, 11.7f, 10.4f, 12.5f, 10.5f)
            curveTo(15.5f, 10.9f, 18.4f, 12.2f, 20.6f, 14.1f)
            curveTo(21.6f, 14.9f, 22.2f, 15.6f, 22.2f, 16.3f)
            curveTo(22.2f, 16.9f, 21.7f, 17.2f, 21.2f, 17.2f)
            lineTo(4.2f, 17.2f)
            curveTo(3.5f, 17.2f, 3f, 16.7f, 3f, 16f)
            close()
            // four studs
            moveTo(5.0f, 17.2f); lineTo(7.1f, 17.2f); lineTo(6.7f, 19.7f); lineTo(5.4f, 19.7f); close()
            moveTo(9.4f, 17.2f); lineTo(11.5f, 17.2f); lineTo(11.1f, 19.7f); lineTo(9.8f, 19.7f); close()
            moveTo(14.2f, 17.2f); lineTo(16.3f, 17.2f); lineTo(15.9f, 19.7f); lineTo(14.6f, 19.7f); close()
            moveTo(18.3f, 17.2f); lineTo(20.4f, 17.2f); lineTo(20.0f, 19.7f); lineTo(18.7f, 19.7f); close()
        }
    }.build()
}
