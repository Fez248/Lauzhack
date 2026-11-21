package tea.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import tea.model.AboutEntry
import tea.model.AppEvent
import tea.model.Attempt
import tea.model.AttemptsResponse
import tea.model.EventTypes
import tea.model.HealthResponse
import tea.model.LeaderboardResponse
import tea.model.LeaderboardRow
import tea.model.LoginResponse
import tea.model.MasteryState
import tea.model.PasswordAttemptResponse
import tea.model.Profile
import tea.model.ReadinessResponse
import tea.model.RevealResponse
import tea.model.SettingsResponse
import tea.model.SignalsRequest
import tea.model.StepKey
import tea.model.StoredEvent
import tea.model.VaultAccountSummary
import tea.model.VaultAccountsResponse
import tea.model.VaultChallengeResponse
import tea.model.VaultCreateResponse
import tea.model.VaultDebriefResponse
import tea.model.VaultDebriefRow
import tea.model.VaultFindingsDto
import tea.model.VaultLoginRequest
import tea.model.VaultLoginResponse
import tea.model.VaultReason
import tea.model.VaultSite
import tea.model.VerifyResponse

/**
 * In-memory stand-in for the server: idempotent on client ids like the real one, with switches to
 * simulate being offline, refusing a token, or losing the answer after the write happened.
 */
class FakeApi(var token: () -> String? = { null }) : TeaApi {
    val onlineFlow = MutableStateFlow(true)
    override val online: StateFlow<Boolean> = onlineFlow
    override fun startMonitor(scope: CoroutineScope) = Unit

    /** Write happens, then the answer is lost (Offline) for the next N calls. */
    var loseResponses = 0

    /** Every session call answers 401. */
    var rejectToken = false

    val events = linkedMapOf<String, AppEvent>()
    val attempts = linkedMapOf<String, Attempt>()
    val mastery = mutableMapOf<tea.model.Skill, MasteryState>()
    var profile: Profile? = null
    val signals = mutableListOf<SignalsRequest>()
    val calls = mutableListOf<String>()
    var postEventsCalls = 0
    var postAttemptsCalls = 0
    var deleted = false
    var fieldEvents: List<StoredEvent> = emptyList()
    var practice = SettingsResponse()
    val codes = mutableMapOf<String, String>()
    /** The Vault drill, in memory: site id -> the username and the password that was accepted. */
    val vault = linkedMapOf<String, Pair<String, String>>()
    val vaultGraduated = mutableSetOf<String>()
    val vaultLogins = mutableListOf<VaultLoginRequest>()

    /** Which site the next challenge names; null = nothing due. */
    var vaultChallengeSite: String? = null
    var vaultSites: List<VaultSite> = listOf(
        VaultSite("catland", "catLand", "catland.example", "A pet forum. Nothing but posts.", "cat"),
        VaultSite("dogland", "dogLand", "dogland.example", "A shop. A saved card.", "dog"),
        VaultSite("fishland", "fishLand", "fishland.example", "Aquarium supplies. Your home address.", "fish"),
        VaultSite("birdland", "birdLand", "birdland.example", "A birdwatching club. Your real name and photo.", "bird"),
        VaultSite("bunnyland", "bunnyLand", "bunnyland.example", "A newsletter. Your email, nothing else.", "bunny"),
    )

    var passwordAccepted: (String) -> PasswordAttemptResponse = { pw ->
        if (pw.length < 10) PasswordAttemptResponse(false, "too_short") else PasswordAttemptResponse(true)
    }

    private fun <T> net(name: String, needsSession: Boolean, block: () -> ApiResult<T>): ApiResult<T> {
        calls += name
        if (!onlineFlow.value) return ApiResult.Offline
        if (needsSession && (rejectToken || token() == null)) return ApiResult.Err("unauthorized", 401)
        val r = block()
        if (loseResponses > 0) {
            loseResponses--
            return ApiResult.Offline
        }
        return r
    }

    override suspend fun health() = net("health", false) { ApiResult.Ok(HealthResponse(version = "test")) }
    override suspend fun authStart(email: String, language: String) = net("start", false) {
        if (!email.contains('@')) ApiResult.Err("invalid_email", 400) else {
            codes[email] = "123456"; ApiResult.Ok(Unit)
        }
    }

    override suspend fun authVerify(email: String, code: String) = net("verify", false) {
        if (codes[email] == code) ApiResult.Ok(VerifyResponse("tok-$email", "user-1", hasPassword = false))
        else ApiResult.Err("invalid_code", 400)
    }

    override suspend fun passwordAttempt(password: String) = net("password", true) { ApiResult.Ok(passwordAccepted(password)) }
    override suspend fun login(email: String, password: String) = net("login", false) {
        if (password == "right-password") ApiResult.Ok(LoginResponse("tok-login", "user-1")) else ApiResult.Err("invalid_credentials", 401)
    }

    override suspend fun logout() = net("logout", true) { ApiResult.Ok(Unit) }
    override suspend fun getProfile() = net("getProfile", true) { ApiResult.Ok(profile ?: Profile()) }
    override suspend fun putProfile(profile: Profile) = net("putProfile", true) {
        val saved = profile.copy(emailProvider = "other")
        this.profile = saved
        ApiResult.Ok(saved)
    }

    override suspend fun postSignals(request: SignalsRequest) = net("signals", true) { signals += request; ApiResult.Ok(Unit) }
    override suspend fun about() = net("about", true) { ApiResult.Ok(listOf(AboutEntry("Email address", "a@b.example", "To sign you in."))) }
    override suspend fun deleteMe() = net("delete", true) { deleted = true; ApiResult.Ok(Unit) }

    override suspend fun postAttempts(attempts: List<Attempt>) = net("attempts", true) {
        postAttemptsCalls++
        var stored = 0
        attempts.forEach { if (this.attempts.put(it.clientId, it) == null) stored++ }
        ApiResult.Ok(AttemptsResponse(stored, attempts.size - stored))
    }

    override suspend fun getMastery() = net("getMastery", true) { ApiResult.Ok(mastery.values.toList()) }
    override suspend fun putMastery(states: List<MasteryState>) = net("putMastery", true) {
        states.forEach { s -> if ((mastery[s.skill]?.updatedAt ?: kotlin.time.Instant.DISTANT_PAST) <= s.updatedAt) mastery[s.skill] = s }
        ApiResult.Ok(mastery.values.toList())
    }

    override suspend fun postEvents(events: List<AppEvent>) = net("events", true) {
        postEventsCalls++
        if (events.any { it.type.startsWith(EventTypes.FIELD_PREFIX) }) ApiResult.Err("forbidden_event_type", 400)
        else {
            events.forEach { this.events.getOrPut(it.clientId) { it } }
            ApiResult.Ok(Unit)
        }
    }

    override suspend fun getEvents(type: String) = net("getEvents", true) { ApiResult.Ok(fieldEvents) }
    override suspend fun getSettings() = net("getSettings", true) { ApiResult.Ok(practice) }
    override suspend fun putSettings(practiceEmails: String) = net("putSettings", true) {
        practice = practice.copy(practiceEmails = when (practiceEmails) { "pause" -> "paused"; "stop" -> "stopped"; else -> "allowed" })
        ApiResult.Ok(practice)
    }

    override suspend fun leaderboard(period: String) = net("leaderboard", false) {
        ApiResult.Ok(LeaderboardResponse(listOf(LeaderboardRow("Ada L.", 120, 1), LeaderboardRow("Sam K.", 80, 2)), LeaderboardRow("Sam K.", 80, 2)))
    }

    override suspend fun readiness(lessonId: String) = net("readiness", true) { ApiResult.Ok(ReadinessResponse(true)) }
    override suspend fun reportField() = net("report", true) { ApiResult.Ok(Unit) }
    override suspend fun vaultAccounts() = net("vaultAccounts", true) {
        ApiResult.Ok(
            VaultAccountsResponse(
                accounts = vault.map { (site, pair) ->
                    VaultAccountSummary(site, pair.first, "2026-11-21T10:00:00Z", site in vaultGraduated)
                },
                sites = vaultSites,
            ),
        )
    }

    /** Mirrors the server's order of checks closely enough for the screens to be exercised. */
    override suspend fun vaultCreate(siteId: String, username: String, password: String) = net("vaultCreate", true) {
        val words = listOf(siteId, siteId.removeSuffix("land"))
        when {
            siteId in vault -> ApiResult.Ok(VaultCreateResponse(false, VaultReason.ALREADY_EXISTS))
            password.length < 10 -> ApiResult.Ok(VaultCreateResponse(false, VaultReason.TOO_SHORT))
            words.any { it.length >= 3 && password.lowercase().contains(it) } ->
                ApiResult.Ok(VaultCreateResponse(false, VaultReason.CONTAINS_SITE))
            vault.values.any { it.second == password } -> ApiResult.Ok(
                VaultCreateResponse(false, VaultReason.REUSED, reusedWith = vault.entries.first { it.value.second == password }.key),
            )
            else -> {
                val family = vault.entries.firstOrNull {
                    it.value.second.trimEnd { c -> !c.isLetter() } == password.trimEnd { c -> !c.isLetter() }
                }
                vault[siteId] = username to password
                ApiResult.Ok(
                    VaultCreateResponse(
                        accepted = true,
                        findings = VaultFindingsDto(length = password.length, classes = 2, score = 3),
                        familyMatch = family != null,
                        familyWith = family?.key,
                    ),
                )
            }
        }
    }

    override suspend fun vaultDebrief() = net("vaultDebrief", true) {
        ApiResult.Ok(
            VaultDebriefResponse(
                rows = vault.map { (site, pair) ->
                    VaultDebriefRow(site, pair.first, VaultFindingsDto(length = pair.second.length, classes = 2, score = 3))
                },
                families = emptyList(),
                guessOrder = vault.keys.toList(),
                graduatedCount = vaultGraduated.size,
            ),
        )
    }

    override suspend fun vaultChallenge() = net("vaultChallenge", true) {
        val site = vaultChallengeSite?.takeIf { it in vault && it !in vaultGraduated }
        ApiResult.Ok(
            if (site == null) VaultChallengeResponse()
            else VaultChallengeResponse(site, vault.getValue(site).first, vaultLogins.count { it.siteId == site }, 3),
        )
    }

    override suspend fun vaultLogin(request: VaultLoginRequest) = net("vaultLogin", true) {
        vaultLogins += request
        val ok = vault[request.siteId]?.second == request.password
        if (ok) vaultGraduated += request.siteId
        val used = vaultLogins.count { it.siteId == request.siteId && !ok }
        ApiResult.Ok(
            VaultLoginResponse(
                ok = ok,
                attemptsUsed = used,
                outOfAttempts = !ok && used >= 3,
                graduated = ok,
                followUp = when {
                    ok && request.inputMethod in setOf("paste", "autofill") -> "manager"
                    ok -> "recall"
                    used >= 3 -> "missed"
                    else -> null
                },
            ),
        )
    }

    override suspend fun vaultReset(siteId: String) = net("vaultReset", true) {
        vault.remove(siteId)
        vaultGraduated -= siteId
        vaultLogins.removeAll { it.siteId == siteId }
        ApiResult.Ok(Unit)
    }

    override suspend fun vaultClear() = net("vaultClear", true) {
        vault.clear(); vaultGraduated.clear(); vaultLogins.clear()
        ApiResult.Ok(Unit)
    }

    override suspend fun reveal(token: String) = net("reveal", false) {
        if (token == "missing") ApiResult.Err("not_found", 404)
        else ApiResult.Ok(RevealResponse("practice-email-01", mapOf(StepKey.WHO to "A sender you did not expect."), listOf("The link went somewhere else.")))
    }
}
