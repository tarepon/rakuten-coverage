package com.example.rakutencoverage.ui.checkin

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * チェックイン写真のサムネイル表示。Coil等の外部ライブラリを使わず、
 * BitmapFactory + inSampleSize による縮小読み込みで表示する。
 * デコードは Dispatchers.IO 上で行い、remember(key=path/uri) でキャッシュする。
 */

/** filesDir 相対パス(既存記録)からサムネイルを表示する */
@Composable
fun CheckInPhotoThumbnail(photoPath: String?, sizeDp: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember(photoPath) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoPath) {
        bitmap = if (photoPath == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val reqPx = with(density) { sizeDp.toPx() }.toInt().coerceAtLeast(1)
                runCatching { decodeSampledBitmap(File(context.filesDir, photoPath), reqPx) }.getOrNull()
            }
        }
    }
    ThumbnailBox(bitmap, sizeDp, modifier)
}

/** 選択直後(未保存)の Uri (カメラ撮影の一時ファイル or ギャラリー選択) からプレビューを表示する */
@Composable
fun CheckInPhotoPreview(uri: Uri?, sizeDp: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = if (uri == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                val reqPx = with(density) { sizeDp.toPx() }.toInt().coerceAtLeast(1)
                runCatching { decodeSampledBitmap(context, uri, reqPx) }.getOrNull()
            }
        }
    }
    ThumbnailBox(bitmap, sizeDp, modifier)
}

@Composable
private fun ThumbnailBox(bitmap: Bitmap?, sizeDp: Dp, modifier: Modifier) {
    Box(
        modifier
            .size(sizeDp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(sizeDp),
                contentScale = ContentScale.Crop
            )
        }
    }
}

private fun decodeSampledBitmap(file: File, reqSize: Int): Bitmap? {
    if (!file.exists()) return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, reqSize, reqSize)
    opts.inJustDecodeBounds = false
    return BitmapFactory.decodeFile(file.absolutePath, opts)
}

private fun decodeSampledBitmap(context: Context, uri: Uri, reqSize: Int): Bitmap? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    opts.inSampleSize = calculateInSampleSize(opts.outWidth, opts.outHeight, reqSize, reqSize)
    opts.inJustDecodeBounds = false
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

/** Android公式ドキュメントの標準アルゴリズム(2の累乗で縮小) */
private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
