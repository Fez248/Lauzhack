package tea.server

import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.origin
import io.ktor.util.AttributeKey
import kotlin.time.Clock
import tea.server.auth.RateLimiter
import tea.server.db.Db
import tea.server.field.FieldScheduler
import tea.server.field.FieldTemplates
import tea.server.leaderboard.ItemCatalog
import tea.server.mail.Mailer
import tea.server.password.PasswordAnalyzer
import tea.server.password.PwnedClient
import tea.server.vault.VaultService

/** Everything the routes need, wired once in [module] (no DI framework). */
class Services(
    val config: Config,
    val db: Db,
    val mailer: Mailer,
    val clock: Clock,
    val analyzer: PasswordAnalyzer,
    /** Shared with [analyzer]; the Vault drill needs the breach count, not just a verdict. */
    val pwned: PwnedClient,
    val limiter: RateLimiter,
    val items: ItemCatalog,
    val templates: FieldTemplates,
    /** Secret mixed into verification-code hashes. */
    val codePepper: String,
) {
    val field: FieldScheduler by lazy { FieldScheduler(this) }
    val vault: VaultService by lazy { VaultService(this) }
}

val ServicesKey = AttributeKey<Services>("tea.services")

val ApplicationCall.services: Services get() = application.attributes[ServicesKey]

/** First X-Forwarded-For hop when behind the tunnel, else the socket peer. Used for rate limits only. */
fun ApplicationCall.clientIp(): String =
    request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
        ?: request.origin.remoteHost
