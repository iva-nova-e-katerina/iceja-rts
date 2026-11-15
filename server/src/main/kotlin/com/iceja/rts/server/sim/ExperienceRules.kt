package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.DerivedStats

object ExperienceRules {
    fun apply(stats: DerivedStats, experience: Int): DerivedStats {
        val maxExperience = GameCatalog.ruleset.maxExperience.coerceAtLeast(1)
        val capped = experience.coerceIn(0, maxExperience)
        if (capped == 0) {
            return stats
        }
        val multiplier = 10_000L + 2_500L * capped / maxExperience
        fun scale(value: Int): Int =
            (value.toLong() * multiplier / 10_000L).toInt().coerceAtLeast(1)

        return stats.copy(
            maxHp = scale(stats.maxHp),
            weapons = stats.weapons.map { weapon ->
                weapon.copy(
                    damage = scale(weapon.damage),
                    dotDamagePerSecond = if (weapon.dotDamagePerSecond > 0) {
                        (weapon.dotDamagePerSecond.toLong() * multiplier / 10_000L).toInt()
                    } else {
                        0
                    }
                )
            }
        )
    }

    fun gain(creature: Creature): Int {
        val damageBonus = (creature.damageDealt / 120L).toInt().coerceAtMost(35)
        val killBonus = (creature.kills * 15).coerceAtMost(45)
        return (80 + damageBonus + killBonus).coerceAtMost(160)
    }
}
