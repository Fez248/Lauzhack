package tea.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `content/challenges/<id>/challenge.<lang>.json` (docs/challenge-boardroom-crisis.md §4.4).
 * A crisis in four sections: briefing, playbook, decisions, fallout. Grading lives in
 * [tea.engine.ChallengeEngine]; numbers (fx, gain, ok) must be identical across languages.
 */
@Serializable
data class Challenge(
    val id: String,
    val language: String,
    val title: String,
    val pill: String,
    val headline: String,
    val subtitle: String,
    val minutes: Int = 12,
    /** "HH:MM": the attacker's deadline. */
    val deadline: String,
    /** Dial key → start value (0..100). */
    val startMeters: Map<String, Int>,
    val dials: List<Dial>,
    val advisers: List<Adviser> = emptyList(),
    val sectionLines: SectionLines = SectionLines(),
    val briefing: Briefing,
    val playbook: Playbook,
    /** Review key (e.g. "cat0") → playbook rule a wrong option points back to. */
    val reviews: Map<String, Review> = emptyMap(),
    val decisionsIntro: Line? = null,
    val decisions: List<Decision>,
    val outcomeThresholds: OutcomeThresholds = OutcomeThresholds(),
    val outcomes: Outcomes,
    val footer: String = "",
)

@Serializable
data class Dial(val key: String, val label: String)

@Serializable
data class Adviser(val persona: Persona, val role: String, val say: String)

@Serializable
data class Line(val persona: Persona, val text: String)

@Serializable
data class SectionLines(
    val briefing: List<Line> = emptyList(),
    val playbook: List<Line> = emptyList(),
    val decisions: List<Line> = emptyList(),
    val fallout: List<Line> = emptyList(),
)

@Serializable
data class Briefing(
    val turtleIntro: String,
    val rosterTitle: String = "",
    val roster: List<Seat> = emptyList(),
    val timelineTitle: String = "",
    val timeline: List<TimelineEntry> = emptyList(),
    val knownTitle: String = "",
    val known: List<String> = emptyList(),
    val unknownTitle: String = "",
    val unknown: List<String> = emptyList(),
    val spiderLine: String = "",
    val noteTitle: String = "",
    val note: List<String> = emptyList(),
    val noteSign: String = "",
    val chips: List<String> = emptyList(),
)

@Serializable
data class Seat(
    val initials: String,
    val name: String,
    val role: String,
    val you: Boolean = false,
    val away: Boolean = false,
)

@Serializable
data class TimelineEntry(val time: String, val text: String, val now: Boolean = false)

@Serializable
data class Playbook(val intro: String = "", val cards: List<RuleCard>)

/** [card] = index in [Playbook.cards], [rule] = index in that card's rules. */
@Serializable
data class Review(val card: Int, val rule: Int, val label: String)

@Serializable
data class Decision(
    val id: String,
    /** "HH:MM" shown on the clock while this decision is open. */
    val time: String,
    val voice: Persona,
    val type: DecisionType = DecisionType.SINGLE,
    val title: String,
    val text: String,
    val artifact: ChatArtifact? = null,
    val options: List<DecisionOption>,
    /** Dial gains when the whole decision is sound. */
    val gain: Map<String, Int> = emptyMap(),
    val explain: String,
    val spiderOk: String,
    val best: String,
)

@Serializable
enum class DecisionType {
    @SerialName("single") SINGLE,
    @SerialName("multi") MULTI,
}

/** A message from a colleague in a generic messenger (no brand). */
@Serializable
data class ChatArtifact(
    val initials: String,
    val name: String,
    val role: String,
    val time: String,
    val text: String,
)

/**
 * [fx] = dial cost when picked while wrong, or missed while sound. [why] = Spider's account of what
 * happened. [rv] = key into [Challenge.reviews].
 */
@Serializable
data class DecisionOption(
    val text: String,
    val short: String,
    val ok: Boolean,
    val fx: Map<String, Int>? = null,
    val why: String? = null,
    val rv: String? = null,
)

/** Good-decision counts: all = contained, at least [bruised] = bruised, else wrecked. */
@Serializable
data class OutcomeThresholds(val bruised: Int = 3)

@Serializable
data class Outcomes(val contained: Outcome, val bruised: Outcome, val wrecked: Outcome)

@Serializable
data class Outcome(val voice: Persona, val title: String, val body: String, val score: String)
