package tea.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tea.ui.screens.vault.InputMethodWatcher
import tea.ui.screens.vault.MeterLevel
import tea.ui.screens.vault.MeterProblem
import tea.ui.screens.vault.foldLeet
import tea.ui.screens.vault.meterState

private val CATLAND = listOf("catland", "cat land", "cat")

class PasswordMeterTest {

    @Test
    fun emptyIsNeitherGoodNorBad() {
        val s = meterState("", CATLAND, emptySet())
        assertEquals(MeterLevel.Empty, s.level)
        assertNull(s.problem)
        assertEquals(0f, s.fraction)
    }

    @Test
    fun theSiteOwnNameIsCaughtLocallyEvenInLeetspeak() {
        for (pw in listOf("catland forever", "my c4tland pass", "a quiet c4t sits")) {
            assertEquals(MeterProblem.ContainsSite, meterState(pw, CATLAND, emptySet()).problem, pw)
        }
    }

    @Test
    fun aShortSiteWordOnlyCountsAsAWholeWord() {
        // blocking "cat" anywhere would refuse these, teach nothing, and make the drill feel broken
        for (pw in listOf("scattered velvet keys", "delicate amber hinge", "a catalogue of moths")) {
            assertNull(meterState(pw, CATLAND, emptySet()).problem, pw)
        }
        // a longer site word still matches anywhere, because nobody types it by accident
        assertEquals(
            MeterProblem.ContainsSite,
            meterState("myspiderwebpass", listOf("spiderweb", "spider web", "spider"), emptySet()).problem,
        )
    }

    @Test
    fun reuseWithinTheDrillIsCaughtBeforeTheServerIsAsked() {
        val others = setOf("velvet otter marble")
        assertEquals(MeterProblem.ReusedInDrill, meterState("velvet otter marble", CATLAND, others).problem)
        assertNull(meterState("harbour lantern quilt", CATLAND, others).problem)
    }

    @Test
    fun lengthComesFirst() {
        assertEquals(MeterProblem.TooShort, meterState("short", CATLAND, emptySet()).problem)
        assertEquals(MeterProblem.TooShort, meterState("123456789", CATLAND, emptySet()).problem)
        assertNull(meterState("velvet otter marble", CATLAND, emptySet()).problem)
    }

    @Test
    fun patternsAndRepeatsAreNotStrengthHoweverLong() {
        assertEquals(MeterProblem.Weak, meterState("qwertyuiopqwerty", CATLAND, emptySet()).problem)
        assertEquals(MeterProblem.Weak, meterState("aaaaaaaaaaaaaaaa", CATLAND, emptySet()).problem)
        assertEquals(MeterProblem.Weak, meterState("asdfghjklasdf", CATLAND, emptySet()).problem)
    }

    @Test
    fun aPassphraseReadsAsGood() {
        val s = meterState("velvet otter marble hinge", CATLAND, emptySet())
        assertEquals(MeterLevel.Good, s.level)
        assertNull(s.problem)
        assertEquals(1f, s.fraction)
    }

    @Test
    fun leetFoldingMatchesTheServers() {
        assertEquals("catland", foldLeet("C4tL4nd"))
        assertEquals("ioooo", foldLeet("1000O"))
        assertEquals("sesame", foldLeet("$3same"))
    }
}

class InputMethodWatcherTest {
    /** A clock we drive by hand, so the test never depends on real time. */
    private class Ticker(var ms: Long = 0) {
        fun now() = ms
    }

    @Test
    fun oneCharacterAtATimeIsTyping() {
        val t = Ticker()
        val w = InputMethodWatcher(t::now)
        w.onFocus()
        var value = ""
        for (c in "velvet otter") {
            t.ms += 120
            val next = value + c
            w.onChange(value, next)
            value = next
        }
        assertEquals(InputMethodWatcher.INPUT_TYPED, w.method())
        assertEquals(120, w.timeToFirstInputMs())
    }

    @Test
    fun oneBigJumpIsAPaste() {
        val t = Ticker()
        val w = InputMethodWatcher(t::now)
        w.onFocus()
        t.ms += 3_000 // they went to the manager and came back
        w.onChange("", "velvet otter marble")
        assertEquals(InputMethodWatcher.INPUT_PASTE, w.method())
        assertEquals(3_000, w.timeToFirstInputMs())
    }

    @Test
    fun anInstantFullFieldIsAutofill() {
        val t = Ticker()
        val w = InputMethodWatcher(t::now)
        w.onFocus()
        t.ms += 80 // the manager filled it as soon as the field was focused
        w.onChange("", "velvet otter marble")
        assertEquals(InputMethodWatcher.INPUT_AUTOFILL, w.method())
    }

    @Test
    fun typingThenPastingTheRestStillCountsAsPasted() {
        val t = Ticker()
        val w = InputMethodWatcher(t::now)
        w.onFocus()
        t.ms += 200
        w.onChange("", "v")
        t.ms += 900
        w.onChange("v", "velvet otter marble")
        assertEquals(InputMethodWatcher.INPUT_PASTE, w.method())
    }

    @Test
    fun nothingTypedSaysNothing() {
        val w = InputMethodWatcher { 0 }
        w.onFocus()
        assertNull(w.method())
        assertNull(w.timeToFirstInputMs())
    }

    @Test
    fun theWatcherKeepsNoText() {
        // the API takes the old and new values but exposes only a method and a duration:
        // there is nowhere for a keystroke stream or the password itself to be kept
        val w = InputMethodWatcher { 0 }
        w.onFocus()
        w.onChange("", "secret passphrase")
        assertTrue(w.method() in setOf(InputMethodWatcher.INPUT_PASTE, InputMethodWatcher.INPUT_AUTOFILL))
    }
}
