package com.iceja.rts.server.match

import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.sim.ArmyPlacement

class BotArmyBuilder {
    private data class Candidate(
        val genome: Genome,
        val stats: com.iceja.rts.server.genome.DerivedStats
    )

    private val preferredNames = listOf(
        "Brawler",
        "Spitter",
        "Scout",
        "Tank",
        "Swarm",
        "Healer",
        "Burrower",
        "Flyer",
        "Artillery",
        "Harvester"
    )

    fun build(playerUnits: List<ArmyPlacement>): List<ArmyPlacement> {
        if (playerUnits.isEmpty()) {
            return emptyList()
        }
        val playerBudget = playerUnits.sumOf { it.stats.costBm }
        val playerCount = playerUnits.size.coerceAtLeast(1)
        val candidates = GenomeTemplates.all
            .mapNotNull { genome ->
                val analysis = GenomeAnalyzer.analyze(genome)
                if (analysis.valid) Candidate(genome, analysis.stats) else null
            }
            .sortedWith(compareBy<Candidate> { it.stats.costBm }.thenBy { it.genome.name })
        if (candidates.isEmpty()) {
            return emptyList()
        }

        val cheapestCost = candidates.first().stats.costBm
        val maxCountByBudget = (playerBudget / cheapestCost).coerceAtLeast(1)
        val targetCount = minOf(playerCount, maxCountByBudget, GameCatalog.ruleset.maxCreaturesPerArmy)
        var remainingBudget = playerBudget
        val chosen = mutableListOf<Candidate>()

        for (index in 0 until targetCount) {
            val slotsLeft = targetCount - index - 1
            val maxAffordable = remainingBudget - cheapestCost * slotsLeft
            val preferredName = preferredNames[index % preferredNames.size]
            val preferred = candidates.firstOrNull {
                it.genome.name == preferredName && it.stats.costBm <= maxAffordable
            }
            val pick = preferred
                ?: candidates.filter { it.stats.costBm <= maxAffordable }.maxByOrNull { it.stats.costBm }
                ?: candidates.first()
            chosen += pick
            remainingBudget -= pick.stats.costBm
        }

        for (index in chosen.indices) {
            val current = chosen[index]
            val replacement = candidates
                .filter {
                    it.stats.costBm > current.stats.costBm &&
                        it.stats.costBm - current.stats.costBm <= remainingBudget
                }
                .maxByOrNull { it.stats.costBm }
            if (replacement !== null) {
                remainingBudget -= replacement.stats.costBm - current.stats.costBm
                chosen[index] = replacement
            }
        }

        return chosen.map { candidate ->
            ArmyPlacement(
                genome = candidate.genome,
                stats = candidate.stats,
                x = 0,
                y = 0
            )
        }
    }
}
