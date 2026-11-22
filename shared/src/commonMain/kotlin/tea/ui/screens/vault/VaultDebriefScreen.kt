package tea.ui.screens.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.EventTypes
import tea.model.VaultDebriefResponse
import tea.model.VaultFindingsDto
import tea.model.VaultSite
import tea.net.ApiResult
import tea.ui.components.Banner
import tea.ui.components.BannerKind
import tea.ui.components.OfflineBanner
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/**
 * The debrief (docs/password-drill.md). The five passwords are gone, so every line here is built
 * from the findings — which reads better anyway, and is the honest version of the lesson: we cannot
 * show you your passwords, because we did not keep them. Your password manager can.
 *
 * Three questions, answered from the learner's own data: did you build a family, which of these
 * would hurt most (they sort that one themselves), and where did these passwords come from.
 */
@Composable
fun VaultDebriefScreen(app: AppState) {
    val online by app.online.collectAsState()
    var data by remember { mutableStateOf<VaultDebriefResponse?>(null) }
    var sites by remember { mutableStateOf<List<VaultSite>>(emptyList()) }

    // Two sites in the one-minute demo, five otherwise; the copy follows the run.
    val demo = app.load(LearnKeys.VAULT_DEMO, LearnKeys.BOOL) == true
    val title = stringResource(if (demo) Res.string.vault_debrief_title_demo else Res.string.vault_debrief_title)

    LaunchedEffect(online) {
        if (online) {
            if (sites.isEmpty()) (app.vaultAccounts() as? ApiResult.Ok)?.let { sites = VaultDemo.sitesFor(it.value.sites, demo) }
            if (data == null) (app.vaultDebrief() as? ApiResult.Ok)?.let { data = it.value }
        }
    }

    ScreenColumn(title = title, onBack = app::back) {
        val d = data
        if (d == null) {
            if (!online) OfflineBanner()
            return@ScreenColumn
        }

        // The drill is over once every site has an account. The rejection count is the device's own
        // tally: the server stores nothing for a refused attempt.
        LaunchedEffect(d, sites) {
            if (sites.isNotEmpty() && d.rows.size >= sites.size) {
                app.emitOnce(
                    LearnKeys.VAULT_COMPLETED,
                    EventTypes.VAULT_DRILL_COMPLETED,
                    JsonObject(
                        mapOf(
                            "accounts" to JsonPrimitive(d.rows.size),
                            "rejections" to JsonPrimitive(app.load(LearnKeys.VAULT_REJECTIONS, LearnKeys.INT) ?: 0),
                            "families" to JsonPrimitive(d.families.size),
                        ),
                    ),
                )
            }
        }

        SectionHeading(title, intro = stringResource(Res.string.vault_debrief_lead), large = true)

        // one row per site: length, strength, the breach check, and what it was built out of
        for (row in d.rows) {
            TeaCard {
                Text(shownName(sites, row.siteId), style = TeaTheme.type.label, color = TeaTheme.colors.ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
                    Text(stringResource(Res.string.vault_debrief_length, row.findings.length), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                    Text(stringResource(Res.string.vault_debrief_score, row.findings.score), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                }
                MeterBar(MeterState(levelOf(row.findings.score), null, (row.findings.score + 1) / 5f))
                if (row.findings.guessableFacts() > 0) {
                    Text(stringResource(Res.string.vault_debrief_facts), style = TeaTheme.type.label, color = TeaTheme.colors.badText)
                }
            }
        }

        // the moment the lesson is actually taught
        SectionHeading(stringResource(Res.string.vault_debrief_families_title))
        if (d.families.isEmpty()) {
            Text(
                stringResource(if (demo) Res.string.vault_debrief_families_none_demo else Res.string.vault_debrief_families_none),
                style = TeaTheme.type.body,
                color = TeaTheme.colors.ink,
            )
        } else {
            for (pair in d.families) {
                Text(
                    stringResource(
                        Res.string.vault_debrief_families_some,
                        pair.joinToString(" + ") { shownName(sites, it) },
                    ),
                    style = TeaTheme.type.body,
                    color = TeaTheme.colors.ink,
                )
            }
        }

        // the second question, which the learner answers by sorting their own work
        TieringSort(d, sites, demo)

        // Spider plays the guesser and takes nothing: weakest first, with the reason
        SectionHeading(stringResource(Res.string.vault_debrief_order_title))
        for ((i, siteId) in d.guessOrder.withIndex()) {
            val findings = d.rows.firstOrNull { it.siteId == siteId }?.findings ?: continue
            TeaCard {
                Text(
                    stringResource(Res.string.vault_debrief_order_row, i + 1, shownName(sites, siteId)),
                    style = TeaTheme.type.label,
                    color = TeaTheme.colors.ink,
                )
                Text(whyGuessableFirst(findings), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
            }
        }

        if (d.graduatedCount > 0) {
            Text(
                stringResource(Res.string.vault_graduated, d.graduatedCount, d.rows.size),
                style = TeaTheme.type.label,
                color = TeaTheme.colors.okText,
            )
        }

        TeaButton(
            stringResource(Res.string.vault_debrief_done),
            onClick = { app.navigate(Screen.Home) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * "Which one would hurt most?" The learner puts the five in the order they would least like to lose,
 * and only then do we lay their order next to the strength they actually chose. Sorting their own
 * work is the lesson; being told which site matters is not.
 *
 * Up and down buttons rather than a drag: the same gesture works on a phone, a mouse and a keyboard,
 * and it needs no library.
 */
@Composable
private fun TieringSort(d: VaultDebriefResponse, sites: List<VaultSite>, demo: Boolean) {
    val ids = d.rows.map { it.siteId }
    var order by remember(ids) { mutableStateOf(ids) }
    var compared by remember(ids) { mutableStateOf(false) }
    if (order.size < 2) return

    SectionHeading(stringResource(Res.string.vault_debrief_tiering_title))
    Text(
        stringResource(if (demo) Res.string.vault_debrief_tiering_body_demo else Res.string.vault_debrief_tiering_body),
        style = TeaTheme.type.body,
        color = TeaTheme.colors.ink,
    )

    val checks = tierChecks(order, d.guessOrder)
    for ((index, siteId) in order.withIndex()) {
        val site = sites.firstOrNull { it.siteId == siteId }
        TeaCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.fillMaxWidth(0.72f)) {
                    Text(
                        stringResource(Res.string.vault_debrief_tier_place, index + 1, site?.shownAs ?: siteId),
                        style = TeaTheme.type.label,
                        color = TeaTheme.colors.ink,
                    )
                    site?.let { Text(it.holds, style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft) }
                    if (compared) {
                        val check = checks.firstOrNull { it.siteId == siteId }
                        if (check != null) {
                            Text(
                                stringResource(Res.string.vault_debrief_tier_row, check.strengthRank, order.size),
                                style = TeaTheme.type.label,
                                color = if (check.weakerThanItsPlace) TeaTheme.colors.badText else TeaTheme.colors.inkSoft,
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
                    TeaButton(
                        stringResource(Res.string.vault_debrief_tier_up),
                        onClick = { order = order.moved(index, -1); compared = false },
                        variant = TeaButtonVariant.Secondary,
                        small = true,
                        enabled = index > 0,
                    )
                    TeaButton(
                        stringResource(Res.string.vault_debrief_tier_down),
                        onClick = { order = order.moved(index, 1); compared = false },
                        variant = TeaButtonVariant.Secondary,
                        small = true,
                        enabled = index < order.size - 1,
                    )
                }
            }
        }
    }

    if (!compared) {
        TeaButton(
            stringResource(Res.string.vault_debrief_tier_compare),
            onClick = { compared = true },
            variant = TeaButtonVariant.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        val worst = checks.filter { it.weakerThanItsPlace }.maxByOrNull { it.gap }
        if (worst == null) {
            Banner(stringResource(Res.string.vault_debrief_tier_ok), kind = BannerKind.Info)
        } else {
            Banner(
                stringResource(
                    Res.string.vault_debrief_tier_mismatch,
                    shownName(sites, worst.siteId),
                    worst.valueRank,
                    worst.strengthRank,
                    order.size,
                ),
                kind = BannerKind.Info,
            )
        }
    }
}

/**
 * One site's two places: where the learner put it for what it holds, and where its password puts it
 * for strength. Both are 1-based and 1 is "most important" / "strongest".
 */
internal data class TierCheck(val siteId: String, val valueRank: Int, val strengthRank: Int) {
    /** The gap that matters: a site the learner values highly, guarded by one of the weaker five. */
    val gap: Int get() = strengthRank - valueRank
    val weakerThanItsPlace: Boolean get() = gap > 0
}

/**
 * Lays the learner's order next to the strength order. [guessOrder] is the server's weakest-first
 * list, so reversing it gives strongest-first — one definition of strength, shared by Spider's
 * ranking and this comparison, rather than a second rule on the device.
 */
internal fun tierChecks(order: List<String>, guessOrder: List<String>): List<TierCheck> {
    val strongestFirst = guessOrder.reversed()
    return order.mapIndexedNotNull { i, siteId ->
        val strength = strongestFirst.indexOf(siteId)
        if (strength < 0) null else TierCheck(siteId, i + 1, strength + 1)
    }
}

/** Moves one entry by [by] places, or returns the list unchanged at either end. */
internal fun List<String>.moved(index: Int, by: Int): List<String> {
    val to = index + by
    if (index !in indices || to !in indices) return this
    return toMutableList().also { it.add(to, it.removeAt(index)) }
}

/** How many guessable facts went into a password: name, email, year, a run across the keyboard. */
internal fun VaultFindingsDto.guessableFacts(): Int =
    listOf(containsName, containsEmail, containsYear, keyboardPattern != null).count { it }

/**
 * Spider's reason for trying one password before the others, in the learner's words and built only
 * from the findings — never from the password, which we do not have.
 */
@Composable
private fun whyGuessableFirst(f: VaultFindingsDto): String {
    val parts = buildList {
        add(stringResource(Res.string.vault_why_score, f.score))
        add(stringResource(Res.string.vault_why_length, f.length))
        if (f.containsName) add(stringResource(Res.string.vault_why_name))
        if (f.containsEmail) add(stringResource(Res.string.vault_why_email))
        if (f.containsYear) add(stringResource(Res.string.vault_why_year))
        if (f.keyboardPattern != null) add(stringResource(Res.string.vault_why_keyboard))
    }
    return parts.joinToString(stringResource(Res.string.vault_why_join))
}

private fun levelOf(score: Int): MeterLevel = when (score) {
    0, 1 -> MeterLevel.Bad
    2 -> MeterLevel.Weak
    3 -> MeterLevel.Fair
    else -> MeterLevel.Good
}
