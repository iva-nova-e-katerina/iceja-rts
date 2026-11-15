package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.DamageType
import com.iceja.rts.server.catalog.MapDefinition
import com.iceja.rts.server.genome.DerivedStats
import com.iceja.rts.server.genome.Genome
import kotlinx.serialization.Serializable

data class ArmyPlacement(
    val genome: Genome,
    val stats: DerivedStats,
    val x: Int,
    val y: Int
)

data class PlayerSetup(
    val playerId: Int,
    val nickname: String,
    val units: List<ArmyPlacement>
)

data class SimulationConfig(
    val map: MapDefinition,
    val maxTicks: Int = 14_400,
    val overtimeTicks: Int = 3_600,
    val snapshotEveryTicks: Int = 3
)

enum class EntityState(val code: Int) {
    IDLE(0),
    MOVING(1),
    ATTACKING(2),
    HARVESTING(3),
    FLEEING(4),
    STUNNED(5),
    BURROWED(6),
    DEAD(7)
}

enum class EffectType {
    ACID,
    TOXIC
}

data class StatusEffect(
    val type: EffectType,
    var remainingTicks: Int,
    val damagePerSecond: Int,
    val sourceId: Int
)

class Hive(
    val id: Int,
    val ownerId: Int,
    val x: Int,
    val y: Int,
    val maxHp: Int,
    val armor: Int,
    val radiusMm: Int
) {
    var hp: Int = maxHp
    var alive: Boolean = true
}

class Creature(
    val id: Int,
    val ownerId: Int,
    val genomeIndex: Int,
    val genome: Genome,
    val stats: DerivedStats,
    var x: Int,
    var y: Int,
    val canFly: Boolean = false
) {
    var hp: Int = stats.maxHp
    var energy: Int = stats.maxEnergy
    var targetId: Int = 0
    var attackTargetId: Int = 0
    var attackHive: Boolean = false
    var desiredX: Int = x
    var desiredY: Int = y
    var fleeFromX: Int = x
    var fleeFromY: Int = y
    var attackCooldownTicks: Int = 0
    var energyCarryMilli: Int = 0
    var starvationTicks: Int = 0
    var stunTicks: Int = 0
    var state: EntityState = EntityState.IDLE
    var alive: Boolean = true
    var path: List<Vec2> = emptyList()
    var pathIndex: Int = 0
    var pathGoalX: Int = 0
    var pathGoalY: Int = 0
    var nextRepathTick: Int = 0
    var lastDamageTick: Int = -10_000
    var lastDecisionTick: Int = 0
    var aiMode: String = "IDLE"
    var aiReason: String = "NO_TARGET"
    var damageDealt: Long = 0
    var kills: Int = 0
    val statuses: MutableList<StatusEffect> = mutableListOf()
    var angle: Int = 0
}

class Projectile(
    val id: Int,
    val ownerId: Int,
    val sourceId: Int,
    val targetId: Int,
    var x: Int,
    var y: Int,
    val speedMmPerTick: Int,
    var ttlTicks: Int,
    val damage: Int,
    val damageType: DamageType,
    val hit: Boolean,
    val splashRadiusMm: Int,
    val dotDamagePerSecond: Int,
    val dotDurationTicks: Int,
    val penetration: Int,
    val radiusMm: Int = 100
) {
    var alive: Boolean = true
}

@Serializable
data class SimEvent(
    val id: Long,
    val tick: Int,
    val type: String,
    val sourceId: Int = 0,
    val targetId: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val value: Int = 0,
    val text: String = ""
)

@Serializable
data class EntitySnapshot(
    val id: Int,
    val kind: String,
    val owner: Int,
    val x: Int,
    val y: Int,
    val angle: Int,
    val hp: Int,
    val maxHp: Int,
    val energy: Int,
    val flags: Int,
    val state: Int,
    val targetId: Int,
    val genomeId: Int,
    val mode: String,
    val reason: String
)

@Serializable
data class ProjectileSnapshot(
    val id: Int,
    val owner: Int,
    val x: Int,
    val y: Int,
    val angle: Int,
    val kind: Int,
    val ttl: Int
)

@Serializable
data class HiveSnapshot(
    val id: Int,
    val owner: Int,
    val x: Int,
    val y: Int,
    val hp: Int,
    val maxHp: Int,
    val flags: Int
)

@Serializable
data class Snapshot(
    val tick: Int,
    val serverTimeMs: Long,
    val matchId: String,
    val entities: List<EntitySnapshot>,
    val projectiles: List<ProjectileSnapshot>,
    val hives: List<HiveSnapshot>,
    val scores: List<Int>,
    val flags: Int,
    val checksum: Long
)

@Serializable
data class PlayerMatchStats(
    val playerId: Int,
    val nickname: String,
    val damageDealt: Long,
    val kills: Int,
    val losses: Int,
    val survivingCreatures: Int,
    val hiveHp: Int
)

@Serializable
data class MatchResult(
    val winner: Int,
    val reason: String,
    val durationTicks: Int,
    val stats: List<PlayerMatchStats>
)
