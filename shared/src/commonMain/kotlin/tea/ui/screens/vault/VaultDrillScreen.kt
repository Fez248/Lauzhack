package tea.ui.screens.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.VaultCreateResponse
import tea.model.VaultReason
import tea.model.VaultSite
import tea.net.ApiResult
import tea.ui.screens.learn.LearnKeys
import tea.ui.components.Banner
import tea.ui.components.OfflineBanner
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaCard
import tea.ui.components.TeaTextField
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * The Vault drill (docs/password-drill.md): five fictional sites, one invented username and password
 * each. Five in a row is the point — two or three can be remembered, and by the fourth most people
 * start a family, which is the lesson.
 *
 * Nothing the learner types is kept on this screen beyond the field itself and the in-memory set
 * used to say "you have already used that one" before the server is asked.
 */
@Composable
fun VaultDrillScreen(app: AppState, siteIndex: Int) {
    val online by app.online.collectAsState()
    var sites by remember { mutableStateOf<List<VaultSite>>(emptyList()) }
    var done by remember { mutableStateOf<Set<String>>(emptySet()) }
    var acknowledged by remember { mutableStateOf(false) }
    val typedHere = remember { mutableStateListOf<String>() }

    var username by remember(siteIndex) { mutableStateOf("") }
    var password by remember(siteIndex) { mutableStateOf("") }
    var result by remember(siteIndex) { mutableStateOf<VaultCreateResponse?>(null) }
    var busy by remember(siteIndex) { mutableStateOf(false) }
    var failed by remember(siteIndex) { mutableStateOf(false) }

    LaunchedEffect(online) {
        if (sites.isEmpty() && online) {
            when (val r = app.vaultAccounts()) {
                is ApiResult.Ok -> {
                    sites = r.value.sites
                    done = r.value.accounts.map { it.siteId }.toSet()
                }
                else -> Unit
            }
        }
    }

    val site = sites.getOrNull(siteIndex)
    if (!online && sites.isEmpty()) {
        ScreenColumn(title = stringResource(Res.string.vault_title), onBack = app::back) { OfflineBanner() }
        return
    }
    if (site == null) {
        ScreenColumn(title = stringResource(Res.string.vault_title), onBack = app::back) {}
        return
    }

    if (!acknowledged && siteIndex == 0 && done.isEmpty()) {
        VaultIntro(onStart = { acknowledged = true }, onBack = app::back)
        return
    }

    val meter = meterState(password, siteWords(site), typedHere.toSet())
    val accepted = result?.accepted == true

    val look = siteLook(site)

    ScreenColumn(title = site.shownAs, onBack = app::back) {
        Text(
            stringResource(Res.string.vault_progress, siteIndex + 1, sites.size),
            style = TeaTheme.type.label,
            color = TeaTheme.colors.inkSoft,
        )
        // the site's own banner, so five sign-ups read as five different places
        TeaCard(background = look.soft) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
                SiteBadge(site)
                Column {
                    Text(site.shownAs, style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
                    Text(site.domain, style = TeaTheme.type.label, color = TeaTheme.colors.inkSoft)
                }
            }
        }
        SectionHeading(stringResource(Res.string.vault_signup_title), intro = stringResource(Res.string.vault_holds, site.holds))

        TeaCard {
            TeaTextField(
                value = username,
                onValueChange = { username = it },
                label = stringResource(Res.string.vault_username),
                enabled = !accepted,
                modifier = Modifier.fillMaxWidth(),
            )
            TeaTextField(
                value = password,
                onValueChange = { password = it; result = null },
                label = stringResource(Res.string.vault_password),
                password = true,
                enabled = !accepted,
                modifier = Modifier.fillMaxWidth(),
            )
            MeterBar(meter)
            meterMessage(meter)?.let { Text(it, style = TeaTheme.type.label, color = TeaTheme.colors.inkSoft) }
        }

        result?.let { r ->
            if (!r.accepted) {
                Banner(reasonText(r), kind = tea.ui.components.BannerKind.Info)
            } else {
                Banner(stringResource(Res.string.vault_saved), kind = tea.ui.components.BannerKind.Info)
                if (r.findings?.breachChecked == false) {
                    Banner(stringResource(Res.string.vault_breach_unchecked), kind = tea.ui.components.BannerKind.Info)
                }
                if (r.familyMatch) {
                    TeaCard(background = TeaTheme.colors.badSoft) {
                        SectionHeading(stringResource(Res.string.vault_family_title))
                        Text(
                            stringResource(Res.string.vault_family_body, shownName(sites, r.familyWith)),
                            style = TeaTheme.type.body,
                            color = TeaTheme.colors.ink,
                        )
                    }
                }
            }
        }
        if (failed) Banner(stringResource(Res.string.shell_error_generic), kind = tea.ui.components.BannerKind.Info)

        val scope = androidx.compose.runtime.rememberCoroutineScope()
        if (!accepted) {
            TeaButton(
                text = stringResource(Res.string.vault_create),
                enabled = !busy && online && username.isNotBlank() && password.isNotEmpty(),
                onClick = {
                    busy = true
                    failed = false
                    scope.launch {
                        when (val r = app.vaultCreate(site.siteId, username.trim(), password)) {
                            is ApiResult.Ok -> {
                                result = r.value
                                if (r.value.accepted) {
                                    typedHere += password
                                    done = done + site.siteId
                                    password = ""
                                    // From here on, opening the password chapter asks for a login
                                    // instead of replaying the lesson.
                                    if (done.size >= sites.size) {
                                        app.save(LearnKeys.VAULT_DRILL_DONE, LearnKeys.BOOL, true)
                                    }
                                }
                            }
                            else -> failed = true
                        }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            TeaButton(
                text = stringResource(Res.string.shell_continue),
                onClick = {
                    if (siteIndex + 1 < sites.size) app.navigate(Screen.VaultDrill(siteIndex + 1))
                    else app.navigate(Screen.VaultDebrief)
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun VaultIntro(onStart: () -> Unit, onBack: () -> Unit) {
    ScreenColumn(title = stringResource(Res.string.vault_title), onBack = onBack) {
        SectionHeading(stringResource(Res.string.vault_intro_title), large = true)
        TeaCard {
            Text(stringResource(Res.string.vault_intro_card), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
        }
        TeaButton(stringResource(Res.string.vault_intro_start), onStart, modifier = Modifier.fillMaxWidth())
    }
}

/** The bar is a hint, not a score: the colour follows the meter, the width follows its fraction. */
@Composable
internal fun MeterBar(state: MeterState) {
    val colors = TeaTheme.colors
    val colour = when (state.level) {
        MeterLevel.Empty -> colors.line
        MeterLevel.Bad, MeterLevel.Weak -> colors.bad
        MeterLevel.Fair -> colors.ink
        MeterLevel.Good -> colors.ok
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.fillMaxWidth().height(8.dp)
                .background(colors.line.copy(alpha = 0.15f), RoundedCornerShape(4.dp)),
        ) {
            Box(
                Modifier.fillMaxWidth(state.fraction).height(8.dp)
                    .background(colour, RoundedCornerShape(4.dp)),
            )
        }
    }
}

@Composable
private fun meterMessage(state: MeterState): String? = when (state.problem) {
    MeterProblem.TooShort -> stringResource(Res.string.vault_meter_length)
    MeterProblem.Weak -> stringResource(Res.string.vault_meter_weak)
    MeterProblem.ContainsSite -> stringResource(Res.string.vault_meter_site)
    MeterProblem.ReusedInDrill -> stringResource(Res.string.vault_meter_own)
    null -> if (state.level == MeterLevel.Good) stringResource(Res.string.vault_meter_ok) else null
}

/** The server's reason, in the learner's words. Never a bare red bar. */
@Composable
private fun reasonText(r: VaultCreateResponse): String = when (r.reason) {
    VaultReason.TOO_SHORT -> stringResource(Res.string.vault_reason_too_short)
    VaultReason.WEAK -> stringResource(Res.string.vault_reason_weak)
    VaultReason.BREACHED -> stringResource(Res.string.vault_reason_breached, (r.pwnedCount ?: 0).toString())
    VaultReason.CONTAINS_SITE -> stringResource(Res.string.vault_reason_contains_site)
    VaultReason.REUSED ->
        if (r.reusedWith == "account") stringResource(Res.string.vault_reason_reused_account)
        else stringResource(Res.string.vault_reason_reused, r.reusedWith.orEmpty())
    VaultReason.ALREADY_EXISTS -> stringResource(Res.string.vault_reason_already)
    else -> stringResource(Res.string.shell_error_generic)
}

/** Words the local meter refuses for this site: the server's own list, carried on the site. */
internal fun siteWords(site: VaultSite): List<String> = site.words

internal fun shownName(sites: List<VaultSite>, siteId: String?): String =
    sites.firstOrNull { it.siteId == siteId }?.shownAs ?: siteId.orEmpty()
