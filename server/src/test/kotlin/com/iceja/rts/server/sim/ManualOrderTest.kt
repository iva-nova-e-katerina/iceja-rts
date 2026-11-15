package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualOrderTest {
    @Test
    fun playerCanOrderOwnCreatureToMove() {
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val stats = GenomeAnalyzer.analyze(brawler).stats
        val simulation = Simulation(
            matchId = "manual-order",
            seed = 121L,
            config = SimulationConfig(map = GameCatalog.map("duel-arena"), maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(0, "Alpha", listOf(ArmyPlacement(brawler, stats, 40_000, 96_000))),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(brawler, stats, 180_000, 20_000)))
            )
        )
        val unit = simulation.creatures().first { it.ownerId == 0 && it.genome.name == "Brawler" }

        assertTrue(simulation.moveCreature(unit.id, 0, 50_000, 96_000))
        assertFalse(simulation.moveCreature(unit.id, 1, 50_000, 96_000))
        simulation.step()

        assertEquals("MANUAL", unit.aiMode)
        assertEquals("PLAYER_ORDER", unit.aiReason)
        assertEquals(50_000, unit.desiredX)
        assertEquals(96_000, unit.desiredY)
    }

    @Test
    fun orderToEnemyHiveStopsAtAttackSurface() {
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val stats = GenomeAnalyzer.analyze(brawler).stats
        val simulation = Simulation(
            matchId = "manual-hive-order",
            seed = 122L,
            config = SimulationConfig(map = GameCatalog.map("duel-arena"), maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(0, "Alpha", listOf(ArmyPlacement(brawler, stats, 159_000, 96_000))),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(brawler, stats, 180_000, 20_000)))
            )
        )
        val unit = simulation.creatures().first { it.ownerId == 0 && it.genome.name == "Brawler" }
        val enemyHive = simulation.hives().first { it.ownerId == 1 }
        val startHp = enemyHive.hp

        assertTrue(simulation.moveCreature(unit.id, 0, enemyHive.x, enemyHive.y))
        repeat(100) {
            simulation.step()
        }

        assertEquals(0, unit.manualOrderTicks)
        assertTrue(enemyHive.hp < startHp, "unit should attack the hive after a move order")
    }
}
