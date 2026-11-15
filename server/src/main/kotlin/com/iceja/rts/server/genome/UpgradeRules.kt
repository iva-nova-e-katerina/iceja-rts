package com.iceja.rts.server.genome


data class UpgradeRule(
    val stat: UpgradeStat,
    val basePrice: Int,
    val growthPercent: Int,
    val maxLevel: Int
)

object UpgradeRules {
    private val rules: Map<UpgradeStat, UpgradeRule> = mapOf(
        UpgradeStat.HP to UpgradeRule(UpgradeStat.HP, 15, 135, 5),
        UpgradeStat.DAMAGE to UpgradeRule(UpgradeStat.DAMAGE, 20, 140, 5),
        UpgradeStat.ARMOR to UpgradeRule(UpgradeStat.ARMOR, 15, 135, 5),
        UpgradeStat.SPEED to UpgradeRule(UpgradeStat.SPEED, 20, 140, 4),
        UpgradeStat.ENERGY to UpgradeRule(UpgradeStat.ENERGY, 12, 130, 5),
        UpgradeStat.ACCURACY to UpgradeRule(UpgradeStat.ACCURACY, 18, 140, 4),
        UpgradeStat.CONTROL to UpgradeRule(UpgradeStat.CONTROL, 15, 135, 4)
    )

    fun rule(stat: UpgradeStat): UpgradeRule =
        rules[stat] ?: throw IllegalArgumentException("Unknown upgrade: $stat")

    fun levelOf(upgrades: List<UpgradePlacement>, stat: UpgradeStat): Int =
        upgrades.filter { it.stat == stat }.sumOf { it.level }

    fun totalCost(upgrades: List<UpgradePlacement>): Int =
        upgrades.sumOf { placement ->
            val rule = rule(placement.stat)
            var price = rule.basePrice
            var total = 0
            repeat(placement.level) {
                total += price
                price = (price * rule.growthPercent + 50) / 100
            }
            total
        }

    fun validate(upgrades: List<UpgradePlacement>, errors: MutableList<ValidationIssue>): Map<UpgradeStat, Int> {
        val levels = mutableMapOf<UpgradeStat, Int>()
        upgrades.forEach { placement ->
            val rule = rule(placement.stat)
            if (placement.level < 0) {
                errors += ValidationIssue(
                    code = "INVALID_UPGRADE",
                    message = "Upgrade level cannot be negative",
                    severity = ValidationSeverity.ERROR,
                    nodeId = placement.stat.name
                )
            }
            if (placement.level > rule.maxLevel) {
                errors += ValidationIssue(
                    code = "INVALID_UPGRADE",
                    message = "${placement.stat.name} upgrade exceeds ${rule.maxLevel} levels",
                    severity = ValidationSeverity.ERROR,
                    nodeId = placement.stat.name
                )
            }
            levels[placement.stat] = (levels[placement.stat] ?: 0) + placement.level
        }
        return levels
    }

}
