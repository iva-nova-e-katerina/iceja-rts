package com.iceja.rts.server.match

import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.sim.ArmyPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BotArmyBuilderTest {
    private val builder = BotArmyBuilder()

    @Test
    fun botMatchesPlayerCountAndDoesNotExceedBudget() {
        val playerUnits = listOf("Scout", "Spitter", "Healer").flatMap { name ->
            List(4) { placement(name) }
        }
        val playerBudget = playerUnits.sumOf { it.stats.costBm }

        val botUnits = builder.build(playerUnits)

        assertEquals(playerUnits.size, botUnits.size)
        assertTrue(botUnits.sumOf { it.stats.costBm } <= playerBudget)
        assertTrue(botUnits.sumOf { it.stats.costBm } >= playerBudget - 400)
    }

    @Test
    fun cheapSwarmKeepsLargeUnitCount() {
        val playerUnits = List(35) { placement("Swarm") }
        val playerBudget = playerUnits.sumOf { it.stats.costBm }

        val botUnits = builder.build(playerUnits)

        assertEquals(35, botUnits.size)
        assertTrue(botUnits.sumOf { it.stats.costBm } <= playerBudget)
    }

    @Test
    fun singleUnitGetsSingleOpponent() {
        val playerUnits = listOf(placement("Brawler"))
        val botUnits = builder.build(playerUnits)

        assertEquals(1, botUnits.size)
        assertTrue(botUnits.first().stats.costBm <= playerUnits.first().stats.costBm)
    }

    @Test
    fun builderIsDeterministic() {
        val playerUnits = List(10) { index -> placement(if (index % 2 == 0) "Scout" else "Tank") }

        val first = builder.build(playerUnits)
        val second = builder.build(playerUnits)

        assertEquals(
            first.map { it.genome.name },
            second.map { it.genome.name }
        )
    }

    @Test
    fun emptyArmyProducesEmptyBotArmy() {
        assertEquals(emptyList(), builder.build(emptyList()))
    }

    private fun placement(name: String): ArmyPlacement {
        val genome = GenomeTemplates.all.first { it.name == name }
        val analysis = GenomeAnalyzer.analyze(genome)
        return ArmyPlacement(
            genome = genome,
            stats = analysis.stats,
            x = 0,
            y = 0
        )
    }
}
