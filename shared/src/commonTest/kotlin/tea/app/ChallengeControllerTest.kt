package tea.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tea.content.ContentRepository
import tea.engine.BoardroomFixture
import tea.engine.ChallengeBest
import tea.engine.ChallengeOutcome
import tea.engine.Xp
import tea.model.EventTypes
import tea.ui.screens.challenge.ChallengeController
import tea.ui.screens.learn.LearnKeys

class ChallengeControllerTest {
    private val challenge = BoardroomFixture.challenge
    private fun app() = FakeTeaApp(ContentRepository(loadBytes = { error("no content") }))

    @Test
    fun lockIsSavedAndSurvivesAReload() {
        val app = app()
        val c = ChallengeController(app, challenge) { 7L }
        assertNull(c.lock(1, listOf(0)), "only the open decision can be locked")
        assertNull(c.lock(0, emptyList()), "an empty selection is ignored")
        c.lock(0, listOf(1))
        assertEquals(1, c.current)

        val reloaded = ChallengeController(app, challenge) { 99L }
        assertEquals(1, reloaded.current)
        assertEquals(7L, reloaded.run.seed)
        assertEquals(listOf(1), reloaded.result(0)?.selected)
        assertNull(reloaded.lock(0, listOf(0)), "no takebacks after a reload")
        assertEquals(mapOf("trust" to 70, "cash" to 75, "systems" to 20, "legal" to 50), reloaded.meters)
    }

    @Test
    fun completionGivesXpOnceAndKeepsTheBest() {
        val app = app()
        val c = ChallengeController(app, challenge) { 1L }
        // First run: 3 sound (bruised).
        listOf(listOf(0), listOf(1), listOf(0), listOf(1), listOf(0, 1, 2)).forEachIndexed { i, sel -> c.lock(i, sel) }
        assertTrue(c.complete)
        assertEquals(3, c.good)
        assertEquals(ChallengeOutcome.BRUISED, c.outcome)
        assertEquals(Xp.CHALLENGE_COMPLETED, app.xp.value)
        assertEquals(1, app.events.count { it.type == EventTypes.CHALLENGE_COMPLETED })

        // Replay perfectly: no new XP, best improves.
        c.replay()
        assertEquals(0, c.current)
        BoardroomFixture.perfect.forEachIndexed { i, sel -> c.lock(i, sel) }
        assertEquals(Xp.CHALLENGE_COMPLETED, app.xp.value)
        assertEquals(1, app.events.count { it.type == EventTypes.CHALLENGE_COMPLETED })
        assertEquals(ChallengeBest(5, 5, ChallengeOutcome.CONTAINED), app.load(LearnKeys.challengeBest(challenge.id), ChallengeBest.serializer()))

        // A worse replay keeps the best.
        c.replay()
        listOf(listOf(1), listOf(3), listOf(1), listOf(1), listOf(3)).forEachIndexed { i, sel -> c.lock(i, sel) }
        assertEquals(5, app.load(LearnKeys.challengeBest(challenge.id), ChallengeBest.serializer())?.good)
    }
}
