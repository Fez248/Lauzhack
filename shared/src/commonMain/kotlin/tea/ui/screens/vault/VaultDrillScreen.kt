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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.model.EventTypes
import tea.model.LESSON_2_DEMO_ID
import tea.model.LESSON_2_ID
import tea.model.VaultCreateResponse
import tea.model.VaultReason
import tea.model.VaultSite
import tea.net.ApiResult
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce
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

    // Two sites in the one-minute demo, five otherwise. Everything else about the drill is the same.
    val demo = app.load(LearnKeys.VAULT_DEMO, LearnKeys.BOOL) == true

    LaunchedEffect(demo) {
        // The drill is lesson 2's test, so the event carries the lesson it belongs to — the
        // one-minute version included, so the two runs can be told apart later.
        app.emitOnce(
            LearnKeys.VAULT_STARTED,
            EventTypes.VAULT_DRILL_STARTED,
            JsonObject(mapOf("lesson_id" to JsonPrimitive(if (demo) LESSON_2_DEMO_ID else LESSON_2_ID))),
        )
    }

    LaunchedEffect(online) {
        if (sites.isEmpty() && online) {
            when (val r = app.vaultAccounts()) {
                is ApiResult.Ok -> {
                    sites = VaultDemo.sitesFor(r.value.sites, demo)
                    done = r.value.accounts.map { it.siteId }.toSet()
                }
                else -> Unit
            }
        }
    }

    val site = sites.getOrNull(siteIndex)
    val runTitle = stringResource(if (demo) Res.string.vault_title_demo else Res.string.vault_title)
    if (!online && sites.isEmpty()) {
        ScreenColumn(title = runTitle, onBack = app::back) { OfflineBanner() }
        return
    }
    if (site == null) {
        ScreenColumn(title = runTitle, onBack = app::back) {}
        return
    }

    // Coming back to the drill (after the demo, or after leaving halfway) must not open a form whose
    // only possible answer is "you already have an account on this site".
    val alreadyOpen = site.siteId in done && result == null
    LaunchedEffect(alreadyOpen, done, sites) {
        if (alreadyOpen) {
            val next = sites.indexOfFirst { it.siteId !in done }
            if (next >= 0) app.navigate(Screen.VaultDrill(next)) else app.navigate(Screen.VaultDebrief)
        }
    }
    if (alreadyOpen) {
        ScreenColumn(title = site.shownAs, onBack = app::back) {}
        return
    }

    if (!acknowledged && siteIndex == 0 && done.isEmpty()) {
        VaultIntro(demo = demo, onStart = { acknowledged = true }, onBack = app::back)
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
                                if (!r.value.accepted) {
                                    // A number only: the server keeps nothing at all for a refusal,
                                    // and neither do we. It is here so the debrief can say how many
                                    // tries the five took.
                                    val seen = app.load(LearnKeys.VAULT_REJECTIONS, LearnKeys.INT) ?: 0
                                    app.save(LearnKeys.VAULT_REJECTIONS, LearnKeys.INT, seen + 1)
                                }
                                if (r.value.accepted) {
                                    typedHere += password
                                    done = done + site.siteId
                                    password = ""
                                    // From here on, opening the password chapter asks for a login
                                    // instead of replaying the lesson.
                                    if (sites.all { it.siteId in done }) {
                                        app.save(LearnKeys.vaultDone(demo), LearnKeys.BOOL, true)
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

/** The card the learner dismisses once, before the first field. The demo says "two", not "five". */
@Composable
private fun VaultIntro(demo: Boolean, onStart: () -> Unit, onBack: () -> Unit) {
    ScreenColumn(title = stringResource(if (demo) Res.string.vault_title_demo else Res.string.vault_title), onBack = onBack) {
        SectionHeading(stringResource(if (demo) Res.string.vault_intro_title_demo else Res.string.vault_intro_title), large = true)
        TeaCard {
            Text(
                stringResource(if (demo) Res.string.vault_intro_card_demo else Res.string.vault_intro_card),
                style = TeaTheme.type.body,
                color = TeaTheme.colors.ink,
            )
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
