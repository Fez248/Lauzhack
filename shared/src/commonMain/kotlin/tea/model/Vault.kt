package tea.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The Vault drill (docs/password-drill.md): five fictional sites where the learner invents one
 * username and password each, then logs back in to a random one in a later session.
 *
 * Nothing here carries a password except the request bodies, which are never stored: the server
 * keeps an argon2id hash, two keyed fingerprints and the findings. See docs/password-drill-prompt.md.
 */

/**
 * One of the five fictional sites. Static content, bundled; `.example` domains only (plan §17).
 *
 * Four of them are pages belonging to the cast, so each sign-up wears that persona's own colours and
 * avatar; the Zoo Card Club belongs to nobody, and the UI gives it its own colour and a letter badge.
 */
@Serializable
data class VaultSite(
    @SerialName("site_id") val siteId: String,
    @SerialName("shown_as") val shownAs: String,
    val domain: String,
    /** What this site holds, for the tiering question in the debrief. */
    val holds: String,
    /** Whose page this is; null for the Zoo Card Club, which has no persona. */
    val persona: Persona? = null,
    /**
     * Words a password for this site must not contain. Short ones (the animal) match whole words
     * only, so "scatter" is not refused for Cat Land; the rest match anywhere.
     */
    val words: List<String> = emptyList(),
)

/** How the password reached the field. Disclosed on the card before the learner types. */
object VaultInputMethod {
    const val PASTE = "paste"
    const val AUTOFILL = "autofill"
    const val TYPED = "typed"
    val all = setOf(PASTE, AUTOFILL, TYPED)
}

/** The learner's own answer, asked once after a login attempt. */
object VaultSelfReport {
    const val LOOKED_IT_UP = "looked_it_up"
    const val REMEMBERED = "remembered"
    const val WRITTEN_DOWN = "written_down"
    val all = setOf(LOOKED_IT_UP, REMEMBERED, WRITTEN_DOWN)
}

/** Why a password was refused. The UI maps these to copy; the server never explains in prose. */
object VaultReason {
    const val TOO_SHORT = "too_short"
    const val WEAK = "weak"
    const val BREACHED = "breached"
    const val CONTAINS_SITE = "contains_site"
    const val REUSED = "reused"
    const val USERNAME_TAKEN = "username_taken"
    const val ALREADY_EXISTS = "already_exists"
}

@Serializable
data class VaultCreateRequest(
    @SerialName("site_id") val siteId: String,
    val username: String,
    val password: String,
)

/**
 * findings is present only when accepted. `reused_with` names the site whose password this matches,
 * so the UI can say which one — never the password itself.
 */
@Serializable
data class VaultCreateResponse(
    val accepted: Boolean,
    val reason: String? = null,
    @SerialName("pwned_count") val pwnedCount: Int? = null,
    @SerialName("reused_with") val reusedWith: String? = null,
    val findings: VaultFindingsDto? = null,
    /** True when this password is a near-variant of another of theirs. Accepted, but taught. */
    @SerialName("family_match") val familyMatch: Boolean = false,
    @SerialName("family_with") val familyWith: String? = null,
)

/** What we keep about an accepted drill password. Never the password. */
@Serializable
data class VaultFindingsDto(
    val length: Int,
    val classes: Int,
    val score: Int,
    val pwned: Boolean = false,
    @SerialName("pwned_count") val pwnedCount: Int = 0,
    @SerialName("contains_name") val containsName: Boolean = false,
    @SerialName("contains_email") val containsEmail: Boolean = false,
    @SerialName("contains_year") val containsYear: Boolean = false,
    @SerialName("contains_site") val containsSite: Boolean = false,
    @SerialName("keyboard_pattern") val keyboardPattern: String? = null,
    /**
     * False when HIBP could not be reached, so `pwned = false` means "not checked", not "not
     * breached". Without this the learner is told nothing and quietly keeps a breached password.
     */
    @SerialName("breach_checked") val breachChecked: Boolean = true,
)

/** One row of the drill list: no secrets, no fingerprints. */
@Serializable
data class VaultAccountSummary(
    @SerialName("site_id") val siteId: String,
    val username: String,
    @SerialName("created_at") val createdAt: String,
    val graduated: Boolean = false,
)

@Serializable
data class VaultAccountsResponse(
    val accounts: List<VaultAccountSummary> = emptyList(),
    val sites: List<VaultSite> = emptyList(),
)

/** The debrief: five rows built only from findings, plus the families we found between them. */
@Serializable
data class VaultDebriefResponse(
    val rows: List<VaultDebriefRow> = emptyList(),
    /** Pairs of site ids that share a family fingerprint. */
    val families: List<List<String>> = emptyList(),
    /** Site ids weakest-first: the order a guesser would try them in. */
    @SerialName("guess_order") val guessOrder: List<String> = emptyList(),
    /** How many of the five have been logged back into. The measure that needs no detection. */
    @SerialName("graduated_count") val graduatedCount: Int = 0,
)

@Serializable
data class VaultDebriefRow(
    @SerialName("site_id") val siteId: String,
    val username: String,
    val findings: VaultFindingsDto,
)

/** `site_id` is null when nothing is due yet; the UI then shows nothing. */
@Serializable
data class VaultChallengeResponse(
    @SerialName("site_id") val siteId: String? = null,
    val username: String? = null,
    @SerialName("attempts_used") val attemptsUsed: Int = 0,
    @SerialName("attempts_allowed") val attemptsAllowed: Int = 3,
)

@Serializable
data class VaultLoginRequest(
    @SerialName("site_id") val siteId: String,
    val password: String,
    /** "paste" | "autofill" | "typed" — recorded only after the card has said so. */
    @SerialName("input_method") val inputMethod: String? = null,
    @SerialName("time_to_first_input_ms") val timeToFirstInputMs: Int? = null,
    @SerialName("self_report") val selfReport: String? = null,
)

/**
 * `out_of_attempts` means the three are spent and the honest message is shown. `hint` is never
 * derived from the findings — it only ever says to open the password manager.
 */
@Serializable
data class VaultLoginResponse(
    val ok: Boolean,
    @SerialName("attempts_used") val attemptsUsed: Int,
    @SerialName("attempts_allowed") val attemptsAllowed: Int = 3,
    @SerialName("out_of_attempts") val outOfAttempts: Boolean = false,
    val graduated: Boolean = false,
    /** Which follow-up line to show: "manager" | "recall" | "missed". Never a password clue. */
    @SerialName("follow_up") val followUp: String? = null,
)

/**
 * The site-name check of the drill, shared so the live meter on the device and the verdict on the
 * server can never disagree about it.
 *
 * Short words match whole words only. Blocking "cat" anywhere would refuse "scatter" and
 * "delicate", which teaches nothing and just makes the drill feel broken; "my cat pass" is still
 * refused, which is the behaviour the lesson is about.
 */
object VaultWords {
    /** Lowercase plus the leetspeak folding, so `C4tL4nd` reads as `catland`. */
    fun fold(s: String): String = buildString(s.length) {
        for (c in s.lowercase()) append(
            when (c) {
                '0' -> 'o'; '1', '!', '|' -> 'i'; '3' -> 'e'; '4', '@' -> 'a'
                '5', '$' -> 's'; '7' -> 't'; '8' -> 'b'; '9' -> 'g'
                else -> c
            },
        )
    }

    /** True when [password] carries one of this site's own words. */
    fun containsSiteWord(password: String, words: List<String>): Boolean {
        val folded = fold(password)
        return words.any { raw ->
            val w = fold(raw)
            when {
                w.length < 3 -> false
                w.length <= 4 && ' ' !in w -> wholeWord(folded, w)
                else -> folded.contains(w)
            }
        }
    }

    private fun wholeWord(haystack: String, word: String): Boolean {
        var i = haystack.indexOf(word)
        while (i >= 0) {
            val before = i == 0 || !haystack[i - 1].isLetter()
            val end = i + word.length
            val after = end == haystack.length || !haystack[end].isLetter()
            if (before && after) return true
            i = haystack.indexOf(word, i + 1)
        }
        return false
    }
}
