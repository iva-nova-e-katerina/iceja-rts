package com.iceja.rts.server.match

import com.iceja.rts.server.api.EventBatchPayload
import com.iceja.rts.server.api.GenomeSummary
import com.iceja.rts.server.api.MatchEndPayload
import com.iceja.rts.server.api.MatchPlayerView
import com.iceja.rts.server.api.MatchStartPayload
import com.iceja.rts.server.api.MatchView
import com.iceja.rts.server.api.ReplayView
import com.iceja.rts.server.api.WsMessage
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.sim.SimEvent
import com.iceja.rts.server.sim.Simulation
import com.iceja.rts.server.sim.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

data class MatchSubscription(
    val id: String,
    val messages: MutableSharedFlow<String>
)

class MatchSession(
    val id: String,
    val ownerId: String,
    val opponent: String,
    val simulation: Simulation,
    val createdAt: Long,
    private val onFinished: (Simulation) -> Unit = {}
) {
    private val json = Json {
        encodeDefaults = true
    }
    private val subscribers = ConcurrentHashMap<String, MutableSharedFlow<String>>()
    private val snapshots = CopyOnWriteArrayList<Snapshot>()
    private val allEvents = CopyOnWriteArrayList<SimEvent>()
    private var started: Boolean = false

    init {
        snapshots += simulation.snapshot()
    }

    fun start(scope: CoroutineScope, tickIntervalMs: Long = 33L) {
        if (started) {
            return
        }
        started = true
        scope.launch {
            delay(500L)
            while (simulation.result == null) {
                simulation.step()
                val tickEvents = simulation.events().toList()
                if (tickEvents.isNotEmpty()) {
                    allEvents += tickEvents
                    broadcast(
                        "event_batch",
                        EventBatchPayload(
                            matchId = id,
                            tick = simulation.tick,
                            events = tickEvents
                        )
                    )
                }
                if (simulation.result != null || simulation.tick % 3 == 0) {
                    val snapshot = simulation.snapshot()
                    snapshots += snapshot
                    broadcast("snapshot", snapshot)
                }
                if (simulation.result != null) {
                    onFinished(simulation)
                    broadcast(
                        "match_end",
                        MatchEndPayload(
                            matchId = id,
                            result = simulation.result ?: return@launch,
                            replayId = id
                        )
                    )
                    break
                }
                delay(tickIntervalMs)
            }
        }
    }

    fun subscribe(): MatchSubscription {
        val subscriberId = UUID.randomUUID().toString()
        val flow = MutableSharedFlow<String>(
            extraBufferCapacity = 128,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        subscribers[subscriberId] = flow
        return MatchSubscription(subscriberId, flow)
    }

    fun unsubscribe(subscription: MatchSubscription) {
        subscribers.remove(subscription.id)
    }

    fun matchStartPayload(): MatchStartPayload = MatchStartPayload(
        matchId = id,
        seed = simulation.seed,
        mapId = simulation.config.map.id,
        rulesetVersion = simulation.genomes().firstOrNull()?.rulesetVersion ?: "1.0.0",
        players = playerViews(),
        genomes = genomeSummaries(),
        snapshot = snapshots.lastOrNull() ?: simulation.snapshot()
    )

    fun matchView(): MatchView = MatchView(
        id = id,
        status = if (simulation.result == null) "LIVE" else "FINISHED",
        ownerId = ownerId,
        opponent = opponent,
        createdAt = createdAt,
        mapId = simulation.config.map.id,
        players = playerViews(),
        result = simulation.result,
        replayId = id
    )

    fun replayView(): ReplayView = ReplayView(
        replayId = id,
        matchId = id,
        mapId = simulation.config.map.id,
        seed = simulation.seed,
        rulesetVersion = simulation.genomes().firstOrNull()?.rulesetVersion ?: "1.0.0",
        genomes = genomeSummaries(),
        players = playerViews(),
        snapshots = snapshots.toList(),
        events = allEvents.toList(),
        result = simulation.result,
        durationTicks = simulation.tick
    )

    fun events(): List<SimEvent> = allEvents.toList()

    private fun playerViews(): List<MatchPlayerView> =
        simulation.players.map { player ->
            MatchPlayerView(
                playerId = player.playerId,
                nickname = player.nickname,
                hiveId = player.playerId + 1
            )
        }

    private fun genomeSummaries(): List<GenomeSummary> =
        simulation.genomes().mapIndexed { index, genome ->
            val analysis = GenomeAnalyzer.analyze(genome)
            GenomeSummary(
                genomeId = index,
                name = genome.name,
                chassisId = genome.chassisId,
                behaviorPresetId = genome.behavior.presetId,
                costBm = analysis.stats.costBm,
                radiusMm = analysis.stats.radiusMm,
                weapons = genome.parts.mapNotNull { placement ->
                    GameCatalog.partsById[placement.partId]
                        ?.takeIf { part -> part.weapon != null }
                        ?.id
                },
                render = GenomeRenderFactory.build(genome, analysis.stats)
            )
        }

    private inline fun <reified T> broadcast(type: String, payload: T) {
        val message = json.encodeToString(
            WsMessage(
                type = type,
                payload = json.encodeToJsonElement(payload)
            )
        )
        subscribers.values.forEach { flow ->
            flow.tryEmit(message)
        }
    }
}
