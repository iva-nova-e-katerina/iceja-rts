package com.iceja.rts.server.match

import com.iceja.rts.server.api.ArmyView
import com.iceja.rts.server.api.CreateMatchRequest
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.sim.ArmyPlacement
import com.iceja.rts.server.sim.ExperienceRules
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
            require(GameCatalog.chassisById[creature.genome.chassisId]?.isStatic != true) {
                "Static chassis is not allowed in duel"
            }
            ArmyPlacement(
                genome = creature.genome,
                stats = ExperienceRules.apply(creature.stats, creature.experience),
                x = unit.x,
                y = unit.y,
                sourceCreatureId = creature.id
            )
        }

        val armyCost = playerUnits.sumOf { it.stats.costBm }
        require(armyCost <= GameCatalog.ruleset.armyBudgetBm) {
            "Army cost $armyCost BM exceeds ${GameCatalog.ruleset.armyBudgetBm} BM"
        }

        val map = GameCatalog.mapsById[request.mapId]
            ?: throw IllegalArgumentException("Unknown map: ${request.mapId}")

        val botUnits = BotArmyBuilder().build(playerUnits)
        val matchId = UUID.randomUUID().toString()
        val seed = random.nextLong()
        val simulation = Simulation(
            matchId = matchId,
            seed = seed,
            config = SimulationConfig(map = map),
            players = listOf(
                PlayerSetup(playerId = 0, nickname = "Архитектор", units = playerUnits),
                PlayerSetup(playerId = 1, nickname = "Бот-улей", units = botUnits)
            )
        )
        val session = MatchSession(
            id = matchId,
            ownerId = ownerId,
            opponent = request.opponent,
            simulation = simulation,
            createdAt = Instant.now().toEpochMilli(),
            onFinished = { finished -> awardExperience(finished) }
        )
        matchRepository.save(session)
        session.start(scope)
        return session
    }

    private fun awardExperience(simulation: Simulation) {
        val awarded = mutableSetOf<String>()
        simulation.livingCreatures(0).forEach { creature ->
            val sourceId = creature.sourceCreatureId
            if (sourceId.isNotBlank() && awarded.add(sourceId)) {
                creatureRepository.addExperience(sourceId, ExperienceRules.gain(creature))
            }
        }
    }

    fun findMatch(matchId: String): MatchSession? = matchRepository.find(matchId)

    fun listMatches(ownerId: String): List<MatchSession> = matchRepository.list(ownerId)

    fun validateArmy(armyId: String, ownerId: String): ArmyView {
        val army = armyRepository.find(armyId)
            ?: throw IllegalArgumentException("Army not found")
        require(army.ownerId == ownerId) { "Army belongs to another player" }
        return armyRepository.toView(army)
    }
}
