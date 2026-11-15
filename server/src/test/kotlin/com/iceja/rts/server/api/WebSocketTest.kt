package com.iceja.rts.server.api

import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.module
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertTrue

class WebSocketTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun matchStartIsDeliveredOverWebSocket() = testApplication {
        application { module() }
        val playerId = "ws-test"
        val genome = GenomeTemplates.all.first { it.name == "Scout" }

        val creatureResponse = client.post("/api/v1/creatures") {
            header("X-Player-Id", playerId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateCreatureRequest(genome)))
        }
        val creature = json.decodeFromString<CreatureView>(creatureResponse.bodyAsText())

        val armyResponse = client.post("/api/v1/armies") {
            header("X-Player-Id", playerId)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateArmyRequest(
                        name = "WebSocket army",
                        units = listOf(ArmyUnitInput(creatureId = creature.id))
                    )
                )
            )
        }
        val army = json.decodeFromString<ArmyView>(armyResponse.bodyAsText())

        val matchResponse = client.post("/api/v1/matches") {
            header("X-Player-Id", playerId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateMatchRequest(armyId = army.id)))
        }
        val match = json.decodeFromString<MatchView>(matchResponse.bodyAsText())

        val ticketResponse = client.post("/api/v1/ws/ticket") {
            header("X-Player-Id", playerId)
        }
        val ticket = json.decodeFromString<TicketResponse>(ticketResponse.bodyAsText())

        val websocketClient = createClient {
            install(WebSockets)
        }
        websocketClient.webSocket("/ws/matches/${match.id}?ticket=${ticket.ticket}") {
            val frame = withTimeout(5_000) { incoming.receive() }
            assertTrue(frame is Frame.Text)
            val text = (frame as Frame.Text).readText()
            assertTrue(text.contains("match_start"))
            assertTrue(text.contains(match.id))
        }
    }
}
