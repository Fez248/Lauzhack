package tea.ui.screens.challenge

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import tea.app.TeaApp
import tea.engine.ChallengeBest
import tea.engine.ChallengeEngine
import tea.engine.ChallengeOutcome
import tea.engine.ChallengeRun
import tea.engine.DecisionResult
import tea.engine.Xp
import tea.model.Challenge
import tea.model.Decision
import tea.model.EventTypes
import tea.ui.screens.learn.LearnKeys
import tea.ui.screens.learn.emitOnce

/**
 * State of one challenge run: which decisions are locked, the dials, the option order.
 * Every lock is saved at once (a reload never unlocks a decision). The first completion emits
 * `challenge_completed` and adds XP once; the best run is kept across replays.
 */
class ChallengeController(
    private val app: TeaApp,
    val challenge: Challenge,
    private val newSeed: () -> Long = ChallengeEngine::newSeed,
) {
    var run: ChallengeRun by mutableStateOf(
        ChallengeEngine.validRun(challenge, app.load(LearnKeys.challengeRun(challenge.id), ChallengeRun.serializer()))
            ?: ChallengeRun(newSeed()).also { save(it) },
    )
        private set

    val total: Int get() = challenge.decisions.size

    /** Index of the decision to answer next; == [total] when the run is complete. */
    val current: Int get() = run.results.size

    val complete: Boolean get() = ChallengeEngine.isComplete(challenge, run)

    val meters: Map<String, Int> get() = ChallengeEngine.meters(challenge, run.results)

    val good: Int get() = ChallengeEngine.good(run)

    val outcome: ChallengeOutcome get() = ChallengeEngine.outcome(challenge, good)

    fun result(index: Int): DecisionResult? = run.results.getOrNull(index)

    /** Display order of [decision]'s options (stable for this run). */
    fun order(decision: Decision): List<Int> = ChallengeEngine.order(decision, run.seed)

    /** Clock shown in the crisis bar: the open decision's time, or the last one when complete. */
    val clock: String get() = challenge.decisions[current.coerceAtMost(total - 1)].time

    /**
     * Locks decision [index] with [selected] (content indexes). Ignored when it is not the open
     * decision or nothing is selected. Returns the result, or null when ignored.
     */
    fun lock(index: Int, selected: Collection<Int>): DecisionResult? {
        if (index != current || index >= total || selected.isEmpty()) return null
        val result = ChallengeEngine.lock(challenge.decisions[index], selected)
        run = run.copy(results = run.results + result)
        save(run)
        if (complete) finish()
        return result
    }

    /** A new run with a new option order. The best score and the XP flag are kept. */
    fun replay() {
        run = ChallengeRun(newSeed())
        save(run)
    }

    private fun finish() {
        val best = app.load(LearnKeys.challengeBest(challenge.id), ChallengeBest.serializer())
        if (best == null || good > best.good) {
            app.save(LearnKeys.challengeBest(challenge.id), ChallengeBest.serializer(), ChallengeBest(good, total, outcome))
        }
        val payload = JsonObject(
            mapOf(
                "challenge_id" to JsonPrimitive(challenge.id),
                "good" to JsonPrimitive(good),
                "total" to JsonPrimitive(total),
                "outcome" to JsonPrimitive(outcome.name.lowercase()),
            ),
        )
        if (app.emitOnce(LearnKeys.challengeCompleted(challenge.id), EventTypes.CHALLENGE_COMPLETED, payload)) {
            app.addXp(Xp.CHALLENGE_COMPLETED)
        }
    }

    private fun save(r: ChallengeRun) = app.save(LearnKeys.challengeRun(challenge.id), ChallengeRun.serializer(), r)
}
