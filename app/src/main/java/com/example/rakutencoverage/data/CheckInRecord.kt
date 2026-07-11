package com.example.rakutencoverage.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * チェックイン刷新機能の1回分の記録。
 * 従来の「計測用タグ付け(MapViewModel._checkIn / Measurement.arenaId等)」とは別に、
 * チェックインという行為そのもの(写真・スピードテスト結果含む)を記録するためのエンティティ。
 *
 * @property spotId        スポットマスタの GeoJSON feature id
 * @property spotType      SpotType.name
 * @property spotName      表示用スポット名
 * @property latitude      チェックイン時のGPS緯度。取得不可なら null
 * @property longitude     チェックイン時のGPS経度。取得不可なら null
 * @property timestamp     ISO8601形式
 * @property seatLabel     自席 (ARENA以外・未入力は null)
 * @property gamePhase     GamePhase.name (ARENA以外は null)
 * @property photoPath     filesDir相対パス "checkin_photos/xxx.jpg"。未撮影は null
 * @property downloadMbps  スピードテストのダウンロード結果。未実施は null
 * @property uploadMbps    スピードテストのアップロード結果。未実施は null
 * @property latencyMs     スピードテストのレイテンシ結果。未実施は null
 */
@Entity(tableName = "checkin_records")
data class CheckInRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spotId: String,
    val spotType: String,
    val spotName: String,
    val latitude: Double?,
    val longitude: Double?,
    val timestamp: String,
    val seatLabel: String?,
    val gamePhase: String?,
    val photoPath: String?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val latencyMs: Int?
)
