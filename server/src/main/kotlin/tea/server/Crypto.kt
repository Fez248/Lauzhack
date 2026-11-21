package tea.server

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Small crypto helpers shared by auth and the field scheduler. */
object Crypto {
    val random = SecureRandom()

    fun sha256Hex(s: String): String = hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8)))

    fun sha1Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

    /** 32 random bytes, base64url without padding (43 chars). */
    fun newToken(bytes: Int = 32): String {
        val b = ByteArray(bytes).also { random.nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    /**
     * Keyed fingerprint, used for the Vault drill's reuse detection: deterministic inside this
     * deployment only, and worthless to anyone who takes the table without the pepper. Never used
     * as a password hash — that is argon2id in [tea.server.auth.PasswordHasher].
     */
    fun hmacSha256Hex(key: String, message: CharArray): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val buf = Charsets.UTF_8.encode(java.nio.CharBuffer.wrap(message))
        val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
        return try {
            hex(mac.doFinal(bytes))
        } finally {
            bytes.fill(0)
        }
    }

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        for (b in bytes) {
            val v = b.toInt() and 0xff
            append("0123456789abcdef"[v ushr 4]); append("0123456789abcdef"[v and 0x0f])
        }
    }
}
