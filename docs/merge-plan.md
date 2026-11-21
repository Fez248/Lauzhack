# Merge plan: integrating everyone's work for MVP 0

> Status: **proposal** (nathann, 2026-11-21). This plan reconciles every branch into one coherent
> MVP 0 and names how it reaches `main`. Design decisions here were taken with the human (nathann)
> in the loop this session; the team confirms them in `docs/decisions.md`.

## MVP 0, the one loop we ship

register → **placement test** → **lesson 1 (email phishing)** → **Duolingo-style practice with
fading four-step prompts** → one opt-in, click-only practice-awareness email to the learner's inbox.

Ownership of the five parts:
| Part | What | Owner |
|---|---|---|
| 1 | Registration + profile (verified email, password findings, onboarding, signals) | nathann |
| 2 | Adaptive placement test (~3 min) | nathann |
| 3 | Lesson 1 theory + page structure (Content → Tools → Attacks → Status) | nicholas |
| 4 | Applied practice inside the lesson (faded four-step scaffolding) | nathann |
| 5 | Readiness button → one opt-in click-only practice email | krishna |
| — | Art + design system (four animal characters) | ferran + nicholas |

nathann's parts 1/2/4 are specified in [docs/prompts/mvp0-part-1-2-4.md](prompts/mvp0-part-1-2-4.md)
with shared contracts **C1–C5**. Part 5 is designed in
[docs/press-to-complete-trigger.md](press-to-complete-trigger.md).

## Current branch state (2026-11-21)

| Branch | Latest | Contributes | Merged into `nathann`? |
|---|---|---|---|
| `nathann` | MVP 0 prompt + contracts | parts 1/2/4 spec, engine docs, tech stack, hosting | — |
| `nicholas` | design system + lesson 1 | `guide-rules.md`, `static/lesson-01-phishing.html`, `static/tea-theme.css` | yes |
| `Fez248/storytelling` | characters | `docs/info.md`, `docs/characters.md` | yes |
| `Krishna` | part 5 design | `docs/press-to-complete-trigger.md`, build prompt | yes |
| `main` | initial commit | — | target of the PR |

All four feature branches only **add** files, so merging them into `nathann` produced **no git
conflicts**. The real work is reconciling design conflicts (below), not resolving merge markers.

## Decisions locked this session (go into decisions.md as PROPOSED)

1. **Name:** the app is **ThisIsTea** (drop "[DCST]"). Email/sender account: `thisistea@gmail.com`.
   Reason: "Tea" alone collides with a real women's-safety app that had a 2025 breach — bad look for a
   security app and a judge may spot it.
2. **Web front end:** **keep Kotlin Multiplatform + Compose** (per docs/tech-stack.md and the JetBrains
   challenge). Nicholas's HTML/CSS + `guide-rules.md` are the **artistic direction / styling
   prototype**, to be **ported into a Compose theme**, not shipped as HTML.
3. **Consent for the practice email = two layers.** Layer 1: a clause lightly buried in the accepted
   Terms of Use (the legal/ethical basis, and itself a future "you didn't read the terms" lesson).
   Layer 2: the end-of-lesson readiness button, which **never mentions an email** — it only gates on
   the learner feeling ready, so the test isn't telegraphed. No press → no email.
4. **The real practice email is click-only and from a plain Gmail address** (`thisistea@gmail.com`),
   **no custom or look-alike domain**. We record only opened/clicked; there is no sign-in page and no
   credentials are collected. A practice sign-in step may come in a later loop.
5. **Hosting:** static wasm build on GitHub Pages (Actions on push to `main`), content bundled so the
   app works offline; Ktor server + DB in Docker Compose on a team laptop behind a cloudflared/ngrok
   tunnel; backend URL set at runtime (`?api=` override). See contract C5.
6. **Shared contracts C1–C5** (database + events bus, email item format, lesson hook, persona hook,
   runtime backend URL) are the interfaces between parts 1/2/4, 3, 5 and the assets.

## Design conflicts found, and how they resolve

| # | Conflict | Resolution |
|---|---|---|
| A | **HTML/CSS+JS (nicholas) vs Compose/KMP (tech-stack.md)** for the web UI | Keep Compose/KMP. Port the HTML/CSS into a Compose `PersonaTheme` + screens; the HTML stays as the design reference, not shipped. |
| B | **Real brands in `lesson-01-phishing.html`** (Microsoft, Slack, `tea.app`/`tae-app.com`) vs AGENTS.md "no real brands, `.example` only" | During the port, replace with fictional brands and `.example` domains. In-app email *items* always use `.example`. |
| C | **App name "Tea"** vs the real breached app | Rename to **ThisIsTea**. |
| D | **Two consent designs** — krishna's button vs nathann's terms clause | Two layers: terms = consent, button = readiness gate that doesn't mention email (decision 3). |
| E | **Look-alike domain + sign-in page (krishna's doc)** vs the team call | For MVP 0: plain Gmail sender, **click-only**, no sign-in page (decision 4). Krishna's scheduler/delay logic stays; the landing-page/credential parts are deferred. |
| F | **Two "email" meanings** — fictional in-app items vs the one real inbox email | Keep them explicitly separate (prompt "Safety and privacy rules"): items = fictional `.example`; real email = Gmail, click-only. |
| G | **Placement/assessment** — nicholas's self-rated confidence + 3-question quiz vs nathann's adaptive engine | The adaptive engine (part 2) is the real placement. Nicholas's in-lesson quiz becomes part-4 practice; the "Status" self-rating feeds the overconfidence flag only. |
| H | **Server shape** — krishna's in-memory Ktor scheduler vs nathann's Ktor + DB + events bus | One Ktor server. Krishna's scheduler writes `field_*` events into the shared `events` table (C1) instead of in-memory. |

## Target architecture (what everyone builds toward)

- **One KMP project.** `commonMain` holds as much as possible: DTOs, placement/mastery/scaffold/
  selection engines, password-finding model, i18n, the ported `PersonaTheme`, and shared AI logic
  (Koog) for free-text four-step grading and cached item-variation generation. Per-platform code is a
  thin `AppShell` + storage + HTTP engine via `expect`/`actual`. The README must state what is shared
  vs platform-specific (JetBrains scoring).
- **≥2 platforms running:** web (`wasmJs`, demo) + desktop (`jvm`) and/or Android (drills).
- **Dark/light mode + localization (en/fr/es)** built in from the start (both are explicit JetBrains
  scoring items; the design system already has a dark Spider palette).
- **Server:** Ktor + Postgres (Exposed) in Docker Compose, Gmail SMTP, events bus (C1). Behind a
  tunnel; runtime backend URL (C5).

## How it reaches `main`

AGENTS.md: nothing lands on `main` except via a PR reviewed by another teammate. gh CLI is not
installed here, so:
1. This plan and the updated prompt are committed to **`nathann`** and pushed.
2. **nathann opens the PR `nathann → main` manually** on GitHub; a teammate (or their agent, human
   approving) reviews. Because it is docs + the merged feature branches, it is safe to review quickly.
3. After merge, everyone gets AGENTS.md, the contracts, the design system and this plan when they
   `git merge origin/main` into their own branch.
4. Code then lands per part in **small PRs** against `main`, each reviewed, in this order:
   KMP scaffold + ported theme → C1 server + auth → part 1 screens → part 2 → part 4 → part 5 wiring
   → part 3 lesson content.

## Safety rails preserved from AGENTS.md and the docs

- No real brands or look-alike domains anywhere; `.example` in content, plain Gmail for real mail.
- Opt-in only (terms + readiness press); pause/stop in Settings; click-only, no credentials collected.
- Core scenarios human-written; AI does reviewed variations only; frugal AI (small model + cache, no
  live calls in the demo).
- Secrets only in `.env` (gitignored); passwords stored as argon2id hashes, findings only, never plaintext.

## Open items / risks

- Port effort: re-implementing the polished HTML design in Compose is the main schedule risk — do the
  theme + one lesson screen first and keep the HTML open side by side as the reference.
- Confirm the Gmail account + app password, and that Gmail SMTP sending is acceptable for the demo
  volume (a few team addresses).
- Krishna + nathann to align the `events`/`field_*` contract (C1) and who owns the Ktor scheduler.
- Nicholas to confirm the lesson hook (C3) maps onto his five sections.
