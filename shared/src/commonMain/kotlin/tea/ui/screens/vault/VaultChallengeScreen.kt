package tea.ui.screens.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import tea.ui.theme.TeaSpacing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.time.Clock
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.VaultChallengeResponse
import tea.model.VaultLoginRequest
import tea.model.VaultLoginResponse
import tea.model.VaultSelfReport
import tea.model.VaultSite
import tea.net.ApiResult
import tea.ui.screens.learn.LearnKeys
import tea.ui.components.Banner
import tea.ui.components.BannerKind
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.OfflineBanner
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaCard
import tea.ui.components.TeaTextField
import tea.ui.theme.TeaTheme

/**
 * The login drill (docs/password-drill.md), a later session: **one of the five at random**, so the
 * learner cannot revise for a specific site and the only strategy that works is having saved all
 * five. Three attempts, then the honest message — we cannot help either, because we only kept a
 * fingerprint — and the offer to set a new one.
 *
 * The card tells the learner, before they type, that we notice whether the password was pasted,
 * autofilled or typed, and why. That disclosure is what makes the signal fair; see
 * [InputMethodWatcher] for what is and is not collected.
 */
@Composable
fun VaultChallengeScreen(app: AppState) {
    val online by app.online.collectAsState()
    var challenge by remember { mutableStateOf<VaultChallengeResponse?>(null) }
    var sites by remember { mutableStateOf<List<VaultSite>>(emptyList()) }
    var password by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<VaultLoginResponse?>(null) }
    var selfReport by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var round by remember { mutableStateOf(0) }
    val watcher = remember(round) { InputMethodWatcher { Clock.System.now().toEpochMilliseconds() } }
    val scope = rememberCoroutineScope()

    LaunchedEffect(online, round) {
        if (online) {
            if (sites.isEmpty()) (app.vaultAccounts() as? ApiResult.Ok)?.let { sites = it.value.sites }
            (app.vaultChallenge() as? ApiResult.Ok)?.let { challenge = it.value }
        }
    }

    ScreenColumn(title = stringResource(Res.string.vault_login_title), onBack = app::back) {
        val c = challenge
        if (c == null) {
            if (!online) OfflineBanner()
            return@ScreenColumn
        }
        if (c.siteId == null) {
            // The password chapter leads here once the five exist, so this is a real destination,
            // not an error: nothing is due yet, or every account has graduated. Offer the debrief
            // rather than leaving the learner at a dead end.
            Text(stringResource(Res.string.vault_login_none), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
            TeaButton(
                stringResource(Res.string.vault_login_none_see),
                { app.navigate(Screen.VaultDebrief) },
                modifier = Modifier.fillMaxWidth(),
            )
            TeaButton(
                stringResource(Res.string.shell_back_home),
                { app.navigate(Screen.Home) },
                modifier = Modifier.fillMaxWidth(),
                variant = tea.ui.components.TeaButtonVariant.Secondary,
            )
            return@ScreenColumn
        }

        // the site's own banner again, so the learner recognises which of the five they are at
        val site = sites.firstOrNull { it.siteId == c.siteId }
        if (site != null) {
            TeaCard(background = siteLook(site).soft) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
                    SiteBadge(site)
                    Column {
                        Text(site.shownAs, style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
                        Text(site.domain, style = TeaTheme.type.label, color = TeaTheme.colors.inkSoft)
                    }
                }
            }
        }
        SectionHeading(
            stringResource(Res.string.vault_login_title),
            intro = stringResource(Res.string.vault_login_lead, shownName(sites, c.siteId), c.username.orEmpty()),
            large = true,
        )

        // said before a single character is typed
        TeaCard {
            Text(stringResource(Res.string.vault_login_card), style = TeaTheme.type.label, color = TeaTheme.colors.inkSoft)
        }

        val r = result
        val spent = r?.outOfAttempts == true || c.attemptsUsed >= c.attemptsAllowed
        val won = r?.ok == true

        if (!won && !spent) {
            TeaCard {
                Text(
                    stringResource(Res.string.vault_login_attempts, (r?.attemptsUsed ?: c.attemptsUsed) + 1, c.attemptsAllowed),
                    style = TeaTheme.type.label,
                    color = TeaTheme.colors.inkSoft,
                )
                TeaTextField(
                    value = password,
                    onValueChange = { new -> watcher.onFocus(); watcher.onChange(password, new); password = new },
                    label = stringResource(Res.string.vault_password),
                    password = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (r?.ok == false && !r.outOfAttempts) {
                Banner(stringResource(Res.string.vault_login_wrong), kind = BannerKind.Info)
            }
            TeaButton(
                text = stringResource(Res.string.vault_login_submit),
                enabled = !busy && online && password.isNotEmpty(),
                onClick = {
                    busy = true
                    scope.launch {
                        val request = VaultLoginRequest(
                            siteId = c.siteId,
                            password = password,
                            inputMethod = watcher.method(),
                            timeToFirstInputMs = watcher.timeToFirstInputMs(),
                            selfReport = selfReport,
                        )
                        (app.vaultLogin(request) as? ApiResult.Ok)?.let { result = it.value }
                        password = ""
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // the learner's own answer: the only one of the three signals that is not a guess
        if (won || spent) {
            when {
                won && r.followUp == "manager" -> Banner(stringResource(Res.string.vault_login_ok_manager), kind = BannerKind.Info)
                won -> Banner(stringResource(Res.string.vault_login_ok_recall), kind = BannerKind.Info)
                else -> TeaCard(background = TeaTheme.colors.badSoft) {
                    Text(stringResource(Res.string.vault_login_missed), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
                }
            }
            SectionHeading(stringResource(Res.string.vault_self_report_q))
            for ((key, label) in listOf(
                VaultSelfReport.LOOKED_IT_UP to stringResource(Res.string.vault_self_report_looked),
                VaultSelfReport.REMEMBERED to stringResource(Res.string.vault_self_report_remembered),
                VaultSelfReport.WRITTEN_DOWN to stringResource(Res.string.vault_self_report_written),
            )) {
                ChoiceRow(
                    text = label,
                    state = if (selfReport == key) ChoiceState.Selected else ChoiceState.Idle,
                    onClick = { selfReport = key },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (spent && !won) {
                TeaButton(
                    stringResource(Res.string.vault_login_reset),
                    onClick = {
                        scope.launch {
                            app.vaultReset(c.siteId)
                            // one of the five is gone again, so the chapter goes back to the drill
                            app.save(LearnKeys.VAULT_DRILL_DONE, LearnKeys.BOOL, false)
                            val index = sites.indexOfFirst { it.siteId == c.siteId }.coerceAtLeast(0)
                            app.navigate(Screen.VaultDrill(index))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                TeaButton(
                    stringResource(Res.string.shell_continue),
                    onClick = { app.navigate(Screen.VaultDebrief) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
