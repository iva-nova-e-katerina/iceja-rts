package com.iceja.rts.server.sim

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.genome.BehaviorSpec
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.genome.PartPlacement
import kotlin.test.Test
import kotlin.test.assertTrue

class MultiWeaponCombatTest {
    @Test
    fun unitFiresAllReadyWeapons() {
        val dual = Genome(
            chassisId = "BRUTE",
            name = "Dual",
            parts = listOf(
                PartPlacement("GANGLION", SocketType.CORE),
                PartPlacement("HEART", SocketType.CORE),
                PartPlacement("SLUGFOOT", SocketType.UNDER),
                PartPlacement("FACET", SocketType.FRONT),
                PartPlacement("MANDIBLE", SocketType.FRONT),
                PartPlacement("CLAWS", SocketType.LEFT)
            ),
            behavior = BehaviorSpec(presetId = "BERSERK")
        )
        val brawler = GenomeTemplates.all.first { it.name == "Brawler" }
        val dualStats = GenomeAnalyzer.analyze(dual).stats
        val brawlerStats = GenomeAnalyzer.analyze(brawler).stats
        val simulation = Simulation(
            matchId = "dual-weapons",
            seed = 141L,
            config = SimulationConfig(
                map = GameCatalog.map("duel-arena"),
                maxTicks = 30,
                overtimeTicks = 0
            ),
            players = listOf(
                PlayerSetup(0, "Alpha", listOf(ArmyPlacement(dual, dualStats, 60_000, 64_000))),
                PlayerSetup(1, "Beta", listOf(ArmyPlacement(brawler, brawlerStats, 60_500, 64_000)))
            )
        )
        val events = mutableListOf<SimEvent>()
        repeat(2) {
            simulation.step()
            events += simulation.events()
        }

        assertTrue(
            events.count { it.type == "ATTACK" && it.sourceId == 3 } >= 2,
            "all ready weapons should fire"
        )
    }
}
