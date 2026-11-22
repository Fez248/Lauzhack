package tea.server.vault

import tea.server.Crypto
import tea.server.password.PasswordAnalyzer

/**
 * The two keyed fingerprints the drill compares passwords by, without keeping anything readable.
 *
 * Argon2id cannot answer "is this the same password as another of theirs", because every hash has
 * its own salt. These can, and only inside this deployment: both are HMAC-SHA256 under
 * PASSWORD_FP_PEPPER, so a stolen table without the pepper says nothing.
 */
class VaultFingerprint(private val pepper: String) {

    /** Exact reuse: the same password, byte for byte. */
    fun exact(password: CharArray): String = Crypto.hmacSha256Hex(pepper, password)

    /**
     * The family a password belongs to: `CatLand1`, `catland2!` and `C4tland3` all land together.
     * Lowercase, drop trailing digits and punctuation, then fold leetspeak — in that order, so the
     * trailing `1` of `catland1` is gone before leet would have turned it into an `i`.
     */
    fun family(password: CharArray): String = exact(normalise(password).toCharArray())

    companion object {
        fun normalise(password: CharArray): String {
            val lower = String(password).lowercase()
            val trimmed = lower.trimEnd { !it.isLetter() }
            val base = trimmed.ifEmpty { lower }
            return PasswordAnalyzer.leet(base)
        }
    }
}
