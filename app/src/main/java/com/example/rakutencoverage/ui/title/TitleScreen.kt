package com.example.rakutencoverage.ui.title

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rakutencoverage.audio.BgmPlayer
import com.example.rakutencoverage.ui.settings.PRIVACY_POLICY_URL
import com.example.rakutencoverage.ui.theme.GoAccent
import com.example.rakutencoverage.ui.theme.GoDarkNavy
import com.example.rakutencoverage.ui.theme.GoDarkNavy2
import com.example.rakutencoverage.ui.theme.GoWhite
import com.example.rakutencoverage.ui.theme.PanelBackgroundAlpha
import com.example.rakutencoverage.util.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

/**
 * 起動演出フロー: オープニングストーリー → タイトル画面。
 * オープニングはタップ or 右下スキップでいつでも飛ばせる。タイトルでタップするとアプリ本体へ。
 * 回転(構成変更)ではフェーズを保持し、ストーリーを最初からやり直さない。
 */
@Composable
fun TitleFlow(onEnter: () -> Unit) {
    var showTitle by rememberSaveable { mutableStateOf(false) }
    if (!showTitle) {
        OpeningStoryScreen(onFinished = { showTitle = true })
    } else {
        TitleScreen(onStart = onEnter)
    }
}

/** オープニングの物語。下から上へスクロールし、流れきるとタイトルへ */
private val STORY_PARAGRAPHS = listOf(
    "むかしむかし、あるところに\n業界に旋風を巻き起こす\n最強軍団が住む世界があった。",
    "軍団は長い戦いの末、\n伝説の電波「プラチナバンド」を\nついに手に入れた。",
    "壁を越え、窓をすり抜け、\nビルの奥までまっすぐ届く\n白金に輝く波──。",
    "だが、その電波が今日\nどこを飛んでいるのかは\n歩いてみないと分からない。",
    "ならば、確かめるしかない。\nこの足で。この端末で。\n（通信料は勇者の自己負担で。）",
    "勇者たちは今日も\n幻のプラチナモンスターを探す\n旅に出るのだった・・・",
    "※つながるかどうかは\n個人の実測です",
)

/** スクロール速度(dp/秒)。全文が流れきるまでの時間はこの速度と画面高で決まる */
private const val SCROLL_SPEED_DP_PER_SEC = 80f

@Composable
private fun OpeningStoryScreen(onFinished: () -> Unit) {
    val scrollY = remember { Animatable(0f) }
    var contentHeightPx by remember { mutableIntStateOf(0) }

    val bgmContext = LocalContext.current
    LaunchedEffect(Unit) { BgmPlayer.request(bgmContext, BgmPlayer.Track.OPENING) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nightSkyBackground()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onFinished() }
    ) {
        StarField()

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val screenHeightPx = constraints.maxHeight
            val density = LocalDensity.current

            // 画面下端の外から本文上端が入り、本文下端が画面上端を抜けるまで等速スクロール
            LaunchedEffect(contentHeightPx) {
                if (contentHeightPx == 0) return@LaunchedEffect
                val travelPx = (screenHeightPx + contentHeightPx).toFloat()
                val speedPxPerSec = with(density) { SCROLL_SPEED_DP_PER_SEC.dp.toPx() }
                val durationMs = (travelPx / speedPxPerSec * 1000).toInt()
                scrollY.snapTo(screenHeightPx.toFloat())
                scrollY.animateTo(
                    targetValue = -contentHeightPx.toFloat(),
                    animationSpec = tween(durationMs, easing = LinearEasing)
                )
                onFinished()
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { contentHeightPx = it.height }
                    .graphicsLayer { translationY = scrollY.value },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(56.dp)
            ) {
                STORY_PARAGRAPHS.forEach { paragraph ->
                    val isNote = paragraph.startsWith("※")
                    Text(
                        paragraph,
                        color = if (isNote) GoWhite.copy(alpha = 0.7f) else GoWhite,
                        fontSize = if (isNote) 13.sp else 18.sp,
                        lineHeight = if (isNote) 22.sp else 32.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp)
                    )
                }
            }
        }

        // 右下に常時表示のスキップ。タップは画面全体でも受け付ける
        Surface(
            color = GoDarkNavy.copy(alpha = PanelBackgroundAlpha),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                "スキップ ▶▶",
                color = GoWhite,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * タイトル画面。ロゴ+点滅TAP TO START。画面タップでアプリ本体へ。
 * 下部にプライバシーポリシー・バックアップ復元・バージョンを配置する
 * (初回起動でデータ復元してから使い始める動線のため、本体に入らず復元できるようにする)。
 */
@Composable
private fun TitleScreen(onStart: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { BgmPlayer.request(context, BgmPlayer.Track.TITLE) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            resultMessage = runCatching {
                withContext(Dispatchers.IO) { BackupManager.restoreFromUri(context, uri) }
            }.getOrElse { "❌ インポートに失敗: ${it.message}" }
            busy = false
        }
    }

    val blink by rememberInfiniteTransition(label = "tapBlink").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "tapBlinkAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nightSkyBackground()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !busy
            ) { onStart() }
    ) {
        StarField()

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("📡", fontSize = 56.sp)
            Text(
                "業界に旋風を巻き起こす",
                color = GoWhite.copy(alpha = 0.8f),
                fontSize = 13.sp,
                letterSpacing = 2.sp
            )
            Text(
                "最強",
                color = GoAccent,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 8.sp
            )
            Text(
                "プラチナハンター",
                color = PlatinumSilver,
                fontSize = 36.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Text(
                "─ PLATINUM HUNTER ─",
                color = GoWhite.copy(alpha = 0.6f),
                fontSize = 11.sp,
                letterSpacing = 3.sp
            )
        }

        Text(
            "▼ TAP TO START ▼",
            color = GoWhite,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
                .alpha(blink)
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)))
                    }
                }) {
                    Text("プライバシーポリシー", color = GoWhite.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                TextButton(
                    enabled = !busy,
                    onClick = { importLauncher.launch(arrayOf("application/json")) }
                ) {
                    Text("バックアップから復元", color = GoWhite.copy(alpha = 0.7f), fontSize = 12.sp)
                }
            }
            val versionName = remember {
                runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "?"
            }
            Text("v$versionName", color = GoWhite.copy(alpha = 0.4f), fontSize = 10.sp)
        }

        if (busy) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }
    }

    resultMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { resultMessage = null },
            title = { Text("バックアップから復元") },
            text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                TextButton(onClick = { resultMessage = null }) { Text("OK") }
            }
        )
    }
}

/** プラチナ(白金)っぽいロゴ色 */
private val PlatinumSilver = Color(0xFFE5E4E2)

/** 夜空グラデーション背景 */
private fun Modifier.nightSkyBackground(): Modifier = background(
    Brush.verticalGradient(listOf(GoDarkNavy2, GoDarkNavy, GoDarkNavy2))
)

/** 固定シードで散らした星屑。ゆっくり明滅する */
@Composable
private fun StarField() {
    val stars = remember {
        val rand = Random(42)
        List(48) {
            Triple(
                Offset(rand.nextFloat(), rand.nextFloat()),
                rand.nextFloat() * 1.6f + 0.6f,   // 半径dp係数
                rand.nextFloat() * 0.5f + 0.25f   // 基本アルファ
            )
        }
    }
    val twinkle by rememberInfiniteTransition(label = "twinkle").animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkleAlpha"
    )
    Canvas(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        stars.forEach { (frac, radius, alpha) ->
            drawCircle(
                color = GoWhite,
                radius = radius.dp.toPx(),
                center = Offset(frac.x * size.width, frac.y * size.height),
                alpha = alpha * twinkle
            )
        }
    }
}
