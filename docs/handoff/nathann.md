# Handoff: nathann

Branch: `nathann` · Last updated: 2026-11-21

## Done
- Set up team coordination: AGENTS.md, CLAUDE.md, docs/idea.md, docs/decisions.md, TASKS.md,
  handoff template, draft scenario format, .gitignore.
- Brainstorm written up: [docs/scenario-engine.md](../scenario-engine.md) (slots, operators,
  seeds incl. Swiss pack), [docs/localization.md](../localization.md) (languages, theme, resource
  cards), [content/resources/resources.json](../../content/resources/resources.json) (all links unverified).
- Scenario format extended: category, language, country, slots, variant_of, operators.
- New idea written up: [docs/onboarding-and-field-mode.md](../onboarding-and-field-mode.md)
  (onboarding questions, placement test, opt-in field mode with practice scams by real email/SMS).
  Two PROPOSED decisions, TASKS #27–30.
- Tech stack set as a requirement: Kotlin Multiplatform + Compose. Web/desktop = simulator + drills,
  phone = drills only. See [docs/tech-stack.md](../tech-stack.md).
- Merged Ferran's `Fez248/storytelling` branch (docs/info.md) into `nathann`.
- Merged Ferran's docs/characters.md and Nicholas's lesson 1 template (static/) into `nathann`.
- Wrote the MVP 0 implementation prompt for parts 1, 2, 4 with shared contracts C1–C5 (now merged
  into docs/mvp0-plan.md).
- Merged Krishna's part-5 design (press-to-complete trigger) and Nicholas's latest (design system
  `guide-rules.md` + `tea-theme.css` + expanded lesson 1) into `nathann`.
- Wrote the integration plan (branch reconciliation, conflicts A–H), now merged into docs/mvp0-plan.md.
- **Consolidated everything into one plan: [docs/mvp0-plan.md](../mvp0-plan.md)**, the single source of
  truth for MVP 0 (every nitty-gritty detail, build order, Definition of done). Deleted the merge plan,
  the part 1/2/4 prompt and the part 5 build prompt; moved Krishna's design to `docs/later/`; added a
  "satellite file" banner to every other doc; AGENTS.md reading order and README point to the plan.
- Reflected this session's decisions in the prompt and decisions.md: rename to **ThisIsTea**
  (`thisistea@gmail.com`); keep KMP/Compose and port the HTML design; two-layer consent; click-only
  Gmail practice email; shared AI via Koog in `commonMain`; JetBrains/KMP scoring notes.

- Merged Krishna's `awareness-sim-*` docs (they replace `docs/later/press-to-complete-trigger.md`).
- Made docs/mvp0-plan.md honest: explicit opt-in for the practice email, password findings for the
  accepted password only, no contacts, no tracking pixel, minimal device data (decisions.md entry).
- Aligned every doc on "cyber security literacy teaching" and the honest rules of plan §17 (see the
  decisions.md entry): edited files of ferran, nicholas and krishna (questions below).

## In progress
- Getting team agreement on the idea (TASKS.md #1).

## Blocked / needs
- `gh` (GitHub CLI) is not installed on this machine, so the task board lives in TASKS.md, not GitHub Issues.

## Next
- **nathann to open the PR `nathann → main` manually** (gh not installed here). It carries AGENTS.md,
  the contracts, the design system and docs/mvp0-plan.md. A teammate reviews (it also touches
  AGENTS.md: reading order now starts with the plan).
- After merge, each person `git merge origin/main` into their branch, then code lands per part in
  small PRs (milestones M0–M9 in docs/mvp0-plan.md §14).

## Questions for others
- To everyone: does the idea in docs/idea.md work for you? Which area do you want to own (TASKS.md)?
- To ferran: I wrote up your AI & sensitive data idea (scenario-engine.md, AI1–AI6, TASKS #26). Want to own it? Please correct anything off.
- To everyone: claim your language in TASKS.md #21 (fr, en, hi, te, ur, de, it, es, ca).
- To everyone: Swiss facts marked ⚠ in scenario-engine.md (2024 cold-call ban, commission caps,
  30 Nov deadline, 2nd-pillar central office) need checking against official sources.
- To everyone: field mode (practice scams sent to your real inbox) needs your opinion, especially
  the safety rules. Would you opt in for the live demo? Two study citations marked ⚠ need checking.

- To everyone: the app must be in Kotlin (docs/tech-stack.md). Who already knows Kotlin/Compose?
  Who has a Mac for the iOS build?

- To nicholas (part 3): please check contracts C1 (events table), C3 (lesson hook — it maps onto your
  five sections Content→Tools→Attacks→Status→Answers) and C5 (backend URL) in
  docs/mvp0-plan.md (§4.2, §5, §12). Two things: (1) your `guide-rules.md` +
  `tea-theme.css` are adopted as the **artistic direction**, to be **ported into a Compose theme** (we
  keep KMP, don't ship the HTML); (2) during the port, swap the real brands (Microsoft, Slack,
  `tea.app`/`tae-app.com`) for fictional ones + `.example` domains, per AGENTS.md.
- To krishna: your press-to-complete trigger is **part 5** of MVP 0. Three alignments (see
  docs/mvp0-plan.md §1.3, §10.6, §12): (1) consent is now **two layers** — the Terms of Use are the
  consent, your button is the **readiness gate and must not mention an email** (it already says so);
  (2) for MVP 0 the email is **click-only from `thisistea@gmail.com`, no look-alike domain and no
  sign-in page** — keep your delay/scheduler, defer the landing-page/credential parts; (3) write your
  outcomes as `field_*` events into the shared `events` table (contract C1), not in-memory.
- To ferran: the app is renamed **ThisIsTea** (sender `thisistea@gmail.com`) — "Tea" alone collides
  with a real women's-safety app that had a 2025 breach. OK with you?

- To krishna: your design doc moved to `docs/later/press-to-complete-trigger.md` (later loop) and the
  build prompt was deleted, because the landing page / resend series are out of scope for MVP 0. Your
  MVP 0 part is in docs/mvp0-plan.md §9.6, §10.6 and §12: the scheduler persists `field_sends` in the DB,
  and you write the `practice-email-01` text with the team. OK?

- To krishna: I merged your `awareness-sim-*` docs (kept where you put them, as later-loop satellites).
  Change for part 5 in MVP 0 (docs/mvp0-plan.md §9.6, §10.6): the button now **says plainly** that one
  practice email will come ("Yes, send me one"); only the timing and look stay secret. The server
  returns `{scheduled, reason?}` instead of hiding it, and there is no open pixel. The old "must not
  hint" rule tripped AI safety filters and reads as deceptive to judges. OK with you?
- To ferran: I edited docs/info.md and docs/characters.md: no more "hack the student". Practice
  emails happen only when the learner asks for one, from our plain address, click-only, nothing typed
  collected; Spider "plays the scammer in practice scenarios"; offboarding phrased as "remove access on
  the last day, planned with HR". Your voice and jokes are kept. Please check you're happy with it.
- To nicholas: in static/lesson-01-phishing.html and guide-rules.md I replaced `tea.app`/`tae-app.com`
  with `thisistea.example`/`thisistae-app.example`, Microsoft/Outlook/Slack with CloudDrive/TeamChat,
  "Tea [DCST]" with ThisIsTea, and Spider's banner no longer says "you typed your password into my
  page": it now says the learner asked for a practice email and clicked, and nothing typed was kept.
  The recovery card is framed as "if a real one ever gets you". OK?
- To krishna: I rewrote docs/awareness-sim-trigger.md and docs/awareness-sim-prompt.md to match the plan
  (§9.6, §10.6, §17): the button says plainly a practice email is coming, plain Gmail sender (no
  `isthistee.com`: we don't own it), the link goes to a reveal with **no form**, outcomes are
  `clicked`/`reported`, and the spaced series is a later loop with its own opt-in text. Your delay,
  scheduler and grading idea are kept. Please confirm or correct.

## Answers
