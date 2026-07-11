package com.example.rakutencoverage.util

import com.example.rakutencoverage.data.CheckInRecord
import com.example.rakutencoverage.data.CollectionRecord
import com.example.rakutencoverage.data.Measurement
import com.example.rakutencoverage.data.StampRecord
import org.json.JSONArray
import org.json.JSONObject

/**
 * 全データ（計測・図鑑・スタンプ・チェックイン記録）のJSONバックアップ生成とパース。
 * フォーマットはトップレベルに version を持ち、将来のスキーマ変更時に分岐できるようにする。
 */
object BackupManager {

    private const val FORMAT_VERSION = 1

    data class BackupData(
        val measurements: List<Measurement>,
        val collections: List<CollectionRecord>,
        val stamps: List<StampRecord>,
        // 旧フォーマットのバックアップJSONには checkins キーが存在しないため、
        // 復元時に fromJson が emptyList() を返せるようデフォルト値を持たせる (後方互換)
        val checkins: List<CheckInRecord> = emptyList()
    )

    fun toJson(data: BackupData): String {
        val root = JSONObject()
        root.put("version", FORMAT_VERSION)
        root.put("app", "rakuten-coverage")

        root.put("measurements", JSONArray().apply {
            data.measurements.forEach { m ->
                put(JSONObject().apply {
                    put("latitude", m.latitude)
                    put("longitude", m.longitude)
                    put("timestamp", m.timestamp)
                    put("networkType", m.networkType)
                    putOpt("band", m.band)
                    putOpt("rssi", m.rssi)
                    put("rttMs", m.rttMs)
                    putOpt("carrier", m.carrier)
                    putOpt("arenaId", m.arenaId)
                    putOpt("arenaName", m.arenaName)
                    putOpt("seatLabel", m.seatLabel)
                    putOpt("gamePhase", m.gamePhase)
                    putOpt("cellId", m.cellId)
                })
            }
        })

        root.put("collections", JSONArray().apply {
            data.collections.forEach { c ->
                put(JSONObject().apply {
                    put("h3Index", c.h3Index)
                    put("signalLevel", c.signalLevel)
                    put("latitude", c.latitude)
                    put("longitude", c.longitude)
                    put("capturedAt", c.capturedAt)
                    put("level", c.level)
                    put("xp", c.xp)
                })
            }
        })

        root.put("stamps", JSONArray().apply {
            data.stamps.forEach { s ->
                put(JSONObject().apply {
                    put("spotId", s.spotId)
                    put("spotType", s.spotType)
                    put("spotName", s.spotName)
                    put("achievedAt", s.achievedAt)
                })
            }
        })

        root.put("checkins", JSONArray().apply {
            data.checkins.forEach { c ->
                put(JSONObject().apply {
                    put("spotId", c.spotId)
                    put("spotType", c.spotType)
                    put("spotName", c.spotName)
                    putOpt("latitude", c.latitude)
                    putOpt("longitude", c.longitude)
                    put("timestamp", c.timestamp)
                    putOpt("seatLabel", c.seatLabel)
                    putOpt("gamePhase", c.gamePhase)
                    putOpt("photoPath", c.photoPath)
                    putOpt("downloadMbps", c.downloadMbps)
                    putOpt("uploadMbps", c.uploadMbps)
                    putOpt("latencyMs", c.latencyMs)
                    putOpt("memo", c.memo)
                })
            }
        })

        return root.toString(2)
    }

    /** @throws org.json.JSONException 形式不正時 */
    fun fromJson(json: String): BackupData {
        val root = JSONObject(json)

        val measurements = root.optJSONArray("measurements")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Measurement(
                    latitude    = o.getDouble("latitude"),
                    longitude   = o.getDouble("longitude"),
                    timestamp   = o.getString("timestamp"),
                    networkType = o.getString("networkType"),
                    band        = o.optStringOrNull("band"),
                    rssi        = if (o.has("rssi") && !o.isNull("rssi")) o.getInt("rssi") else null,
                    rttMs       = o.getInt("rttMs"),
                    carrier     = o.optStringOrNull("carrier"),
                    arenaId     = o.optStringOrNull("arenaId"),
                    arenaName   = o.optStringOrNull("arenaName"),
                    seatLabel   = o.optStringOrNull("seatLabel"),
                    gamePhase   = o.optStringOrNull("gamePhase"),
                    cellId      = o.optStringOrNull("cellId")
                )
            }
        } ?: emptyList()

        val collections = root.optJSONArray("collections")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CollectionRecord(
                    h3Index     = o.getString("h3Index"),
                    signalLevel = o.getString("signalLevel"),
                    latitude    = o.getDouble("latitude"),
                    longitude   = o.getDouble("longitude"),
                    capturedAt  = o.getString("capturedAt"),
                    level       = o.optInt("level", 5),  // 旧フォーマットはマイグレーション初期値と同じ5
                    xp          = o.optInt("xp", 0)
                )
            }
        } ?: emptyList()

        val stamps = root.optJSONArray("stamps")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                StampRecord(
                    spotId     = o.getString("spotId"),
                    spotType   = o.getString("spotType"),
                    spotName   = o.getString("spotName"),
                    achievedAt = o.getString("achievedAt")
                )
            }
        } ?: emptyList()

        // 旧フォーマットのJSONには "checkins" キーが無いため、無ければ空リスト(後方互換)
        val checkins = root.optJSONArray("checkins")?.let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CheckInRecord(
                    spotId       = o.getString("spotId"),
                    spotType     = o.getString("spotType"),
                    spotName     = o.getString("spotName"),
                    latitude     = o.optDoubleOrNull("latitude"),
                    longitude    = o.optDoubleOrNull("longitude"),
                    timestamp    = o.getString("timestamp"),
                    seatLabel    = o.optStringOrNull("seatLabel"),
                    gamePhase    = o.optStringOrNull("gamePhase"),
                    photoPath    = o.optStringOrNull("photoPath"),
                    downloadMbps = o.optDoubleOrNull("downloadMbps"),
                    uploadMbps   = o.optDoubleOrNull("uploadMbps"),
                    latencyMs    = if (o.has("latencyMs") && !o.isNull("latencyMs")) o.getInt("latencyMs") else null,
                    memo         = o.optStringOrNull("memo")
                )
            }
        } ?: emptyList()

        return BackupData(measurements, collections, stamps, checkins)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) getString(key) else null

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) getDouble(key) else null
}
