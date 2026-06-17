package me.aleesk.bandit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Color Palette ────────────────────────────────────────────────────────────

object BanDITColors {
    val NavyDeep        = Color(0xFF0A0F1E)
    val NavySurface     = Color(0xFF111827)
    val NavyCard        = Color(0xFF1A2236)
    val NavyBorder      = Color(0xFF243049)
    val CyanPrimary     = Color(0xFF00D9E8)
    val CyanMuted       = Color(0xFF0891B2)
    val CyanDim         = Color(0xFF164E63)
    val TextPrimary     = Color(0xFFE8F4FD)
    val TextSecond      = Color(0xFF8BA3BF)
    val TextMuted       = Color(0xFF4A6070)
    val AlertRed        = Color(0xFFEF4444)
    val AlertRedDim     = Color(0xFF2D1515)
    val SuccessGreen    = Color(0xFF10B981)
    val SuccessGreenDim = Color(0xFF0D2D22)
    val WarnOrange      = Color(0xFFF59E0B)
}

// ─── Dark Color Scheme ────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary          = BanDITColors.CyanPrimary,
    onPrimary        = BanDITColors.NavyDeep,
    primaryContainer = BanDITColors.CyanDim,
    secondary        = BanDITColors.CyanMuted,
    background       = BanDITColors.NavyDeep,
    surface          = BanDITColors.NavySurface,
    surfaceVariant   = BanDITColors.NavyCard,
    onBackground     = BanDITColors.TextPrimary,
    onSurface        = BanDITColors.TextPrimary,
    onSurfaceVariant = BanDITColors.TextSecond,
    error            = BanDITColors.AlertRed,
    outline          = BanDITColors.NavyBorder
)

// ─── Theme Wrapper ────────────────────────────────────────────────────────────

@Composable
fun BanDITTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content     = content
    )
}

// ─── Splash ───────────────────────────────────────────────────────────────────

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BanDITColors.NavyDeep),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(color = BanDITColors.CyanPrimary)
            Text(
                "BanDIT",
                color      = BanDITColors.CyanPrimary,
                fontSize   = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp
            )
        }
    }
}