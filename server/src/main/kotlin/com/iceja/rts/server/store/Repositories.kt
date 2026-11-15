package com.iceja.rts.server.store

import com.iceja.rts.server.api.ArmyUnitInput
import com.iceja.rts.server.api.ArmyUnitView
import com.iceja.rts.server.api.ArmyView
import com.iceja.rts.server.api.CreatureView
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.DerivedStats
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.ValidationResult
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class CreatureRecord(
    val id: String,
    val ownerId: String,
    val genome: Genome,
    val stats: DerivedStats,
    val validation: ValidationResult,
    val version: Int,
    val createdAt: Long,
    val experience: Int = 0,
    val isTemplate: Boolean = false
) {
    fun toView(): CreatureView = CreatureView(
        id = id,
        genome = genome,
        stats = stats,
        validation = validation,
        version = version,
        createdAt = createdAt,
        experience = experience,
        isTemplate = isTemplate
    )
}

data class ArmyUnitRecord(
    val creatureId: String,
    val x: Int,
    val y: Int
)

data class ArmyRecord(
    val id: String,
    val ownerId: String,
    val name: String,
    val units: List<ArmyUnitRecord>,
    val createdAt: Long
) {
    fun toView(creatureRepository: CreatureRepository): ArmyView {
        val views = units.mapNotNull { unit ->
            creatureRepository.find(unit.creatureId)?.let { creature ->
                ArmyUnitView(
                    creatureId = creature.id,
                    name = creature.genome.name,
                    costBm = creature.stats.costBm,
                    x = unit.x,
                    y = unit.y
                )
            }
        }
        return ArmyView(
            id = id,
            name = name,
            units = views,
            totalCostBm = views.sumOf { it.costBm },
            creatureCount = views.size
        )
    }
}

class CreatureRepository {
    private val creatures = ConcurrentHashMap<String, CreatureRecord>()

    fun create(
        ownerId: String,
        genome: Genome,
        validation: ValidationResult,
        isTemplate: Boolean = false
    ): CreatureRecord {
        val record = CreatureRecord(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            genome = genome,
            stats = validation.stats,
            validation = validation,
            version = 1,
            createdAt = Instant.now().toEpochMilli(),
            experience = 0,
            isTemplate = isTemplate
        )
        creatures[record.id] = record
        return record
    }

    fun addExperience(creatureId: String, amount: Int): CreatureRecord? {
        if (amount <= 0) {
            return creatures[creatureId]
        }
        val current = creatures[creatureId] ?: return null
        val nextExperience = (current.experience + amount)
            .coerceAtMost(GameCatalog.ruleset.maxExperience)
        if (nextExperience == current.experience) {
            return current
        }
        val updated = current.copy(
            experience = nextExperience,
            version = current.version + 1
        )
        creatures[creatureId] = updated
        return updated
    }

    fun find(creatureId: String): CreatureRecord? = creatures[creatureId]

    fun list(ownerId: String): List<CreatureRecord> =
        creatures.values
            .filter { it.ownerId == ownerId }
            .sortedWith(compareByDescending<CreatureRecord> { it.createdAt }.thenBy { it.id })
}

class ArmyRepository(
    private val creatureRepository: CreatureRepository
) {
    private val armies = ConcurrentHashMap<String, ArmyRecord>()

    fun create(ownerId: String, name: String, units: List<ArmyUnitInput>): ArmyRecord {
        val record = ArmyRecord(
            id = UUID.randomUUID().toString(),
            ownerId = ownerId,
            name = name,
            units = units.map { ArmyUnitRecord(it.creatureId, it.x, it.y) },
            createdAt = Instant.now().toEpochMilli()
        )
        armies[record.id] = record
        return record
    }

    fun find(armyId: String): ArmyRecord? = armies[armyId]

    fun list(ownerId: String): List<ArmyRecord> =
        armies.values
            .filter { it.ownerId == ownerId }
            .sortedWith(compareByDescending<ArmyRecord> { it.createdAt }.thenBy { it.id })

    fun toView(army: ArmyRecord): ArmyView = army.toView(creatureRepository)

    fun totalCost(army: ArmyRecord): Int =
        army.units.sumOf { unit -> creatureRepository.find(unit.creatureId)?.stats?.costBm ?: 0 }
}
