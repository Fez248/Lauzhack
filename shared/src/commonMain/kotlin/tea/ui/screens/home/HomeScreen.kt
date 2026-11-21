package tea.ui.screens.home

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.AppState
import tea.app.Screen
import tea.engine.Streak
import tea.engine.StreakState
import tea.generated.resources.*
import tea.net.ApiResult
import tea.model.LESSON_1_ID
import tea.model.LESSON_2_ID
import tea.model.Mood
import tea.model.Persona
import tea.store.FieldCache
import tea.ui.components.Banner
import tea.ui.components.PersonaBubble
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.SpiderPanel
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaDialog
import tea.ui.components.TeaIcons
import tea.ui.screens.FourStepsList
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme

/** What Home shows (plan §9.7). */
data class HomeUi(
    val firstName: String? = null,
    val xp: Int = 0,
    val streak: Int = 0,
    val dailyMinutes: Int = 5,
    val lessonStarted: Boolean = false,
    val lessonDone: Boolean = false,
    val online: Boolean = true,
    val loggedIn: Boolean = false,
    /** Lesson 2 (passwords) and its drill. */
    val lesson2Started: Boolean = false,
    val lesson2Done: Boolean = false,
    /** A drill account is due to be logged back in to; we never say which one here. */
    val vaultChallengeDue: Boolean = false,
    val vaultGraduated: Int = 0,
    val vaultTotal: Int = 0,
    val sessionExpired: Boolean = false,
    val field: FieldCache = FieldCache(),
)

/** Home actions; the screen wires them to AppState. */
class HomeActions(
    val lesson: () -> Unit = {},
    val practise: () -> Unit = {},
    val reviewLesson: () -> Unit = {},
    val leaderboard: () -> Unit = {},
    val about: () -> Unit = {},
    val settings: () -> Unit = {},
    val suspicious: () -> Unit = {},
    val signIn: () -> Unit = {},
    val openReveal: () -> Unit = {},
    val practiseFocus: () -> Unit = {},
    val lesson2: () -> Unit = {},
    val vaultDrill: () -> Unit = {},
    val vaultChallenge: () -> Unit = {},
)

@Composable
fun HomeScreen(app: AppState) {
    val profile by app.profile.collectAsState()
    val xp by app.xp.collectAsState()
    val online by app.online.collectAsState()
    val loggedIn by app.loggedIn.collectAsState()
    val expired by app.sessionExpired.collectAsState()
    val field by app.field.collectAsState()
    val flags by app.flags.collectAsState()
    val scope = rememberCoroutineScope()
    var showSuspicious by remember { mutableStateOf(false) }

    LaunchedEffect(loggedIn) { if (loggedIn) app.refreshField() }

    // The drill's own state: how many of the five exist, how many have been logged back in, and
    // whether one is due now. Read once per visit; a null challenge simply means nothing is due.
    var vaultTotal by remember { mutableStateOf(0) }
    var vaultGraduated by remember { mutableStateOf(0) }
    var vaultDue by remember { mutableStateOf(false) }
    LaunchedEffect(loggedIn, online) {
        if (loggedIn && online) {
            (app.vaultAccounts() as? ApiResult.Ok)?.let { r ->
                vaultTotal = r.value.accounts.size
                vaultGraduated = r.value.accounts.count { it.graduated }
            }
            vaultDue = (app.vaultChallenge() as? ApiResult.Ok)?.value?.siteId != null
        }
    }

    val streakState = remember(xp, flags) { app.load("streak", StreakState.serializer()) ?: StreakState() }
    val ui = HomeUi(
        firstName = profile.firstName,
        xp = xp,
        streak = Streak.display(streakState, Streak.localDay(app.now())),
        dailyMinutes = profile.dailyMinutes,
        lessonStarted = LESSON_1_ID in flags.lessonSections,
        lessonDone = LESSON_1_ID in flags.lessonsDone,
        lesson2Started = LESSON_2_ID in flags.lessonSections,
        lesson2Done = LESSON_2_ID in flags.lessonsDone,
        vaultChallengeDue = vaultDue,
        vaultGraduated = vaultGraduated,
        vaultTotal = vaultTotal,
        online = online,
        loggedIn = loggedIn,
        sessionExpired = expired,
        field = field,
    )
    HomeContent(
        ui,
        HomeActions(
            lesson = { app.navigate(Screen.Lesson(LESSON_1_ID, app.savedSection(LESSON_1_ID))) },
            practise = { app.navigate(Screen.Practice()) },
            reviewLesson = { app.navigate(Screen.Lesson(LESSON_1_ID, 0)) },
            leaderboard = { app.navigate(Screen.Leaderboard) },
            about = { app.navigate(Screen.About) },
            settings = { app.navigate(Screen.Settings) },
            suspicious = {
                showSuspicious = true
                scope.launch { app.reportSuspicious() }
            },
            signIn = { app.navigate(Screen.Email) },
            openReveal = { field.revealToken?.let { app.navigate(Screen.Reveal(it)) } },
            practiseFocus = { app.navigate(Screen.Practice(focusWeakest = true)) },
            lesson2 = { app.navigate(Screen.Lesson(LESSON_2_ID, app.savedSection(LESSON_2_ID))) },
            vaultDrill = { app.navigate(Screen.VaultDrill(0)) },
            vaultChallenge = { app.navigate(Screen.VaultChallenge) },
        ),
    )
    if (showSuspicious) {
        SuspiciousDialog(
            onDismiss = { showSuspicious = false },
            onPractise = {
                showSuspicious = false
                app.navigate(Screen.Practice())
            },
        )
    }
}

@Composable
fun HomeContent(ui: HomeUi, actions: HomeActions) {
    ScreenColumn {
        SectionHeading(
            if (ui.firstName.isNullOrBlank()) stringResource(Res.string.home_hello_guest) else stringResource(Res.string.home_hello, ui.firstName),
            large = true,
            intro = stringResource(Res.string.home_intro),
        )
        if (ui.sessionExpired) {
            Banner(stringResource(Res.string.shell_session_expired), actionText = stringResource(Res.string.shell_sign_in), onAction = actions.signIn)
        }
        when (ui.field.status) {
            "caught" -> DebriefCard(
                caught = true,
                buttonText = stringResource(if (ui.field.revealToken != null) Res.string.home_debrief_open else Res.string.home_debrief_practise),
                onClick = if (ui.field.revealToken != null) actions.openReveal else actions.practiseFocus,
            )
            "dodged" -> DebriefCard(caught = false, buttonText = stringResource(Res.string.home_debrief_practise), onClick = actions.practiseFocus)
        }
        TeaCard(Modifier.fillMaxWidth()) {
            Text(stringResource(Res.string.home_lesson_title), style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
            if (ui.lessonDone) {
                Text(stringResource(Res.string.home_lesson_done), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                TeaButton(stringResource(Res.string.home_practise), actions.practise, Modifier.fillMaxWidth())
                TeaButton(stringResource(Res.string.home_review_lesson), actions.reviewLesson, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
            } else {
                TeaButton(
                    stringResource(if (ui.lessonStarted) Res.string.home_continue_lesson else Res.string.home_start_lesson),
                    actions.lesson,
                    Modifier.fillMaxWidth(),
                )
                TeaButton(stringResource(Res.string.home_practise), actions.practise, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
            }
        }
        // One of the five is due: the test of whether the manager habit stuck. Shown above lesson 2,
        // because it is time-sensitive and the lesson is not.
        if (ui.vaultChallengeDue) {
            TeaCard(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.home_vault_title), style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
                Text(stringResource(Res.string.home_vault_body), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                TeaButton(stringResource(Res.string.home_vault_action), actions.vaultChallenge, Modifier.fillMaxWidth())
            }
        }
        if (ui.lessonDone) {
            TeaCard(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.home_lesson2_title), style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
                if (ui.vaultTotal > 0) {
                    Text(
                        stringResource(Res.string.home_vault_done, ui.vaultGraduated, ui.vaultTotal),
                        style = TeaTheme.type.body,
                        color = TeaTheme.colors.inkSoft,
                    )
                }
                TeaButton(
                    stringResource(
                        if (ui.lesson2Started) Res.string.home_lesson2_continue else Res.string.home_lesson2_start,
                    ),
                    actions.lesson2,
                    Modifier.fillMaxWidth(),
                )
                TeaButton(
                    stringResource(Res.string.home_lesson2_drill),
                    actions.vaultDrill,
                    Modifier.fillMaxWidth(),
                    variant = TeaButtonVariant.Secondary,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            StatCard(
                stringResource(Res.string.home_streak_label),
                pluralStringResource(Res.plurals.home_streak_days, ui.streak, ui.streak),
                Modifier.weight(1f),
                note = if (ui.streak == 0) stringResource(Res.string.home_streak_none) else null,
            )
            StatCard(
                stringResource(Res.string.home_xp_label),
                stringResource(Res.string.home_xp, ui.xp),
                Modifier.weight(1f),
                note = stringResource(Res.string.home_goal, ui.dailyMinutes),
            )
        }
        if (!ui.loggedIn && !ui.sessionExpired) {
            TeaCard(Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.home_guest_title), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
                Text(stringResource(Res.string.home_guest_body), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
                TeaButton(stringResource(Res.string.home_guest_action), actions.signIn, variant = TeaButtonVariant.Secondary, enabled = ui.online)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            if (ui.online) {
                TeaButton(stringResource(Res.string.home_leaderboard), actions.leaderboard, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
            }
            TeaButton(stringResource(Res.string.home_about), actions.about, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
            TeaButton(stringResource(Res.string.home_settings), actions.settings, Modifier.fillMaxWidth(), variant = TeaButtonVariant.Secondary)
            TeaButton(
                stringResource(Res.string.home_suspicious), actions.suspicious, Modifier.fillMaxWidth(),
                variant = TeaButtonVariant.Secondary, icon = TeaIcons.Info,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier, note: String? = null) {
    TeaCard(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
        Text(value, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        if (note != null) Text(note, style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
    }
}

/** Spider's debrief after a practice email: "caught" (clicked) or "dodged" (reported). Blame-free. */
@Composable
fun DebriefCard(caught: Boolean, buttonText: String, onClick: () -> Unit) {
    SpiderPanel {
        SectionHeading(stringResource(if (caught) Res.string.home_debrief_caught_title else Res.string.home_debrief_dodged_title))
        PersonaBubble(
            Persona.SPIDER,
            if (caught) Mood.SMUG else Mood.WORRIED,
            stringResource(if (caught) Res.string.home_debrief_caught_body else Res.string.home_debrief_dodged_body),
        )
        TeaButton(buttonText, onClick)
    }
}

/** "Got a suspicious email?": the same careful answer whether or not it was ours. */
@Composable
fun SuspiciousDialog(onDismiss: () -> Unit, onPractise: () -> Unit) {
    TeaDialog(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.home_suspicious),
        confirmText = stringResource(Res.string.home_suspicious_practise),
        onConfirm = onPractise,
    ) {
        SuspiciousBody()
    }
}

@Composable
fun SuspiciousBody() {
    Text(stringResource(Res.string.home_suspicious_body), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
    FourStepsList()
    Text(stringResource(Res.string.home_suspicious_never), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
}
