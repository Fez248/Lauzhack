package tea.ui.screens.challenge

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import tea.app.Screen
import tea.app.TeaApp
import tea.engine.ChallengeEngine
import tea.engine.ChallengeOutcome
import tea.generated.resources.*
import tea.model.Challenge
import tea.model.Decision
import tea.model.DecisionType
import tea.model.EventTypes
import tea.model.Line
import tea.model.Mood
import tea.model.Persona
import tea.model.Review
import tea.model.Seat
import tea.model.TimelineEntry
import tea.ui.components.ChatMessage
import tea.ui.components.ChoiceRow
import tea.ui.components.ChoiceState
import tea.ui.components.CrisisDial
import tea.ui.components.DeltaChips
import tea.ui.components.FeedbackCard
import tea.ui.components.FeedbackKind
import tea.ui.components.InitialsCircle
import tea.ui.components.PersonaBubble
import tea.ui.components.PersonaNameTag
import tea.ui.components.PersonaSpeech
import tea.ui.components.PersonaTitle
import tea.ui.components.Pill
import tea.ui.components.ScreenColumn
import tea.ui.components.SectionHeading
import tea.ui.components.SpiderPanel
import tea.ui.components.StepBar
import tea.ui.components.TeaButton
import tea.ui.components.TeaButtonVariant
import tea.ui.components.TeaCard
import tea.ui.components.TeaDialog
import tea.ui.components.TeaIcon
import tea.ui.components.TeaIcons
import tea.ui.components.TimeChip
import tea.ui.components.TopBar
import tea.ui.components.VoiceLine
import tea.ui.screens.ErrorLine
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce
import tea.ui.screens.lesson.NumberDot
import tea.ui.screens.lesson.RuleCardBody
import tea.ui.screens.lesson.RuleCardView
import tea.ui.screens.lesson.boldMarkdown
import tea.ui.theme.TeaColors
import tea.ui.theme.TeaShapes
import tea.ui.theme.TeaSize
import tea.ui.theme.TeaSpacing
import tea.ui.theme.TeaTheme
import tea.ui.theme.personaStyle

const val CHALLENGE_SECTIONS = 4
private const val BRIEFING = 0
private const val PLAYBOOK = 1
private const val DECISIONS = 2
private const val FALLOUT = 3

private sealed interface Loaded {
    data object Loading : Loaded
    data class Done(val value: Challenge?) : Loaded
}

/**
 * Challenge renderer (docs/challenge-boardroom-crisis.md §4): top bar, a step bar of the four
 * sections, then section [section]. Emits `challenge_started` once, stores the section for
 * Home's "Continue", moves with `navigate(Screen.Challenge(id, n))`; after Fallout, Home.
 */
@Composable
fun ChallengeContent(app: TeaApp, id: String, section: Int) {
    val lang by app.lang.collectAsState()
    val loaded by produceState<Loaded>(Loaded.Loading, id, lang) {
        value = Loaded.Done(runCatching { app.content.challenge(id, lang) }.getOrNull())
    }
    val index = section.coerceIn(0, CHALLENGE_SECTIONS - 1)
    LaunchedEffect(id, index) { app.save(LearnKeys.challengeSection(id), LearnKeys.INT, index) }

    val state = loaded
    val challenge = (state as? Loaded.Done)?.value
    // A demo is not a real run: it emits no start event (the controller also skips completion).
    LaunchedEffect(id, challenge?.demo) {
        if (challenge?.demo == false) {
            app.emitOnce(LearnKeys.challengeStarted(id), EventTypes.CHALLENGE_STARTED, JsonObject(mapOf("challenge_id" to JsonPrimitive(id))))
        }
    }
    Column(Modifier.fillMaxSize().background(TeaTheme.colors.paper)) {
        TopBar(title = challenge?.title, onBack = { app.back() })
        if (challenge == null) {
            Box(Modifier.fillMaxSize().padding(TeaSpacing.s3), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (state is Loaded.Loading) Res.string.challenge_loading else Res.string.challenge_missing),
                    style = TeaTheme.type.body,
                    color = TeaTheme.colors.inkSoft,
                )
            }
            return@Column
        }
        val controller = remember(challenge) { ChallengeController(app, challenge) }
        ChallengeBody(
            controller,
            index,
            Modifier.weight(1f),
            goTo = { n -> goToChallengeSection(app, id, n) },
            onDemo = challenge.demoId?.let { demoId -> { app.navigate(Screen.Challenge(demoId, 0)) } },
        )
    }
}

fun goToChallengeSection(app: TeaApp, id: String, n: Int) {
    if (n >= CHALLENGE_SECTIONS) app.navigateRoot(Screen.Home) else app.navigate(Screen.Challenge(id, n))
}

@Composable
fun challengeSectionTitles(): List<String> = listOf(
    stringResource(Res.string.challenge_section_briefing),
    stringResource(Res.string.challenge_section_playbook),
    stringResource(Res.string.challenge_section_decisions),
    stringResource(Res.string.challenge_section_fallout),
)

/**
 * Step bar + one section. Separate from loading so screenshots can pass a prepared controller.
 * [onDemo] (null = no demo) starts the challenge's 1-minute demo from the briefing.
 */
@Composable
fun ChallengeBody(
    controller: ChallengeController,
    section: Int,
    modifier: Modifier = Modifier,
    onDemo: (() -> Unit)? = null,
    goTo: (Int) -> Unit,
) {
    val titles = challengeSectionTitles()
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            StepBar(titles, section, Modifier.widthIn(max = TeaSize.maxContentWidth).fillMaxWidth().padding(horizontal = TeaSpacing.s2))
        }
        var review by remember { mutableStateOf<Review?>(null) }
        key(section) {
            when (section) {
                DECISIONS -> DecisionsSection(controller, Modifier.weight(1f), onReview = { review = it }, goTo = goTo)
                else -> ScreenColumn(Modifier.weight(1f), scrollState = rememberScrollState()) {
                    when (section) {
                        BRIEFING -> BriefingSection(controller.challenge, onDemo)
                        PLAYBOOK -> PlaybookSection(controller.challenge)
                        FALLOUT -> FalloutSection(controller, onReview = { review = it }, goTo = goTo)
                    }
                    if (section < FALLOUT) {
                        TeaButton(
                            stringResource(Res.string.challenge_continue_to, titles[section + 1]),
                            { goTo(section + 1) },
                            Modifier.padding(top = TeaSpacing.s2),
                        )
                    }
                }
            }
        }
        review?.let { r -> ReviewDialog(controller.challenge, r) { review = null } }
    }
}

/** The playbook card a wrong decision points back to, with that rule highlighted. */
@Composable
private fun ReviewDialog(challenge: Challenge, review: Review, onDismiss: () -> Unit) {
    val card = challenge.playbook.cards.getOrNull(review.card) ?: return
    TeaDialog(onDismissRequest = onDismiss, title = card.title) { RuleCardBody(card, large = true, highlightRule = review.rule) }
}

@Composable
private fun SectionLines(lines: List<Line>) {
    lines.forEach { PersonaBubble(it.persona, if (it.persona == Persona.SPIDER) Mood.SMUG else Mood.NEUTRAL, it.text, onSoft = true) }
}

// ---- 0 · Briefing -----------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.BriefingSection(challenge: Challenge, onDemo: (() -> Unit)?) {
    val compact = TeaTheme.isCompact
    val b = challenge.briefing
    // Hero
    Pill(challenge.pill)
    Text(challenge.headline, Modifier.semantics { heading() }, style = TeaTheme.type.h1, color = TeaTheme.colors.ink)
    Text(challenge.subtitle, style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
    Text(
        // The demo's headline already says "one minute" ("About 1 minutes" would not read well).
        (if (challenge.demo) "" else stringResource(Res.string.challenge_minutes, challenge.minutes) + ", ") +
            pluralStringResource(Res.plurals.challenge_decisions_count, challenge.decisions.size, challenge.decisions.size),
        style = TeaTheme.type.smallStrong,
        color = TeaTheme.colors.inkSoft,
    )
    // The full crisis stays as it is; a short demo is one tap away.
    if (onDemo != null) TeaButton(stringResource(Res.string.challenge_demo_start), onDemo, variant = TeaButtonVariant.Secondary)
    if (challenge.advisers.isNotEmpty()) {
        Text(stringResource(Res.string.challenge_advisers), style = TeaTheme.type.label, color = TeaTheme.colors.ink)
        val rows = if (compact) challenge.advisers.map { listOf(it) } else challenge.advisers.chunked(2)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
                row.forEach { a ->
                    TeaCard(Modifier.weight(1f).fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(TeaSpacing.s2)) {
                        PersonaNameTag(a.persona, role = a.role, avatarSize = TeaSize.avatarSm)
                        PersonaSpeech(a.persona, a.say)
                    }
                }
                if (row.size == 1 && !compact) Box(Modifier.weight(1f))
            }
        }
    }
    SectionHeading(stringResource(Res.string.challenge_section_briefing), number = 1)
    SectionLines(challenge.sectionLines.briefing)
    PersonaBubble(Persona.TURTLE, Mood.NEUTRAL, b.turtleIntro, onSoft = true)

    if (b.roster.isNotEmpty()) {
        if (b.rosterTitle.isNotBlank()) Text(b.rosterTitle, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
            b.roster.forEach { SeatChip(it) }
        }
    }

    val timeline: @Composable (Modifier) -> Unit = { m ->
        TeaCard(m) {
            if (b.timelineTitle.isNotBlank()) Text(b.timelineTitle, style = TeaTheme.type.title, color = TeaTheme.colors.ink)
            b.timeline.forEach { TimelineRow(it) }
        }
    }
    val know: @Composable (Modifier) -> Unit = { m ->
        Column(m, verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            KnowCard(b.knownTitle, b.known, unknown = false, Modifier.fillMaxWidth())
            KnowCard(b.unknownTitle, b.unknown, unknown = true, Modifier.fillMaxWidth())
        }
    }
    if (compact) {
        timeline(Modifier.fillMaxWidth())
        know(Modifier.fillMaxWidth())
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
            timeline(Modifier.weight(1f).fillMaxHeight())
            know(Modifier.weight(1f))
        }
    }

    SpiderPanel(Modifier.padding(top = TeaSpacing.s2)) {
        if (b.spiderLine.isNotBlank()) PersonaBubble(Persona.SPIDER, Mood.SMUG, b.spiderLine)
        if (b.noteTitle.isNotBlank()) PersonaTitle(Persona.SPIDER, b.noteTitle)
        RansomNote(b.note, b.noteSign)
        if (b.chips.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1)) {
                b.chips.forEach { Pill(it, background = TeaColors.webCard, color = TeaColors.webInk, borderColor = TeaColors.webLine) }
            }
        }
    }
}

/** One person at the table (`.seat`): you are highlighted, someone away is faded and says why. */
@Composable
fun SeatChip(seat: Seat, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    Row(
        modifier
            .alpha(if (seat.away) 0.55f else 1f)
            .clip(TeaShapes.md)
            .background(if (seat.you) colors.highlight else colors.card)
            .border(TeaSize.border, colors.line, TeaShapes.md)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        InitialsCircle(seat.initials)
        Column {
            val ink = if (seat.you) colors.onHighlight else colors.ink
            Text(seat.name, style = TeaTheme.type.bodyStrong, color = ink)
            Text(seat.role, style = TeaTheme.type.small, color = if (seat.you) colors.onHighlight else colors.inkSoft)
        }
    }
}

/** One line of the timeline (`.feed li`): time chip + text; "now" is highlighted and bold. */
@Composable
fun TimelineRow(entry: TimelineEntry, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeChip(entry.time, now = entry.now)
        Text(
            boldMarkdown(entry.text),
            Modifier.weight(1f),
            style = if (entry.now) TeaTheme.type.bodyStrong else TeaTheme.type.body,
            color = TeaTheme.colors.ink,
        )
    }
}

/** "What we know" (check marks) or "What we don't know yet" (question marks on paper). */
@Composable
fun KnowCard(title: String, lines: List<String>, unknown: Boolean, modifier: Modifier = Modifier) {
    val colors = TeaTheme.colors
    TeaCard(modifier, background = if (unknown) colors.paper else colors.card, shadow = if (unknown) 0.dp else TeaSize.shadow) {
        Text(title, style = TeaTheme.type.title, color = colors.ink)
        lines.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (unknown) {
                    Text("?", style = TeaTheme.type.bodyStrong, color = colors.badText)
                } else {
                    TeaIcon(TeaIcons.Check, null, tint = colors.okText, size = 20.dp)
                }
                Text(line, Modifier.weight(1f), style = TeaTheme.type.body, color = colors.ink)
            }
        }
    }
}

/** The attacker's note (`.ransom`): night card, Spider's italic voice, signed. */
@Composable
fun RansomNote(paragraphs: List<String>, sign: String, modifier: Modifier = Modifier) {
    val style = personaStyle(Persona.SPIDER)
    Column(
        modifier
            .fillMaxWidth()
            .clip(TeaShapes.md)
            .background(TeaColors.webCard)
            .border(TeaSize.border, TeaColors.webLine, TeaShapes.md)
            .padding(TeaSpacing.s2),
        verticalArrangement = Arrangement.spacedBy(TeaSpacing.s1),
    ) {
        paragraphs.forEach { Text(it, style = style.speech(), color = TeaColors.webInk) }
        if (sign.isNotBlank()) {
            Text(sign, Modifier.fillMaxWidth(), style = style.speech(), color = TeaColors.webInk, textAlign = TextAlign.End)
        }
    }
}

// ---- 1 · Playbook -----------------------------------------------------------------------------

@Composable
private fun ColumnScope.PlaybookSection(challenge: Challenge) {
    var open by remember { mutableStateOf<Int?>(null) }
    SectionHeading(stringResource(Res.string.challenge_section_playbook), number = 2, intro = challenge.playbook.intro.ifBlank { null })
    SectionLines(challenge.sectionLines.playbook)
    val cards = challenge.playbook.cards
    if (TeaTheme.isCompact) {
        cards.forEachIndexed { i, card -> RuleCardView(card, tilt(i), Modifier.fillMaxWidth()) { open = i } }
    } else {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s3)) {
            cards.forEachIndexed { i, card -> RuleCardView(card, tilt(i), Modifier.weight(1f).fillMaxHeight()) { open = i } }
        }
    }
    open?.let { i ->
        val card = cards[i]
        TeaDialog(onDismissRequest = { open = null }, title = card.title) { RuleCardBody(card, large = true) }
    }
}

private fun tilt(i: Int) = if (i % 2 == 0) -1f else 1f

// ---- 2 · Decisions ----------------------------------------------------------------------------

/** Dial key → label, in dial order. */
private fun Challenge.dialLabels(): Map<String, String> = dials.associate { it.key to it.label }

/** Clock + the four dials (`.crisis-bar`). */
@Composable
fun CrisisBar(challenge: Challenge, time: String, meters: Map<String, Int>, modifier: Modifier = Modifier) {
    val left = ChallengeEngine.minutesLeft(challenge, time)
    TeaCard(modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(TeaSpacing.s2), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            Text(time, style = TeaTheme.type.h2, color = TeaTheme.colors.ink)
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.challenge_clock_note, challenge.deadline), style = TeaTheme.type.small, color = TeaTheme.colors.inkSoft)
                Text(stringResource(Res.string.challenge_time_left, left / 60, left % 60), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.ink)
            }
        }
        DialGrid(challenge, meters)
    }
}

@Composable
fun DialGrid(challenge: Challenge, meters: Map<String, Int>) {
    val rows = if (TeaTheme.isCompact) challenge.dials.chunked(2) else listOf(challenge.dials)
    rows.forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            row.forEach { d -> CrisisDial(d.label, meters[d.key] ?: 0, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun DecisionsSection(controller: ChallengeController, modifier: Modifier, onReview: (Review) -> Unit, goTo: (Int) -> Unit) {
    val challenge = controller.challenge
    // The card on screen: the open decision, or the last one once the run is complete.
    var viewing by remember { mutableIntStateOf(controller.current.coerceAtMost(controller.total - 1)) }
    val scroll = rememberScrollState()
    LaunchedEffect(viewing) { scroll.animateScrollTo(0) }
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().padding(top = TeaSpacing.s1, bottom = TeaSpacing.s1), contentAlignment = Alignment.Center) {
            CrisisBar(
                challenge,
                challenge.decisions[viewing].time,
                controller.meters,
                Modifier.widthIn(max = TeaSize.maxContentWidth).padding(horizontal = TeaSpacing.s2),
            )
        }
        ScreenColumn(Modifier.weight(1f), scrollState = scroll) {
            SectionHeading(stringResource(Res.string.challenge_section_decisions), number = 3)
            if (viewing == 0 && controller.result(0) == null) {
                challenge.decisionsIntro?.let { PersonaBubble(it.persona, Mood.NEUTRAL, it.text, onSoft = true) }
            }
            SectionLines(challenge.sectionLines.decisions)
            if (viewing > 0) {
                Text(stringResource(Res.string.challenge_earlier), style = TeaTheme.type.label, color = TeaTheme.colors.inkSoft)
                for (i in 0 until viewing) EarlierDecision(controller, i, onReview)
            }
            key(viewing) {
                DecisionCard(
                    controller,
                    viewing,
                    onReview = onReview,
                    onNext = {
                        if (viewing + 1 < controller.total) viewing++ else goTo(FALLOUT)
                    },
                )
            }
        }
    }
}

/** A locked earlier decision: one line (time, title, verdict) that opens the full card. */
@Composable
private fun EarlierDecision(controller: ChallengeController, index: Int, onReview: (Review) -> Unit) {
    val d = controller.challenge.decisions[index]
    val r = controller.result(index) ?: return
    var open by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(TeaShapes.md)
            .background(TeaTheme.colors.card)
            .border(TeaSize.border, TeaTheme.colors.rule, TeaShapes.md)
            .clickable(role = Role.Button) { open = !open }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TimeChip(d.time)
        Text(d.title, Modifier.weight(1f), style = TeaTheme.type.bodyStrong, color = TeaTheme.colors.ink)
        VerdictTag(r.correct)
        TeaIcon(TeaIcons.ChevronDown, null, tint = TeaTheme.colors.inkSoft, size = 18.dp)
    }
    if (open) DecisionCard(controller, index, onReview = onReview, onNext = null)
}

@Composable
private fun VerdictTag(correct: Boolean) {
    val colors = TeaTheme.colors
    Text(
        (if (correct) "✓ " else "✗ ") + stringResource(if (correct) Res.string.challenge_sound else Res.string.challenge_costly),
        style = TeaTheme.type.smallStrong,
        color = if (correct) colors.okText else colors.badText,
    )
}

/**
 * One decision (`fieldset.decision`): time, question, the colleague's message, options (shuffled
 * per run), Lock. Once locked: every option marked in words, the persona's verdict and lesson,
 * review links, Spider's account of the cost, and [onNext] (null for a read-only earlier card).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DecisionCard(controller: ChallengeController, index: Int, onReview: (Review) -> Unit, onNext: (() -> Unit)?) {
    val challenge = controller.challenge
    val d = challenge.decisions[index]
    val result = controller.result(index)
    val multi = d.type == DecisionType.MULTI
    val selection = remember(index) { mutableStateListOf<Int>() }
    var warn by remember(index) { mutableStateOf(false) }
    val feedbackRequester = remember { BringIntoViewRequester() }
    var justLocked by remember(index) { mutableStateOf(false) }
    LaunchedEffect(justLocked) { if (justLocked) feedbackRequester.bringIntoView() }

    TeaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeChip(d.time)
            Text(stringResource(Res.string.challenge_decision_of, index + 1, controller.total), style = TeaTheme.type.smallStrong, color = TeaTheme.colors.inkSoft)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberDot(index + 1, d.voice)
            Text(d.text, Modifier.weight(1f).semantics { heading() }, style = if (TeaTheme.isCompact) TeaTheme.type.title else TeaTheme.type.h3, color = TeaTheme.colors.ink)
        }
        d.artifact?.let { ChatMessage(it.initials, it.name, it.role, it.time, it.text) }
        Text(
            stringResource(if (multi) Res.string.challenge_choose_many else Res.string.challenge_choose_one),
            style = TeaTheme.type.smallStrong,
            color = TeaTheme.colors.inkSoft,
        )
        val selected = result?.selected?.toSet() ?: selection.toSet()
        controller.order(d).forEach { i ->
            val o = d.options[i]
            val picked = i in selected
            val state = when {
                result == null -> if (picked) ChoiceState.Selected else ChoiceState.Idle
                o.ok -> ChoiceState.Correct
                picked -> ChoiceState.Wrong
                else -> ChoiceState.Disabled
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ChoiceRow(o.text, state, onClick = {
                    warn = false
                    if (multi) {
                        if (i in selection) selection.remove(i) else selection.add(i)
                    } else {
                        selection.clear()
                        selection.add(i)
                    }
                }, multi = multi)
                if (result != null && (o.ok || picked)) {
                    val caption = when {
                        o.ok && picked -> Res.string.challenge_pick_sound
                        o.ok -> Res.string.challenge_missed_sound
                        else -> Res.string.challenge_pick_costly
                    }
                    Text(
                        stringResource(caption),
                        Modifier.padding(start = 12.dp),
                        style = TeaTheme.type.small,
                        color = if (o.ok) TeaTheme.colors.okText else TeaTheme.colors.badText,
                    )
                }
            }
        }
        if (result == null) {
            if (warn) ErrorLine(stringResource(Res.string.challenge_choose_warn))
            TeaButton(stringResource(Res.string.challenge_lock), {
                if (selection.isEmpty()) {
                    warn = true
                } else if (controller.lock(index, selection.toList()) != null) {
                    justLocked = true
                }
            })
        } else {
            Column(Modifier.bringIntoViewRequester(feedbackRequester), verticalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
                FeedbackCard(
                    if (result.correct) FeedbackKind.Correct else FeedbackKind.NotQuite,
                    d.voice,
                    d.explain,
                    verdict = stringResource(if (result.correct) Res.string.challenge_sound else Res.string.challenge_costly),
                )
                ReviewLinks(challenge, result.reviews, onReview)
                SpiderFallout(challenge, d, result.selected, result.correct)
            }
            if (onNext != null) {
                val next = challenge.decisions.getOrNull(index + 1)
                TeaButton(
                    if (next != null) stringResource(Res.string.challenge_next_time, next.time) else stringResource(Res.string.challenge_see_fallout),
                    onNext,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewLinks(challenge: Challenge, keys: List<String>, onReview: (Review) -> Unit) {
    val reviews = keys.mapNotNull { challenge.reviews[it] }
    if (reviews.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s1), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        reviews.forEach { r -> TeaButton("↗ " + r.label, { onReview(r) }, variant = TeaButtonVariant.Text, small = true) }
    }
}

/** What Spider did with the decision (`.fallout`): a grudging line, or one item per cost. */
@Composable
fun SpiderFallout(challenge: Challenge, d: Decision, selected: List<Int>, correct: Boolean) {
    val labels = challenge.dialLabels()
    SpiderPanel(padding = TeaSpacing.s2) {
        PersonaNameTag(
            Persona.SPIDER,
            role = stringResource(if (correct) Res.string.challenge_spider_reaction else Res.string.challenge_spider_next),
            mood = if (correct) Mood.WORRIED else Mood.SMUG,
            avatarSize = TeaSize.avatarSm,
        )
        if (correct) {
            VoiceLine(Persona.SPIDER, d.spiderOk)
        } else {
            ChallengeEngine.hits(d, selected).forEach { h ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(if (h.picked) Res.string.challenge_you_chose else Res.string.challenge_you_missed, h.option.short),
                        style = TeaTheme.type.smallStrong,
                        color = TeaColors.webSoft,
                    )
                    h.option.why?.let { VoiceLine(Persona.SPIDER, it) }
                    DeltaChips(h.option.fx.orEmpty(), labels)
                }
            }
        }
    }
}

// ---- 3 · Fallout ------------------------------------------------------------------------------

@Composable
fun outcomeWord(outcome: ChallengeOutcome): String = stringResource(
    when (outcome) {
        ChallengeOutcome.CONTAINED -> Res.string.challenge_outcome_contained
        ChallengeOutcome.BRUISED -> Res.string.challenge_outcome_bruised
        ChallengeOutcome.WRECKED -> Res.string.challenge_outcome_wrecked
    },
)

@Composable
private fun ColumnScope.FalloutSection(controller: ChallengeController, onReview: (Review) -> Unit, goTo: (Int) -> Unit) {
    val challenge = controller.challenge
    SectionHeading(stringResource(Res.string.challenge_section_fallout), number = 4)
    if (!controller.complete) {
        Text(stringResource(Res.string.challenge_fallout_wait), style = TeaTheme.type.body, color = TeaTheme.colors.inkSoft)
        TeaButton(stringResource(Res.string.challenge_go_decisions), { goTo(DECISIONS) }, variant = TeaButtonVariant.Secondary)
        return
    }
    SectionLines(challenge.sectionLines.fallout)
    val outcome = controller.outcome
    val o = when (outcome) {
        ChallengeOutcome.CONTAINED -> challenge.outcomes.contained
        ChallengeOutcome.BRUISED -> challenge.outcomes.bruised
        ChallengeOutcome.WRECKED -> challenge.outcomes.wrecked
    }
    ScoreBanner(controller.good, controller.total, o.voice, o.score, happy = outcome == ChallengeOutcome.CONTAINED)
    SpiderPanel {
        PersonaNameTag(Persona.SPIDER, role = outcomeWord(outcome), mood = if (outcome == ChallengeOutcome.CONTAINED) Mood.WORRIED else Mood.SMUG)
        PersonaTitle(Persona.SPIDER, o.title)
        VoiceLine(Persona.SPIDER, o.body)
    }
    Text(stringResource(Res.string.challenge_your_decisions), Modifier.semantics { heading() }, style = TeaTheme.type.h3, color = TeaTheme.colors.ink)
    challenge.decisions.forEachIndexed { i, d -> controller.result(i)?.let { RecapRow(challenge, d, it.correct, it.cost, it.reviews, onReview) } }
    TeaCard(Modifier.fillMaxWidth()) {
        Text(stringResource(Res.string.challenge_final_title), style = TeaTheme.type.title, color = TeaTheme.colors.ink)
        DialGrid(challenge, controller.meters)
    }
    var confirm by remember { mutableStateOf(false) }
    TeaButton(stringResource(Res.string.challenge_replay), { confirm = true })
    TeaButton(stringResource(Res.string.challenge_home), { goTo(CHALLENGE_SECTIONS) }, variant = TeaButtonVariant.Secondary)
    if (challenge.footer.isNotBlank()) VoiceLine(Persona.SPIDER, challenge.footer, colored = true)
    if (confirm) {
        TeaDialog(
            onDismissRequest = { confirm = false },
            title = stringResource(Res.string.challenge_replay_title),
            confirmText = stringResource(Res.string.challenge_replay_confirm),
            onConfirm = {
                confirm = false
                controller.replay()
                goTo(DECISIONS)
            },
            dismissText = stringResource(Res.string.challenge_cancel),
        ) {
            Text(stringResource(Res.string.challenge_replay_body), style = TeaTheme.type.body, color = TeaTheme.colors.ink)
        }
    }
}

/** "4 / 5" in big type, with the persona's verdict (`.score`). */
@Composable
fun ScoreBanner(good: Int, total: Int, voice: Persona, text: String, happy: Boolean) {
    TeaCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(TeaSpacing.s2)) {
            Text(stringResource(Res.string.challenge_score, good, total), style = TeaTheme.type.h1, color = TeaTheme.colors.ink)
        }
        PersonaBubble(voice, if (happy) Mood.HAPPY else Mood.NEUTRAL, text, onSoft = true)
    }
}

/** One line of the ledger (`.recap li`): time, title, verdict, best move; costs and review links if costly. */
@Composable
fun RecapRow(challenge: Challenge, d: Decision, correct: Boolean, cost: Map<String, Int>, reviews: List<String>, onReview: (Review) -> Unit) {
    val colors = TeaTheme.colors
    TeaCard(Modifier.fillMaxWidth(), background = if (correct) colors.okSoft else colors.badSoft, shadow = TeaSize.shadowSm) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TimeChip(d.time)
            Text(d.title, Modifier.weight(1f), style = TeaTheme.type.bodyStrong, color = colors.ink)
        }
        VerdictTag(correct)
        Text(
            boldMarkdown("**" + stringResource(Res.string.challenge_best_move) + "** " + d.best),
            style = TeaTheme.type.body,
            color = colors.ink,
        )
        if (!correct) {
            DeltaChips(cost.mapValues { -kotlin.math.abs(it.value) }, challenge.dialLabels())
            ReviewLinks(challenge, reviews, onReview)
        }
    }
}
