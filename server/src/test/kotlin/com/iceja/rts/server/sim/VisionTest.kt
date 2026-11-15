package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.genome.PartPlacement
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VisionTest {
    private val genome = GenomeTemplates.all.first { it.name == "Brawler" }
    private val stats = GenomeAnalyzer.analyze(genome).stats
    private val rangeGenome = genome.copy(parts = genome.parts + PartPlacement("CHEMO", SocketType.FRONT))
    private val rangeStats = GenomeAnalyzer.analyze(rangeGenome).stats
    private val map = GameCatalog.map("pillars")

    @Test
    fun hivesAreOutsideVisionRange() {
        val arena = GameCatalog.map("duel-arena")
        val gap = IntegerMath.distance(arena.spawnA.x, arena.spawnA.y, arena.spawnB.x, arena.spawnB.y)
        val maxVision = GenomeTemplates.all.maxOf { template ->
            GenomeAnalyzer.analyze(template).stats.visionRangeMm
        }

        assertTrue(gap > maxVision, "hives should be farther apart than any creature can see")
    }

    @Test
    fun seesEnemyInsideVisionCone() {
        val observer = creature(1, 30_000, 20_000, angle = 0)
        val target = creature(2, 36_000, 20_000)
        val navigation = NavigationGrid(map)

        assertTrue(Vision.canSee(observer, target, navigation))
    }

    @Test
    fun doesNotSeeEnemyBehindItsBack() {
        val observer = creature(1, 30_000, 20_000, angle = 0)
        val target = creature(2, 24_000, 20_000)
        val navigation = NavigationGrid(map)

        assertFalse(Vision.canSee(observer, target, navigation))
    }

    @Test
    fun obstacleBlocksLineOfSight() {
        val observer = creature(1, 50_000, 60_000, withChemo = true)
        val target = creature(2, 70_000, 60_000, withChemo = true)
        val navigation = NavigationGrid(map)

        assertTrue(observer.stats.visionRangeMm >= 20_000)
        assertFalse(Vision.canSee(observer, target, navigation))
    }

    private fun creature(id: Int, x: Int, y: Int, angle: Int = 0, withChemo: Boolean = false): Creature =
        Creature(
            id = id,
            ownerId = if (id == 1) 0 else 1,
            genomeIndex = 0,
            genome = if (withChemo) rangeGenome else genome,
            stats = if (withChemo) rangeStats else stats,
            x = x,
            y = y
        ).also { it.angle = angle }
}
