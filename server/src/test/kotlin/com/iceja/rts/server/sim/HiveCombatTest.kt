package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HiveCombatTest {
    private val map = GameCatalog.map("duel-arena")
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }

    @Test
    fun unitDamagesHiveAndHiveCallsForHelp() {
        val simulation = simulation(alphaX = 160_000, betaX = 180_000)
        val enemyHive = simulation.hives().first { it.ownerId == 1 }
        val startHp = enemyHive.hp
        val events = mutableListOf<SimEvent>()

        repeat(8) {
            simulation.step()
            events += simulation.events()
        }

        assertTrue(enemyHive.hp < startHp, "unit should damage the enemy hive")
        assertTrue(events.any { it.type == "TELEPATHY" && it.text == "HIVE_CALL" })
        val defender = simulation.creatures().first { it.ownerId == 1 }
        assertEquals("RESPOND", defender.aiMode)
    }

    @Test
    fun scoutSharesHiveLocationWithTeam() {
        val scoutStats = GenomeAnalyzer.analyze(GenomeTemplates.all.first { it.name == "Scout" }).stats
        val brawlerStats = GenomeAnalyzer.analyze(brawler).stats
        val scout = GenomeTemplates.all.first { it.name == "Scout" }
        val teamSimulation = Simulation(
            matchId = "hive-scout-call",
            seed = 112L,
            config = SimulationConfig(map = map, maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(
                    0,
                    "Alpha",
                    listOf(
                        ArmyPlacement(scout, scoutStats, 155_000, 96_000),
                        ArmyPlacement(brawler, brawlerStats, 40_000, 96_000)
                    )
                ),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(brawler, brawlerStats, 180_000, 20_000)))
            )
        )
        val events = mutableListOf<SimEvent>()
        repeat(2) {
            teamSimulation.step()
            events += teamSimulation.events()
        }
        assertTrue(events.any { it.type == "HIVE_FOUND" })
        val ally = teamSimulation.creatures().first { it.ownerId == 0 && it.genome.name == "Brawler" }
        assertEquals("SIEGE", ally.aiMode)
    }

    @Test
    fun destroyingTheHiveWinsTheMatch() {
        val simulation = simulation(alphaX = 160_000, betaX = 180_000)
        val enemyHive = simulation.hives().first { it.ownerId == 1 }
        enemyHive.hp = 1

        repeat(8) {
            simulation.step()
        }

        assertEquals(0, simulation.result?.winner)
        assertEquals("HIVE_DESTROYED", simulation.result?.reason)
    }

    private fun simulation(alphaX: Int, betaX: Int): Simulation {
        val stats = GenomeAnalyzer.analyze(brawler).stats
        return Simulation(
            matchId = "hive-combat",
            seed = 111L,
            config = SimulationConfig(map = map, maxTicks = 300, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(0, "Alpha", listOf(ArmyPlacement(brawler, stats, alphaX, 96_000))),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(brawler, stats, betaX, 20_000)))
            )
        )
    }
}
