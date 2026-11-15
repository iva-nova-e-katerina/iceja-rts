package com.iceja.rts.server.api

import com.iceja.rts.server.HealthResponse
import com.iceja.rts.server.auth.TicketService
import com.iceja.rts.server.catalog.GameCatalog
import com.iceja.rts.server.genome.GenomeAnalyzer
import com.iceja.rts.server.genome.PresetGraphs
import com.iceja.rts.server.genome.UpgradeRules
import com.iceja.rts.server.match.MatchService
import com.iceja.rts.server.store.ArmyRepository
import com.iceja.rts.server.store.CreatureRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import io.ktor.websocket.close
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val apiJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

private val guestNicknames = ConcurrentHashMap<String, String>()

fun Application.configureApi(
    creatureRepository: CreatureRepository,
    armyRepository: ArmyRepository,
    matchService: MatchService,
    ticketService: TicketService
) {
    routing {
        get("/") {
            call.respondText("ICEJA-RTS server")
        }
        get("/health") {
            call.respond(HealthResponse("ok", "iceja-rts-server", "0.1.0"))
        }
        route("/api/v1") {
            get("/health") {
                call.respond(HealthResponse("ok", "iceja-rts-server", "0.1.0"))
            }

            post("/auth/guest") {
                val playerId = "guest-${UUID.randomUUID()}"
                val nickname = "Архитектор-${playerId.takeLast(4)}"
                guestNicknames[playerId] = nickname
                call.respond(GuestSession(playerId, nickname))
            }

            get("/catalog") {
                call.respond(catalogResponse())
            }
            get("/catalog/version") {
                call.respond(
                    mapOf(
                        "catalogVersion" to GameCatalog.CATALOG_VERSION,
                        "rulesetVersion" to GameCatalog.ruleset.version
                    )
                )
            }
            get("/rulesets/current") {
                call.respond(GameCatalog.ruleset)
            }
            get("/maps") {
                call.respond(GameCatalog.maps)
            }

            post("/creatures/validate") {
                val request = call.receive<ValidateGenomeRequest>()
                call.respond(GenomeAnalyzer.analyze(request.genome).toValidationResult())
            }
            post("/creatures") {
                val request = call.receive<CreateCreatureRequest>()
                val analysis = GenomeAnalyzer.analyze(request.genome)
                if (!analysis.valid) {
                    call.respond(HttpStatusCode.UnprocessableEntity, analysis.toValidationResult())
                    return@post
                }
                val ownerId = playerId(call)
                val record = creatureRepository.create(ownerId, request.genome, analysis.toValidationResult())
                call.respond(HttpStatusCode.Created, record.toView())
            }
            get("/creatures") {
                val ownerId = playerId(call)
                call.respond(creatureRepository.list(ownerId).map { it.toView() })
            }
            get("/creatures/{id}") {
                val creatureId = call.parameters["id"]
                if (creatureId == null) {
                    call.respond(HttpStatusCode.BadRequest, error("VALIDATION_FAILED", "Creature id is required"))
                    return@get
                }
                val record = creatureRepository.find(creatureId)
                if (record == null) {
                    call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Creature not found"))
                } else {
                    call.respond(record.toView())
                }
            }
            delete("/creatures/{id}") {
                call.respond(HttpStatusCode.NoContent)
            }

            post("/armies") {
                val request = call.receive<CreateArmyRequest>()
                val ownerId = playerId(call)
                val validationError = validateArmyRequest(request, ownerId, creatureRepository)
                if (validationError != null) {
                    call.respond(HttpStatusCode.UnprocessableEntity, validationError)
                    return@post
                }
                val army = armyRepository.create(ownerId, request.name, request.units)
                call.respond(HttpStatusCode.Created, armyRepository.toView(army))
            }
            get("/armies") {
                val ownerId = playerId(call)
                call.respond(armyRepository.list(ownerId).map { armyRepository.toView(it) })
            }
            post("/armies/{id}/validate") {
                val armyId = call.parameters["id"]
                if (armyId == null) {
                    call.respond(HttpStatusCode.BadRequest, error("VALIDATION_FAILED", "Army id is required"))
                    return@post
                }
                val ownerId = playerId(call)
                val army = armyRepository.find(armyId)
                if (army == null) {
                    call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Army not found"))
                    return@post
                }
                if (army.ownerId != ownerId) {
                    call.respond(HttpStatusCode.Forbidden, error("FORBIDDEN", "Army belongs to another player"))
                    return@post
                }
                call.respond(armyRepository.toView(army))
            }

            post("/matches") {
                val request = call.receive<CreateMatchRequest>()
                val ownerId = playerId(call)
                val session = try {
                    matchService.createMatch(ownerId, request)
                } catch (exception: IllegalArgumentException) {
                    call.respond(
                        HttpStatusCode.UnprocessableEntity,
                        error("MATCH_INVALID", exception.message ?: "Match cannot be created")
                    )
                    return@post
                }
                call.respond(HttpStatusCode.Created, session.matchView())
            }
            get("/matches") {
                val ownerId = playerId(call)
                call.respond(matchService.listMatches(ownerId).map { it.matchView() })
            }
            get("/matches/{id}") {
                val matchId = call.parameters["id"]
                val session = matchId?.let { matchService.findMatch(it) }
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, error("MATCH_NOT_FOUND", "Match not found"))
                } else {
                    call.respond(session.matchView())
                }
            }
            get("/matches/{id}/result") {
                val matchId = call.parameters["id"]
                val session = matchId?.let { matchService.findMatch(it) }
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, error("MATCH_NOT_FOUND", "Match not found"))
                    return@get
                }
                val result = session.simulation.result
                if (result == null) {
                    call.respond(HttpStatusCode.Conflict, error("MATCH_ACTIVE", "Match is still running"))
                } else {
                    call.respond(result)
                }
            }
            get("/replays/{id}") {
                val replayId = call.parameters["id"]
                val session = replayId?.let { matchService.findMatch(it) }
                if (session == null) {
                    call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", "Replay not found"))
                } else {
                    call.respond(session.replayView())
                }
            }
            post("/ws/ticket") {
                call.respond(ticketService.issue())
            }
        }

        webSocket("/ws/matches/{matchId}") {
            val matchId = call.parameters["matchId"]
            val ticket = call.request.queryParameters["ticket"]
            if (matchId == null || ticket == null || !ticketService.consume(ticket)) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Invalid ticket"))
                return@webSocket
            }
            val session = matchService.findMatch(matchId)
            if (session == null) {
                close(CloseReason(CloseReason.Codes.NORMAL, "Match not found"))
                return@webSocket
            }

            val startMessage = try {
                apiJson.encodeToString(
                    WsMessage(
                        type = "match_start",
                        payload = apiJson.encodeToJsonElement(session.matchStartPayload())
                    )
                )
            } catch (exception: Throwable) {
                close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Cannot build match start"))
                return@webSocket
            }
            send(Frame.Text(startMessage))

            val subscription = session.subscribe()
            try {
                val sender = launch {
                    subscription.messages.collect { message ->
                        send(Frame.Text(message))
                    }
                }
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        val message = runCatching {
                            apiJson.decodeFromString<WsMessage>(text)
                        }.getOrNull()
                        when (message?.type) {
                            "ping" -> send(Frame.Text("""{"type":"pong"}"""))
                            "move", "attack", "spawn", "use", "command" -> {
                                send(
                                    Frame.Text(
                                        apiJson.encodeToString(
                                            WsMessage(
                                                type = "error",
                                                payload = apiJson.encodeToJsonElement(
                                                    error(
                                                        "COMMAND_NOT_ALLOWED",
                                                        "Live match commands are not allowed"
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
                sender.cancel()
            } finally {
                session.unsubscribe(subscription)
            }
        }
    }
}

private fun catalogResponse(): CatalogResponse {
    val templates = com.iceja.rts.server.genome.GenomeTemplates.all.mapIndexed { index, genome ->
        val analysis = GenomeAnalyzer.analyze(genome)
        TemplateView(
            id = index.toString(),
            name = genome.name,
            genome = genome,
            validation = analysis.toValidationResult()
        )
    }
    return CatalogResponse(
        catalogVersion = GameCatalog.CATALOG_VERSION,
        ruleset = GameCatalog.ruleset,
        chassis = GameCatalog.chassis,
        parts = GameCatalog.parts,
        behaviorPresets = GameCatalog.behaviorPresets,
        maps = GameCatalog.maps,
        templates = templates,
        upgrades = UpgradeRules.all().map { rule ->
            UpgradeRuleView(
                stat = rule.stat,
                basePrice = rule.basePrice,
                growthPercent = rule.growthPercent,
                maxLevel = rule.maxLevel
            )
        },
        behaviorGraphs = PresetGraphs.all.map { preset ->
            PresetGraphView(
                presetId = preset.presetId,
                graph = preset.graph
            )
        }
    )
}

private fun validateArmyRequest(
    request: CreateArmyRequest,
    ownerId: String,
    creatureRepository: CreatureRepository
): ErrorResponse? {
    if (request.name.isBlank()) {
        return error("ARMY_INVALID", "Army name cannot be blank")
    }
    if (request.units.isEmpty()) {
        return error("ARMY_INVALID", "Army must contain at least one creature")
    }
    if (request.units.size > GameCatalog.ruleset.maxCreaturesPerArmy) {
        return error(
            "ARMY_INVALID",
            "Army cannot contain more than ${GameCatalog.ruleset.maxCreaturesPerArmy} creatures"
        )
    }
    var totalCost = 0
    request.units.forEach { unit ->
        val creature = creatureRepository.find(unit.creatureId)
            ?: return error("ARMY_INVALID", "Creature not found: ${unit.creatureId}")
        if (creature.ownerId != ownerId) {
            return error("ARMY_INVALID", "Creature belongs to another player")
        }
        totalCost += creature.stats.costBm
        if (unit.x < 0 || unit.y < 0 || unit.x > 128_000 || unit.y > 128_000) {
            return error("ARMY_INVALID", "Creature position is outside the map")
        }
    }
    if (totalCost > GameCatalog.ruleset.armyBudgetBm) {
        return error(
            "ARMY_INVALID",
            "Army cost $totalCost BM exceeds ${GameCatalog.ruleset.armyBudgetBm} BM"
        )
    }
    return null
}

private fun playerId(call: io.ktor.server.application.ApplicationCall): String =
    call.request.header("X-Player-Id") ?: "local"

private fun error(code: String, message: String): ErrorResponse =
    ErrorResponse(code, message)
