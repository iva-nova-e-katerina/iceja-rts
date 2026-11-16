package com.iceja.rts.server.api

import com.iceja.rts.server.catalog.BehaviorPresetDefinition
import com.iceja.rts.server.catalog.ChassisDefinition
import com.iceja.rts.server.catalog.MapDefinition
import com.iceja.rts.server.catalog.PartDefinition
import com.iceja.rts.server.catalog.PartShape
import com.iceja.rts.server.catalog.Ruleset
import com.iceja.rts.server.catalog.SocketType
import com.iceja.rts.server.genome.DerivedStats
import com.iceja.rts.server.genome.Genome
import com.iceja.rts.server.genome.BehaviorGraph
import com.iceja.rts.server.genome.UpgradeStat
import com.iceja.rts.server.genome.ValidationResult
import com.iceja.rts.server.sim.MatchResult
import com.iceja.rts.server.sim.SimEvent
import com.iceja.rts.server.sim.Snapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class GuestSession(
    val playerId: String,
    val nickname: String
)

@Serializable
data class TemplateView(
    val id: String,
    val name: String,
    val genome: Genome,
    val validation: ValidationResult
)

@Serializable
data class UpgradeRuleView(
    val stat: UpgradeStat,
    val basePrice: Int,
    val growthPercent: Int,
    val maxLevel: Int
)

@Serializable
data class PresetGraphView(
    val presetId: String,
    val graph: BehaviorGraph
)

@Serializable
data class CatalogResponse(
    val catalogVersion: String,
    val ruleset: Ruleset,
    val chassis: List<ChassisDefinition>,
    val parts: List<PartDefinition>,
    val behaviorPresets: List<BehaviorPresetDefinition>,
    val maps: List<MapDefinition>,
    val templates: List<TemplateView>,
    val upgrades: List<UpgradeRuleView>,
    val behaviorGraphs: List<PresetGraphView>
)

@Serializable
data class ValidateGenomeRequest(
    val genome: Genome
)

@Serializable
data class CreateCreatureRequest(
    val genome: Genome
)

@Serializable
data class CreateCreatureFromTemplateRequest(
    val templateName: String,
    val name: String = ""
)

@Serializable
data class CreatureView(
    val id: String,
    val genome: Genome,
    val stats: DerivedStats,
    val validation: ValidationResult,
    val version: Int,
    val createdAt: Long,
    val experience: Int = 0,
    val isTemplate: Boolean = false
)

@Serializable
data class ArmyUnitInput(
    val creatureId: String,
    val x: Int = 0,
    val y: Int = 0
)

@Serializable
data class CreateArmyRequest(
    val name: String,
    val units: List<ArmyUnitInput>,
    val hiveX: Int = 0,
    val hiveY: Int = 0
)

@Serializable
data class ArmyUnitView(
    val creatureId: String,
    val name: String,
    val costBm: Int,
    val x: Int,
    val y: Int
)

@Serializable
data class ArmyView(
    val id: String,
    val name: String,
    val units: List<ArmyUnitView>,
    val totalCostBm: Int,
    val creatureCount: Int,
    val hiveX: Int = 0,
    val hiveY: Int = 0
)

@Serializable
data class MoveOrderRequest(
    val entityId: Int,
    val x: Int,
    val y: Int
)

@Serializable
data class MoveOrderResponse(
    val applied: Boolean
)

@Serializable
data class WhipRequest(
    val entityId: Int
)

@Serializable
data class WhipResponse(
    val applied: Boolean
)

@Serializable
data class CreateMatchRequest(
    val armyId: String,
    val mapId: String = "duel-arena",
    val opponent: String = "bot"
)

@Serializable
data class MatchPlayerView(
    val playerId: Int,
    val nickname: String,
    val hiveId: Int
)

@Serializable
data class CreatureRenderPart(
    val partId: String,
    val socketId: SocketType,
    val shape: PartShape,
    val scale: Int,
    val mirrored: Boolean
)

@Serializable
data class CreatureRenderView(
    val chassisId: String,
    val sizeClass: String,
    val bodyRadiusMm: Int,
    val parts: List<CreatureRenderPart>
)

@Serializable
data class GenomeSummary(
    val genomeId: Int,
    val name: String,
    val chassisId: String,
    val behaviorPresetId: String,
    val costBm: Int,
    val radiusMm: Int,
    val weapons: List<String>,
    val render: CreatureRenderView
)

@Serializable
data class MatchStartPayload(
    val matchId: String,
    val seed: Long,
    val mapId: String,
    val rulesetVersion: String,
    val players: List<MatchPlayerView>,
    val genomes: List<GenomeSummary>,
    val snapshot: Snapshot
)

@Serializable
data class EventBatchPayload(
    val matchId: String,
    val tick: Int,
    val events: List<SimEvent>
)

@Serializable
data class MatchEndPayload(
    val matchId: String,
    val result: MatchResult,
    val replayId: String
)

@Serializable
data class MatchView(
    val id: String,
    val status: String,
    val ownerId: String,
    val opponent: String,
    val createdAt: Long,
    val mapId: String,
    val players: List<MatchPlayerView>,
    val result: MatchResult? = null,
    val replayId: String
)

@Serializable
data class ReplayView(
    val replayId: String,
    val matchId: String,
    val mapId: String,
    val seed: Long,
    val rulesetVersion: String,
    val genomes: List<GenomeSummary>,
    val players: List<MatchPlayerView>,
    val snapshots: List<Snapshot>,
    val events: List<SimEvent>,
    val result: MatchResult?,
    val durationTicks: Int
)

@Serializable
data class TicketResponse(
    val ticket: String,
    val expiresIn: Int
)

@Serializable
data class WsMessage(
    val type: String,
    val payload: JsonElement? = null
)
