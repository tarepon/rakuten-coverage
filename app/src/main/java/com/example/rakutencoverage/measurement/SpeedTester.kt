package com.example.rakutencoverage.measurement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloudflare の公開スピードテストエンドポイント (speed.cloudflare.com) を使った自前計測。
 * APIキー不要・追加ライブラリ不要 (HttpURLConnection のみ)。
 * チェックイン画面から「⚡ スピードテスト実行」で呼び出される。
 */
object SpeedTester {

    data class Result(
        val downloadMbps: Double?,
        val uploadMbps: Double?,
        val latencyMs: Int?,
        val error: String? = null
    )

    private const val TIMEOUT_MS   = 15_000
    private const val LATENCY_URL  = "https://speed.cloudflare.com/__down?bytes=0"
    private const val DOWNLOAD_URL = "https://speed.cloudflare.com/__down?bytes=10000000"
    private const val UPLOAD_URL   = "https://speed.cloudflare.com/__up"
    private const val UPLOAD_BYTES = 2_000_000
    private const val LATENCY_SAMPLES = 5

    /**
     * レイテンシ → ダウンロード → アップロードの順に計測する。
     * 各段階の開始時に onProgress を呼ぶ。失敗した段階は該当フィールドが null になり、
     * error に最後に発生した例外のメッセージを入れる（取れた値はそのまま返す）。
     */
    suspend fun run(onProgress: (String) -> Unit = {}): Result = withContext(Dispatchers.IO) {
        var latencyMs: Int? = null
        var downloadMbps: Double? = null
        var uploadMbps: Double? = null
        var error: String? = null

        try {
            onProgress("レイテンシ計測中…")
            latencyMs = measureLatency()
        } catch (e: Exception) {
            error = "レイテンシ計測に失敗しました: ${e.message}"
        }

        try {
            onProgress("ダウンロード速度を計測中…")
            downloadMbps = measureDownload()
        } catch (e: Exception) {
            error = "ダウンロード計測に失敗しました: ${e.message}"
        }

        try {
            onProgress("アップロード速度を計測中…")
            uploadMbps = measureUpload()
        } catch (e: Exception) {
            error = "アップロード計測に失敗しました: ${e.message}"
        }

        Result(downloadMbps, uploadMbps, latencyMs, error)
    }

    /** __down?bytes=0 への GET を5回行い、所要時間(ms)の中央値を返す */
    private fun measureLatency(): Int {
        val samples = mutableListOf<Long>()
        repeat(LATENCY_SAMPLES) {
            val conn = (URL(LATENCY_URL).openConnection() as HttpURLConnection).apply {
                requestMethod  = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout    = TIMEOUT_MS
            }
            try {
                val start = System.currentTimeMillis()
                conn.connect()
                conn.inputStream.use { it.readBytes() }
                samples.add(System.currentTimeMillis() - start)
            } finally {
                conn.disconnect()
            }
        }
        samples.sort()
        return samples[samples.size / 2].toInt()
    }

    /** __down?bytes=10000000 (10MB) を全読みし、Mbps を算出する */
    private fun measureDownload(): Double {
        val conn = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod  = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
        }
        try {
            conn.connect()
            val start = System.currentTimeMillis()
            var total = 0L
            val buffer = ByteArray(64 * 1024)
            conn.inputStream.use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                }
            }
            val elapsed = System.currentTimeMillis() - start
            return calcMbps(total, elapsed)
        } finally {
            conn.disconnect()
        }
    }

    /** __up へ 2MB のゼロ埋めデータを POST し、Mbps を算出する */
    private fun measureUpload(): Double {
        val payload = ByteArray(UPLOAD_BYTES) // ゼロ埋め
        val conn = (URL(UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod  = "POST"
            doOutput       = true
            connectTimeout = TIMEOUT_MS
            readTimeout    = TIMEOUT_MS
            setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(payload.size)
        }
        try {
            conn.connect()
            val start = System.currentTimeMillis()
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode // レスポンスを読み切ってから経過時間を確定させる
            val elapsed = System.currentTimeMillis() - start
            if (code !in 200..299) error("HTTP $code")
            return calcMbps(payload.size.toLong(), elapsed)
        } finally {
            conn.disconnect()
        }
    }

    /** バイト数と経過時間(ms)から Mbps を算出する純粋関数。単体テスト用に切り出し */
    fun calcMbps(bytes: Long, elapsedMs: Long): Double {
        if (elapsedMs <= 0) return 0.0
        return bytes * 8.0 / (elapsedMs / 1000.0) / 1_000_000.0
    }
}
