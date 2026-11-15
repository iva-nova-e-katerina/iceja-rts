package com.iceja.rts.server.genome

import com.iceja.rts.server.catalog.ArmorProfile
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.PartCategory
import com.iceja.rts.server.catalog.PartFlag
import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.catalog.WeaponProfile
import kotlin.math.max
import kotlin.math.min

object GenomeAnalyzer {
    private val namePattern = Regex("^[\\p{L}\\p{N}][\\p{L}\\p{N} _-]{1,22}[\\p{L}\\p{N}]$")

    fun analyze(genome: Genome): GenomeAnalysis {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val info = mutableListOf<ValidationIssue>()
        val chassis = GameCatalog.chassisById[genome.chassisId]

        if (chassis == null) {
            errors += error("NO_CHASSIS", "Chassis is not selected or unknown")
        }
        val resolvedChassis = chassis ?: GameCatalog.chassis.first()
        val upgrades = UpgradeRules.validate(genome.upgrades, errors)
        val behaviorCp = analyzeBehavior(genome.behavior, errors, warnings)
        val stats = calculateStats(genome, resolvedChassis, behaviorCp, upgrades)

        if (genome.parts.size > 24) {
            errors += error("TOO_MANY_PARTS", "Creature has more than 24 parts")
        }
        if (!namePattern.matches(genome.name)) {
            errors += error("INVALID_NAME", "Name must contain 3-24 letters, digits, spaces, dashes or underscores")
        }

        var brainCount = 0
        var metabolismCount = 0
        var locomotionCount = 0
        var weaponCount = 0

        genome.parts.forEach { placement ->
            val part = GameCatalog.partsById[placement.partId]
            if (part == null) {
                errors += error("INVALID_PART_VERSION", "Part is missing from catalog: ${placement.partId}", placement.partId)
                return@forEach
            }

            if (part.flags.contains(PartFlag.BRAIN)) {
                brainCount += 1
            }
            if (part.flags.contains(PartFlag.HEART) || part.flags.contains(PartFlag.METABOLIC)) {
                metabolismCount += 1
            }
            if (part.traction > 0) {
                locomotionCount += 1
            }
            if (part.weapon != null) {
                weaponCount += 1
            }

            if (part.socket != placement.socketId) {
                errors += error(
                    "SOCKET_MISMATCH",
                    "Part ${part.name} requires ${part.socket.name}, not ${placement.socketId.name}",
                    placement.partId
                )
            }
            if (placement.mirrored && placement.socketId != SocketType.LEFT && placement.socketId != SocketType.RIGHT) {
                errors += error(
                    "SOCKET_MISMATCH",
                    "Mirrored parts are only supported on LEFT and RIGHT sockets",
                    placement.partId
                )
            }
            if (resolvedChassis.capacity(placement.socketId) == 0) {
                errors += error(
                    "SOCKET_MISMATCH",
                    "Chassis ${resolvedChassis.name} has no ${placement.socketId.name} socket",
                    placement.partId
                )
            }

            if (part.flags.contains(PartFlag.FLIGHT) && !resolvedChassis.canFly && resolvedChassis.id != "TITAN") {
                errors += error("CHASSIS_INCOMPATIBLE", "Flight requires FLYER or TITAN chassis", part.id)
            }
            if (part.flags.contains(PartFlag.BURROW) && !resolvedChassis.canBurrow && resolvedChassis.id != "TITAN") {
                errors += error("CHASSIS_INCOMPATIBLE", "Burrowing requires BURROWER or TITAN chassis", part.id)
            }
            if (part.category == PartCategory.LOCOMOTION && !placement.mirrored &&
                (placement.socketId == SocketType.LEFT || placement.socketId == SocketType.RIGHT)
            ) {
                warnings += warning(
                    "ASYM_LIMBS",
                    "Single limb without mirroring gives -10% turn rate",
                    part.id
                )
            }
        }

        if (brainCount == 0) {
            errors += error("NO_BRAIN", "Creature needs exactly one brain")
        }
        if (brainCount > 1) {
            errors += error("MULTIPLE_BRAINS", "Creature can have only one brain")
        }
        if (metabolismCount == 0) {
            errors += error("NO_METABOLISM", "Creature needs a heart or metabolic reactor")
        }
        if (resolvedChassis.isStatic && locomotionCount > 0) {
            errors += error("STATIC_LOCOMOTION", "Static chassis cannot have locomotion")
        }
        if (!resolvedChassis.isStatic && locomotionCount == 0) {
            errors += error("NO_LOCOMOTION", "Creature needs locomotion")
        }

        val socketUsage = resolvedChassis.sockets.map { capacity ->
            val used = usedSocketPoints(genome, capacity.type)
            if (used > capacity.count) {
                errors += error(
                    "SOCKET_OCCUPIED",
                    "Socket ${capacity.type.name} uses $used of ${capacity.count} points"
                )
            }
            SocketUsage(capacity.type, used, capacity.count)
        }

        if (stats.massKg > resolvedChassis.hardMassKg) {
            errors += error(
                "MASS_LIMIT",
                "Mass ${stats.massKg} kg exceeds hard limit ${resolvedChassis.hardMassKg} kg"
            )
        } else if (stats.massKg > resolvedChassis.comfortableMassKg) {
            warnings += warning(
                "OVERWEIGHT",
                "Mass ${stats.massKg} kg exceeds comfortable limit ${resolvedChassis.comfortableMassKg} kg"
            )
        }

        if (stats.costBm > GameCatalog.ruleset.creatureMaxCostBm) {
            errors += error(
                "BUDGET_EXCEEDED",
                "Creature cost ${stats.costBm} BM exceeds ${GameCatalog.ruleset.creatureMaxCostBm} BM"
            )
        }

        val energyBalance = stats.regenMilliPerSecond - stats.upkeepMilliPerSecond
        if (energyBalance < -5_000) {
            errors += error(
                "ENERGY_NEGATIVE",
                "Energy balance is below -5 per second"
            )
        } else if (energyBalance < 2_000) {
            warnings += warning(
                "ENERGY_TIGHT",
                "Energy balance is below 2 per second"
            )
        }

        val controlExcess = stats.controlLoad - stats.controlCapacity
        if (controlExcess > 50) {
            errors += error(
                "CONTROL_OVERLOAD",
                "Control load exceeds capacity by more than 50"
            )
        } else if (controlExcess > 0) {
            warnings += warning(
                "CONTROL_OVERLOAD",
                "Control load exceeds capacity by $controlExcess"
            )
        }

        if (stats.visionRangeMm == 0) {
            warnings += warning("NO_SENSORS", "Creature has no sensors and cannot search for enemies")
            info += info("SENSOR_HINT", "Add eyes or chemoreceptors to let the creature find targets")
        }
        if (weaponCount == 0) {
            errors += error("NO_WEAPON", "Creature needs at least one weapon")
        }
        if (stats.maxHp < 100) {
            warnings += warning("LOW_HP", "Creature has less than 100 HP")
        }
        if (stats.turnRateDegPerSecond < 120) {
            warnings += warning("SLOW_TURN", "Turn rate is below 120 degrees per second")
        }

        return GenomeAnalysis(
            genome = genome,
            stats = stats.copy(sockets = socketUsage),
            errors = errors.distinctBy { it.code to it.nodeId },
            warnings = warnings.distinctBy { it.code to it.nodeId },
            info = info.distinctBy { it.code to it.nodeId }
        )
    }

    private fun calculateStats(
        genome: Genome,
        chassis: com.iceja.rts.server.catalog.ChassisDefinition,
        behaviorCp: Int,
        upgradeLevels: Map<UpgradeStat, Int>
    ): DerivedStats {
        var massKg = chassis.massKg
        var hpBonus = 0
        var baseEnergy = 0
        var regenPerSecond = 0
        var regenPercentPerSecond = 0
        var controlBonus = 0
        var controlCost = 0
        var traction = 0
        var accuracyBonus = 0
        var evasion = 0
        var energyDrainMilliPerSecond = 0
        var turnMultiplierPct = 100
        var visionRangeMm = 0
        var visionAngleDeg = 0
        var partCost = 0
        var partMass = 0
        var armor = ArmorProfile()
        val weapons = mutableListOf<WeaponStats>()
        val flags = mutableSetOf<PartFlag>()

        genome.parts.forEach { placement ->
            val part = GameCatalog.partsById[placement.partId] ?: return@forEach
            val copies = if (placement.mirrored) 2 else 1
            partMass += part.massKg * copies
            hpBonus += part.hpBonus * copies
            baseEnergy += part.energyBonus * copies
            regenPerSecond += part.regenPerSecond * copies
            regenPercentPerSecond += part.regenPercentPerSecond * copies
            controlBonus += part.controlBonus * copies
            controlCost += part.controlCost * copies
            traction += part.traction * part.speedMultiplierPct / 100 * copies
            accuracyBonus += part.accuracyBonus * copies
            evasion += part.evasionBonus * copies
            energyDrainMilliPerSecond += part.energyDrainMilliPerSecond * copies
            turnMultiplierPct = max(turnMultiplierPct, part.turnMultiplierPct)
            visionRangeMm = max(visionRangeMm, part.visionRangeMm)
            visionAngleDeg = max(visionAngleDeg, part.visionAngleDeg)
            partCost += part.cost * copies
            armor = armor.plus(part.armor.times(copies))
            flags += part.flags
            part.weapon?.let { weapon ->
                repeat(copies) {
                    weapons += weapon.toStats(part.id)
                }
            }
        }

        massKg += partMass
        val hpLevel = cappedLevel(upgradeLevels, UpgradeStat.HP)
        val damageLevel = cappedLevel(upgradeLevels, UpgradeStat.DAMAGE)
        val armorLevel = cappedLevel(upgradeLevels, UpgradeStat.ARMOR)
        val speedLevel = cappedLevel(upgradeLevels, UpgradeStat.SPEED)
        val energyLevel = cappedLevel(upgradeLevels, UpgradeStat.ENERGY)
        val accuracyLevel = cappedLevel(upgradeLevels, UpgradeStat.ACCURACY)
        val controlLevel = cappedLevel(upgradeLevels, UpgradeStat.CONTROL)

        val maxHp = chassis.hp + hpBonus + hpLevel * (chassis.hp * 8 / 100) + massKg / 2
        val maxEnergy = if (baseEnergy == 0) {
            50
        } else {
            baseEnergy + energyLevel * (baseEnergy * 10 / 100)
        }
        val tractionWithUpgrade = traction * (100 + speedLevel * 4) / 100
        val safeMass = max(1, massKg)
        val speedMmPerSecond = clamp(500, 8_000, tractionWithUpgrade * 2_000 / safeMass)
        val speedMmPerTick = max(16, speedMmPerSecond / GameCatalog.ruleset.tickRate)
        val accelMmPerSecond2 = clamp(2_000, 20_000, tractionWithUpgrade * 8_000 / safeMass)
        val controlCapacity = controlBonus + controlLevel * 8
        val weaponComplexity = weapons.sumOf { weapon -> GameCatalog.partsById[weapon.partId]?.weapon?.complexity ?: 1 }
        val controlLoad = controlCost + weaponComplexity + behaviorCp / 2
        val controlExcess = controlLoad - controlCapacity
        val decisionPenaltyTicks = if (controlExcess > 0) min(5, (controlExcess + 9) / 10) else 0
        val rawTurnRate = clamp(90, 720, controlCapacity * 720 / safeMass)
        val turnRateDegPerSecond = clamp(90, 720, rawTurnRate * turnMultiplierPct / 100)
        val regenMilliPerSecond = regenPerSecond * 1_000 +
            (regenPercentPerSecond * maxHp / 100) * 1_000
        val upkeepMilliPerSecond = massKg * 20 + energyDrainMilliPerSecond + 500 + behaviorCp * 20
        val upgradeCost = UpgradeRules.totalCost(genome.upgrades)
        val costBm = chassis.cost + partCost + upgradeCost + behaviorCp * 2 + massKg * 5 / 100
        val radiusMm = clamp(200, 1_500, chassis.baseRadiusMm + massKg / 20)

        return DerivedStats(
            maxHp = maxHp,
            massKg = massKg,
            costBm = costBm,
            maxEnergy = maxEnergy,
            regenMilliPerSecond = regenMilliPerSecond,
            hpRegenPercentPerSecond = regenPercentPerSecond,
            upkeepMilliPerSecond = upkeepMilliPerSecond,
            speedMmPerTick = speedMmPerTick,
            accelMmPerSecond2 = accelMmPerSecond2,
            turnRateDegPerSecond = turnRateDegPerSecond,
            controlCapacity = controlCapacity,
            controlLoad = controlLoad,
            decisionPenaltyTicks = decisionPenaltyTicks,
            armor = armor.plus(
                ArmorProfile(
                    kinetic = armorLevel * 8,
                    piercing = armorLevel * 8,
                    thermal = armorLevel * 8,
                    acid = armorLevel * 8,
                    electric = armorLevel * 8,
                    toxic = armorLevel * 8
                )
            ).capped(200),
            visionRangeMm = visionRangeMm,
            visionAngleDeg = visionAngleDeg,
            accuracyBonus = accuracyBonus + accuracyLevel * 5,
            evasion = evasion,
            radiusMm = radiusMm,
            behaviorCp = behaviorCp,
            weapons = weapons,
            flags = flags.sortedBy { it.name },
            sockets = emptyList()
        )
    }

    private fun analyzeBehavior(
        behavior: BehaviorSpec,
        errors: MutableList<ValidationIssue>,
        warnings: MutableList<ValidationIssue>
    ): Int {
        val preset = GameCatalog.presetsById[behavior.presetId]
        if (preset == null) {
            errors += error("INVALID_BEHAVIOR", "Unknown behavior preset: ${behavior.presetId}")
        }
        val graph = behavior.graph
        if (graph != null) {
            val validation = BehaviorGraphValidator.validate(graph)
            errors += validation.errors
            warnings += validation.warnings
            return validation.cp
        }
        return preset?.cp ?: 0
    }

    private fun usedSocketPoints(genome: Genome, socketType: SocketType): Int =
        genome.parts.fold(0) { total: Int, placement: PartPlacement ->
            val part = GameCatalog.partsById[placement.partId]
            val used = if (part == null) {
                0
            } else if (placement.mirrored &&
                (placement.socketId == SocketType.LEFT || placement.socketId == SocketType.RIGHT)
            ) {
                if (socketType == SocketType.LEFT || socketType == SocketType.RIGHT) 1 else 0
            } else if (part.socket == socketType) {
                1
            } else {
                0
            }
            total + used
        }

    private fun cappedLevel(levels: Map<UpgradeStat, Int>, stat: UpgradeStat): Int {
        val rule = UpgradeRules.rule(stat)
        return (levels[stat] ?: 0).coerceIn(0, rule.maxLevel)
    }

    private fun WeaponProfile.toStats(partId: String): WeaponStats = WeaponStats(
        partId = partId,
        damage = damage,
        cooldownTicks = cooldownTicks,
        rangeMm = rangeMm,
        damageType = damageType,
        accuracy = accuracy,
        projectileSpeedMmPerTick = projectileSpeedMmPerTick,
        splashRadiusMm = splashRadiusMm,
        dotDamagePerSecond = dotDamagePerSecond,
        dotDurationTicks = dotDurationTicks,
        penetration = penetration,
        ranged = ranged
    )

    private fun clamp(minimum: Int, maximum: Int, value: Int): Int =
        value.coerceIn(minimum, maximum)

    private fun error(code: String, message: String, nodeId: String = ""): ValidationIssue =
        ValidationIssue(code, message, ValidationSeverity.ERROR, nodeId)

    private fun warning(code: String, message: String, nodeId: String = ""): ValidationIssue =
        ValidationIssue(code, message, ValidationSeverity.WARNING, nodeId)

    private fun info(code: String, message: String, nodeId: String = ""): ValidationIssue =
        ValidationIssue(code, message, ValidationSeverity.INFO, nodeId)

}
