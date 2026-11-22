package tea.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import tea.model.VaultFindingsDto
import tea.ui.screens.vault.guessableFacts
import tea.ui.screens.vault.moved
import tea.ui.screens.vault.tierChecks

/**
 * The debrief's tiering question (docs/password-drill.md): the learner sorts the five sites by what
 * each holds, and only then do we lay that order next to the strength they actually chose.
 */
class VaultDebriefTest {

    /**
     * The server's own weakest-first list, so strength has one definition on both sides. Here the
     * Zoo Card Club — the one holding a card number — carries the weakest of the five.
     */
    private val guessOrder = listOf("zoocard", "catland", "dogsden", "spiderweb", "seaturtle")

    @Test
    fun aSiteValuedHighlyAndGuardedWeaklyIsTheMismatch() {
        // they say the card club would hurt most, and it is the one guarded worst
        val order = listOf("zoocard", "seaturtle", "spiderweb", "dogsden", "catland")
        val checks = tierChecks(order, guessOrder)

        val zoo = checks.single { it.siteId == "zoocard" }
        assertEquals(1, zoo.valueRank)
        assertEquals(5, zoo.strengthRank)
        assertTrue(zoo.weakerThanItsPlace)

        // the worst offender is the one the screen names first
        assertEquals("zoocard", checks.filter { it.weakerThanItsPlace }.maxByOrNull { it.gap }?.siteId)
    }

    @Test
    fun anOrderThatMatchesTheirPasswordsHasNothingToFlag() {
        // strongest first is the reverse of the guesser's order
        val checks = tierChecks(guessOrder.reversed(), guessOrder)
        assertTrue(checks.none { it.weakerThanItsPlace }, "an aligned order should flag nothing")
        assertEquals(1, checks.first().strengthRank)
    }

    @Test
    fun aSiteWithoutFindingsIsLeftOutRatherThanRankedWrongly() {
        val checks = tierChecks(listOf("catland", "unknown"), guessOrder)
        assertEquals(listOf("catland"), checks.map { it.siteId })
        assertNull(checks.firstOrNull { it.siteId == "unknown" })
    }

    @Test
    fun movingAnEntryStaysInsideTheList() {
        val list = listOf("a", "b", "c")
        assertEquals(listOf("b", "a", "c"), list.moved(1, -1))
        assertEquals(listOf("a", "c", "b"), list.moved(1, 1))
        assertEquals(list, list.moved(0, -1), "the top entry cannot move up")
        assertEquals(list, list.moved(2, 1), "the last entry cannot move down")
    }

    @Test
    fun guessableFactsCountOnlyWhatTheFindingsSay() {
        val plain = VaultFindingsDto(length = 19, classes = 1, score = 4)
        assertEquals(0, plain.guessableFacts())
        assertEquals(
            3,
            plain.copy(containsName = true, containsYear = true, keyboardPattern = "qwerty").guessableFacts(),
        )
        // nothing here can come from the password itself: it is four booleans and a pattern name
        assertFalse(plain.copy(pwned = true).guessableFacts() > 0)
    }
}
