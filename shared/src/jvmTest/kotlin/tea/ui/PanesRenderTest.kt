package tea.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import tea.app.AppState
import tea.app.FakePlatformInfo
import tea.app.Screen
import tea.content.ContentRepository
import tea.model.LESSON_1_ID
import tea.model.PlacementResult
import tea.model.Profile
import tea.model.StepKey
import tea.model.TERMS_VERSION
import tea.net.FakeApi
import tea.store.InMemoryKeyValueStore
import tea.store.LocalFlags
import tea.store.LocalState
import tea.store.Session
import tea.ui.screens.AppRoot
import tea.ui.screens.onboarding.OnboardingQuestion
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * One screenshot of EVERY pane of the app, through the real [AppRoot], to
 * `shared/build/screenshots/pane_NN_<name>.png`. The screenshots workflow
 * (`.github/workflows/screenshots.yml`) turns them into a browsable doc.
 *
 * [name] is an exhaustive `when` over [Screen]: adding a new screen breaks this file until the
 * screen is added to [panes] too, so the doc never silently misses a pane.
 */
class PanesRenderTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val now = Instant.parse("2026-11-21T14:05:00Z")
    private val placement = PlacementResult(
        level = 2, stepP = StepKey.entries.associateWith { 0.5 }, hitRate = 0.8, falseAlarmRate = 0.1,
        calibration = 0.2, overconfident = false, totalMs = 180_000, strength = StepKey.WHO, focus = StepKey.VERIFY,
    )
    private val profile = Profile(firstName = "Ada", lastName = "Lovelace", displayName = "Ada L.", placement = placement, country = "CH")
    private val flags = LocalFlags(termsVersion = TERMS_VERSION, onboardingDone = true, passwordSet = true)

    @AfterTest
    fun close() = scope.cancel()

    private fun name(screen: Screen): String = when (screen) {
        Screen.Language -> "language"
        Screen.Email -> "email"
        is Screen.Code -> "code"
        Screen.Name -> "name"
        Screen.Password -> "password"
        is Screen.Onboarding -> "onboarding_${screen.q}"
        Screen.Terms -> "terms"
        Screen.PlacementIntro -> "placement_intro"
        Screen.Placement -> "placement"
        Screen.PlacementResult -> "placement_result"
        Screen.Home -> "home"
        is Screen.Lesson -> "lesson_${screen.section}"
        is Screen.Practice -> if (screen.focusWeakest) "practice_focus_weakest" else "practice"
        Screen.PracticeSummary -> "practice_summary"
        is Screen.Reveal -> "reveal"
        Screen.About -> "about"
        Screen.Settings -> "settings"
        Screen.Leaderboard -> "leaderboard"
        Screen.Gallery -> "gallery"
    }

    /** Every pane, in the order a learner meets them. */
    private val panes: List<Screen> = buildList {
        add(Screen.Language)
        add(Screen.Email)
        add(Screen.Code("ada@mail.example"))
        add(Screen.Name)
        add(Screen.Password)
        OnboardingQuestion.entries.indices.forEach { add(Screen.Onboarding(it)) }
        add(Screen.Terms)
        add(Screen.PlacementIntro)
        add(Screen.Placement)
        add(Screen.PlacementResult)
        add(Screen.Home)
        // Lesson 1 sections (rules, tools, attacks, status, practice, readiness).
        (0..5).forEach { add(Screen.Lesson(LESSON_1_ID, it)) }
        add(Screen.Practice())
        add(Screen.PracticeSummary)
        add(Screen.Reveal("AbCdEf123456789012345"))
        add(Screen.Settings)
        add(Screen.About)
        add(Screen.Leaderboard)
        add(Screen.Gallery)
    }

    private fun app(screen: Screen): AppState {
        val store = InMemoryKeyValueStore()
        LocalState(store).apply {
            this.profile = this@PanesRenderTest.profile
            this.flags = this@PanesRenderTest.flags
            session = Session("tok", "ada@mail.example", "u1")
            pendingEmail = "ada@mail.example"
        }
        val api = FakeApi().apply { onlineFlow.value = true }
        val app = AppState(
            platform = FakePlatformInfo(),
            local = LocalState(store),
            content = ContentRepository(),
            scope = scope,
            apiFactory = { _, token -> api.also { it.token = token } },
            clock = object : Clock { override fun now() = now },
        )
        app.navigateRoot(screen)
        return app
    }

    @Test
    fun everyPane() {
        val written = panes.mapIndexed { i, screen ->
            val file = "pane_${(i + 1).toString().padStart(2, '0')}_${name(screen)}"
            val tall = screen is Screen.Lesson || screen is Screen.Practice || screen == Screen.Settings || screen == Screen.About
            val app = app(screen)
            Screenshots.render(file, widthDp = 760, heightDp = if (tall) 2400 else 1400, density = 1.25f, tileHeightPx = Int.MAX_VALUE) {
                AppRoot(app)
            }.first()
        }
        assertTrue(written.all { it.length() > 0 })
    }
}
