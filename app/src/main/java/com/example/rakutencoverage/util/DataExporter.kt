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

    fun toGeoJson(measurements: List<Measurement>, context: Context): File {
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
        val geojson = """
        {
          "type": "FeatureCollection",
          "metadata": {},
          "features": [$features]
        }
        """.trimIndent()
        return writeTemp(context, "coverage_export.geojson", geojson)
    }

    private fun writeTemp(context: Context, name: String, content: String): File {
        val file = File(context.cacheDir, name)
        file.writeText(content, Charsets.UTF_8)
        return file
    }
}
