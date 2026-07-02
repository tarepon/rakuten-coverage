package com.example.rakutencoverage.measurement

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * HTTP HEAD リクエストでラウンドトリップタイム (RTT) を計測する。
 * エンドポイントは Google の接続確認用 URL (204 No Content を返す軽量サーバー)。
 * アプリ側に余計なデータを送らず、サーバー負荷も最小。
 */
object PingMeasurer {

    private const val PING_URL   = "https://www.gstatic.com/generate_204"
    private const val TIMEOUT_MS = 5000

    /**
     * インターネット到達性を確認しつつ RTT を計測する。
     * Dispatchers.IO 上で実行するため suspend 関数。
     *
     * @return RTT (ms)。圏外・タイムアウト・2xx 以外のレスポンスは -1
     */
    suspend fun measureRttMs(): Int = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        try {
            val conn = (URL(PING_URL).openConnection() as HttpURLConnection).apply {
                requestMethod        = "HEAD"
                connectTimeout       = TIMEOUT_MS
                readTimeout          = TIMEOUT_MS
                instanceFollowRedirects = false
                connect()
            }
            val code = conn.responseCode
            conn.disconnect()
            // generate_204 は正常時 204 を返す。2xx 以外はプロキシ等の割り込みを疑い失敗扱い
            if (code in 200..299) (System.currentTimeMillis() - start).toInt() else -1
        } catch (_: Exception) {
            -1
        }
    }
}
