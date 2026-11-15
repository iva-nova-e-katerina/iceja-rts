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

class CorpseScavengingTest {
    @Test
    fun scavengerEatsCorpseAfterKill() {
        val scavengerGenome = Genome(
            chassisId = "BRUTE",
            name = "Scavenger Test",
            parts = listOf(
                PartPlacement("GANGLION", SocketType.CORE),
                PartPlacement("HEART", SocketType.CORE),
                PartPlacement("SLUGFOOT", SocketType.UNDER),
                PartPlacement("FACET", SocketType.FRONT),
                PartPlacement("SCAVENGER", SocketType.FRONT),
                PartPlacement("CLAWS", SocketType.LEFT)
            ),
            behavior = BehaviorSpec(presetId = "SCAVENGER")
        )
        val scavengerStats = GenomeAnalyzer.analyze(scavengerGenome).stats
        val swarm = GenomeTemplates.all.first { it.name == "Swarm" }
        val swarmStats = GenomeAnalyzer.analyze(swarm).stats
        val simulation = Simulation(
            matchId = "scavenger",
            seed = 21L,
            config = SimulationConfig(
                map = GameCatalog.map("duel-arena").copy(objectives = emptyList(), hazards = emptyList()),
                maxTicks = 900,
                overtimeTicks = 0
            ),
            players = listOf(
                PlayerSetup(
                    playerId = 0,
                    nickname = "Scavenger",
                    units = listOf(ArmyPlacement(scavengerGenome, scavengerStats, 60_000, 64_000))
                ),
                PlayerSetup(
                    playerId = 1,
                    nickname = "Prey",
                    units = listOf(
                        ArmyPlacement(swarm, swarmStats, 61_000, 64_000),
                        ArmyPlacement(swarm, swarmStats, 115_000, 64_000)
                    )
                )
            )
        )

        val events = mutableListOf<SimEvent>()
        repeat(900) {
            if (simulation.result == null) {
                simulation.step()
                events += simulation.events()
            }
        }

        assertTrue(events.any { event -> event.type == "DEATH" })
        assertTrue(
            events.any { event -> event.type == "RESOURCE" && event.sourceId == 3 && event.text == "CORPSE" },
            "scavenger should consume the corpse"
        )
        assertTrue(simulation.corpses().isEmpty(), "eaten corpse should be removed")
    }
}
