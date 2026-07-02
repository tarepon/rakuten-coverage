package com.example.rakutencoverage.ui.theme

import androidx.compose.ui.graphics.Color

// ポケGO風パレット — マップUIの基調色
val GoBlue        = Color(0xFF29B6F6)   // メインアクション（捕獲・追従・実行中）
val GoBlueDark    = Color(0xFF0288D1)   // GoBlue の濃色（プレス時・境界線）
val GoDarkNavy    = Color(0xFF1A2B3C)   // パネル背景（半透明で使用）
val GoDarkNavy2   = Color(0xFF0F1D2A)   // より濃いパネル背景（Surface用）
val GoWhite       = Color(0xFFF5F7FA)   // テキスト・アイコン
val GoAccent      = Color(0xFFFFC107)   // レア・強調・GET演出
val GoDanger      = Color(0xFFEF5350)   // 停止・エラー

// パネル背景（半透明ダークネイビー + 角丸24dpで使用する想定）
val PanelBackgroundAlpha = 0.85f
