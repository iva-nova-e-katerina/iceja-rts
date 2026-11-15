package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.BehaviorSpec
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MapObjectiveTest {
    private val map = GameCatalog.map("duel-arena")
    private val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
    private val scout = GenomeTemplates.all.first { it.name == "Scout" }

    @Test
    fun unitCapturesNeutralObjective() {
        val simulation = simulation(
            alpha = listOf(placement(brawler, 64_000, 96_000)),
            beta = listOf(placement(brawler, 180_000, 20_000))
        )

        repeat(120) {
            simulation.step()
        }

        val west = simulation.snapshot().objectives.first { it.id == "west-nectar" }
        assertEquals(0, west.owner)
        assertTrue(simulation.snapshot().controlScores[0] > 0)
    }

    @Test
    fun presenceOfBothSidesContestsObjective() {
        val simulation = simulation(
            alpha = listOf(placement(scout.copy(behavior = BehaviorSpec(presetId = "BERSERK")), 96_000, 96_000)),
            beta = listOf(placement(scout.copy(behavior = BehaviorSpec(presetId = "BERSERK")), 96_000, 96_000))
        )

        repeat(30) {
            simulation.step()
        }

        val center = simulation.snapshot().objectives.first { it.id == "center-nectar" }
        assertEquals(-1, center.owner)
        assertTrue(center.contested)
    }

    @Test
    fun idlePatrolMovesTowardNearestObjective() {
        val simulation = simulation(
            alpha = listOf(placement(brawler, 40_000, 96_000)),
            beta = listOf(placement(brawler, 180_000, 20_000))
        )

        repeat(6) {
            simulation.step()
        }

        val unit = simulation.creatures().first { it.ownerId == 0 }
        assertEquals("CAPTURE", unit.aiMode)
        assertTrue(unit.desiredX > 40_000)
    }

    @Test
    fun acidHazardDamagesCreature() {
        val simulation = simulation(
            alpha = listOf(placement(brawler, 72_000, 36_000)),
            beta = listOf(placement(brawler, 180_000, 20_000))
        )
        val unit = simulation.creatures().first { it.ownerId == 0 }
        val startHp = unit.hp

        repeat(30) {
            simulation.step()
        }

        assertTrue(unit.hp < startHp, "creature standing in acid should lose HP")
    }

    private fun simulation(alpha: List<ArmyPlacement>, beta: List<ArmyPlacement>): Simulation =
        Simulation(
            matchId = "objective-test",
            seed = 77L,
            config = SimulationConfig(map = map, maxTicks = 600, overtimeTicks = 0),
            players = listOf(
                PlayerSetup(0, "Alpha", alpha),
                PlayerSetup(1, "Beta", beta)
            )
        )

    private fun placement(
        genome: com.iceja.rts.server.genome.Genome,
        x: Int,
        y: Int
    ): ArmyPlacement = ArmyPlacement(
        genome = genome,
        stats = GenomeAnalyzer.analyze(genome).stats,
        x = x,
        y = y
    )
}
