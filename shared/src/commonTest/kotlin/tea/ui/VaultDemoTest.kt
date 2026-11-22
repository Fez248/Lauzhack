package tea.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import tea.app.AppState
import tea.app.FakePlatformInfo
import tea.app.Screen
import tea.content.ContentRepository
import tea.model.LESSON_2_DEMO_ID
import tea.model.LESSON_2_ID
import tea.model.Persona
import tea.model.VaultSite
import tea.model.isPasswordLesson
import tea.net.FakeApi
import tea.store.InMemoryKeyValueStore
import tea.store.LocalState
import tea.ui.screens.home.startPasswordDemo
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.vault.VaultDemo

/**
 * The one-minute demo (Home's second button under the password chapter): the same drill, cut to
 * two sites, so a stage slot can show the whole loop.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class VaultDemoTest {

    private val five = listOf(
        VaultSite("catland", "Cat Land", "catland.example", "A pet forum. Nothing but posts.", Persona.CAT),
        VaultSite("dogsden", "Dog's Den", "dogsden.example", "A shop. A saved card.", Persona.DOG),
        VaultSite("spiderweb", "Spider Web Design", "spiderweb.example", "A studio account.", Persona.SPIDER),
        VaultSite("zoocard", "Zoo Card Club", "zoocard.example", "A membership card and a card number.", null),
        VaultSite("seaturtle", "Sea Turtle Fan Club", "seaturtle.example", "A fan page.", Persona.TURTLE),
    )

    @Test
    fun theDemoRunsTwoSitesAndTheRealOneRunsFive() {
        assertEquals(five, VaultDemo.sitesFor(five, demo = false))

        val demo = VaultDemo.sitesFor(five, demo = true)
        assertEquals(listOf("catland", "zoocard"), demo.map { it.siteId })
        // the pair is the contrast the debrief's tiering question needs: nothing, and a card number
        assertTrue("card number" in demo.last().holds)
    }

    @Test
    fun anUnknownSiteListStillGivesTheDemoTwoSites() {
        // the five come from the server, so the demo must not end up with an empty run if they change
        val renamed = five.map { it.copy(siteId = it.siteId + "-2") }
        assertEquals(2, VaultDemo.sitesFor(renamed, demo = true).size)
    }

    @Test
    fun bothVersionsOfTheLessonHandOverToTheDrill() {
        assertTrue(isPasswordLesson(LESSON_2_ID))
        assertTrue(isPasswordLesson(LESSON_2_DEMO_ID))
        assertTrue(!isPasswordLesson("email-01"))
    }

    @Test
    fun theDemoButtonOpensTheShortLessonAndSetsTheMode() = runTest {
        val app = app()
        assertEquals(Screen.Lesson(LESSON_2_DEMO_ID, 0), startPasswordDemo(app))
        assertEquals(true, app.load(LearnKeys.VAULT_DEMO, LearnKeys.BOOL))
    }

    @Test
    fun aFinishedRunStartsAtTheLoginTestSoTheDemoCanBeShownAgain() = runTest {
        val app = app()
        app.save(LearnKeys.VAULT_DEMO_DONE, LearnKeys.BOOL, true)
        assertEquals(Screen.VaultChallenge, startPasswordDemo(app))
        assertEquals(true, app.load(LearnKeys.VAULT_DEMO, LearnKeys.BOOL))
    }

    @Test
    fun finishingTheDemoDoesNotTellTheRealChapterItsFiveAreDone() = runTest {
        // the two runs keep their own "finished" flag, so the chapter still opens its own lesson
        val app = app()
        app.save(LearnKeys.vaultDone(demo = true), LearnKeys.BOOL, true)
        assertEquals(true, app.load(LearnKeys.VAULT_DEMO_DONE, LearnKeys.BOOL))
        assertEquals(null, app.load(LearnKeys.VAULT_DRILL_DONE, LearnKeys.BOOL))
    }

    private fun kotlinx.coroutines.test.TestScope.app(): AppState = AppState(
        platform = FakePlatformInfo(),
        local = LocalState(InMemoryKeyValueStore()),
        content = ContentRepository(loadBytes = { error("no content in tests") }),
        scope = backgroundScope,
        apiFactory = { _, token -> FakeApi().also { it.token = token } },
        debug = true,
    )
}
