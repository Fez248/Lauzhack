package tea.server.vault

import java.nio.CharBuffer
import kotlin.random.Random
import kotlin.time.Instant
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import tea.model.EventTypes
import tea.model.VaultAccountSummary
import tea.model.VaultAccountsResponse
import tea.model.VaultChallengeResponse
import tea.model.VaultCreateResponse
import tea.model.VaultDebriefResponse
import tea.model.VaultDebriefRow
import tea.model.VaultFindingsDto
import tea.model.VaultInputMethod
import tea.model.VaultLoginRequest
import tea.model.VaultLoginResponse
import tea.model.VaultReason
import tea.model.VaultSelfReport
import tea.server.Crypto
import tea.server.Services
import tea.server.auth.PasswordHasher
import tea.server.db.Users
import tea.server.db.VaultAccounts
import tea.server.db.VaultFindings
import tea.server.db.VaultLogins
import tea.server.db.emitEvent
import tea.server.db.newId
import tea.server.db.toOdt
import tea.server.password.PasswordAnalyzer

/**
 * The Vault drill (docs/password-drill.md).
 *
 * Five fictional sites; the learner invents a username and password for each one. Every password is
 * graded here, while the plaintext is briefly in memory, and only the **verdict** is kept: an
 * argon2id hash so a later login can be checked, two keyed fingerprints so reuse is answerable, and
 * the findings. The plaintext is never stored, logged or returned, and a refused attempt leaves
 * nothing behind at all (plan §1.4, §17).
 *
 * That is also the answer to "how is a common password penalised if we don't keep it": the breach
 * and strength checks run once, here, and `score` / `pwned` / `pwned_count` outlive the password.
 */
class VaultService(
    private val services: Services,
    private val random: Random = Random.Default,
) {
    private val log = LoggerFactory.getLogger(VaultService::class.java)
    private val fp = VaultFingerprint(
        services.config.passwordFpPepper.ifBlank {
            log.warn("PASSWORD_FP_PEPPER is not set: using a random pepper (reuse detection will not match rows written before a restart)")
            Crypto.newToken()
        },
    )

    /** Three attempts per challenge, then the honest message and the offer to start that site again. */
    private val attemptsAllowed = ATTEMPTS_ALLOWED

    // ---------------------------------------------------------------- read

    suspend fun accounts(userId: String): VaultAccountsResponse = services.db.tx {
        VaultAccountsResponse(
            accounts = VaultAccounts.selectAll()
                .where { VaultAccounts.userId eq userId }
                .orderBy(VaultAccounts.createdAt, SortOrder.ASC)
                .map {
                    VaultAccountSummary(
                        siteId = it[VaultAccounts.siteId],
                        username = it[VaultAccounts.username],
                        createdAt = it[VaultAccounts.createdAt].toInstant().toString(),
                        graduated = it[VaultAccounts.graduatedAt] != null,
                    )
                },
            sites = VaultSites.all,
        )
    }

    /** The debrief reads findings only: no password, no fingerprint ever leaves this class. */
    suspend fun debrief(userId: String): VaultDebriefResponse = services.db.tx {
        val accounts = VaultAccounts.selectAll()
            .where { VaultAccounts.userId eq userId }
            .orderBy(VaultAccounts.createdAt, SortOrder.ASC)
            .map { it[VaultAccounts.id] to it }
        val findings = accounts.mapNotNull { (id, acc) ->
            VaultFindings.selectAll().where { VaultFindings.accountId eq id }.singleOrNull()?.let { acc to it }
        }

        val rows = findings.map { (acc, f) ->
            VaultDebriefRow(
                siteId = acc[VaultAccounts.siteId],
                username = acc[VaultAccounts.username],
                findings = f.toDto(),
            )
        }

        // Families: every pair of this learner's sites sharing a family fingerprint.
        val families = mutableListOf<List<String>>()
        for (i in findings.indices) for (j in i + 1 until findings.size) {
            if (findings[i].second[VaultFindings.familyFp] == findings[j].second[VaultFindings.familyFp]) {
                families += listOf(findings[i].first[VaultAccounts.siteId], findings[j].first[VaultAccounts.siteId])
            }
        }

        VaultDebriefResponse(
            rows = rows,
            families = families,
            // Weakest first: the order a guesser would try them in. Score, then length, then the
            // guessable-facts count — all from findings, never from the password.
            guessOrder = rows.sortedWith(
                compareBy(
                    { it.findings.score },
                    { it.findings.length },
                    { -it.findings.guessableFacts() },
                ),
            ).map { it.siteId },
            graduatedCount = accounts.count { it.second[VaultAccounts.graduatedAt] != null },
        )
    }

    /**
     * Which site to ask about now: **one of the five, picked uniformly at random** among those not
     * yet graduated and old enough to be due. Random on purpose — the learner cannot revise for a
     * specific site, so the only strategy that works is having saved all five.
     */
    suspend fun challenge(userId: String): VaultChallengeResponse = services.db.tx {
        val due = services.clock.now().minus(services.config.vaultChallengeDelay)
        val candidates = VaultAccounts.selectAll()
            .where {
                (VaultAccounts.userId eq userId) and
                    VaultAccounts.graduatedAt.isNull() and
                    (VaultAccounts.createdAt lessEq due.toOdt())
            }
            .toList()
        val pick = candidates.randomOrNull(random) ?: return@tx VaultChallengeResponse()
        VaultChallengeResponse(
            siteId = pick[VaultAccounts.siteId],
            username = pick[VaultAccounts.username],
            attemptsUsed = failedSince(userId, pick[VaultAccounts.siteId]),
            attemptsAllowed = attemptsAllowed,
        )
    }

    // ---------------------------------------------------------------- create

    /**
     * Grades one password and, if it passes, opens the account. [password] is cleared by the caller;
     * nothing about a refused attempt is written anywhere.
     */
    suspend fun create(userId: String, siteId: String, username: String, password: CharArray): VaultCreateResponse {
        require(VaultSites.isValid(siteId)) { "unknown site" }
        val name = username.trim()

        // One read for everything this needs: the account hash, the name and the email local part.
        val who = services.db.tx {
            val user = Users.selectAll().where { Users.id eq userId }.singleOrNull()
            val taken = VaultAccounts.selectAll()
                .where { (VaultAccounts.userId eq userId) and (VaultAccounts.siteId eq siteId) }
                .singleOrNull() != null
            Who(
                accountHash = user?.get(Users.passwordHash),
                firstName = user?.get(Users.firstName),
                emailLocalPart = user?.get(Users.email)?.substringBefore('@'),
                siteTaken = taken,
            )
        }
        if (who.siteTaken) return refuse(userId, siteId, VaultReason.ALREADY_EXISTS)

        // 1. Length: the cheapest check, and the one that actually buys time.
        val length = Character.codePointCount(password, 0, password.size)
        if (length < PasswordAnalyzer.MIN_LENGTH) return refuse(userId, siteId, VaultReason.TOO_SHORT)

        val raw = String(password)
        val folded = PasswordAnalyzer.leet(raw.lowercase())

        // 2. The site's own name inside the password: the first thing anyone guessing it tries.
        if (VaultSites.words(siteId).any { folded.contains(PasswordAnalyzer.leet(it)) }) {
            return refuse(userId, siteId, VaultReason.CONTAINS_SITE)
        }

        // 3. Known breached passwords. Only the first five hex chars of the SHA-1 leave the server.
        val sha1 = sha1Of(password)
        val pwnedCount = services.pwned.pwnedCount(sha1) ?: 0 // unavailable -> not pwned, logged there
        if (pwnedCount > 0) return refuse(userId, siteId, VaultReason.BREACHED, pwnedCount = pwnedCount)

        // 4. Strength, as a guesser sees it rather than as a composition rule.
        val estimate = PasswordAnalyzer.estimate(raw)
        if (estimate.score < MIN_SCORE) return refuse(userId, siteId, VaultReason.WEAK)

        // 5. Reuse, across their own drill sites and their ThisIsTea account password. The account
        //    password is checked against its argon2id hash, so nothing extra is stored for it.
        //    Both argon2id calls run outside any transaction: they are deliberately slow.
        val exactFp = fp.exact(password)
        val familyFp = fp.family(password)
        if (who.accountHash != null && PasswordHasher.verify(password, who.accountHash)) {
            return refuse(userId, siteId, VaultReason.REUSED, reusedWith = ACCOUNT_SITE)
        }
        val clash = services.db.tx {
            var family: String? = null
            for (acc in VaultAccounts.selectAll().where { VaultAccounts.userId eq userId }) {
                val f = VaultFindings.selectAll()
                    .where { VaultFindings.accountId eq acc[VaultAccounts.id] }
                    .singleOrNull() ?: continue
                if (f[VaultFindings.reuseFp] == exactFp) return@tx Clash(acc[VaultAccounts.siteId], exact = true)
                if (family == null && f[VaultFindings.familyFp] == familyFp) family = acc[VaultAccounts.siteId]
            }
            family?.let { Clash(it, exact = false) }
        }
        if (clash != null && clash.exact) {
            return refuse(userId, siteId, VaultReason.REUSED, reusedWith = clash.siteId)
        }

        val findings = VaultFindingsDto(
            length = length,
            classes = PasswordAnalyzer.classes(password),
            score = estimate.score,
            pwned = false,
            pwnedCount = 0,
            containsName = containsWord(folded, who.firstName),
            containsEmail = PasswordAnalyzer.emailTokens(who.emailLocalPart).any { folded.contains(it) },
            containsYear = YEAR.containsMatchIn(raw),
            containsSite = false,
            keyboardPattern = PasswordAnalyzer.rowWalk(raw.lowercase()) ?: if (estimate.spatial) "spatial" else null,
        )
        val hash = PasswordHasher.hash(password)
        val now = services.clock.now()

        services.db.tx {
            val accountId = newId()
            VaultAccounts.insert {
                it[VaultAccounts.id] = accountId
                it[VaultAccounts.userId] = userId
                it[VaultAccounts.siteId] = siteId
                it[VaultAccounts.username] = name
                it[VaultAccounts.passwordHash] = hash
                it[VaultAccounts.createdAt] = now.toOdt()
            }
            VaultFindings.insert {
                it[VaultFindings.accountId] = accountId
                it[VaultFindings.length] = findings.length
                it[VaultFindings.classes] = findings.classes
                it[VaultFindings.score] = findings.score
                it[VaultFindings.pwned] = false
                it[VaultFindings.pwnedCount] = 0
                it[VaultFindings.containsName] = findings.containsName
                it[VaultFindings.containsEmail] = findings.containsEmail
                it[VaultFindings.containsYear] = findings.containsYear
                it[VaultFindings.containsSite] = false
                it[VaultFindings.keyboardPattern] = findings.keyboardPattern
                it[VaultFindings.reuseFp] = exactFp
                it[VaultFindings.familyFp] = familyFp
                it[VaultFindings.analysedAt] = now.toOdt()
            }
            emitEvent(
                userId,
                EventTypes.VAULT_ACCOUNT_CREATED,
                JsonObject(
                    mapOf(
                        "site_id" to JsonPrimitive(siteId),
                        "score" to JsonPrimitive(findings.score),
                        "pwned" to JsonPrimitive(false),
                        "family_match" to JsonPrimitive(clash != null),
                    ),
                ),
                now,
            )
        }
        log.info("vault account created for site {} (score {})", siteId, findings.score)

        return VaultCreateResponse(
            accepted = true,
            findings = findings,
            familyMatch = clash != null,
            familyWith = clash?.siteId,
        )
    }

    // ---------------------------------------------------------------- login

    /** Verifies one drill login against the argon2id hash. No hint is ever derived from the findings. */
    suspend fun login(userId: String, req: VaultLoginRequest, password: CharArray): VaultLoginResponse {
        val siteId = req.siteId
        val account = services.db.tx {
            VaultAccounts.selectAll()
                .where { (VaultAccounts.userId eq userId) and (VaultAccounts.siteId eq siteId) }
                .singleOrNull()
        } ?: return VaultLoginResponse(ok = false, attemptsUsed = 0, attemptsAllowed = attemptsAllowed)

        val used = services.db.tx { failedSince(userId, siteId) }
        if (used >= attemptsAllowed) {
            return VaultLoginResponse(
                ok = false,
                attemptsUsed = used,
                attemptsAllowed = attemptsAllowed,
                outOfAttempts = true,
                followUp = FOLLOW_UP_MISSED,
            )
        }

        val ok = PasswordHasher.verify(password, account[VaultAccounts.passwordHash])
        val attemptNo = used + 1
        val now = services.clock.now()
        val method = req.inputMethod?.takeIf { it in VaultInputMethod.all }
        val selfReport = req.selfReport?.takeIf { it in VaultSelfReport.all }

        services.db.tx {
            VaultLogins.insert {
                it[VaultLogins.id] = newId()
                it[VaultLogins.userId] = userId
                it[VaultLogins.siteId] = siteId
                it[VaultLogins.succeeded] = ok
                it[VaultLogins.attemptNo] = attemptNo
                it[VaultLogins.inputMethod] = method
                it[VaultLogins.timeToFirstInputMs] = req.timeToFirstInputMs?.coerceIn(0, MAX_TIME_MS)
                it[VaultLogins.selfReport] = selfReport
                it[VaultLogins.at] = now.toOdt()
            }
            if (ok && account[VaultAccounts.graduatedAt] == null) {
                VaultAccounts.update({ VaultAccounts.id eq account[VaultAccounts.id] }) {
                    it[VaultAccounts.graduatedAt] = now.toOdt()
                }
            }
            emitEvent(
                userId,
                EventTypes.VAULT_LOGIN_ATTEMPTED,
                JsonObject(
                    mapOf(
                        "site_id" to JsonPrimitive(siteId),
                        "succeeded" to JsonPrimitive(ok),
                        "attempt_no" to JsonPrimitive(attemptNo),
                        "input_method" to (method?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?)),
                    ),
                ),
                now,
            )
        }

        // The signal changes the follow-up line and nothing else: never the verdict, never the XP.
        // It is soft in both directions — a paste can come from a sticky note, and typing can mean a
        // manager open on the phone beside the laptop.
        val followUp = when {
            ok && (method == VaultInputMethod.PASTE || method == VaultInputMethod.AUTOFILL) -> FOLLOW_UP_MANAGER
            ok -> FOLLOW_UP_RECALL
            attemptNo >= attemptsAllowed -> FOLLOW_UP_MISSED
            else -> null
        }
        return VaultLoginResponse(
            ok = ok,
            attemptsUsed = if (ok) used else attemptNo,
            attemptsAllowed = attemptsAllowed,
            outOfAttempts = !ok && attemptNo >= attemptsAllowed,
            graduated = ok,
            followUp = followUp,
        )
    }

    /** Starts one site over after three misses, so the learner can set a password they will save. */
    suspend fun reset(userId: String, siteId: String): Boolean = services.db.tx {
        val account = VaultAccounts.selectAll()
            .where { (VaultAccounts.userId eq userId) and (VaultAccounts.siteId eq siteId) }
            .singleOrNull() ?: return@tx false
        VaultFindings.deleteWhere { VaultFindings.accountId eq account[VaultAccounts.id] }
        VaultLogins.deleteWhere { (VaultLogins.userId eq userId) and (VaultLogins.siteId eq siteId) }
        VaultAccounts.deleteWhere { VaultAccounts.id eq account[VaultAccounts.id] }
        true
    }

    /** Demo reset: clears the whole drill for one learner. */
    suspend fun clear(userId: String) = services.db.tx {
        val ids = VaultAccounts.selectAll().where { VaultAccounts.userId eq userId }.map { it[VaultAccounts.id] }
        for (id in ids) VaultFindings.deleteWhere { VaultFindings.accountId eq id }
        VaultLogins.deleteWhere { VaultLogins.userId eq userId }
        VaultAccounts.deleteWhere { VaultAccounts.userId eq userId }
        Unit
    }

    // ---------------------------------------------------------------- helpers

    private data class Clash(val siteId: String, val exact: Boolean)

    /** Everything about the learner that grading one password needs, read in one go. */
    private data class Who(
        val accountHash: String?,
        val firstName: String?,
        val emailLocalPart: String?,
        val siteTaken: Boolean,
    )

    /**
     * A refusal records the reason and nothing else — no row, no counter keyed to the password, and
     * no fragment of what was typed (plan §1.4).
     */
    private suspend fun refuse(
        userId: String,
        siteId: String,
        reason: String,
        pwnedCount: Int? = null,
        reusedWith: String? = null,
    ): VaultCreateResponse {
        val now = services.clock.now()
        services.db.tx {
            emitEvent(
                userId,
                EventTypes.VAULT_ATTEMPT_REJECTED,
                JsonObject(mapOf("site_id" to JsonPrimitive(siteId), "reason" to JsonPrimitive(reason))),
                now,
            )
        }
        return VaultCreateResponse(accepted = false, reason = reason, pwnedCount = pwnedCount, reusedWith = reusedWith)
    }

    /** Failed attempts since the last success for this site (a success clears the count). */
    private fun failedSince(userId: String, siteId: String): Int {
        val rows = VaultLogins.selectAll()
            .where { (VaultLogins.userId eq userId) and (VaultLogins.siteId eq siteId) }
            .orderBy(VaultLogins.at, SortOrder.DESC)
            .toList()
        var failed = 0
        for (row in rows) {
            if (row[VaultLogins.succeeded]) break
            failed++
        }
        return failed
    }

    companion object {
        const val ATTEMPTS_ALLOWED = 3

        /** `reused_with` value meaning "your ThisIsTea account password", not one of the five. */
        const val ACCOUNT_SITE = "account"

        /** A guesser's view, not a composition rule: nbvcxz 0–4, and 2 is the floor. */
        const val MIN_SCORE = 2
        const val FOLLOW_UP_MANAGER = "manager"
        const val FOLLOW_UP_RECALL = "recall"
        const val FOLLOW_UP_MISSED = "missed"
        private const val MAX_TIME_MS = 10 * 60 * 1000
        private val YEAR = Regex("19\\d\\d|20\\d\\d")

        /** UTF-8 SHA-1 hex of the password; only its first five chars ever leave the server. */
        private fun sha1Of(password: CharArray): String {
            val buf = Charsets.UTF_8.encode(CharBuffer.wrap(password))
            val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
            return try {
                Crypto.sha1Hex(bytes)
            } finally {
                bytes.fill(0)
            }
        }

        private fun containsWord(foldedPassword: String, word: String?): Boolean {
            val w = word?.trim()?.lowercase()?.let(PasswordAnalyzer::leet) ?: return false
            return w.length >= 3 && foldedPassword.contains(w)
        }

        private fun VaultFindingsDto.guessableFacts(): Int =
            listOf(containsName, containsEmail, containsYear, keyboardPattern != null).count { it }

        private fun org.jetbrains.exposed.v1.core.ResultRow.toDto() = VaultFindingsDto(
            length = this[VaultFindings.length],
            classes = this[VaultFindings.classes],
            score = this[VaultFindings.score],
            pwned = this[VaultFindings.pwned],
            pwnedCount = this[VaultFindings.pwnedCount],
            containsName = this[VaultFindings.containsName],
            containsEmail = this[VaultFindings.containsEmail],
            containsYear = this[VaultFindings.containsYear],
            containsSite = this[VaultFindings.containsSite],
            keyboardPattern = this[VaultFindings.keyboardPattern],
        )
    }
}
