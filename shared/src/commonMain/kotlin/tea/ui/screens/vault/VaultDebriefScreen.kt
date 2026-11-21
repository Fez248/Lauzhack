package tea.ui.screens.vault

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.generated.resources.*
import tea.model.VaultDebriefResponse
import tea.model.VaultSite
import tea.net.ApiResult
import tea.ui.components.OfflineBanner
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.TeaButton
import tea.ui.components.TeaCard
import tea.ui.theme.TeaTheme

/**
 * The debrief (docs/password-drill.md). The five passwords are gone, so every line here is built
 * from the findings — which reads better anyway, and is the honest version of the lesson: we cannot
 * show you your passwords, because we did not keep them. Your password manager can.
 */
@Composable
fun VaultDebriefScreen(app: AppState) {
    val online by app.online.collectAsState()
    var data by remember { mutableStateOf<VaultDebriefResponse?>(null) }
    var sites by remember { mutableStateOf<List<VaultSite>>(emptyList()) }

    LaunchedEffect(online) {
        if (online) {
            if (sites.isEmpty()) (app.vaultAccounts() as? ApiResult.Ok)?.let { sites = it.value.sites }
            if (data == null) (app.vaultDebrief() as? ApiResult.Ok)?.let { data = it.value }
        }
    }

    ScreenColumn(title = stringResource(Res.string.vault_debrief_title), onBack = app::back) {
        val d = data
        if (d == null) {
            if (!online) OfflineBanner()
            return@ScreenColumn
        }

        SectionHeading(stringResource(Res.string.vault_debrief_title), intro = stringResource(Res.string.vault_debrief_lead), large = true)

        // one row per site: length, strength, the breach check, and what it was built out of
        for (row in d.rows) {
            TeaCard {
                Text(shownName(sites, row.siteId), style = TeaTheme.type.label, color = TeaTheme.colors.ink)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(tea.ui.theme.TeaSpacing.s2)) {
                    Text(stringResource(Res.string.vault_debrief_length, row.findings.length), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                    Text(stringResource(Res.string.vault_debrief_score, row.findings.score), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                }
                MeterBar(MeterState(levelOf(row.findings.score), null, (row.findings.score + 1) / 5f))
                val facts = listOf(
                    row.findings.containsName,
                    row.findings.containsEmail,
                    row.findings.containsYear,
                    row.findings.keyboardPattern != null,
                ).count { it }
                if (facts > 0) {
                    Text(stringResource(Res.string.vault_debrief_facts), style = TeaTheme.type.label, color = TeaTheme.colors.badText)
                }
            }
        }

        // the moment the lesson is actually taught
        SectionHeading(stringResource(Res.string.vault_debrief_families_title))
        if (d.families.isEmpty()) {
            Text(stringResource(Res.string.vault_debrief_families_none), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
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

        // the second question: which of these would actually hurt?
        SectionHeading(stringResource(Res.string.vault_debrief_tiering_title))
        Text(stringResource(Res.string.vault_debrief_tiering_body), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
        for (site in sites) {
            TeaCard {
                Text(site.shownAs, style = TeaTheme.type.label, color = TeaTheme.colors.ink)
                Text(site.holds, style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
            }
        }

        // Spider plays the guesser and takes nothing
        SectionHeading(stringResource(Res.string.vault_debrief_order_title))
        Text(
            d.guessOrder.mapIndexed { i, s -> "${i + 1}. ${shownName(sites, s)}" }.joinToString("\n"),
            style = TeaTheme.type.body,
            color = TeaTheme.colors.ink,
        )

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

private fun levelOf(score: Int): MeterLevel = when (score) {
    0, 1 -> MeterLevel.Bad
    2 -> MeterLevel.Weak
    3 -> MeterLevel.Fair
    else -> MeterLevel.Good
}
