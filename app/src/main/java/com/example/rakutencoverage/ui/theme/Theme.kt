package com.example.rakutencoverage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val RakutenCoverageDarkColorScheme = darkColorScheme(
    primary            = GoBlue,
    onPrimary          = Color.White,
    primaryContainer   = GoBlueDark,
    onPrimaryContainer = GoWhite,
    secondary          = GoAccent,
    onSecondary        = Color(0xFF1A2B3C),
    error              = GoDanger,
    onError            = Color.White,
    background         = GoDarkNavy2,
    onBackground       = GoWhite,
    surface            = GoDarkNavy,
    onSurface          = GoWhite,
    surfaceVariant     = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
    onSurfaceVariant   = GoWhite.copy(alpha = 0.75f),
)

/**
 * ポケモンGO風のダークテーマ。マップ画面は常時これを使用する。
 * 既存の MaterialTheme { } の置き換えとして MainActivity から呼び出す。
 */
@Composable
fun RakutenCoverageTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RakutenCoverageDarkColorScheme,
        content = content
    )
}
