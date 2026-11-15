package com.iceja.rts.server.auth

import com.iceja.rts.server.api.TicketResponse
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TicketService {
    private val random = SecureRandom()
    private val tickets = ConcurrentHashMap<String, Long>()

    fun issue(): TicketResponse {
        val ticket = "wt_${UUID.randomUUID()}-${random.nextInt(Int.MAX_VALUE)}"
        tickets[ticket] = System.currentTimeMillis() + 30_000L
        return TicketResponse(ticket = ticket, expiresIn = 30)
    }

    fun consume(ticket: String): Boolean {
        val expiresAt = tickets.remove(ticket) ?: return false
        return expiresAt >= System.currentTimeMillis()
    }
}
