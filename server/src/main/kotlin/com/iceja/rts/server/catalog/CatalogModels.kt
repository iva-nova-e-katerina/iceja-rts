package com.iceja.rts.server.catalog

import kotlinx.serialization.Serializable

@Serializable
enum class SocketType {
    CORE,
    FRONT,
    BACK,
    LEFT,
    RIGHT,
    TOP,
    UNDER
}

@Serializable
enum class PartCategory {
    CORE,
    LOCOMOTION,
    SENSOR,
    MELEE,
    RANGED,
    DEFENSE,
    UTILITY
}

@Serializable
enum class DamageType {
    KINETIC,
    PIERCING,
    THERMAL,
    ACID,
    ELECTRIC,
    TOXIC
}

@Serializable
enum class PartVisibility {
    INTERNAL,
    EXTERNAL
}

@Serializable
enum class PartShape {
    LEG,
    WING,
    CLAW,
    FANG,
    SPIKE,
    TAIL,
    HAMMER,
    EYE,
    CHEMO,
    WHISKER,
    ORB,
    PLATE,
    NOZZLE,
    MOUTH,
    GLAND,
    LURE,
    VENT,
    FIN,
    SLIME,
    SKIN,
    HARPOON,
    MORTAR,
    BURROW_CLAW
}

@Serializable
data class PartVisual(
    val visibility: PartVisibility,
    val shape: PartShape,
    val scale: Int = 100
)

@Serializable
enum class PartFlag {
    BRAIN,
    HEART,
    METABOLIC,
    NECTAR_CROP,
    SYMBIOTE,
    SCAVENGER,
    BURROW,
    FLIGHT,
    CAMOUFLAGE,
    STICKY,
    PHEROMONE,
    ADRENAL,
    NEUROLINK,
    LURE,
    OVIPOSITOR
}

@Serializable
data class ArmorProfile(
    val kinetic: Int = 0,
    val piercing: Int = 0,
    val thermal: Int = 0,
    val acid: Int = 0,
    val electric: Int = 0,
    val toxic: Int = 0
) {
    fun forType(damageType: DamageType): Int = when (damageType) {
        DamageType.KINETIC -> kinetic
        DamageType.PIERCING -> piercing
        DamageType.THERMAL -> thermal
        DamageType.ACID -> acid
        DamageType.ELECTRIC -> electric
        DamageType.TOXIC -> toxic
    }

    fun plus(other: ArmorProfile): ArmorProfile = ArmorProfile(
        kinetic = kinetic + other.kinetic,
        piercing = piercing + other.piercing,
        thermal = thermal + other.thermal,
        acid = acid + other.acid,
        electric = electric + other.electric,
        toxic = toxic + other.toxic
    )

    fun times(factor: Int): ArmorProfile = ArmorProfile(
        kinetic = kinetic * factor,
        piercing = piercing * factor,
        thermal = thermal * factor,
        acid = acid * factor,
        electric = electric * factor,
        toxic = toxic * factor
    )

    fun capped(maxValue: Int): ArmorProfile = ArmorProfile(
        kinetic = kinetic.coerceAtMost(maxValue),
        piercing = piercing.coerceAtMost(maxValue),
        thermal = thermal.coerceAtMost(maxValue),
        acid = acid.coerceAtMost(maxValue),
        electric = electric.coerceAtMost(maxValue),
        toxic = toxic.coerceAtMost(maxValue)
    )
}

@Serializable
data class WeaponProfile(
    val damage: Int,
    val cooldownTicks: Int,
    val rangeMm: Int,
    val damageType: DamageType,
    val accuracy: Int,
    val projectileSpeedMmPerTick: Int = 0,
    val splashRadiusMm: Int = 0,
    val dotDamagePerSecond: Int = 0,
    val dotDurationTicks: Int = 0,
    val penetration: Int = 0,
    val complexity: Int = 1,
    val ranged: Boolean = false
)

@Serializable
data class PartDefinition(
    val id: String,
    val name: String,
    val category: PartCategory,
    val socket: SocketType,
    val cost: Int,
    val massKg: Int,
    val hpBonus: Int = 0,
    val energyBonus: Int = 0,
    val regenPerSecond: Int = 0,
    val regenPercentPerSecond: Int = 0,
    val controlBonus: Int = 0,
    val controlCost: Int = 0,
    val traction: Int = 0,
    val visionRangeMm: Int = 0,
    val visionAngleDeg: Int = 0,
    val weapon: WeaponProfile? = null,
    val armor: ArmorProfile = ArmorProfile(),
    val evasionBonus: Int = 0,
    val accuracyBonus: Int = 0,
    val speedMultiplierPct: Int = 100,
    val turnMultiplierPct: Int = 100,
    val energyDrainMilliPerSecond: Int = 0,
    val flags: Set<PartFlag> = emptySet(),
    val visual: PartVisual = PartVisual(PartVisibility.INTERNAL, PartShape.ORB),
    val description: String = ""
)

@Serializable
data class SocketCapacity(
    val type: SocketType,
    val count: Int
)

@Serializable
data class ChassisDefinition(
    val id: String,
    val name: String,
    val sizeClass: String,
    val massKg: Int,
    val hp: Int,
    val cost: Int,
    val sockets: List<SocketCapacity>,
    val comfortableMassKg: Int,
    val hardMassKg: Int,
    val baseRadiusMm: Int,
    val canFly: Boolean = false,
    val canBurrow: Boolean = false,
    val isStatic: Boolean = false
) {
    val totalSockets: Int
        get() = sockets.sumOf { it.count }

    fun capacity(socketType: SocketType): Int =
        sockets.firstOrNull { it.type == socketType }?.count ?: 0
}

@Serializable
data class Obstacle(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

@Serializable
data class SpawnPoint(
    val x: Int,
    val y: Int
)

@Serializable
data class MapDefinition(
    val id: String,
    val name: String,
    val widthMm: Int = 128_000,
    val heightMm: Int = 128_000,
    val spawnA: SpawnPoint = SpawnPoint(20_000, 64_000),
    val spawnB: SpawnPoint = SpawnPoint(108_000, 64_000),
    val deployRadiusMm: Int = 20_000,
    val obstacles: List<Obstacle> = emptyList()
)

@Serializable
data class Ruleset(
    val version: String = "1.0.0",
    val tickRate: Int = 30,
    val maxCreaturesPerArmy: Int = 40,
    val armyBudgetBm: Int = 6_000,
    val creatureMinCostBm: Int = 300,
    val creatureMaxCostBm: Int = 3_000,
    val matchDurationTicks: Int = 14_400,
    val overtimeTicks: Int = 3_600,
    val hiveHp: Int = 3_000,
    val hiveRadiusMm: Int = 4_000,
    val hiveArmor: Int = 50
)

@Serializable
data class BehaviorPresetDefinition(
    val id: String,
    val name: String,
    val cp: Int,
    val description: String,
    val aggression: Int,
    val caution: Int,
    val cohesion: Int,
    val greed: Int,
    val loyalty: Int,
    val focus: Int,
    val adaptability: Int
)
