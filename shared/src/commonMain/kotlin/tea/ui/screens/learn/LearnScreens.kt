package tea.ui.screens.learn

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import tea.app.TeaApp

/*
 * Entry points of the learning screens (plan §9.3–9.6), called by the app shell's router.
 * STUBS: the learning-screens agent replaces the bodies (and may move them into the
 * placement/, lesson/ and practice/ packages as long as these signatures stay in this file).
 */

@Composable
fun PlacementIntroScreen(app: TeaApp) {
    Text("PlacementIntro (stub)")
}

@Composable
fun PlacementScreen(app: TeaApp) {
    Text("Placement (stub)")
}

@Composable
fun PlacementResultScreen(app: TeaApp) {
    Text("PlacementResult (stub)")
}

/** Renders section [section] of lesson [id], including the practice and readiness sections. */
@Composable
fun LessonScreen(app: TeaApp, id: String, section: Int) {
    Text("Lesson $id / $section (stub)")
}

/** "Practise again": one practice section from the selector, outside a lesson. */
@Composable
fun FreePracticeScreen(app: TeaApp, focusWeakest: Boolean) {
    Text("Practice (stub)")
}

@Composable
fun PracticeSummaryScreen(app: TeaApp) {
    Text("PracticeSummary (stub)")
}
