package com.noir.lynx.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

// ============================================================
//  Material 3 Expressive — Clean Minimalist Obsidian Design Tokens
// ============================================================

val BgDeepOled       = Color(0xFF07080A) // Midnight Obsidian (True OLED)
val BgSurfaceLowest  = Color(0xFF090A0F) // Surface Container Lowest
val BgCard           = Color(0xFF12141D) // Deep Graphite Container (Clean Matte)
val BgElevated       = Color(0xFF181B26) // Elevated Tile Container
val BgGlassPill      = Color(0x14FFFFFF) // Subtle Frosted Pill Background

val AccentCyan       = Color(0xFF00F5A0) // Luminous Cyber Mint
val AccentCyanDim    = Color(0x1F00F5A0)
val AccentBlue       = Color(0xFF00A3FF) // Electric Azure
val AccentBlueDim    = Color(0x1F00A3FF)
val AccentOrange     = Color(0xFFFF9F2E) // Sunset Amber Flame
val AccentOrangeDim  = Color(0x1FFF9F2E)
val AccentRed        = Color(0xFFFF3B5C) // Neon Crimson
val AccentRedDim     = Color(0x1FFF3B5C)
val AccentPurple     = Color(0xFFA855F7) // Royal Amethyst
val AccentGreen      = Color(0xFF10B981) // Emerald Mint
val AccentGreenDim   = Color(0x1F10B981)

val TextPrimary      = Color(0xFFF8FAFC) // Ultra Clean Soft White
val TextSecondary    = Color(0xFF94A3B8) // Cool Slate Grey
val TextTertiary     = Color(0xFF64748B) // Subtle Metallic Grey
val BorderSubtle     = Color(0x14FFFFFF) // Hairline 8% Border
val BorderGlass      = Color(0x1EFFFFFF) // Subtle 12% Border

data class LynxPresetOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val color: Color
)
