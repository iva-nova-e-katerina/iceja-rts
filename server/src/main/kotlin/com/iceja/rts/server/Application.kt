package com.iceja.rts.server

import com.iceja.rts.server.api.ErrorResponse
import com.iceja.rts.server.api.configureApi
import com.iceja.rts.server.auth.TicketService
import com.iceja.rts.server.match.MatchRepository
import com.iceja.rts.server.match.MatchService
import com.iceja.rts.server.store.ArmyRepository
import com.iceja.rts.server.store.CreatureRepository
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.websocket.WebSockets
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val version: String
)

private const val SERVICE_NAME = "iceja-rts-server"
private const val SERVICE_VERSION = "0.1.0"

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    embeddedServer(Netty, port = port, host = "0.0.0.0") {
        module()
    }.start(wait = true)
}

fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = false
            encodeDefaults = true
            ignoreUnknownKeys = true
        })
    }
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-Player-Id")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
    }
    install(WebSockets)
    install(StatusPages) {
        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("VALIDATION_FAILED", cause.message ?: "Invalid request")
            )
        }
        exception<Exception> { call, cause ->
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL", cause.message ?: "Internal error")
            )
        }
    }

    val creatureRepository = CreatureRepository()
    val armyRepository = ArmyRepository(creatureRepository)
    val matchRepository = MatchRepository()
    val matchService = MatchService(creatureRepository, armyRepository, matchRepository)
    val ticketService = TicketService()

    configureApi(
        creatureRepository = creatureRepository,
        armyRepository = armyRepository,
        matchService = matchService,
        ticketService = ticketService
    )
}
