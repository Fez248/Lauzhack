package tea.server.vault

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import tea.model.VaultCreateRequest
import tea.model.VaultLoginRequest
import tea.server.auth.requireUser
import tea.server.badRequest
import tea.server.services

/**
 * Endpoints of the Vault drill (docs/password-drill-prompt.md).
 *
 * Every one of these takes the session's own user: a learner can only ever see and change their own
 * five accounts. The password fields are turned into a CharArray and cleared in a `finally`, the
 * same way `POST /auth/password/attempt` does it; the JSON string itself is immutable and lives
 * until it is collected, which is the one copy no server-side code can shorten.
 */
private const val USERNAME_MAX = 64

fun Route.vaultRoutes() = route("/me/vault") {

    /** The learner's accounts so far, plus the five sites. Never a secret, never a fingerprint. */
    get("/accounts") {
        val session = call.requireUser()
        call.respond(call.services.vault.accounts(session.userId))
    }

    /** Grades one password and opens the account if it passes. A refusal stores nothing. */
    post("/accounts") {
        val s = call.services
        val session = call.requireUser()
        val req = call.receive<VaultCreateRequest>()
        if (!VaultSites.isValid(req.siteId)) throw badRequest("invalid_site", "Unknown site.")
        val name = req.username.trim()
        if (name.isEmpty() || name.length > USERNAME_MAX) {
            throw badRequest("invalid_username", "Pick a username of 1 to $USERNAME_MAX characters.")
        }
        val chars = req.password.toCharArray()
        try {
            call.respond(s.vault.create(session.userId, req.siteId, name, chars))
        } finally {
            chars.fill('\u0000')
        }
    }

    /** The five findings side by side, the families between them, and the guesser's order. */
    get("/debrief") {
        val session = call.requireUser()
        call.respond(call.services.vault.debrief(session.userId))
    }

    /** Which site to log back in to now: one of the five at random, or none if nothing is due. */
    get("/challenge") {
        val session = call.requireUser()
        call.respond(call.services.vault.challenge(session.userId))
    }

    /** One drill login, verified against the argon2id hash. No hint is ever derived from findings. */
    post("/login") {
        val s = call.services
        val session = call.requireUser()
        val req = call.receive<VaultLoginRequest>()
        if (!VaultSites.isValid(req.siteId)) throw badRequest("invalid_site", "Unknown site.")
        val chars = req.password.toCharArray()
        try {
            call.respond(s.vault.login(session.userId, req, chars))
        } finally {
            chars.fill('\u0000')
        }
    }

    /** After three misses: start that one site again, through the same checks. */
    post("/{siteId}/reset") {
        val session = call.requireUser()
        val siteId = call.parameters["siteId"].orEmpty()
        if (!VaultSites.isValid(siteId)) throw badRequest("invalid_site", "Unknown site.")
        call.services.vault.reset(session.userId, siteId)
        call.respond(HttpStatusCode.NoContent)
    }

    /** Demo reset: clears the learner's whole drill. */
    delete {
        val session = call.requireUser()
        call.services.vault.clear(session.userId)
        call.respond(HttpStatusCode.NoContent)
    }
}
