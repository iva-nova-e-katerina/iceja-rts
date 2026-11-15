package com.iceja.rts.server.api

import com.iceja.rts.server.genome.GenomeTemplates
import com.iceja.rts.server.genome.ValidationResult
import com.iceja.rts.server.module
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiTest {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @Test
    fun healthCatalogAndGuestAreAvailable() = testApplication {
        application { module() }

        val health = client.get("/api/v1/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.bodyAsText().contains("iceja-rts-server"))

        val catalog = client.get("/api/v1/catalog")
        assertEquals(HttpStatusCode.OK, catalog.status)
        val catalogBody = catalog.bodyAsText()
        assertTrue(catalogBody.contains("GANGLION"))
        assertTrue(catalogBody.contains("BERSERK"))

        val guest = client.post("/api/v1/auth/guest")
        assertEquals(HttpStatusCode.OK, guest.status)
        val session = json.decodeFromString<GuestSession>(guest.bodyAsText())
        assertTrue(session.playerId.startsWith("guest-"))
    }

    @Test
    fun creatureArmyAndMatchCanBeCreated() = testApplication {
        application { module() }
        val playerId = "test-player"
        val genome = GenomeTemplates.all.first { it.name == "Scout" }

        val validateResponse = client.post("/api/v1/creatures/validate") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ValidateGenomeRequest(genome)))
        }
        assertEquals(HttpStatusCode.OK, validateResponse.status)
        val validation = json.decodeFromString<ValidationResult>(validateResponse.bodyAsText())
        assertTrue(validation.valid)

        val createResponse = client.post("/api/v1/creatures") {
            header("X-Player-Id", playerId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateCreatureRequest(genome)))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)
        val creature = json.decodeFromString<CreatureView>(createResponse.bodyAsText())
        assertTrue(creature.stats.costBm > 0)

        val armyResponse = client.post("/api/v1/armies") {
            header("X-Player-Id", playerId)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateArmyRequest(
                        name = "Test swarm",
                        units = listOf(ArmyUnitInput(creatureId = creature.id))
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, armyResponse.status)
        val army = json.decodeFromString<ArmyView>(armyResponse.bodyAsText())
        assertEquals(1, army.creatureCount)

        val matchResponse = client.post("/api/v1/matches") {
            header("X-Player-Id", playerId)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateMatchRequest(armyId = army.id)))
        }
        assertEquals(HttpStatusCode.Created, matchResponse.status)
        val match = json.decodeFromString<MatchView>(matchResponse.bodyAsText())
        assertEquals("LIVE", match.status)
        assertTrue(match.replayId.isNotBlank())
    }

    @Test
    fun invalidCreatureIsRejected() = testApplication {
        application { module() }
        val invalid = GenomeTemplates.all.first().copy(
            name = "Invalid",
            parts = GenomeTemplates.all.first().parts.filterNot { it.partId == "GANGLION" }
        )
        val response = client.post("/api/v1/creatures") {
            header("X-Player-Id", "test-player")
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateCreatureRequest(invalid)))
        }
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertTrue(response.bodyAsText().contains("NO_BRAIN"))
    }

}
