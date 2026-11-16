package com.iceja.rts.server.genome

import com.iceja.rts.server.catalog.ArmorProfile
import com.iceja.rts.server.catalog.DamageType
import com.iceja.rts.server.catalog.PartFlag
import com.iceja.rts.server.catalog.SocketType
import kotlinx.serialization.Serializable

@Serializable
data class Offset(
    val x: Int = 0,
    val y: Int = 0
)

@Serializable
data class PartPlacement(
    val partId: String,
    val socketId: SocketType,
    val offset: Offset = Offset(),
    val rotation: Int = 0,
    val mirrored: Boolean = false,
    val slotIndex: Int? = null
)

@Serializable
enum class UpgradeStat {
    HP,
    DAMAGE,
    ARMOR,
    SPEED,
    ENERGY,
    ACCURACY,
    CONTROL
}

@Serializable
data class UpgradePlacement(
    val stat: UpgradeStat,
    val level: Int
)

@Serializable
data class BehaviorNode(
    val id: String,
    val type: String,
    val children: List<String> = emptyList(),
    val params: Map<String, Int> = emptyMap(),
    val text: String = ""
)

@Serializable
data class BehaviorGraph(
    val schemaVersion: Int = 1,
    val rootId: String,
    val parameters: Map<String, Int> = emptyMap(),
    val nodes: List<BehaviorNode>
)

@Serializable
data class BehaviorSpec(
    val presetId: String = "BERSERK",
    val graph: BehaviorGraph? = null
)

@Serializable
data class Genome(
    val schemaVersion: Int = 1,
    val chassisId: String,
    val name: String,
    val parts: List<PartPlacement>,
    val upgrades: List<UpgradePlacement> = emptyList(),
    val behavior: BehaviorSpec = BehaviorSpec(),
    val paletteId: String = "default",
    val catalogVersion: String = "2026.11.1",
    val rulesetVersion: String = "1.0.0"
)

@Serializable
enum class ValidationSeverity {
    ERROR,
    WARNING,
    INFO
}

@Serializable
data class ValidationIssue(
    val code: String,
    val message: String,
    val severity: ValidationSeverity,
    val nodeId: String = ""
)

@Serializable
data class WeaponStats(
    val partId: String,
    val damage: Int,
    val cooldownTicks: Int,
    val rangeMm: Int,
    val damageType: DamageType,
    val accuracy: Int,
    val projectileSpeedMmPerTick: Int,
    val splashRadiusMm: Int,
    val dotDamagePerSecond: Int,
    val dotDurationTicks: Int,
    val penetration: Int,
    val ranged: Boolean
)

@Serializable
data class SocketUsage(
    val type: SocketType,
    val used: Int,
    val capacity: Int
)

@Serializable
data class DerivedStats(
    val maxHp: Int,
    val massKg: Int,
    val costBm: Int,
    val maxEnergy: Int,
    val regenMilliPerSecond: Int,
    val hpRegenPercentPerSecond: Int,
    val upkeepMilliPerSecond: Int,
    val speedMmPerTick: Int,
    val accelMmPerSecond2: Int,
    val turnRateDegPerSecond: Int,
    val controlCapacity: Int,
    val controlLoad: Int,
    val decisionPenaltyTicks: Int,
    val armor: ArmorProfile,
    val visionRangeMm: Int,
    val visionAngleDeg: Int,
    val accuracyBonus: Int,
    val evasion: Int,
    val radiusMm: Int,
    val behaviorCp: Int,
    val weapons: List<WeaponStats>,
    val flags: List<PartFlag>,
    val sockets: List<SocketUsage>
)

@Serializable
data class ValidationResult(
    val valid: Boolean,
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
    val info: List<ValidationIssue>,
    val stats: DerivedStats
)

data class GenomeAnalysis(
    val genome: Genome,
    val stats: DerivedStats,
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
    val info: List<ValidationIssue>
) {
    val valid: Boolean
        get() = errors.isEmpty()

    fun toValidationResult(): ValidationResult = ValidationResult(
        valid = valid,
        errors = errors,
        warnings = warnings,
        info = info,
        stats = stats
    )
}
