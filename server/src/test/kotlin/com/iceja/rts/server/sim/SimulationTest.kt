package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimulationTest {
    @Test
    fun identicalInputsProduceIdenticalHashes() {
        val first = simulation(seed = 77L, maxTicks = 300)
        val second = simulation(seed = 77L, maxTicks = 300)

        repeat(300) {
            first.step()
            second.step()
        }

        assertEquals(first.stateHash(), second.stateHash())
        assertEquals(first.snapshot(), second.snapshot())
    }

    @Test
    fun differentArmyCompositionProducesDifferentHashes() {
        val first = simulation(seed = 1L, maxTicks = 120)
        val second = simulation(seed = 1L, maxTicks = 120, botTemplateName = "Spitter")

        repeat(120) {
            first.step()
            second.step()
        }

        assertNotEquals(first.stateHash(), second.stateHash())
    }

    @Test
    fun simulationMovesCreaturesAndProducesCombatOrTimeout() {
        val simulation = simulation(seed = 9L, maxTicks = 900, closeSpawn = true)
        repeat(900) {
            if (simulation.result == null) {
                simulation.step()
            }
        }

        val snapshot = simulation.snapshot()
        assertEquals(2, snapshot.hives.size)
        assertTrue(snapshot.entities.isNotEmpty())
        assertNotNull(simulation.result)
        assertTrue(simulation.events().isNotEmpty())
    }

    @Test
    fun timeoutAlwaysFinishesMatch() {
        val simulation = simulation(seed = 12L, maxTicks = 60, closeSpawn = true)
        repeat(70) {
            if (simulation.result == null) {
                simulation.step()
            }
        }
        assertNotNull(simulation.result)
        assertTrue(simulation.result?.reason?.startsWith("TIMEOUT") == true)
    }

    private fun simulation(
        seed: Long,
        maxTicks: Int,
        closeSpawn: Boolean = false,
        botTemplateName: String = "Brawler"
    ): Simulation {
        val scout = GenomeTemplates.all.first { it.name == "Scout" }
        val brawler = GenomeTemplates.all.first { it.name == botTemplateName }
        val scoutAnalysis = GenomeAnalyzer.analyze(scout)
        val brawlerAnalysis = GenomeAnalyzer.analyze(brawler)
        val playerUnits = listOf(
            ArmyPlacement(
                genome = scout,
                stats = scoutAnalysis.stats,
                x = if (closeSpawn) 60_000 else 0,
                y = if (closeSpawn) 64_000 else 0
            )
        )
        val botUnits = listOf(
            ArmyPlacement(
                genome = brawler,
                stats = brawlerAnalysis.stats,
                x = if (closeSpawn) 61_000 else 0,
                y = if (closeSpawn) 64_000 else 0
            )
        )
        return Simulation(
            matchId = "test-$seed",
            seed = seed,
            config = SimulationConfig(
                map = GameCatalog.map("duel-arena"),
                maxTicks = maxTicks,
                overtimeTicks = 0
            ),
            players = listOf(
                PlayerSetup(0, "Alpha", playerUnits),
                PlayerSetup(1, "Beta", botUnits)
            )
        )
    }
}
