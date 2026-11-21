package tea.server

import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import tea.model.PasswordAttemptRequest
import tea.model.StoredEvent
import tea.model.VaultAccountsResponse
import tea.model.VaultChallengeResponse
import tea.model.VaultCreateRequest
import tea.model.VaultCreateResponse
import tea.model.VaultDebriefResponse
import tea.model.VaultInputMethod
import tea.model.VaultLoginRequest
import tea.model.VaultLoginResponse
import tea.model.VaultReason
import tea.model.VaultSelfReport
import tea.server.db.VaultAccounts
import tea.server.db.VaultFindings
import tea.server.db.VaultLogins
import tea.server.vault.VaultFingerprint
import tea.server.vault.VaultService

/** Five invented passphrases that pass the checks and contain none of the five site names. */
private val PW = mapOf(
    "catland" to "velvet otter marble",
    "dogland" to "harbour lantern quilt",
    "fishland" to "copper thistle wagon",
    "birdland" to "meadow bicycle plum",
    "bunnyland" to "crimson kettle fern",
)

class VaultTest {

    private suspend fun TestCtx.create(token: String, site: String, pw: String, user: String = "learner") =
        decode<VaultCreateResponse>(post("/me/vault/accounts", token, VaultCreateRequest(site, user, pw)).bodyText())

    private suspend fun TestCtx.login(
        token: String,
        site: String,
        pw: String,
        method: String? = null,
        selfReport: String? = null,
    ) = decode<VaultLoginResponse>(
        post("/me/vault/login", token, VaultLoginRequest(site, pw, method, 2_400, selfReport)).bodyText(),
    )

    private suspend fun TestCtx.createAll(token: String) {
        for ((site, pw) in PW) assertTrue(create(token, site, pw).accepted, "expected $site to be accepted")
    }

    private suspend fun TestCtx.vaultEvents(token: String) =
        decode<List<StoredEvent>>(get("/me/events?type=vault_*", token).bodyText())

    // ------------------------------------------------------------------ refusals

    @Test
    fun refusesShortWeakBreachedAndTheSiteOwnName() = teaTest(pwned = FakePwned.of("correcthorsebattery")) {
        val s = register()
        assertEquals(VaultReason.TOO_SHORT, create(s.token, "catland", "short").reason)
        assertEquals(VaultReason.WEAK, create(s.token, "catland", "aaaaaaaaaa").reason)

        val breached = create(s.token, "catland", "correcthorsebattery")
        assertEquals(VaultReason.BREACHED, breached.reason)
        assertEquals(4242, breached.pwnedCount) // the count is why the debrief can say "seen N times"

        // the site's own name, including through leetspeak
        assertEquals(VaultReason.CONTAINS_SITE, create(s.token, "catland", "my catland words").reason)
        assertEquals(VaultReason.CONTAINS_SITE, create(s.token, "catland", "sly c4t marbles").reason)
        assertEquals(VaultReason.CONTAINS_SITE, create(s.token, "bunnyland", "quiet bunny marble").reason)

        // nothing whatsoever is stored for a refused attempt (plan §1.4)
        services.db.tx {
            assertEquals(0, VaultAccounts.selectAll().count().toInt())
            assertEquals(0, VaultFindings.selectAll().count().toInt())
        }
        // the event carries the reason and nothing about the password
        val rejected = vaultEvents(s.token).filter { it.type == "vault_attempt_rejected" }
        assertEquals(6, rejected.size)
        for (e in rejected) {
            assertEquals(setOf("site_id", "reason"), e.payload.keys)
        }
    }

    @Test
    fun refusesALongPasswordMadeOfTwoCharacters() = teaTest {
        val s = register()
        // nbvcxz scores this a 2 on length alone; the distinct-character floor is what catches it,
        // and it is what keeps the server's verdict and the client's meter saying the same thing.
        assertEquals(VaultReason.WEAK, create(s.token, "catland", "zzzzzxzzzzzz").reason)
        assertEquals(VaultReason.WEAK, create(s.token, "catland", "ababababababab").reason)
        assertTrue(create(s.token, "catland", PW["catland"]!!).accepted)
    }

    @Test
    fun refusesExactReuseAcrossTheirOwnSitesAndNamesTheOne() = teaTest {
        val s = register()
        assertTrue(create(s.token, "catland", PW["catland"]!!).accepted)
        val again = create(s.token, "dogland", PW["catland"]!!)
        assertEquals(VaultReason.REUSED, again.reason)
        assertEquals("catland", again.reusedWith)
        assertNull(again.findings)
    }

    @Test
    fun refusesReuseOfTheThisIsTeaAccountPassword() = teaTest {
        val s = register()
        val accountPw = "tangerine ladder frost"
        assertEquals(
            HttpStatusCode.OK,
            post("/auth/password/attempt", s.token, PasswordAttemptRequest(accountPw)).status,
        )
        val r = create(s.token, "catland", accountPw)
        assertEquals(VaultReason.REUSED, r.reason)
        assertEquals(VaultService.ACCOUNT_SITE, r.reusedWith)
    }

    @Test
    fun oneAccountPerSite() = teaTest {
        val s = register()
        assertTrue(create(s.token, "catland", PW["catland"]!!).accepted)
        assertEquals(VaultReason.ALREADY_EXISTS, create(s.token, "catland", PW["dogland"]!!).reason)
    }

    // ------------------------------------------------------------------ families

    @Test
    fun aFamilyIsAcceptedAndTaught() = teaTest {
        val s = register()
        assertTrue(create(s.token, "catland", "velvet otter marble").accepted)
        // the family people actually build: same stem, a digit on the end
        val second = create(s.token, "dogland", "velvet otter marble2!")
        assertTrue(second.accepted, "a family member is accepted, not walled off")
        assertTrue(second.familyMatch)
        assertEquals("catland", second.familyWith)

        val debrief = decode<VaultDebriefResponse>(get("/me/vault/debrief", s.token).bodyText())
        assertEquals(listOf(listOf("catland", "dogland")), debrief.families)
    }

    @Test
    fun familyNormalisationFoldsDigitsAndLeetspeak() {
        val n = { s: String -> VaultFingerprint.normalise(s.toCharArray()) }
        assertEquals("catland", n("CatLand1"))
        assertEquals("catland", n("catland2!"))
        assertEquals("catland", n("C4tL4nd3"))
        assertEquals("fishland", n("f1shland"))
        // an all-digit password has nothing to trim down to, so it keeps its folded form
        assertEquals("iooooooooo", n("1000000000"))
    }

    // ------------------------------------------------------------------ storage

    @Test
    fun keepsTheVerdictAndNeverThePassword() = teaTest {
        val s = register()
        createAll(s.token)

        services.db.tx {
            assertEquals(5, VaultAccounts.selectAll().count().toInt())
            val stored = mutableListOf<String>()
            VaultAccounts.selectAll().forEach {
                stored += it[VaultAccounts.username]
                stored += it[VaultAccounts.passwordHash]
            }
            VaultFindings.selectAll().forEach {
                stored += it[VaultFindings.reuseFp]
                stored += it[VaultFindings.familyFp]
            }
            for (pw in PW.values) {
                for (cell in stored) {
                    assertFalse(cell.contains(pw), "a stored value contains a password")
                    assertFalse(cell.contains(pw.take(8)), "a stored value contains part of a password")
                }
            }
            // the hash is argon2id, which is what makes the login drill possible without plaintext
            VaultAccounts.selectAll().forEach {
                assertTrue(it[VaultAccounts.passwordHash].startsWith("\$argon2id\$v=19\$m=19456,t=2,p=1\$"))
            }
            // and the verdict outlives the password: this is how a weak one stays penalised
            VaultFindings.selectAll().forEach {
                assertTrue(it[VaultFindings.score] >= VaultService.MIN_SCORE)
                assertTrue(it[VaultFindings.length] >= 10)
                assertFalse(it[VaultFindings.pwned])
            }
        }
    }

    @Test
    fun anUnreachableBreachListIsRecordedNotAssumedClean() = teaTest(pwned = FakePwned(unavailable = true)) {
        val s = register()
        val r = create(s.token, "catland", PW["catland"]!!)
        // the learner is not blocked by our network...
        assertTrue(r.accepted)
        // ...but nothing may imply the password was checked and found clean
        assertEquals(false, r.findings?.breachChecked)
        services.db.tx {
            assertFalse(VaultFindings.selectAll().single()[VaultFindings.breachChecked])
        }
        val about = get("/me/about", s.token).bodyText()
        assertTrue("not checked" in about, "About must say the breach check did not run")
    }

    @Test
    fun aReachableBreachListIsRecordedAsChecked() = teaTest {
        val s = register()
        assertEquals(true, create(s.token, "catland", PW["catland"]!!).findings?.breachChecked)
        assertFalse("not checked" in get("/me/about", s.token).bodyText())
    }

    @Test
    fun debriefIsBuiltFromFindingsOnly() = teaTest {
        val s = register()
        createAll(s.token)
        val body = get("/me/vault/debrief", s.token).bodyText()
        for (pw in PW.values) assertFalse(pw in body, "the debrief body carries a password")
        assertFalse("reuse_fp" in body)
        assertFalse("family_fp" in body)

        val debrief = decode<VaultDebriefResponse>(body)
        assertEquals(5, debrief.rows.size)
        assertEquals(5, debrief.guessOrder.size)
        assertEquals(0, debrief.graduatedCount)
        // weakest first
        val scores = debrief.guessOrder.map { site -> debrief.rows.first { it.siteId == site }.findings.score }
        assertEquals(scores.sorted(), scores)
    }

    // ------------------------------------------------------------------ the login drill

    @Test
    fun challengeIsOneRandomSiteAndOnlyOnceItIsDue() = teaTest {
        val s = register()
        createAll(s.token)

        // nothing is due yet: the gap between making a password and being asked for it is the test
        assertNull(decode<VaultChallengeResponse>(get("/me/vault/challenge", s.token).bodyText()).siteId)

        clock.advance(120.seconds)
        val picks = mutableSetOf<String>()
        repeat(40) {
            decode<VaultChallengeResponse>(get("/me/vault/challenge", s.token).bodyText()).siteId?.let { picks += it }
        }
        assertTrue(picks.size > 1, "the challenged site must vary; got $picks")
        assertTrue(picks.all { it in PW.keys })

        // a graduated site drops out of the pool
        assertTrue(login(s.token, "catland", PW["catland"]!!).ok)
        val after = mutableSetOf<String>()
        repeat(40) {
            decode<VaultChallengeResponse>(get("/me/vault/challenge", s.token).bodyText()).siteId?.let { after += it }
        }
        assertFalse("catland" in after)
    }

    @Test
    fun pasteAndTypingBothSucceedAndOnlyChangeTheFollowUp() = teaTest {
        val s = register()
        createAll(s.token)
        clock.advance(120.seconds)

        val pasted = login(s.token, "catland", PW["catland"]!!, VaultInputMethod.PASTE)
        assertTrue(pasted.ok)
        assertTrue(pasted.graduated)
        assertEquals(VaultService.FOLLOW_UP_MANAGER, pasted.followUp)

        val typed = login(s.token, "dogland", PW["dogland"]!!, VaultInputMethod.TYPED, VaultSelfReport.REMEMBERED)
        assertTrue(typed.ok, "typing is not a wrong answer")
        assertTrue(typed.graduated)
        assertEquals(VaultService.FOLLOW_UP_RECALL, typed.followUp)

        // the signal is recorded, and it is one enum plus one number — never a keystroke stream
        services.db.tx {
            val rows = VaultLogins.selectAll().toList()
            assertEquals(2, rows.size)
            assertEquals(setOf(VaultInputMethod.PASTE, VaultInputMethod.TYPED), rows.mapNotNull { it[VaultLogins.inputMethod] }.toSet())
            assertEquals(listOf(2_400, 2_400), rows.map { it[VaultLogins.timeToFirstInputMs] })
            assertEquals(setOf(null, VaultSelfReport.REMEMBERED), rows.map { it[VaultLogins.selfReport] }.toSet())
        }
        val attempted = vaultEvents(s.token).filter { it.type == "vault_login_attempted" }
        assertEquals(2, attempted.size)
    }

    @Test
    fun threeMissesGiveTheHonestMessageAndNeverAHint() = teaTest {
        val s = register()
        createAll(s.token)
        clock.advance(120.seconds)

        repeat(2) { i ->
            val r = login(s.token, "catland", "wrong guess number $i")
            assertFalse(r.ok)
            assertFalse(r.outOfAttempts)
            assertEquals(i + 1, r.attemptsUsed)
        }
        val third = login(s.token, "catland", "wrong guess again")
        assertFalse(third.ok)
        assertTrue(third.outOfAttempts)
        assertEquals(VaultService.FOLLOW_UP_MISSED, third.followUp)

        // spent: we do not even check the password any more, and we never leak a clue
        val fourth = login(s.token, "catland", PW["catland"]!!)
        assertFalse(fourth.ok)
        assertTrue(fourth.outOfAttempts)
        val body = post("/me/vault/login", s.token, VaultLoginRequest("catland", PW["catland"]!!)).bodyText()
        for (clue in listOf("length", "starts", "character", "velvet")) assertFalse(clue in body, "login leaks $clue")

        // start that one site over, through the same checks
        assertEquals(HttpStatusCode.NoContent, post("/me/vault/catland/reset", s.token, Unit).status)
        assertTrue(create(s.token, "catland", PW["catland"]!!).accepted)
        clock.advance(120.seconds)
        assertTrue(login(s.token, "catland", PW["catland"]!!, VaultInputMethod.AUTOFILL).ok)
    }

    @Test
    fun aSuccessClearsTheAttemptCount() = teaTest {
        val s = register()
        createAll(s.token)
        clock.advance(120.seconds)
        assertFalse(login(s.token, "catland", "not the one").ok)
        assertTrue(login(s.token, "catland", PW["catland"]!!).ok)
        // graduated, so it is no longer challenged; its failed count is behind a success
        assertEquals(0, decode<VaultChallengeResponse>(get("/me/vault/challenge", s.token).bodyText()).attemptsUsed)
    }

    // ------------------------------------------------------------------ transparency and deletion

    @Test
    fun accountsListCarriesNoSecrets() = teaTest {
        val s = register()
        createAll(s.token)
        val body = get("/me/vault/accounts", s.token).bodyText()
        for (pw in PW.values) assertFalse(pw in body)
        assertFalse("password_hash" in body)
        val r = decode<VaultAccountsResponse>(body)
        assertEquals(5, r.accounts.size)
        assertEquals(5, r.sites.size)
        assertTrue(r.sites.all { it.domain.endsWith(".example") }, "fictional domains only")
    }

    @Test
    fun aboutListsTheDrillAndDeleteMeLeavesNothing() = teaTest {
        val s = register()
        createAll(s.token)
        clock.advance(120.seconds)
        login(s.token, "catland", PW["catland"]!!, VaultInputMethod.PASTE)

        val about = get("/me/about", s.token).bodyText()
        assertTrue("Practice accounts" in about)
        assertTrue("Practice passwords" in about)
        assertTrue("Practice logins" in about)
        assertTrue("Not stored." in about)
        for (pw in PW.values) assertFalse(pw in about, "about leaks a password")

        assertEquals(HttpStatusCode.NoContent, client.delete("/me") { bearerAuth(s.token) }.status)
        services.db.tx {
            assertEquals(0, VaultAccounts.selectAll().count().toInt())
            assertEquals(0, VaultFindings.selectAll().count().toInt())
            assertEquals(0, VaultLogins.selectAll().count().toInt())
        }
    }

    @Test
    fun demoResetClearsTheDrill() = teaTest {
        val s = register()
        createAll(s.token)
        assertEquals(HttpStatusCode.NoContent, client.delete("/me/vault") { bearerAuth(s.token) }.status)
        assertEquals(0, decode<VaultAccountsResponse>(get("/me/vault/accounts", s.token).bodyText()).accounts.size)
    }

    @Test
    fun clientsCannotForgeTheServersVaultEvents() = teaTest {
        val s = register()
        val r = client.post("/me/events") {
            bearerAuth(s.token)
            json(
                listOf(
                    tea.model.AppEvent(
                        java.util.UUID.randomUUID().toString(),
                        "vault_account_created",
                        kotlinx.serialization.json.JsonObject(emptyMap()),
                        clock.now(),
                    ),
                ),
            )
        }
        assertEquals(HttpStatusCode.BadRequest, r.status)
    }

    // ------------------------------------------------------------------ frugality

    @Test
    fun breachLookupsAreCachedForAnHour() = teaTest {
        val s = register()
        assertTrue(create(s.token, "catland", PW["catland"]!!).accepted)
        val first = pwned.calls.size
        assertEquals(1, first)
        // the same password on another site is refused for reuse before HIBP is consulted again
        create(s.token, "dogland", PW["catland"]!!)
        // a different password is a different hash, so it does cost one lookup
        assertTrue(create(s.token, "dogland", PW["dogland"]!!).accepted)
        assertEquals(2, pwned.calls.size)
        // and asking about the first one again is free
        runBlocking {
            val sha1 = Crypto.sha1Hex(PW["catland"]!!.toByteArray())
            services.pwned.pwnedCount(sha1)
            services.pwned.pwnedCount(sha1)
        }
        assertEquals(2, pwned.calls.size)
    }
}
