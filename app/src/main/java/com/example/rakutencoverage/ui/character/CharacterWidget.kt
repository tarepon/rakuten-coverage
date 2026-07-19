package com.example.rakutencoverage.ui.character

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rakutencoverage.data.SignalLevel

@Composable
fun CharacterWidget(state: CharacterState, modifier: Modifier = Modifier) {
    val isExcited = state.level == SignalLevel.PLATINUM_5G || state.level == SignalLevel.FIVE_G

    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isExcited) 1.22f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val bgColor by animateColorAsState(
        targetValue = when (state.level) {
            SignalLevel.PLATINUM_5G   -> Color(0xFFFFD700)
            SignalLevel.FIVE_G        -> Color(0xFF42A5F5)
            SignalLevel.PLATINUM      -> Color(0xFFBA68C8)
            SignalLevel.LTE           -> Color(0xFF66BB6A)
            SignalLevel.WEAK          -> Color(0xFF9E9E9E)
            SignalLevel.NO_SIGNAL     -> Color(0xFF424242)
            SignalLevel.AIRPLANE_MODE -> Color(0xFF1565C0)
            SignalLevel.NO_SIM        -> Color(0xFFB71C1C)
            null                      -> Color(0xFFE0E0E0)
        },
        label = "bg"
    )

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 将来3Dアバターに差し替えるコンテナ
        Box(
            modifier = Modifier
                .size(72.dp)
                .scale(scale),
            contentAlignment = Alignment.Center
        ) {
            Text(text = state.emoji, fontSize = 56.sp)
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = state.message,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1A1A1A)
            )
            state.level?.let {
                Text(
                    text = it.label(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF424242)
                )
            }
        }
    }
}

private fun SignalLevel.label() = when (this) {
    SignalLevel.PLATINUM_5G   -> "🏆 プラチナ5G"
    SignalLevel.FIVE_G        -> "⚡ 5G"
    SignalLevel.PLATINUM      -> "💎 プラチナバンド(Band 28)"
    SignalLevel.LTE           -> "📶 LTE(楽天回線)"
    SignalLevel.WEAK          -> "📉 弱電界/パートナー回線"
    SignalLevel.NO_SIGNAL     -> "🚫 圏外"
    SignalLevel.AIRPLANE_MODE -> "✈️ 機内モード（スタンプ無効）"
    SignalLevel.NO_SIM        -> "📵 SIMなし（スタンプ無効）"
}
