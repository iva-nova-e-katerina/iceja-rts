package com.iceja.rts.server.genome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UpgradeRulesTest {
    @Test
    fun upgradeCostGrowsByRule() {
        val twoLevels = listOf(UpgradePlacement(UpgradeStat.HP, 2))
        assertEquals(35, UpgradeRules.totalCost(twoLevels))
    }

    @Test
    fun upgradeLevelAboveMaximumIsRejected() {
        val errors = mutableListOf<ValidationIssue>()
        UpgradeRules.validate(
            listOf(UpgradePlacement(UpgradeStat.SPEED, 5)),
            errors
        )
        assertTrue(errors.any { it.code == "INVALID_UPGRADE" })
    }

    @Test
    fun everyStatHasRule() {
        UpgradeStat.entries.forEach { stat ->
            val rule = UpgradeRules.rule(stat)
            assertTrue(rule.maxLevel > 0)
            assertTrue(rule.basePrice > 0)
            assertTrue(rule.growthPercent >= 100)
        }
    }
}
