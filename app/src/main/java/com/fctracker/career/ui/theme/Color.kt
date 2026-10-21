package com.fctracker.career.ui.theme

import androidx.compose.ui.graphics.Color

// The final design, "Story Analyst": a dark green-black palette with one accent (the club colour, or mint).

val FcBg = Color(0xFF0B0F0D)
val FcSurface = Color(0xFF131A16)
val FcSurface2 = Color(0xFF1B241F)
val FcInset = Color(0xFF0E1411)
val FcBorder = Color(0xFF28352E)
val FcText = Color(0xFFE9F1EC)
val FcMuted = Color(0xFF9CB1A5)
val FcNavBg = Color(0xFF161F1A)
val FcScrim = Color(0xC7020403)

val FcMint = Color(0xFF62E0B0)
val FcOnMint = Color(0xFF05140D)

val FcGood = Color(0xFF7EE0A4)
val FcGoodBg = Color(0xFF11301F)
val FcWarn = Color(0xFFFFC857)
val FcWarnBg = Color(0xFF3A2D0B)
val FcBad = Color(0xFFFF8F86)
val FcBadBg = Color(0xFF3C1816)
val FcInfo = Color(0xFF7CC4FF)
val FcInfoBg = Color(0xFF112A3D)
val FcLive = Color(0xFF62E0B0)
val FcLiveBg = Color(0xFF0F2A1F)
val FcLiveLine = Color(0xFF235A44)

// The names the existing screens use, mapped onto the final palette so every screen takes the new look.
val BackgroundDark = FcBg
val SurfaceDark = FcSurface
val SurfaceVariantDark = FcSurface2
val PrimaryDark = FcMint
val OnPrimaryDark = FcOnMint
val OnBackgroundDark = FcText
val OnSurfaceVariantDark = FcMuted
val OutlineDark = FcBorder

// Results never take the club colour, and always come with W, D or L as well.
val WinColor = FcGood
val DrawColor = FcWarn
val LossColor = FcBad

// Connection and data-quality signals. Shapes and words always go with them, so colour is never the only cue.
val StatusLive = FcLive
val StatusWarn = FcWarn
val StatusDown = FcBad
val StatusIdle = FcMuted
