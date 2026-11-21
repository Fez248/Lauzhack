package tea.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import tea.model.Challenge
import tea.model.Decision
import tea.model.DecisionOption
import tea.model.DecisionType
import kotlin.random.Random

/** Word shown under a dial; colour alone never carries the meaning. */
enum class DialLevel { STEADY, STRAINED, CRITICAL }

@Serializable
enum class ChallengeOutcome {
    @SerialName("contained") CONTAINED,
    @SerialName("bruised") BRUISED,
    @SerialName("wrecked") WRECKED,
}

/** One option that cost something: picked while wrong ([picked] = true) or missed while sound. */
data class Hit(val index: Int, val option: DecisionOption, val picked: Boolean)

/** One locked decision. [selected] are indexes into the decision's options (content order). */
@Serializable
data class DecisionResult(
    val decisionId: String,
    val selected: List<Int>,
    val correct: Boolean,
    val cost: Map<String, Int> = emptyMap(),
    val reviews: List<String> = emptyList(),
)

/** A run in progress or finished. Meters are recomputed from [results], never stored. */
@Serializable
data class ChallengeRun(val seed: Long, val results: List<DecisionResult> = emptyList())

/** Best finished run of a challenge (kept across replays). */
@Serializable
data class ChallengeBest(val good: Int, val total: Int, val outcome: ChallengeOutcome)

/**
 * Grading of a Boardroom-style challenge (guide-rules §14, docs/challenge-boardroom-crisis.md §4.3).
 * Pure and deterministic. Same rules as the HTML mockup.
 */
object ChallengeEngine {
    const val MIN = 0
    const val MAX = 100

    fun levelOf(value: Int): DialLevel = when {
        value >= 70 -> DialLevel.STEADY
        value >= 40 -> DialLevel.STRAINED
        else -> DialLevel.CRITICAL
    }

    fun soundIndexes(decision: Decision): Set<Int> =
        decision.options.indices.filter { decision.options[it].ok }.toSet()

    /** Exact match: every sound option picked, nothing else. */
    fun isCorrect(decision: Decision, selected: Collection<Int>): Boolean = selected.toSet() == soundIndexes(decision)

    /** Options that cost something, in content order. Options without fx cost nothing. */
    fun hits(decision: Decision, selected: Collection<Int>): List<Hit> {
        val sel = selected.toSet()
        return decision.options.mapIndexedNotNull { i, o ->
            val picked = i in sel
            if (picked != o.ok && o.fx != null) Hit(i, o, picked) else null
        }
    }

    fun sum(maps: List<Map<String, Int>>): Map<String, Int> {
        val total = linkedMapOf<String, Int>()
        maps.forEach { m -> m.forEach { (k, v) -> total[k] = (total[k] ?: 0) + v } }
        return total
    }

    /** Adds [fx] to [meters], each value clamped to 0..100. Unknown keys are ignored. */
    fun apply(meters: Map<String, Int>, fx: Map<String, Int>): Map<String, Int> =
        meters.mapValues { (k, v) -> (v + (fx[k] ?: 0)).coerceIn(MIN, MAX) }

    /** Grades one decision. A selection must not be empty (the UI blocks that). */
    fun lock(decision: Decision, selected: Collection<Int>): DecisionResult {
        val sel = selected.toSet().sorted()
        val hits = hits(decision, sel)
        return DecisionResult(
            decisionId = decision.id,
            selected = sel,
            correct = isCorrect(decision, sel),
            cost = sum(hits.map { it.option.fx.orEmpty() }),
            reviews = hits.mapNotNull { it.option.rv }.distinct(),
        )
    }

    /** Meters after [results]: per decision, cost first (clamped), then gain if sound (clamped). */
    fun meters(challenge: Challenge, results: List<DecisionResult>): Map<String, Int> {
        var m = challenge.dials.associate { it.key to (challenge.startMeters[it.key] ?: 0).coerceIn(MIN, MAX) }
        results.forEach { r ->
            m = apply(m, r.cost)
            if (r.correct) {
                val gain = challenge.decisions.firstOrNull { it.id == r.decisionId }?.gain.orEmpty()
                m = apply(m, gain)
            }
        }
        return m
    }

    fun outcome(challenge: Challenge, good: Int): ChallengeOutcome = when {
        good >= challenge.decisions.size -> ChallengeOutcome.CONTAINED
        good >= challenge.outcomeThresholds.bruised -> ChallengeOutcome.BRUISED
        else -> ChallengeOutcome.WRECKED
    }

    fun toMinutes(hhmm: String): Int {
        val (h, m) = hhmm.split(':').map { it.trim().toInt() }
        return h * 60 + m
    }

    /** Minutes from [time] to the challenge deadline (never negative). */
    fun minutesLeft(challenge: Challenge, time: String): Int =
        (toMinutes(challenge.deadline) - toMinutes(time)).coerceAtLeast(0)

    /** Option display order for a decision: a shuffle seeded by the run, stable across reloads. */
    fun order(decision: Decision, seed: Long): List<Int> {
        val idx = decision.options.indices.toMutableList()
        idx.shuffle(Random(seed xor decision.id.hashCode().toLong()))
        return idx
    }

    /**
     * The saved run if it still fits [challenge] (each result matches the decision at its position
     * and its indexes are in range); null otherwise, so content edits never break a save.
     */
    fun validRun(challenge: Challenge, run: ChallengeRun?): ChallengeRun? {
        run ?: return null
        if (run.results.size > challenge.decisions.size) return null
        run.results.forEachIndexed { i, r ->
            val d = challenge.decisions[i]
            if (r.decisionId != d.id || r.selected.any { it !in d.options.indices }) return null
            if (d.type == DecisionType.SINGLE && r.selected.size != 1) return null
        }
        return run
    }

    fun isComplete(challenge: Challenge, run: ChallengeRun): Boolean = run.results.size >= challenge.decisions.size

    fun good(run: ChallengeRun): Int = run.results.count { it.correct }

    fun newSeed(): Long = Random.nextLong()
}
