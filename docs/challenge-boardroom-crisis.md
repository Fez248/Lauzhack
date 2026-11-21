# Chapter Home + Boardroom crisis challenge: implementation brief

> **Satellite of [docs/mvp0-plan.md](mvp0-plan.md).** Status: DRAFT, to be reviewed by Nicholas.
> Implements two things: (A) a chapter-based Home that registration lands on, and (B) the
> "Boardroom crisis" challenge, ported from the HTML mockup to Compose. **No HTML is shipped.**

## 0. How to use this brief (for the implementing agent)
You are implementing a feature in a Kotlin Multiplatform + Compose Multiplatform app (module
`shared`, package `tea`, web target = wasm). Before writing code, read in this order:
1. `AGENTS.md` (git rules, ownership, no real brands, frugal AI).
2. `docs/mvp0-plan.md` §8 (design system), §9.1 (navigation), §9.4 (lesson renderer), §13 (localisation).
3. `guide-rules.md` §14 (Boardroom Crisis rules) and §15 (do/don't).
4. `static/challenge-boardroom-crisis.html`: the **visual and behavioural reference**. Its copy and
   data (lines 62-246 markup, 290-455 data, 538-662 logic) are the source for the English JSON.
   Never ship, copy or link this file from the app.
5. Existing code you must reuse (paths under `shared/src/commonMain/kotlin/tea/`):
   - `ui/screens/lesson/LessonScreen.kt` (section paging, `StepBar` progress, `RuleCardView`,
     enlarged-card `TeaDialog`, `goToSection`), `ui/screens/lesson/AttacksSection.kt` (review link pattern).
   - `ui/components/*`: `ScreenColumn`, `TopBar`, `StepBar`, `SectionHeading`, `TeaCard`, `PersonaCard`,
     `PersonaBubble`, `PersonaSpeech`, `PersonaNameTag`, `PersonaAvatar`, `PersonaTitle`, `VoiceLine`,
     `SpiderPanel`, `ChoiceRow`/`ChoiceState`, `FeedbackCard`/`FeedbackKind`/`VerdictLabel`, `TeaButton`
     (Primary/Secondary/Text), `TeaDialog`, `TeaProgressBar`, `Banner`, `DashedDivider`, `TeaIcons`.
   - `ui/theme/*`: `TeaTheme.colors/type/isCompact`, `TeaSpacing`, `TeaSize`, `TeaShapes`, `hardShadow`,
     `personaStyle`, `Voice(persona)`. **No raw hex, no new colours, no blur, no gradients.**
   - `model/Lesson.kt` (`RuleCard`, `Rule`), `model/TeaJson.kt` (snake_case), `content/ContentRepository.kt`.
   - `ui/screens/learn/LearnStore.kt` (`LearnKeys`, `emitOnce`), `engine/Xp.kt`, `model/Events.kt`.
   - `ui/screens/leaderboard/LeaderboardScreen.kt` (`app.leaderboard(period)`, `LeaderboardResponse`, `RankRow`).
Work on your human's branch, never `main`. Suggested PR split: **PR1** Home + flow, **PR2** challenge
engine + content + validation, **PR3** challenge screen UI + screenshots.

## 1. Goals and non-goals
Goals
- Registration (Language → Email → Code → Name → Password → Onboarding → Terms) ends on **Home**.
- Home lists all chapters with their status, plus a score/ranking column; responsive from 360dp to desktop.
- The Boardroom crisis is a native Compose screen with the four sections of the mockup, the same
  rules, the same scoring, en/fr/es, resumable after reload, XP once on first completion.

Non-goals
- No server-side grading yet (the client grades; same caveat as the quiz, see guide-rules §14).
- No floating typewriter "corner guide" (the mockup's `.buddy`). Its lines become a static
  `PersonaBubble` at the top of each section (see §4.2).
- No new chapters' content: Passwords etc. are "Coming soon" placeholders.
- Challenge XP is local (`addXp`); the server leaderboard does not count it yet.

## 2. Flow change: registration lands on Home
Replace every "go to `Screen.PlacementIntro` after registration" with `Screen.Home`:
- `ui/screens/onboarding/TermsScreen.kt:~78` (`goNext(if placement == null PlacementIntro else Home)` → `goNext(Screen.Home)`).
- `ui/screens/onboarding/OnboardingScreen.kt:~108` (`finishOnboarding`).
- `app/AppState.kt` `nextAfterSignIn()` (~288) and `startScreen()` (~313): terms accepted → Home.
- Placement stays reachable: from the Home placement card (§3.3) via `app.navigate(Screen.PlacementIntro)`
  (push, so Back returns to Home). `PlacementIntro` must no longer be a stack root in `goNext`
  (`ui/screens/ShellUi.kt:~114`), only `Home` is.
- Update tests that expect PlacementIntro after Terms (`commonTest/.../AppStateTest.kt`, `jvmTest/.../AppUiFlowTest.kt`).

## 3. Home page (rewrite of `ui/screens/home/HomeScreen.kt`)

### 3.1 Data: chapter catalogue
New content file per language: `content/chapters/chapters.{en,fr,es}.json`:
```json
{
  "chapters": [
    { "id": "phishing", "kind": "lesson", "target": "email-01", "persona": "cat",
      "title": "Spot a phishing email", "blurb": "Read the sender, the link and the pressure. Four steps, one email at a time.",
      "minutes": 10, "status": "available" },
    { "id": "passwords", "kind": "lesson", "target": null, "persona": "dog",
      "title": "Passwords and second locks", "blurb": "Long, different, and a second factor everywhere it matters.",
      "minutes": 8, "status": "soon" },
    { "id": "boardroom-crisis", "kind": "challenge", "target": "boardroom-crisis", "persona": "turtle",
      "title": "Boardroom crisis", "blurb": "A breach is under way and the board is looking at you. Five decisions, six hours.",
      "minutes": 12, "status": "available" },
    { "id": "scam-calls", "kind": "lesson", "target": null, "persona": "spider",
      "title": "Scam calls and messages", "blurb": "The fake bank, the grandson in trouble, the group that moved.",
      "minutes": 10, "status": "soon" }
  ]
}
```
- Model `model/Chapter.kt`: `@Serializable data class Chapter(id, kind: ChapterKind {LESSON, CHALLENGE}, target: String?, persona: Persona, title, blurb, minutes: Int, status: ChapterStatus {AVAILABLE, SOON})`.
- `ContentRepository.chapters(lang): List<Chapter>` with the same en fallback as `lesson()`.
- Extend `:shared:syncContent` (`shared/build.gradle.kts:127-157`) to copy `content/chapters/` and
  `content/challenges/`, and include them in the generated `index.json` if the index lists files.
- Order on screen = order in the file.

### 3.2 Chapter progress (derived, no new server data)
| Kind | Not started | In progress | Done |
|---|---|---|---|
| lesson | no `LearnKeys.lessonStarted(id)` | `flags.lessonSections[id]` = n → "Section n+1 of N" + `TeaProgressBar` | id in `flags.lessonsDone` → "Done" |
| challenge | no saved run | run saved, k decisions locked → "Decision k of 5" + bar | best result saved → "Best: 4 / 5 · Bruised" |

Button per state: **Start** / **Continue** / **Review** (lesson) or **Replay** (challenge).
Lesson → `Screen.Lesson(target, app.savedSection(target))`; challenge → `Screen.Challenge(target, savedSection)`.

### 3.3 Layout
Breakpoints: compact `< 600dp` (existing `TeaTheme.isCompact`), medium `600-959dp`, wide `≥ 960dp`
(add `TeaSize.wideBreakpoint = 960.dp`, `TeaSize.maxWideWidth = 1120.dp`, `TeaSize.sidebarWidth = 320.dp`).
Use `BoxWithConstraints`.

**Wide (≥ 960dp): two columns**, centred, max 1120dp, gap `s4`; whole page scrolls.
```
┌──────────────────────────────── main (weight 1) ──────────────┬── sidebar 320dp ────────┐
│ Hello Sam!  (SectionHeading large + intro)                     │ [Your score]            │
│ [session-expired Banner] [field DebriefCard]  (existing logic) │  XP 340   streak 3 days │
│ [Test your level card]   (only if profile.placement == null)   │  goal 5 min/day         │
│ Chapters (SectionHeading)                                      │ [Ranking · this week]   │
│ ┌ChapterCard┐ ┌ChapterCard┐   ← 2-column grid when main ≥ 640dp │  #7 of 112              │
│ └───────────┘ └───────────┘                                    │  1 Lea  520 / 2 ... / 3 │
│ ┌ChapterCard┐ ┌ ChapterCard (Coming soon, greyed) ┐            │  ── 7 You 340           │
│                                                                │  [Full leaderboard]     │
│                                                                │ About · Settings ·      │
│                                                                │ Got a suspicious email? │
└────────────────────────────────────────────────────────────────┴─────────────────────────┘
```
**Medium (600-959dp): one column**, max 720dp: greeting → banners → **stats strip** (one `TeaCard`
row: XP | streak | rank, each a label + value) → placement card → chapters in a 2-column grid →
ranking card → secondary buttons.
**Compact (< 600dp):** same order as medium, stats strip as a 3-cell row that wraps to 2+1, chapters
in 1 column, ranking card shows top 3 + you only.

Components (new, in `ui/screens/home/`, add a Gallery entry for each in `ui/screens/gallery/GalleryScreen.kt`):
- `ChapterCard(chapter, progress, onOpen)`: `TeaCard` (tilt alternates `+0.6f / -0.6f` by index, 0 when
  compact), header row = `PersonaAvatar(size = avatarSm)` + kind pill ("Lesson"/"Challenge", pill shape,
  persona soft background, label style) + minutes ("10 min", small inkSoft); `title` style title; blurb body
  inkSoft; status line + `TeaProgressBar` when in progress; `TeaButton` full width. Whole card clickable
  (same action). **Soon**: card content at 50% alpha, pill "Coming soon" instead of the button,
  `onClick = null`, semantics `disabled`, no shadow press.
- `PlacementCard(onStart)`: Turtle `PersonaCard` or `TeaCard` with `PersonaBubble(TURTLE)`: title "Test
  your level", body "About 3 minutes, no score. We use it to pick where you start.", Primary button
  "Start the test" → `navigate(Screen.PlacementIntro)`. Visible until `profile.placement != null`.
- `ScoreCard(xp, streak, dailyMinutes)`: reuses the current `StatCard` content (keep strings `home_xp*`, `home_streak*`, `home_goal`).
- `RankingCard(state)`: loads `app.leaderboard(<weekly period, PERIODS[0] in LeaderboardScreen>)` in a
  `LaunchedEffect(online, loggedIn)`; shows "#rank this week" big (from `data.me.rank`), top 3 with
  `RankRow` (make it `internal` instead of `private`), a `DashedDivider` + your row if you are not in
  the top 3, and a Text button "Full leaderboard" → `Screen.Leaderboard`. States: loading (`about_loading`),
  offline/failed → small `Banner(leaderboard_offline)`, guest → text + Secondary "Create an account" →
  `Screen.Email`.
- Keep existing behaviours: suspicious dialog, session expired banner, field debrief card, About/Settings.
- Remove the old single "lesson card"; Phishing is now a chapter card.

## 4. The Boardroom crisis challenge

### 4.1 Route and screen
- `app/TeaApp.kt`: `data class Challenge(val id: String, val section: Int = 0) : Screen`.
- `app/Routes.kt`: `toHash` → `"/challenge/$id/$section"`; `parse` → `"challenge"` branch like `"lesson"`.
- `ui/screens/App.kt` `Router`: `is Screen.Challenge -> ChallengeScreen(app, screen.id, screen.section)`
  (thin wrapper in `ui/screens/learn/LearnScreens.kt`, real UI in new `ui/screens/challenge/`).
- Mirror the lesson: `Column { TopBar(title, onBack = app::back); StepBar(["Briefing","Playbook","Decisions","Fallout"], section); key(section) { ... } }`.
  Save the section under `LearnKeys.challengeSection(id)`; `goToSection(n)` = `navigate(Screen.Challenge(id, n))`,
  after Fallout → `navigateRoot(Screen.Home)`.
- Every section except Decisions runs in `ScreenColumn(scrollState)` and ends with a Primary
  **Continue** button ("Continue to Playbook", ...). Section header = `SectionHeading(title, number = n+1, intro)`.

### 4.2 Section layouts (mockup → Compose)
Section opening line: each section starts with a `PersonaBubble` carrying `section_lines[section][0]`
(mockup `SECTION_LINES`); a second line, if any, follows as a second bubble.

**0 · Briefing** (Turtle chairs, Spider threatens)
1. **Hero** (only here): pill "Challenge: Boardroom crisis" (pill, highlight bg), `h1` "The first six
   hours", subtitle (body, inkSoft), meta line "About 12 minutes, 5 decisions" (**computed from
   `minutes` and `decisions.size`**, never hard-coded). Below: **advisers grid** = 4 small cards
   (2×2 wide/medium, 1 column compact), each `PersonaNameTag(persona, role)` + `PersonaSpeech(say)`.
2. Turtle `PersonaBubble(onSoft = true)` with `briefing.turtle_intro`.
3. **Roster** ("Who is at the table"): `FlowRow` of seat chips. Chip = circle with initials (mono, 2px
   outline) + name (bodyStrong) + role (small). `you` → highlight background + bold; `away` → 50%
   alpha + role says why. New component `SeatChip`.
4. Two columns on wide/medium, stacked on compact:
   - left `TeaCard` "What has happened so far": **timeline** rows = time chip (mono, small, outlined)
     + text; the `now` row is bold with highlight background ("**Now.** ..."). New `TimelineRow`.
   - right: two `TeaCard`s stacked. "What we know": bullets with `TeaIcons.Check` in ok colour.
     "What we don't know yet": dashed 2px outline (`DashedDivider` style border) and "?" bullets in
     bad colour. Meaning by icon + title, never by colour alone.
5. `SpiderPanel`: `PersonaBubble(SPIDER, SMUG, spider_line)`, `PersonaTitle(SPIDER, note_title)`, the
   **ransom note** = `webCard` card, Spider font italic, paragraphs, right-aligned sign "S."; then two
   chips "Deadline: 14:00", "Demand: €1.2 million". New `RansomNote`.
6. Continue → Playbook.

**1 · Playbook** (Cat and Turtle)
- Intro text from JSON. Two `RuleCardView`s exactly as in lesson `RulesSection` (side by side, stacked
  when compact, tilt ±1, tap → `TeaDialog { RuleCardBody(card, large = true) }`). Reuse, don't fork:
  move `RuleCardBody` to `internal` if needed and add an optional `highlightRule: Int?` parameter that
  draws a highlight background behind that rule (the mockup's `.is-ref`), used by review links.

**2 · Decisions** (voice per decision)
- Layout: `Column { CrisisBar (pinned, not scrolling); ScreenColumn(scroll) { current decision } }`.
- **CrisisBar** (new): `TeaCard` with row 1 = clock `08:15` (display font, h2 size) + note "The attacker
  publishes at 14:00. **5 h 45 min left.**" (plural strings for h/min); row 2 = **4 dials** in a row
  (wide/medium) or 2×2 grid (compact). **Dial** (new `CrisisDial`): label + "70%" on one line, a bar
  (track colour, 2px outline, fill animated with `animateFloatAsState`, 400ms, respect reduced
  motion if `TeaTheme` exposes it), and the level word under it. Level: Steady ≥70 (ok), Strained ≥40
  (highlight), Critical <40 (bad). Word always shown. Semantics: `progressBarRangeInfo` + content
  description "Trust 70 percent, steady".
- First visit of the section: Cat `PersonaBubble(onSoft)` with `decisions_intro`, shown above decision 1 only.
- **One decision visible at a time** (the current one = first unlocked). Already locked decisions are
  listed above it as collapsed one-line rows (time chip + title + ✓/✗) that expand on tap to show
  their full locked card (read-only). No takebacks.
- **DecisionCard** (new): `TeaCard`:
  1. meta row: time chip + "Decision 2 of 5".
  2. question: `NumberDot(n, voice)` + text (h3 → title size when compact).
  3. artifact: **ChatMessage** (new) = initials circle + name (bodyStrong) + role pill + time (mono xs)
     + message text, on `card` bg with 2px outline and `shadowSm`. Generic messenger look, no brand.
  4. note: "Choose one." / "Select every move you would make."
  5. options: `ChoiceRow(text, state, onClick, multi = type == multi)`, **order shuffled with a seed
     stored in the run** (stable across reloads; new seed on replay).
  6. warning (`ErrorLine`) "Choose at least one option first." if Lock is pressed with nothing selected.
  7. Primary **Lock in this decision**.
- **After lock** (same card, inputs disabled):
  - Each ok option → `ChoiceState.Correct` + caption "Your pick: a sound call" or "You missed this: a
    sound call"; each picked wrong option → `ChoiceState.Wrong` + "Your pick: a costly call"; other
    options → `Disabled`. Captions are small text under the row (meaning in words, not only colour).
  - `FeedbackCard(kind = Correct | NotQuite, persona = voice, verdict = "A sound call" | "A costly
    call", text = explain)`, followed by **review links**: one `TeaButton(variant = Text, small)` per
    distinct `rv` of the hits, label from `reviews[rv].label` with a leading "↗". Tap → open the
    playbook card dialog with `highlightRule = rule` (do not navigate away).
  - **Spider block** in `SpiderPanel`: name tag "Spider" + role "My reaction" (sound) or "What I did
    next" (costly). Sound → one quiet line `spider_ok`. Costly → one item per hit: cause line "You
    chose: {short}" / "You missed: {short}" (smallStrong), `why` as Spider speech, then **delta chips**
    "▼ Legal standing −20" (bad) / "▲ Systems +15" (ok). Use the minus sign `−`, not a hyphen.
  - Primary button "Continue to 09:40" (next decision time) or "See the fallout" (last) → advance
    clock and current decision; on last → `goToSection(3)`.
  - Scroll the card so the feedback is visible after lock (`scrollState.animateScrollTo`).
- Leaving the section mid-run and coming back resumes at the current decision with all locked state.

**3 · Fallout** (Spider's ledger)
- If the run is not complete: intro text "Make your five decisions first. Spider is keeping a ledger,
  and it isn't ready until you are." + Secondary "Go to the decisions" → section 2. Nothing else.
- Complete:
  1. **Score banner** (new `ScoreBanner`): big "4 / 5" (display font, h1) + `PersonaBubble(outcome.voice, score)`.
  2. **Outcome**: `SpiderPanel` with Spider avatar, `outcome.title` (PersonaTitle), `outcome.body`.
     Tier `contained` uses Mood.WORRIED for Spider and an ok accent on the title; `bruised`/`wrecked` Mood.SMUG.
  3. "Your five decisions" (h3) + **recap list**: per decision a row with time chip, title, verdict tag
     "✓ A sound call" / "✗ A costly call", "**Best move:** {best}", and for costly ones the summed
     delta chips (all shown as losses) + review links.
  4. `TeaCard` "Where the company stands at the deadline" + the 4 dials (final values).
  5. Primary "Replay the crisis with fresh order" → confirmation `TeaDialog` ("Start again? Your best
     score is kept.") → new run (new seed, start meters) → section 2. Secondary "Back to Home".
- Footer of the section: `VoiceLine(SPIDER, footer)`.

### 4.3 Behaviour and scoring (pure Kotlin, `engine/Challenge.kt`, fully unit-tested)
Transcribe the mockup logic exactly:
- `levelOf(v) = v ≥ 70 → STEADY, v ≥ 40 → STRAINED, else CRITICAL`.
- Selection rule: single = exactly one selected (radio); multi = any subset (checkbox), at least one.
- `isCorrect(decision, selected) = selected == set of indexes where ok`.
- `hits = options where (picked != ok) && fx != null`, each tagged `picked` (true = chose a bad one,
  false = missed a good one).
- `cost = sum of hits' fx`; apply cost to meters with clamp 0..100; then if correct apply `gain`, clamp
  again. (A correct decision has no hits, so cost is empty.)
- `reviews = distinct rv of hits`, in option order.
- Clock: current decision's `time`; minutes left = `deadline − time`.
- Outcome: good = count correct; `good == total → contained`, `good ≥ 3 → bruised`, else `wrecked`
  (thresholds in JSON: `"outcome_thresholds": {"bruised": 3}`).
- Test vectors (must pass): perfect run from `{trust 70, cash 75, systems 30, legal 70}` ends at
  `{trust 90, cash 85, systems 90, legal 95}`, outcome contained. Decision 1 "Wipe and rebuild" →
  `legal 50, systems 20`, reviews `[cat0]`. Decision 2 picking only "Legal and the insurer" → misses
  out-of-band (systems −15, trust −5) and response team (systems −15, legal −5), reviews `[cat1, turtle0]`.
  A value never goes below 0 or above 100.

**State** (`@Serializable data class ChallengeRun(seed: Long, results: List<DecisionResult>)`,
`DecisionResult(decisionId, selected: List<Int>, correct, cost: Map<String,Int>, reviews)`). Meters are
**recomputed** from `start_meters` + results on load (never stored), so content edits don't corrupt
saves. Keys in `LearnKeys`: `challengeRun(id)`, `challengeSection(id)`, `challengeBest(id)` (best good
count + outcome), `challengeCompleted(id)` (flag for `emitOnce`).
- On lock: append the result and save immediately (a reload can never unlock a decision).
- On first completion: `if (app.emitOnce(LearnKeys.challengeCompleted(id), EventTypes.CHALLENGE_COMPLETED, payload)) app.addXp(Xp.CHALLENGE_COMPLETED)`
  with `Xp.CHALLENGE_COMPLETED = 50`, `EventTypes.CHALLENGE_COMPLETED = "challenge_completed"`, payload
  `{id, good, total, outcome}`. Emit `challenge_started` once on first open (`emitOnce`). Update best on
  every completion.
- If the saved run references a decision id that no longer exists, discard the run.

### 4.4 Content format: `content/challenges/boardroom-crisis/challenge.{en,fr,es}.json`
Model `model/Challenge.kt`, snake_case via `TeaJson`, loaded by `ContentRepository.challenge(id, lang)`
(same fallback as `lesson`). Reuse `RuleCard`/`Rule` for the playbook. Shape (one decision shown in full):
```json
{
  "id": "boardroom-crisis", "language": "en",
  "title": "Boardroom crisis", "pill": "Challenge: Boardroom crisis",
  "headline": "The first six hours",
  "subtitle": "A breach is under way and the board is looking at you. Decide what happens next, then see what each choice costs.",
  "minutes": 12, "deadline": "14:00",
  "start_meters": { "trust": 70, "cash": 75, "systems": 30, "legal": 70 },
  "dials": [ { "key": "trust", "label": "Trust" }, { "key": "cash", "label": "Cash" },
             { "key": "systems", "label": "Systems" }, { "key": "legal", "label": "Legal standing" } ],
  "advisers": [ { "persona": "cat", "role": "Facts first", "say": "Pull the cable. Keep the evidence." } ],
  "section_lines": { "briefing": [ { "persona": "turtle", "text": "..." }, { "persona": "spider", "text": "..." } ],
                     "playbook": [], "decisions": [], "fallout": [] },
  "briefing": {
    "turtle_intro": "Welcome to the room, dear. ...",
    "roster": [ { "initials": "SM", "name": "You (Sam)", "role": "COO, in the chair", "you": true, "away": false } ],
    "timeline": [ { "time": "07:40", "text": "...", "now": false } ],
    "known": [ "..." ], "unknown": [ "..." ],
    "spider_line": "Six hours. Plenty of time to make mistakes... I do hope you make some.",
    "note_title": "A little note for the board",
    "note": [ "Dear board...", "..." ], "note_sign": "S.",
    "chips": [ "Deadline: 14:00", "Demand: €1.2 million" ]
  },
  "playbook": { "intro": "...", "cards": [ { "persona": "cat", "role": "...", "title": "...", "say": "...",
                "rules": [ { "headline": "Cut the cable, keep the power.", "body": "..." } ], "sign_off": "..." } ] },
  "reviews": { "cat0": { "card": 0, "rule": 0, "label": "Cat's first rule: cut the cable" } },
  "decisions_intro": { "persona": "cat", "text": "Five decisions. Lock each one in and I'll check it. ..." },
  "decisions": [ {
    "id": "d1", "time": "08:15", "voice": "cat", "type": "single", "title": "Stop the spread",
    "text": "The encryption is moving from server to server. ...",
    "artifact": { "initials": "DK", "name": "Dana Keller", "role": "Head of IT", "time": "08:14",
                  "text": "It's spreading. Fourteen servers in ten minutes, ..." },
    "options": [
      { "text": "Cut the affected machines off from the network, but leave them switched on. Nobody wipes anything.",
        "short": "Isolate, keep power on", "ok": true },
      { "text": "Switch everything off and wipe it, then rebuild from scratch. A clean slate.",
        "short": "Wipe and rebuild", "ok": false, "fx": { "legal": -20, "systems": -10 }, "rv": "cat0",
        "why": "You wiped the evidence along with the problem. ..." }
    ],
    "gain": { "systems": 15 },
    "explain": "Isolate first, ask questions later. ...",
    "spider_ok": "Hm. A cable pulled and the evidence untouched. ...",
    "best": "Disconnect the affected machines from the network, leave them powered on, and wipe nothing."
  } ],
  "outcome_thresholds": { "bruised": 3 },
  "outcomes": { "contained": { "voice": "turtle", "title": "...", "body": "...", "score": "..." },
                "bruised": { "voice": "cat", ... }, "wrecked": { "voice": "turtle", ... } },
  "footer": "Until next time... the next note won't come by email. Do ask whether your VPN has a second lock."
}
```
Field mapping from the mockup: `t→text`, `s→short`, `ok`, `fx`, `why`, `rv`, `gain`, `explain`,
`spiderOk→spider_ok`, `best`; `REVIEW` → `reviews` with `card` = index in `playbook.cards`;
`OUTCOMES` (drop `trap`, derived from tier); `SECTION_LINES` → `section_lines`; hero, roster, feed, know
lists, ransom note and footer from the markup. Transcribe **all five decisions and every line** (en).

**Interface strings** (not content) go in a new `composeResources/values{,-fr,-es}/strings_challenge.xml`,
prefix `challenge_`: section names, "Continue to %s", "Decision %1$d of %2$d", "Choose one.", "Select every
move you would make.", "Choose at least one option first.", "Lock in this decision", "See the fallout",
caption texts, "A sound call"/"A costly call", "You chose: %s"/"You missed: %s", "My reaction"/"What I
did next", level words, clock note with plurals, "About %1$d minutes, %2$d decisions", "Best move:",
"Your five decisions" (use plural/number), replay dialog, "Coming soon", "Lesson"/"Challenge", Home
strings (placement card, ranking card, chapters heading).

### 4.5 Copy rules and required fixes
- No real brands: change `tea.app` in decision 5's artifact to `thisistea.example` (the project's fake
  domain). Decision 5's `explain` names a real password manager: keep it only if the same product is
  already listed in `content/resources/resources.json`; otherwise say "a password manager".
- British spelling, no em dashes, Spider keeps his ellipses and italic voice, Turtle says "dear", Dog
  says "buddy". "5 decisions" is never hard-coded (computed).
- **French and Spanish**: full translations of every content and interface string, natural not literal.
  Turtle's "dear" → a warm, gender-neutral endearment used consistently (proposal: fr "mon petit",
  es "cariño"; Nicholas to confirm); Dog's "buddy" → fr "mon pote", es "colega". GDPR → RGPD (fr, es). Swiss
  law "dès que possible" / "lo antes posible". Money "1,2 million d'euros" / "1,2 millones de euros".
  Times stay 24h. Numbers in `fx`, `gain`, ids, `ok` flags and option counts are **identical** across languages.
  Mark the fr/es files as "AI translation, needs human review" in the PR description (AXA: no AI slop).

### 4.6 New components summary (all in Gallery + screenshot)
`ChapterCard`, `PlacementCard`, `ScoreCard`, `RankingCard` (home); `SeatChip`, `TimelineRow`,
`KnowCard(unknown: Boolean)`, `RansomNote`, `CrisisBar`, `CrisisDial`, `ChatMessage`, `DecisionCard`,
`DeltaChips`, `SpiderFallout`, `ScoreBanner`, `RecapRow` (challenge). Put generic ones (`CrisisDial`,
`ChatMessage`, `DeltaChips`, `TimeChip`, `Pill`) in `ui/components/`, the rest in `ui/screens/challenge/`.
All use theme tokens, 2px outlines, hard shadows, `TeaShapes`; touch targets ≥ `TeaSize.minTouch`.

## 5. Tests and verification
Unit (commonTest)
- `engine/ChallengeTest.kt`: the test vectors in §4.3, clamp, levelOf boundaries (39/40/69/70),
  outcome tiers, multi exact-match, minutes left, meters recomputed from results, unknown decision id discards run.
- `RoutesTest`: add `Screen.Challenge("boardroom-crisis", 2)` to the round-trip list and assert hash `#/challenge/boardroom-crisis/2`.
- `AppStateTest`: after Terms the screen is Home; PlacementIntro opened from Home → Back returns to Home.
- Home progress derivation (lesson not started / in progress / done; challenge none / k locked / best).
- Persistence: lock → reload (new `ChallengeController` on same `FakeTeaApp`) keeps the decision locked;
  completion emits `challenge_completed` once and adds XP once even after replay.

Content validation (jvmTest, extend `ContentValidationTest`)
- Each `challenge.*.json` parses; single decisions have exactly one ok, multi ≥ 1; every wrong option and
  every ok option of a multi decision has `fx`, `why`, `rv`; every `rv` exists in `reviews` and points to
  an existing card/rule; times strictly increase and are before `deadline`; dial keys in `fx/gain` exist.
- en/fr/es structural parity: same ids, option count, ok flags, fx, gain, rv, times.
- `chapters.*.json` parse; every `available` chapter's target exists (lesson or challenge); ids unique and equal across languages.

Screenshots (jvmTest, `LearnScreenshotsTest` style with `FakeTeaApp(TestContent.repository())`; extend
`TestContent` to read `content/challenges` and `content/chapters`)
- Home at 400, 800 and 1280 dp width, light and dark, guest and logged-in-with-rank, placement card on/off.
- Each challenge section at 400 and 1280 dp, light and dark; Decisions before lock, after a sound
  single, after a costly multi; Fallout for contained and wrecked.
- Look at the PNGs in `shared/build/screenshots/` and compare with the mockup opened in a browser.

Manual end-to-end (web)
- `./gradlew :composeApp:wasmJsBrowserDevelopmentRun`: register a new account → land on Home →
  start the challenge → play all five (one deliberately wrong, check dials, Spider block, review link
  opens the highlighted rule) → reload mid-way (resumes, locked stays locked) → Fallout → XP +50 once →
  Home card shows "Best: 4 / 5 · Bruised" → Replay. Switch language to fr and es and repeat briefly.
  Resize the window across 600 and 960 dp. Run the full test suite: `./gradlew :shared:allTests`.

## 6. Decision entry to propose (a human must approve, then append to `docs/decisions.md`)
`2026-11-21 · nicholas · PROPOSED: Home becomes a chapter list (lessons + challenges, "coming soon"
placeholders) with a score/ranking column; registration lands on Home and the placement test becomes
an optional Home card. New content formats content/chapters/chapters.<lang>.json and
content/challenges/<id>/challenge.<lang>.json (spec: docs/challenge-boardroom-crisis.md). The Boardroom
crisis is ported to Compose; the HTML mockup stays a design reference. · Gives the app a clear map of
what to learn, and adds a decision-making challenge (Scholé: whole-task practice).`

## 7. Definition of done
- No HTML from `static/` is shipped or linked by the app; `openUrl` is not used for the challenge.
- Registration → Home; Home responsive at 360 / 800 / 1280 dp; chapters reflect real progress.
- Challenge playable end to end in en/fr/es, scoring matches §4.3, resumable, XP once.
- All new tests green, screenshots reviewed, Gallery updated, `TASKS.md` line and handoff file updated,
  PRs small and reviewed by another teammate.
