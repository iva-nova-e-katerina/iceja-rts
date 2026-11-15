package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.genome.BehaviorGraph
import com.iceja.rts.server.genome.BehaviorNode
import com.iceja.rts.server.genome.BehaviorGraphValidator
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.genome.PartPlacement
import com.iceja.rts.server.genome.PresetGraphs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BehaviorGraphInterpreterTest {
    private val hiveA = Hive(1, 0, 20_000, 64_000, 3_000, 50, 4_000)
    private val hiveB = Hive(2, 1, 108_000, 64_000, 3_000, 50, 4_000)

    @Test
    fun presetGraphsAreValid() {
        PresetGraphs.all.forEach { preset ->
            val validation = BehaviorGraphValidator.validate(preset.graph)
            assertTrue(
                validation.errors.isEmpty(),
                "${preset.presetId}: ${validation.errors.map { it.code }}"
            )
        }
    }

    @Test
    fun attackGraphAttacksVisibleEnemy() {
        val graph = BehaviorGraph(
            rootId = "root",
            nodes = listOf(
                BehaviorNode("root", "Selector", children = listOf("sequence")),
                BehaviorNode("sequence", "Sequence", children = listOf("target", "attack")),
                BehaviorNode("target", "HasTarget"),
                BehaviorNode("attack", "AttackTarget", text = "enemy.nearest")
            )
        )
        val creature = creature(3, 0, genomeWithSensor(), 60_000, 64_000)
        val enemy = creature(4, 1, GenomeTemplates.all.first { it.name == "Scout" }, 61_000, 64_000)

        val decision = BehaviorGraphInterpreter.decide(
            graph = graph,
            creature = creature,
            friends = listOf(creature),
            enemies = listOf(enemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0,
            random = Xoshiro128.fromSeed(1L)
        )

        assertEquals("ATTACK", decision.mode)
        assertEquals(enemy.id, decision.attackTargetId)
        assertEquals("attack", decision.nodeId)
    }

    @Test
    fun lowHpGraphFlees() {
        val graph = BehaviorGraph(
            rootId = "root",
            nodes = listOf(
                BehaviorNode("root", "Sequence", children = listOf("hp", "flee")),
                BehaviorNode("hp", "HpBelow", params = mapOf("pct" to 50)),
                BehaviorNode("flee", "Flee")
            )
        )
        val creature = creature(3, 0, genomeWithSensor(), 60_000, 64_000).apply {
            hp = 1
        }

        val decision = BehaviorGraphInterpreter.decide(
            graph = graph,
            creature = creature,
            friends = listOf(creature),
            enemies = emptyList(),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0,
            random = Xoshiro128.fromSeed(1L)
        )

        assertEquals("SURVIVE", decision.mode)
        assertEquals("flee", decision.nodeId)
    }

    @Test
    fun graphWithoutActionHoldsPosition() {
        val graph = BehaviorGraph(
            rootId = "root",
            nodes = listOf(BehaviorNode("root", "HasTarget"))
        )
        val creature = creature(3, 0, genomeWithSensor(), 60_000, 64_000)
        val enemy = creature(4, 1, GenomeTemplates.all.first { it.name == "Scout" }, 61_000, 64_000)

        val decision = BehaviorGraphInterpreter.decide(
            graph = graph,
            creature = creature,
            friends = listOf(creature),
            enemies = listOf(enemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0,
            random = Xoshiro128.fromSeed(1L)
        )

        assertEquals("IDLE", decision.mode)
        assertEquals("NO_ACTION", decision.reason)
    }

    @Test
    fun presetGraphRunsThroughInterpreter() {
        val graph = PresetGraphs.forPreset("BERSERK") ?: error("BERSERK graph missing")
        val creature = creature(3, 0, genomeWithSensor(), 60_000, 64_000)
        val enemy = creature(4, 1, GenomeTemplates.all.first { it.name == "Scout" }, 61_000, 64_000)

        val decision = BehaviorGraphInterpreter.decide(
            graph = graph,
            creature = creature,
            friends = listOf(creature),
            enemies = listOf(enemy),
            ownHive = hiveA,
            enemyHive = hiveB,
            tick = 0,
            random = Xoshiro128.fromSeed(1L)
        )

        assertEquals("ATTACK", decision.mode)
        assertTrue(decision.nodeId.isNotBlank())
    }

    private fun creature(id: Int, ownerId: Int, genome: com.iceja.rts.server.genome.Genome, x: Int, y: Int): Creature {
        val stats = GenomeAnalyzer.analyze(genome).stats
        return Creature(
            id = id,
            ownerId = ownerId,
            genomeIndex = 0,
            genome = genome,
            stats = stats,
            x = x,
            y = y
        )
    }

    private fun genomeWithSensor(): com.iceja.rts.server.genome.Genome {
        val base = GenomeTemplates.all.first { it.name == "Brawler" }
        return base.copy(parts = base.parts + PartPlacement("FACET", SocketType.FRONT))
    }
}
