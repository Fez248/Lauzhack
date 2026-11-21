package tea.ui.screens.vault

/**
 * The live meter of the Vault drill (docs/password-drill.md): a fast, local hint while the learner
 * types, so an obviously poor password never needs a round trip.
 *
 * It is deliberately **not** the authority. The verdict comes from the server, which has nbvcxz and
 * the breach list; this only has arithmetic. When the two disagree the server wins, and the learner
 * sees the server's reason.
 *
 * Nothing here leaves the device and nothing is remembered: [MeterState] is computed from the field
 * on each keystroke and thrown away.
 */
enum class MeterLevel { Empty, Bad, Weak, Fair, Good }

data class MeterState(
    val level: MeterLevel,
    /** Which message to show. The screen maps these to localised strings. */
    val problem: MeterProblem?,
    /** 0f..1f, for the bar. */
    val fraction: Float,
)

enum class MeterProblem { TooShort, Weak, ContainsSite, ReusedInDrill }

private const val MIN_LENGTH = 10

/** 4+ consecutive keys of one keyboard row, forwards or backwards (mirrors the server's check). */
private val ROWS = listOf(
    "qwertyuiop", "asdfghjkl", "zxcvbnm", "1234567890",
    "azertyuiop", "qsdfghjklm", "wxcvbn", "qwertzuiop", "yxcvbnm",
)

/** Lowercase + the same leetspeak folding the server uses, so both see `c4t` as `cat`. */
fun foldLeet(s: String): String = buildString(s.length) {
    for (c in s.lowercase()) append(
        when (c) {
            '0' -> 'o'; '1', '!', '|' -> 'i'; '3' -> 'e'; '4', '@' -> 'a'
            '5', '$' -> 's'; '7' -> 't'; '8' -> 'b'; '9' -> 'g'
            else -> c
        },
    )
}

/**
 * @param siteWords words this site's password must not contain (its id and shown name)
 * @param othersInDrill passwords already typed in this drill, held in memory on this device only,
 *   so "you have used this one already" can be said before the server is asked
 */
fun meterState(password: String, siteWords: List<String>, othersInDrill: Set<String>): MeterState {
    if (password.isEmpty()) return MeterState(MeterLevel.Empty, null, 0f)
    val folded = foldLeet(password)

    if (siteWords.any { it.length >= 3 && folded.contains(foldLeet(it)) }) {
        return MeterState(MeterLevel.Bad, MeterProblem.ContainsSite, 0.15f)
    }
    if (password in othersInDrill) {
        return MeterState(MeterLevel.Bad, MeterProblem.ReusedInDrill, 0.15f)
    }
    if (password.length < MIN_LENGTH) {
        return MeterState(MeterLevel.Bad, MeterProblem.TooShort, 0.1f + 0.3f * (password.length / MIN_LENGTH.toFloat()))
    }

    // Rough stand-in for the server's score: length does most of the work, variety and the number of
    // distinct characters help, and an obvious pattern takes it all back.
    val distinct = password.toSet().size
    val classes = listOf(
        password.any { it.isLowerCase() },
        password.any { it.isUpperCase() },
        password.any { it.isDigit() },
        password.any { !it.isLetterOrDigit() },
    ).count { it }
    val words = password.split(' ', '-', '_', '.').filter { it.length >= 3 }.size
    val patterned = ROWS.any { row ->
        listOf(row, row.reversed()).any { r -> (0..r.length - 4).any { i -> folded.contains(r.substring(i, i + 4)) } }
    } || distinct <= 3

    var points = 0
    points += when {
        password.length >= 20 -> 3
        password.length >= 16 -> 2
        password.length >= 12 -> 1
        else -> 0
    }
    if (words >= 3) points += 2 else if (words >= 2) points += 1
    if (classes >= 3) points += 1
    if (distinct >= 10) points += 1
    if (patterned) points -= 3

    return when {
        points <= 0 -> MeterState(MeterLevel.Weak, MeterProblem.Weak, 0.3f)
        points <= 2 -> MeterState(MeterLevel.Fair, null, 0.6f)
        else -> MeterState(MeterLevel.Good, null, 1f)
    }
}

/**
 * How the password reached the field, worked out without touching the clipboard or recording a
 * single keystroke. It is a **heuristic over the size of each change**: typing arrives one character
 * at a time, a paste or an autofill arrives in one jump.
 *
 * Deliberately not keystroke timing or typing dynamics — per-key intervals are behavioural
 * biometrics under the GDPR and the Swiss nFADP, and would answer a question we are not asking.
 * The learner is told we look at this before they type (vault_login_card).
 */
class InputMethodWatcher(private val nowMs: () -> Long) {
    private var focusedAt: Long? = null
    private var firstInputAt: Long? = null
    private var biggestJump = 0
    private var changes = 0

    fun onFocus() {
        if (focusedAt == null) focusedAt = nowMs()
    }

    fun onChange(oldValue: String, newValue: String) {
        val added = newValue.length - oldValue.length
        if (added > biggestJump) biggestJump = added
        changes++
        if (firstInputAt == null && newValue.isNotEmpty()) firstInputAt = nowMs()
    }

    /** Milliseconds from the field being focused to the first character, or null if never typed. */
    fun timeToFirstInputMs(): Int? {
        val from = focusedAt ?: return null
        val to = firstInputAt ?: return null
        return (to - from).coerceIn(0, 10 * 60 * 1000).toInt()
    }

    /**
     * "paste" | "autofill" | "typed". A single jump of four or more characters is not typing; when
     * it also lands almost immediately after focus, a password manager filled it rather than the
     * learner pasting it by hand. Both mean the same thing for teaching: the password was looked up,
     * not remembered.
     */
    fun method(): String? {
        if (changes == 0) return null
        if (biggestJump < JUMP) return INPUT_TYPED
        val t = timeToFirstInputMs()
        return if (changes == 1 && t != null && t < AUTOFILL_MS) INPUT_AUTOFILL else INPUT_PASTE
    }

    companion object {
        private const val JUMP = 4
        private const val AUTOFILL_MS = 400
        const val INPUT_TYPED = "typed"
        const val INPUT_PASTE = "paste"
        const val INPUT_AUTOFILL = "autofill"
    }
}
