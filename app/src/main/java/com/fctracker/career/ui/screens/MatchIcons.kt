package com.fctracker.career.ui.screens

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** The assist icon: "Footwear" by Magnific, from Flaticon (credited in Settings). */
const val ASSIST_ICON_URL = "https://cdn-icons-png.flaticon.com/256/2173/2173410.png"

/**
 * The boot for an assist, tinted like the other match symbols. It is loaded once and then kept by the image cache;
 * until then (or offline) the boot drawn in the app stands in.
 */
@Composable
fun AssistIcon(tint: Color, size: Dp = 14.dp, contentDescription: String? = null) {
    val drawn = rememberVectorPainter(SoccerBootIcon)
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current).data(ASSIST_ICON_URL).crossfade(false).build(),
        contentDescription = contentDescription,
        modifier = Modifier.size(size),
        placeholder = drawn,
        error = drawn,
        fallback = drawn,
        colorFilter = ColorFilter.tint(tint)
    )
}
