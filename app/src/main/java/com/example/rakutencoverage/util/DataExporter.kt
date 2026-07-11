package com.example.rakutencoverage.util

import android.content.Context
import com.example.rakutencoverage.data.Measurement
import java.io.File

object DataExporter {

    fun toCsv(measurements: List<Measurement>, context: Context): File {
        val header = "id,latitude,longitude,timestamp,network_type,band,rssi,rtt_ms,carrier\n"
        val rows = measurements.joinToString("\n") { m ->
            "${m.id},${m.latitude},${m.longitude},${m.timestamp}," +
                "${m.networkType},${m.band ?: ""},${m.rssi ?: ""},${m.rttMs},${m.carrier ?: ""}"
        }
        return writeTemp(context, "coverage_export.csv", header + rows)
    }

    /**
     * GeoJSON文字列を生成する純粋関数。cacheDirへの書き込みを伴わない。
     * 囲って保存(ラッソエクスポート)からSAF経由で直接書き出す際にも使う。
     */
    fun toGeoJsonString(measurements: List<Measurement>): String {
        val features = measurements.joinToString(",\n") { m ->
            """
            {
              "type": "Feature",
              "properties": {
                "id": ${m.id},
                "timestamp": "${m.timestamp}",
                "network_type": "${m.networkType}",
                "band": ${m.band?.let { "\"$it\"" } ?: "null"},
                "rssi": ${m.rssi ?: "null"},
                "rtt_ms": ${m.rttMs},
                "carrier": ${m.carrier?.let { "\"$it\"" } ?: "null"},
                "signal_level": "${m.signalLevel}"
              },
              "geometry": {
                "type": "Point",
                "coordinates": [${m.longitude}, ${m.latitude}]
              }
            }
            """.trimIndent()
        }
        return """
        {
          "type": "FeatureCollection",
          "metadata": {},
          "features": [$features]
        }
        """.trimIndent()
    }

    fun toGeoJson(measurements: List<Measurement>, context: Context): File =
        writeTemp(context, "coverage_export.geojson", toGeoJsonString(measurements))

    private fun writeTemp(context: Context, name: String, content: String): File {
        val file = File(context.cacheDir, name)
        file.writeText(content, Charsets.UTF_8)
        return file
    }
}
