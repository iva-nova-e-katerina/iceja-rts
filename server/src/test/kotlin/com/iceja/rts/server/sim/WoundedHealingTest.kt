package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WoundedHealingTest {
    private val map = GameCatalog.map("duel-arena")
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }

    @Test
    fun woundedCreatureSeeksNectarWhenNoEnemyIsVisible() {
        val simulation = simulation(40_000, 96_000)
        val wounded = simulation.creatures().first { it.ownerId == 0 }
        wounded.hp = wounded.stats.maxHp * 20 / 100

        repeat(9) {
            simulation.step()
        }

        assertEquals("HEAL", wounded.aiMode)
        assertEquals("SEEK_NECTAR", wounded.aiReason)
        assertTrue(wounded.desiredX > 40_000)
    }

    @Test
    fun nectarHealsCreatureStandingOnIt() {
        val simulation = simulation(64_000, 96_000)
        val wounded = simulation.creatures().first { it.ownerId == 0 }
        wounded.hp = wounded.stats.maxHp * 20 / 100
        val startHp = wounded.hp

        repeat(30) {
            simulation.step()
        }

        assertEquals("HEAL", wounded.aiMode)
        assertTrue(wounded.hp > startHp, "creature should heal at the nectar source")
    }

    private fun simulation(x: Int, y: Int): Simulation {
        val stats = GenomeAnalyzer.analyze(brawler).stats
        return Simulation(
            matchId = "wounded-healing",
            seed = 91L,
            config = SimulationConfig(map = map, maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Wounded",
                    units = listOf(ArmyPlacement(brawler, stats, x, y))
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Far",
                    units = listOf(ArmyPlacement(brawler, stats, 180_000, 20_000))
                )
            )
        )
    }
}
