package tea.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tea.model.Briefing
import tea.model.Challenge
import tea.model.Decision
import tea.model.DecisionOption
import tea.model.DecisionType
import tea.model.Dial
import tea.model.Outcome
import tea.model.Outcomes
import tea.model.Persona
import tea.model.Playbook

/**
 * The Boardroom crisis numbers (content/challenges/boardroom-crisis), text stripped. The scoring
 * must match the HTML mockup (docs/challenge-boardroom-crisis.md §4.3).
 */
object BoardroomFixture {
    private fun ok(fx: Map<String, Int>? = null, rv: String? = null) = DecisionOption("", "", ok = true, fx = fx, why = fx?.let { "" }, rv = rv)
    private fun bad(fx: Map<String, Int>, rv: String) = DecisionOption("", "", ok = false, fx = fx, why = "", rv = rv)
    private fun decision(id: String, time: String, type: DecisionType, gain: Map<String, Int>, vararg options: DecisionOption) =
        Decision(id, time, Persona.CAT, type, id, "", null, options.toList(), gain, "", "", "")

    private val outcome = Outcome(Persona.TURTLE, "", "", "")

    val challenge = Challenge(
        id = "boardroom-crisis", language = "en", title = "", pill = "", headline = "", subtitle = "",
        deadline = "14:00",
        startMeters = mapOf("trust" to 70, "cash" to 75, "systems" to 30, "legal" to 70),
        dials = listOf(Dial("trust", "Trust"), Dial("cash", "Cash"), Dial("systems", "Systems"), Dial("legal", "Legal standing")),
        briefing = Briefing(turtleIntro = ""),
        playbook = Playbook(cards = emptyList()),
        decisions = listOf(
            decision(
                "d1", "08:15", DecisionType.SINGLE, mapOf("systems" to 15),
                ok(),
                bad(mapOf("legal" to -20, "systems" to -10), "cat0"),
                bad(mapOf("systems" to -25, "cash" to -10), "cat0"),
                bad(mapOf("systems" to -20, "legal" to -10), "cat0"),
            ),
            decision(
                "d2", "08:50", DecisionType.MULTI, mapOf("legal" to 10),
                ok(mapOf("systems" to -15, "trust" to -5), "cat1"),
                ok(mapOf("legal" to -15, "cash" to -15), "turtle0"),
                ok(mapOf("systems" to -15, "legal" to -5), "turtle0"),
                bad(mapOf("trust" to -25, "legal" to -15), "cat2"),
                bad(mapOf("systems" to -20, "cash" to -10), "turtle0"),
            ),
            decision(
                "d3", "09:40", DecisionType.SINGLE, mapOf("cash" to 10, "legal" to 5),
                ok(),
                bad(mapOf("cash" to -30, "legal" to -10), "turtle0"),
                bad(mapOf("cash" to -15, "legal" to -15), "turtle0"),
                bad(mapOf("legal" to -20, "trust" to -10), "turtle0"),
            ),
            decision(
                "d4", "11:15", DecisionType.SINGLE, mapOf("trust" to 15, "legal" to 10),
                ok(),
                bad(mapOf("legal" to -25, "trust" to -10), "turtle1"),
                bad(mapOf("trust" to -30, "legal" to -20), "turtle1"),
                bad(mapOf("trust" to -30, "legal" to -10), "cat2"),
            ),
            decision(
                "d5", "13:10", DecisionType.MULTI, mapOf("systems" to 45, "trust" to 5),
                ok(mapOf("systems" to -20, "legal" to -10), "turtle2"),
                ok(mapOf("systems" to -20, "cash" to -10), "turtle2"),
                ok(mapOf("trust" to -10, "legal" to -5), "turtle2"),
                bad(mapOf("systems" to -25, "cash" to -15), "turtle2"),
                bad(mapOf("trust" to -20, "legal" to -10), "turtle2"),
            ),
        ),
        outcomes = Outcomes(outcome, outcome, outcome),
    )

    /** The sound selection of every decision, in order. */
    val perfect: List<List<Int>> = challenge.decisions.map { d -> d.options.indices.filter { d.options[it].ok } }
}

class ChallengeEngineTest {
    private val c = BoardroomFixture.challenge
    private fun d(i: Int) = c.decisions[i]

    @Test
    fun perfectRunEndsInTheNineties() {
        val results = BoardroomFixture.perfect.mapIndexed { i, sel -> ChallengeEngine.lock(d(i), sel) }
        assertTrue(results.all { it.correct })
        assertEquals(mapOf("trust" to 90, "cash" to 85, "systems" to 90, "legal" to 95), ChallengeEngine.meters(c, results))
        assertEquals(ChallengeOutcome.CONTAINED, ChallengeEngine.outcome(c, results.count { it.correct }))
    }

    @Test
    fun wipeAndRebuildCostsLegalAndSystems() {
        val r = ChallengeEngine.lock(d(0), listOf(1))
        assertFalse(r.correct)
        assertEquals(listOf("cat0"), r.reviews)
        assertEquals(mapOf("legal" to 50, "systems" to 20, "trust" to 70, "cash" to 75), ChallengeEngine.meters(c, listOf(r)))
    }

    @Test
    fun missingSoundOptionsInAMultiCostsToo() {
        val r = ChallengeEngine.lock(d(1), listOf(1)) // only "legal and the insurer"
        assertFalse(r.correct)
        val hits = ChallengeEngine.hits(d(1), listOf(1))
        assertEquals(listOf(0, 2), hits.map { it.index })
        assertTrue(hits.none { it.picked })
        assertEquals(mapOf("systems" to -30, "trust" to -5, "legal" to -5), r.cost)
        assertEquals(listOf("cat1", "turtle0"), r.reviews)
    }

    @Test
    fun multiIsSoundOnlyOnExactMatch() {
        assertTrue(ChallengeEngine.isCorrect(d(1), listOf(0, 1, 2)))
        assertTrue(ChallengeEngine.isCorrect(d(1), listOf(2, 1, 0)))
        assertFalse(ChallengeEngine.isCorrect(d(1), listOf(0, 1, 2, 3)))
        assertFalse(ChallengeEngine.isCorrect(d(1), listOf(0, 1)))
    }

    @Test
    fun pickingAWrongOneInAMultiShowsAsPicked() {
        val hits = ChallengeEngine.hits(d(1), listOf(0, 1, 2, 3))
        assertEquals(1, hits.size)
        assertTrue(hits.single().picked)
        assertEquals(3, hits.single().index)
    }

    @Test
    fun metersAreClamped() {
        val worst = listOf(listOf(2), listOf(3, 4), listOf(1), listOf(2), listOf(3, 4))
        val results = worst.mapIndexed { i, sel -> ChallengeEngine.lock(d(i), sel) }
        val m = ChallengeEngine.meters(c, results)
        assertTrue(m.values.all { it in 0..100 }, "$m")
        assertEquals(0, m.getValue("systems"))
        assertEquals(ChallengeOutcome.WRECKED, ChallengeEngine.outcome(c, 0))
        assertEquals(100, ChallengeEngine.apply(mapOf("a" to 95), mapOf("a" to 20)).getValue("a"))
    }

    @Test
    fun levelBoundaries() {
        assertEquals(DialLevel.CRITICAL, ChallengeEngine.levelOf(39))
        assertEquals(DialLevel.STRAINED, ChallengeEngine.levelOf(40))
        assertEquals(DialLevel.STRAINED, ChallengeEngine.levelOf(69))
        assertEquals(DialLevel.STEADY, ChallengeEngine.levelOf(70))
    }

    @Test
    fun outcomeTiers() {
        assertEquals(ChallengeOutcome.CONTAINED, ChallengeEngine.outcome(c, 5))
        assertEquals(ChallengeOutcome.BRUISED, ChallengeEngine.outcome(c, 4))
        assertEquals(ChallengeOutcome.BRUISED, ChallengeEngine.outcome(c, 3))
        assertEquals(ChallengeOutcome.WRECKED, ChallengeEngine.outcome(c, 2))
    }

    @Test
    fun minutesLeft() {
        assertEquals(345, ChallengeEngine.minutesLeft(c, "08:15"))
        assertEquals(50, ChallengeEngine.minutesLeft(c, "13:10"))
        assertEquals(0, ChallengeEngine.minutesLeft(c, "15:00"))
    }

    @Test
    fun orderIsAStablePermutation() {
        val a = ChallengeEngine.order(d(1), 42L)
        assertEquals(a, ChallengeEngine.order(d(1), 42L))
        assertEquals(d(1).options.indices.toSet(), a.toSet())
    }

    @Test
    fun savedRunMustFitTheContent() {
        val good = ChallengeRun(1, listOf(ChallengeEngine.lock(d(0), listOf(0))))
        assertNotNull(ChallengeEngine.validRun(c, good))
        assertNull(ChallengeEngine.validRun(c, ChallengeRun(1, listOf(DecisionResult("gone", listOf(0), true)))))
        assertNull(ChallengeEngine.validRun(c, ChallengeRun(1, listOf(DecisionResult("d1", listOf(9), true)))))
        assertNull(ChallengeEngine.validRun(c, ChallengeRun(1, listOf(DecisionResult("d1", listOf(0, 1), false)))))
        assertNull(ChallengeEngine.validRun(c, null))
    }
}
