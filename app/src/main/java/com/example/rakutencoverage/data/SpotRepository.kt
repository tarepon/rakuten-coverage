package com.example.rakutencoverage.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class SpotRepository(private val context: Context) {

    private val fileMap = mapOf(
        SpotType.ARENA     to "bleague_arena.geojson",
        SpotType.MICHINOEKI to "michinoeki.geojson"
    )

    suspend fun loadSpots(type: SpotType): List<Spot> = withContext(Dispatchers.IO) {
        val fileName = fileMap[type] ?: return@withContext emptyList()
        try {
            val json = context.assets.open(fileName).bufferedReader().readText()
            val fc = JSONObject(json)
            val features = fc.getJSONArray("features")
            (0 until features.length()).map { i ->
                val feature = features.getJSONObject(i)
                val props = feature.getJSONObject("properties")
                val coords = feature.getJSONObject("geometry").getJSONArray("coordinates")
                Spot(
                    id = props.getString("id"),
                    name = props.getString("name"),
                    latitude = coords.getDouble(1),
                    longitude = coords.getDouble(0),
                    type = type,
                    pref = props.optString("pref"),
                    city = props.optString("city"),
                    club = props.optString("club"),
                    division = props.optString("division")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun loadAll(): List<Spot> =
        SpotType.entries.flatMap { loadSpots(it) }
}
