package tea.server.vault

import tea.model.VaultSite

/**
 * The five fictional sites of the drill (docs/password-drill.md). All `.example` domains, invented
 * animals, no real brand or service imitated (plan §17). They differ in what they hold so the
 * debrief can ask the second question: which of these would actually hurt if it leaked?
 */
object VaultSites {
    val all: List<VaultSite> = listOf(
        VaultSite("catland", "catLand", "catland.example", "A pet forum. Nothing but posts.", "cat"),
        VaultSite("dogland", "dogLand", "dogland.example", "A shop. A saved card.", "dog"),
        VaultSite("fishland", "fishLand", "fishland.example", "Aquarium supplies. Your home address.", "fish"),
        VaultSite("birdland", "birdLand", "birdland.example", "A birdwatching club. Your real name and photo.", "bird"),
        VaultSite("bunnyland", "bunnyLand", "bunnyland.example", "A newsletter. Your email, nothing else.", "bunny"),
    )

    private val byId = all.associateBy { it.siteId }

    fun byId(siteId: String): VaultSite? = byId[siteId]

    fun isValid(siteId: String): Boolean = siteId in byId

    /** Words a password for this site must not contain: the site id and its shown name, folded. */
    fun words(siteId: String): List<String> {
        val site = byId[siteId] ?: return emptyList()
        return listOf(site.siteId, site.shownAs.lowercase(), site.siteId.removeSuffix("land"))
            .filter { it.length >= 3 }
            .distinct()
    }
}
