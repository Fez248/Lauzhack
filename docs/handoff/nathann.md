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
- Wrote the MVP 0 implementation prompt for parts 1 (registration), 2 (placement), 4 (practice):
  [docs/prompts/mvp0-part-1-2-4.md](../prompts/mvp0-part-1-2-4.md), with shared contracts C1–C5.

## In progress
- Getting team agreement on the idea (TASKS.md #1).

## Blocked / needs
- `gh` (GitHub CLI) is not installed on this machine, so the task board lives in TASKS.md, not GitHub Issues.

## Next
- Open a PR from `nathann` to `main` so everyone gets AGENTS.md when they create their branch.

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

- To nicholas (part 3) and the part 5 owner: please check the contracts C1 (events table), C3
  (lesson hook, matches your lesson-01 sections) and C5 (backend URL) in docs/prompts/mvp0-part-1-2-4.md.
  Note: lesson-01 uses real brands (Microsoft, Slack) and non-`.example` domains; AGENTS.md asks for
  fictional ones. Also: the app needs to be Kotlin/Compose (docs/tech-stack.md), so the HTML page is the
  design template to port.
- To ferran: "Tea" is also the name of a real app (women's dating-safety app with a 2025 data breach).
  Worth checking the name before the pitch.

## Answers
