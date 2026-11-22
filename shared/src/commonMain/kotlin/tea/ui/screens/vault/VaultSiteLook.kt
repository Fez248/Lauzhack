package tea.ui.screens.vault

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import tea.model.Mood
import tea.model.VaultSite
import tea.ui.components.PersonaAvatar
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaTheme
import tea.ui.theme.personaStyle

/**
 * Each fake site wears its own colours, so five sign-ups in a row are visibly five different
 * places rather than one form shown five times. Four of them borrow their owner's palette from the
 * design system; the Zoo Card Club has no owner, so it carries the one colour defined here.
 */
data class SiteLook(val accent: Color, val soft: Color, val on: Color)

/**
 * The Zoo Card Club's purple. Local on purpose: it is one drill's decoration, not a cast member.
 * If the Zoo ever becomes a recurring character, this belongs in [TeaColors] with the others.
 */
private val ZOO = Color(0xFF8B5CF6)
private val ZOO_SOFT = Color(0xFFEDE4FF)

@Composable
fun siteLook(site: VaultSite): SiteLook {
    val persona = site.persona ?: return SiteLook(ZOO, ZOO_SOFT, Color.White)
    val style = personaStyle(persona)
    return SiteLook(style.accent, style.soft, style.on)
}

/**
 * The site's badge: the owner's avatar where there is one, otherwise a letter in the site's colour.
 * [PersonaAvatar] already falls back to a letter when a persona's art is missing, so a site with an
 * owner is covered either way.
 */
@Composable
fun SiteBadge(site: VaultSite, modifier: Modifier = Modifier, size: Dp = TeaSize.avatar) {
    val persona = site.persona
    if (persona != null) {
        PersonaAvatar(persona, modifier, Mood.NEUTRAL, size)
        return
    }
    val look = siteLook(site)
    Box(
        modifier
            .size(size)
            .clip(TeaShapes.circle)
            .background(look.accent)
            .border(TeaSize.border, TeaTheme.colors.line, TeaShapes.circle)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            site.shownAs.take(1),
            style = TeaTheme.type.h3.copy(fontSize = (size.value * 0.46f).sp, lineHeight = (size.value * 0.5f).sp),
            color = look.on,
        )
    }
}

/**
 * The one-minute demo (Home's second button under the password chapter): the same drill, cut to
 * two sites so the whole loop — sign up, debrief, log back in — fits a stage slot.
 *
 * The two are chosen for the contrast the debrief's tiering question needs: a pet forum that holds
 * nothing, and a card club that holds an address and a card number. One account would teach nothing
 * about reuse; these two still make the point that the valuable one deserves the better password.
 */
object VaultDemo {
    val siteIds = listOf("catland", "zoocard")

    /** The sites this run walks through: two in the demo, all five otherwise. */
    fun sitesFor(all: List<VaultSite>, demo: Boolean): List<VaultSite> =
        if (!demo) all else siteIds.mapNotNull { id -> all.firstOrNull { it.siteId == id } }.ifEmpty { all.take(siteIds.size) }
}
