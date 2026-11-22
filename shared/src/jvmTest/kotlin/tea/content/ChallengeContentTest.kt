package tea.content

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail
import tea.engine.ChallengeEngine
import tea.model.Challenge
import tea.model.ChapterKind
import tea.model.ChapterStatus
import tea.model.DecisionType

/**
 * Validates `content/challenges/` and `content/chapters/` (docs/challenge-boardroom-crisis.md §5):
 * every costly option explains itself, review links point at real rules, and the numbers are the
 * same in every language.
 */
class ChallengeContentTest {
    private val contentDir: File = System.getProperty("tea.contentDir")?.let(::File)
        ?: generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "content") }
            .first { File(it, "languages.txt").isFile }

    private val languages = File(contentDir, "languages.txt").readLines().map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun challenges(): Map<String, Map<String, Challenge>> =
        File(contentDir, "challenges").listFiles().orEmpty().filter { it.isDirectory }.associate { dir ->
            dir.name to dir.listFiles().orEmpty()
                .mapNotNull { f -> Regex("""^challenge\.([a-z]{2})\.json$""").matchEntire(f.name)?.let { it.groupValues[1] to f } }
                .associate { (lang, f) -> lang to ContentRepository.parseChallenge(f.readBytes()) }
        }

    private fun check(c: Challenge, where: String, problems: MutableList<String>) {
        val dialKeys = c.dials.map { it.key }.toSet()
        if (c.startMeters.keys != dialKeys) problems += "$where: start_meters keys ${c.startMeters.keys} != dials $dialKeys"
        c.reviews.forEach { (key, r) ->
            val card = c.playbook.cards.getOrNull(r.card)
            if (card == null || r.rule !in card.rules.indices) problems += "$where: review $key points to a missing rule"
        }
        var last = -1
        c.decisions.forEach { d ->
            val at = "$where/${d.id}"
            val t = ChallengeEngine.toMinutes(d.time)
            if (t <= last) problems += "$at: times must increase"
            if (t >= ChallengeEngine.toMinutes(c.deadline)) problems += "$at: time after the deadline"
            last = t
            val sound = d.options.count { it.ok }
            if (d.type == DecisionType.SINGLE && sound != 1) problems += "$at: a single decision needs exactly one sound option"
            if (d.type == DecisionType.MULTI && sound < 1) problems += "$at: a multi decision needs a sound option"
            d.options.forEachIndexed { i, o ->
                val needsCost = !o.ok || d.type == DecisionType.MULTI
                if (needsCost && (o.fx.isNullOrEmpty() || o.why.isNullOrBlank() || o.rv == null)) {
                    problems += "$at option $i: needs fx, why and rv"
                }
                if (o.rv != null && o.rv !in c.reviews) problems += "$at option $i: unknown rv '${o.rv}'"
                o.fx?.keys?.filter { it !in dialKeys }?.forEach { problems += "$at option $i: unknown dial '$it'" }
                if (o.short.isBlank()) problems += "$at option $i: short name missing"
            }
            d.gain.keys.filter { it !in dialKeys }.forEach { problems += "$at: unknown dial '$it' in gain" }
        }
    }

    /** What must be identical across languages: ids, times, voices, flags and numbers. */
    private fun shape(c: Challenge) = listOf(
        c.id, c.deadline, c.startMeters, c.dials.map { it.key }, c.reviews.mapValues { it.value.card to it.value.rule },
        c.outcomeThresholds, c.playbook.cards.map { it.persona to it.rules.size },
        c.decisions.map { d -> listOf(d.id, d.time, d.voice, d.type, d.gain, d.options.map { listOf(it.ok, it.fx, it.rv) }) },
    )

    @Test
    fun challengesAreValid() {
        val problems = mutableListOf<String>()
        val all = challenges()
        assertTrue(all.isNotEmpty(), "no challenges found")
        all.forEach { (id, byLang) ->
            val en = byLang["en"] ?: return@forEach run { problems += "$id: English file missing" }
            languages.forEach { lang ->
                val c = byLang[lang] ?: return@forEach run { problems += "$id: $lang missing" }
                if (c.id != id) problems += "$id/$lang: id '${c.id}' does not match the folder"
                if (c.language != lang) problems += "$id/$lang: language '${c.language}'"
                check(c, "$id/$lang", problems)
                if (shape(c) != shape(en)) problems += "$id/$lang: numbers, ids or flags differ from English"
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }

    @Test
    fun chaptersPointAtRealContent() {
        val problems = mutableListOf<String>()
        val challengeIds = challenges().keys
        val lessonIds = File(contentDir, "lessons").listFiles().orEmpty().filter { it.isDirectory }.map { it.name }.toSet()
        val byLang = languages.associateWith { lang ->
            val f = File(contentDir, "chapters/chapters.$lang.json")
            if (!f.isFile) {
                problems += "chapters.$lang.json missing"
                emptyList()
            } else {
                ContentRepository.parseChapters(f.readBytes()).chapters
            }
        }
        val enIds = byLang["en"].orEmpty().map { it.id }
        byLang.forEach { (lang, chapters) ->
            val ids = chapters.map { it.id }
            if (ids.toSet().size != ids.size) problems += "chapters.$lang: duplicate ids"
            if (ids != enIds) problems += "chapters.$lang: ids/order differ from English"
            chapters.filter { it.status == ChapterStatus.AVAILABLE }.forEach { c ->
                val known = when (c.kind) {
                    ChapterKind.LESSON -> c.target in lessonIds
                    ChapterKind.CHALLENGE -> c.target in challengeIds
                }
                if (!known) problems += "chapters.$lang/${c.id}: target '${c.target}' does not exist"
                if (c.demoTarget != null && challenges()[c.demoTarget]?.get(lang)?.demo != true) {
                    problems += "chapters.$lang/${c.id}: demo_target '${c.demoTarget}' is not a demo challenge"
                }
            }
        }
        if (problems.isNotEmpty()) fail(problems.joinToString("\n"))
    }
}
