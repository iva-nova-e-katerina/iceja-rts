package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExperienceRulesTest {
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
    private val baseStats = GenomeAnalyzer.analyze(brawler).stats

    @Test
    fun maxExperienceGivesQuarterBonusToHpAndDamage() {
        val scaled = ExperienceRules.apply(baseStats, GameCatalog.ruleset.maxExperience)

        assertEquals(baseStats.maxHp * 125 / 100, scaled.maxHp)
        baseStats.weapons.zip(scaled.weapons).forEach { (base, experienced) ->
            assertEquals(base.damage * 125 / 100, experienced.damage)
        }
    }

    @Test
    fun noExperienceKeepsOriginalStats() {
        val scaled = ExperienceRules.apply(baseStats, 0)

        assertEquals(baseStats, scaled)
    }

    @Test
    fun survivorGainIsPositiveAndBounded() {
        val creature = Creature(
            id = 1,
            ownerId = 0,
            genomeIndex = 0,
            genome = brawler,
            stats = baseStats,
            x = 10_000,
            y = 10_000
        )
        creature.damageDealt = 500
        creature.kills = 3

        val gain = ExperienceRules.gain(creature)
        assertTrue(gain > 80)
        assertTrue(gain <= 160)
    }
}
