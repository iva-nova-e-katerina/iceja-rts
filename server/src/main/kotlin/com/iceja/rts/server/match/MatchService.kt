package com.iceja.rts.server.match

import com.iceja.rts.server.api.ArmyView
import com.iceja.rts.server.api.CreateMatchRequest
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.sim.ArmyPlacement
import com.iceja.rts.server.sim.PlayerSetup
import com.iceja.rts.server.sim.Simulation
import com.iceja.rts.server.sim.SimulationConfig
import com.iceja.rts.server.store.ArmyRepository
import com.iceja.rts.server.store.CreatureRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.security.SecureRandom
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MatchRepository {
    private val matches = ConcurrentHashMap<String, MatchSession>()

    fun save(session: MatchSession) {
        matches[session.id] = session
    }

    fun find(matchId: String): MatchSession? = matches[matchId]

    fun list(ownerId: String): List<MatchSession> =
        matches.values
            .filter { it.ownerId == ownerId }
            .sortedWith(compareByDescending<MatchSession> { it.createdAt }.thenBy { it.id })
}

class MatchService(
    private val creatureRepository: CreatureRepository,
    private val armyRepository: ArmyRepository,
    private val matchRepository: MatchRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val random = SecureRandom()

    fun createMatch(ownerId: String, request: CreateMatchRequest): MatchSession {
        val army = armyRepository.find(request.armyId)
            ?: throw IllegalArgumentException("Army not found")
        require(army.ownerId == ownerId) { "Army belongs to another player" }
        require(army.units.isNotEmpty()) { "Army has no creatures" }
        require(army.units.size <= GameCatalog.ruleset.maxCreaturesPerArmy) {
            "Army has more than ${GameCatalog.ruleset.maxCreaturesPerArmy} creatures"
        }

        val playerUnits = army.units.map { unit ->
            val creature = creatureRepository.find(unit.creatureId)
                ?: throw IllegalArgumentException("Creature not found: ${unit.creatureId}")
            ArmyPlacement(
                genome = creature.genome,
                stats = creature.stats,
                x = unit.x,
                y = unit.y
            )
        }

        val armyCost = playerUnits.sumOf { it.stats.costBm }
        require(armyCost <= GameCatalog.ruleset.armyBudgetBm) {
            "Army cost $armyCost BM exceeds ${GameCatalog.ruleset.armyBudgetBm} BM"
        }

        val map = GameCatalog.mapsById[request.mapId]
            ?: throw IllegalArgumentException("Unknown map: ${request.mapId}")

        val botUnits = botArmy(playerUnits.size)
        val matchId = UUID.randomUUID().toString()
        val seed = random.nextLong()
        val simulation = Simulation(
            matchId = matchId,
            seed = seed,
            config = SimulationConfig(map = map),
            players = listOf(
                PlayerSetup(playerId = 0, nickname = "Architect", units = playerUnits),
                PlayerSetup(playerId = 1, nickname = "Brood Bot", units = botUnits)
            )
        )
        val session = MatchSession(
            id = matchId,
            ownerId = ownerId,
            opponent = request.opponent,
            simulation = simulation,
            createdAt = Instant.now().toEpochMilli()
        )
        matchRepository.save(session)
        session.start(scope)
        return session
    }

    fun findMatch(matchId: String): MatchSession? = matchRepository.find(matchId)

    fun listMatches(ownerId: String): List<MatchSession> = matchRepository.list(ownerId)

    fun validateArmy(armyId: String, ownerId: String): ArmyView {
        val army = armyRepository.find(armyId)
            ?: throw IllegalArgumentException("Army not found")
        require(army.ownerId == ownerId) { "Army belongs to another player" }
        return armyRepository.toView(army)
    }

    private fun botArmy(playerUnitCount: Int): List<ArmyPlacement> {
        val targetCount = playerUnitCount.coerceIn(1, 3)
        val preferredNames = listOf("Brawler", "Spitter", "Scout")
        return GenomeTemplates.all
            .filter { it.name in preferredNames }
            .take(targetCount)
            .mapNotNull { genome ->
                val analysis = GenomeAnalyzer.analyze(genome)
                if (analysis.valid) {
                    ArmyPlacement(genome = genome, stats = analysis.stats, x = 0, y = 0)
                } else {
                    null
                }
            }
            .ifEmpty {
                val fallback = GenomeTemplates.all.first()
                val analysis = GenomeAnalyzer.analyze(fallback)
                listOf(ArmyPlacement(fallback, analysis.stats, 0, 0))
            }
    }
}
