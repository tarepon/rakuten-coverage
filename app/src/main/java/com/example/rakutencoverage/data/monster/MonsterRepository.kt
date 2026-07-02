package com.example.rakutencoverage.data.monster

import com.example.rakutencoverage.data.MeasurementDao
import com.example.rakutencoverage.data.resolveSignalLevel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MonsterRepository(private val dao: MeasurementDao) {

    fun observeMonsters(): Flow<List<Monster>> =
        dao.observeMonsterEncounters().map { encounters ->
            encounters.map { encounter ->
                val signalLevel = resolveSignalLevel(encounter.networkType, encounter.band)
                MonsterGenerator.generate(encounter.cellId, encounter.encounterCount, signalLevel)
            }
        }
}
