package tea.server.vault

import tea.model.Persona
import tea.model.VaultSite

/**
 * The five fictional sites of the drill (docs/password-drill.md). All `.example` domains, invented
 * pages, no real brand or service imitated (plan §17).
 *
 * Four belong to the cast, so each sign-up can wear that persona's colours and avatar; the Zoo Card
 * Club belongs to nobody and gets its own. They differ in what they hold so the debrief can ask the
 * second question: which of these would actually hurt if it leaked?
 */
object VaultSites {
    val all: List<VaultSite> = listOf(
        VaultSite(
            siteId = "catland",
            shownAs = "Cat Land",
            domain = "catland.example",
            holds = "A pet forum. Nothing but posts.",
            persona = Persona.CAT,
            words = listOf("catland", "cat land", "cat"),
        ),
        VaultSite(
            siteId = "dogsden",
            shownAs = "Dog's Den",
            domain = "dogsden.example",
            holds = "A shop. A saved card.",
            persona = Persona.DOG,
            words = listOf("dogsden", "dogs den", "dog"),
        ),
        VaultSite(
            siteId = "spiderweb",
            shownAs = "Spider Web Design",
            domain = "spiderweb.example",
            holds = "A studio account. Your client list and invoices.",
            persona = Persona.SPIDER,
            words = listOf("spiderweb", "spider web", "spider"),
        ),
        VaultSite(
            siteId = "zoocard",
            shownAs = "Zoo Card Club",
            domain = "zoocard.example",
            holds = "A membership card. Your address and your card number.",
            persona = null,
            words = listOf("zoocard", "zoo card", "zoo"),
        ),
        VaultSite(
            siteId = "seaturtle",
            shownAs = "Sea Turtle Fan Club",
            domain = "seaturtle.example",
            holds = "A fan page. Your real name and your photo.",
            persona = Persona.TURTLE,
            words = listOf("seaturtle", "sea turtle", "turtle"),
        ),
    )

    private val byId = all.associateBy { it.siteId }

    fun byId(siteId: String): VaultSite? = byId[siteId]

    fun isValid(siteId: String): Boolean = siteId in byId

    fun words(siteId: String): List<String> = byId[siteId]?.words.orEmpty()
}
