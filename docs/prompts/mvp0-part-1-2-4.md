# MVP 0: Part 1 (registration), Part 2 (placement test), Part 4 (applied practice)

> Draft implementation prompt (nathann, 2026-11-21). To be appended to the prompts for part 3
> (lesson theory), part 5 (real practice-phishing email) and the persona assets, then handed to a
> coding agent. Contracts marked **C1–C5** are shared with the other parts: change them only with a
> `docs/decisions.md` entry.

## Read first

AGENTS.md, docs/idea.md, docs/tech-stack.md, docs/onboarding-and-field-mode.md,
docs/scenario-engine.md, docs/localization.md, docs/characters.md, content/scenarios/_example.json,
static/lesson-01-phishing.html (Nicholas's lesson 1 template, the look and structure to match).
Follow every rule in AGENTS.md: own branch, no real brands, `.example` domains in content, secrets
only in `.env`.

## Goal

A learner opens the web app ("Tea"), creates an account with a **verified email**, answers a few
profile questions, takes a ~3-minute **adaptive placement test**, then enters lesson 1 (email
phishing). Inside the lesson they practise on emails, Duolingo-style, with **four-step prompts that
fade per step** as they master them. Everything we learn about the learner is stored, so later
chapters (real practice-phishing email, passwords, SMS, permissions) can be personalised and can use
the learner's own behaviour as teaching material.

Other parts, built by other agents. Do not implement them, but respect the contracts:
- **Part 3:** lesson 1 theory and structure (Content → Tools → Attacks → Status → Answers, see
  `static/lesson-01-phishing.html`).
- **Part 5:** sends the learner a real practice-phishing email (lookalike of our own domain) before
  lesson 1, and reports what happened through the events table (C1).
- **Assets:** persona art and story (docs/characters.md): Cat (she, personal security), Turtle (she,
  organisational security), Dog (he, free tools), Spider (he, the attacker).

## Stack and hosting (required)

### App
- Kotlin Multiplatform + Compose Multiplatform. Targets: `wasmJs` (demo), `jvm` desktop (dev
  loop), `android` (parts 1, 2 and 4 are all "drills", so they run on phones too; never a simulated
  phone frame).
- **The web build is static files.** A GitHub Actions workflow builds it on each push to `main`
  (`wasmJsBrowserDistribution`) and publishes it to **GitHub Pages**.
- **Content is bundled into the build** (items, lessons, strings), so placement and practice work
  with **no backend**: all engine logic is in `commonMain`, progress is saved locally (localStorage
  on web, files/DataStore elsewhere). Features that need the server (registration, email
  verification, leaderboard, sync) show a calm "offline: your progress stays on this device" state
  and sync when the server is reachable.
- **Backend URL is set at runtime, never hardcoded.** The default is injected by the Pages build (e.g.
  workflow env var → generated `config.json` read at startup). `?api=<url>` overrides it and is saved in
  localStorage, so each dev can point the live site at their own `http://localhost:8080`
  (works in Chrome/Firefox). Show the active backend in a small debug footer.

### Server
- **Ktor** module `server/` in the same Gradle build, reusing the `commonMain` models
  (kotlinx.serialization DTOs). JSON REST API. `GET /health`.
- **Docker Compose stack: app + db** (Postgres container with a named volume; Exposed as the DB
  layer so tests can run on H2/SQLite). Secrets (SMTP credentials, the OpenRouter key) come from `.env`
  and are **never baked into the image or the web build**. Provide `.env.example`.
- Self-hosted on a team laptop, exposed through a tunnel: `cloudflared tunnel --url
  http://localhost:8080` (outbound connection only, public HTTPS URL) or ngrok for a fixed domain. Note
  in the README that quick cloudflared URLs change on restart, so use `?api=` or a fixed domain.
- **CORS:** allow our `https://<org>.github.io` origin and `http://localhost:*`; nothing else.
- The same image must run on any container host later (Fly.io, Railway, Cloud Run); switching is
  one URL change.
- Email sending: SMTP from `.env` (free-tier transactional provider). If SMTP is not configured, a
  `ConsoleMailer` prints the mail to the server log (dev only).

### Layout
```
shared/commonMain/  model/   DTOs, Item, Profile, Mastery, Events
                    engine/  placement, bkt (mastery), scaffold, selector, shortcutBreaker
                    content/ bundled item + lesson loader, validation
                    ui/      ItemRenderer, EmailView, PersonaBubble, onboarding, placement,
                             practice, leaderboard, "what we know about you"
                    net/     ApiClient (runtime base URL), offline queue
composeApp/         AppShell per platform (wasmJs, jvm, android)
server/             Ktor: auth, profile, signals, attempts, events, leaderboard, mailer, password analysis
deploy/             docker-compose.yml, Dockerfile (fat jar), .env.example
.github/workflows/  pages.yml (wasmJsBrowserDistribution → GitHub Pages)
content/items/email/<id>.<lang>.json   content/items/breadth/<id>.<lang>.json
```

### Languages
English, French and Spanish. UI strings in Compose resources (`composeResources/values/strings.xml`,
`values-fr/`, `values-es/`); item and lesson text in one file per language (`<id>.<lang>.json`),
falling back to `en`. **Adding a language = adding files, no code change.** Write
`docs/adding-a-language.md` (5 lines). Language switch without restart; large-text mode.

## Shared contracts

### C1. Database (read and written by part 5)
```
users(id, email, email_verified_at, first_name, last_name?, display_name, language, country,
      created_at, password_hash, terms_accepted_at, terms_version,
      practice_emails_allowed (bool, default true via terms), practice_emails_paused_at?)
profiles(user_id, json)                    -- onboarding answers + derived data
password_findings(user_id, attempt_no, length, classes, score, pwned (bool), pwned_count,
      contains_name, contains_email, contains_year, keyboard_pattern, similar_to_attempt_1,
      accepted (bool), analysed_at)        -- never plaintext, never an unsalted hash
client_signals(user_id, platform, json, created_at)
mastery(user_id, skill, p, level, attempts, tested_out, next_review_at, updated_at)
      -- skill ∈ who | emotion | ask | verify | verdict | breadth_news | breadth_image
attempts(id, user_id, item_id, context (placement|practice|review), answers_json, correct,
      confidence, scaffold_json, hints_used, ms, created_at)
shared_contacts(id, user_id, name?, email?, phone?, source (picker|manual), created_at)
groups(id, name, created_at)   group_members(group_id, user_id)   -- schema only in MVP 0
events(id, user_id, type, payload_json, created_at)  -- append-only bus between parts
```
Events we emit: `registered`, `email_verified`, `terms_accepted` (payload: opened, scroll depth,
ms), `onboarding_completed`, `placement_completed`, `lesson_started`, `practice_item_answered`,
`lesson_completed`, `contacts_shared`. Part 5 emits `field_sent`, `field_opened`, `field_clicked`,
`field_submitted`, `field_reported`. Lesson 1's "Attacks" section (part 3) reads them to decide
whether Spider says "I caught you" or "you dodged me" (`fellForTrap` in Nicholas's template).
API: `GET /me/events?type=field_*`, `POST /events` (server-side key for part 5).

### C2. Email item format (`content/items/email/<id>.<lang>.json`)
Same slot vocabulary as `content/scenarios/_example.json`, so the four-step answer key comes from
the slots, not from free text.
```json
{
  "id": "email-bank-login-01", "language": "en", "status": "draft",
  "kind": "email", "category": "scam", "difficulty": 2, "twin_of": null,
  "held_out": false, "unsolicited": false, "expected_ms": 25000,
  "slots": { "identity": "bank", "channel": "email", "emotion": ["fear", "urgency"],
             "ask": "click_login", "surface_clues": ["lookalike_domain", "generic_greeting"],
             "verify": "official_app_or_card_number" },
  "email": {
    "from_name": "SwissAlpBank", "from_address": "security@swissalp-bank-alerts.example",
    "reply_to": null, "to": "{{email}}", "subject": "Funds moved out of your account",
    "received": "-PT2H",
    "body": [
      { "t": "text", "v": "Dear customer," },
      { "t": "text", "v": "CHF 2,400 was moved out of your account 12 minutes ago." },
      { "t": "link", "v": "Change my password now", "href": "https://swissalp-secure-login.example/reset", "hotspot": "h3" }
    ],
    "attachments": []
  },
  "hotspots": [
    { "id": "h1", "target": "from_address", "step": "who",     "red_flag": true, "explain": "..." },
    { "id": "h2", "target": "body:1",       "step": "emotion", "red_flag": true, "explain": "..." },
    { "id": "h3", "target": "link",         "step": "ask",     "red_flag": true, "explain": "..." }
  ],
  "steps": {
    "who":     { "prompt": "...", "answer": "...", "choices": ["...", "...", "..."], "correct": 0 },
    "emotion": { "...": "..." }, "ask": { "...": "..." }, "verify": { "...": "..." }
  },
  "actions": [
    { "id": "open_link", "label": "...", "outcome": "unsafe", "consequence": "..." },
    { "id": "open_app_yourself", "label": "...", "outcome": "safe", "consequence": "..." }
  ],
  "attacker_reveal": "Spider: what I was after and how I tried to get it."
}
```
Placeholders `{{first_name}}`, `{{email}}`, `{{email_domain}}` are filled from the profile.
Breadth items (`content/items/breadth/`): `kind: "news"` (headline, source, date: real or fake?) and
`kind: "image"` (bundled image: real photo or AI-generated?, with the tells to point out).

### C3. Lesson hook (part 3)
Part 3 delivers `content/lessons/email-01/lesson.<lang>.json`: an ordered list of sections matching
Nicholas's template: `content` (Cat, Turtle), `tools` (Dog), `attacks` (Spider reveal, reads C1
events), `status` (self-rated confidence), `practice`. Part 3 renders all but `practice`.
A `practice` section is `{ "type": "practice", "count": 8, "focus": ["who", "ask"], "end_with_held_out": true }`
and is rendered by **our** practice engine (part 4). It replaces/extends the template's 3-question
"Answers" quiz. The `status` self-rating is stored as an event and feeds the overconfidence flag.
Until part 3 lands, ship a stub lesson with placeholder sections.

### C4. Persona hook (assets)
`@Composable PersonaBubble(persona: Persona, mood: Mood, text: String)`,
Persona = CAT | DOG | SPIDER | TURTLE. Placeholders until art lands: the emoji map from Nicholas's
template (🐱 🐢 🐶 🕷️). Art goes in `composeResources/drawable/persona_<name>_<mood>.png`.
In our parts: **Cat** gives step prompts and hints, **Spider** speaks the `attacker_reveal` after
each item, **Dog** shows the "what to do now" card / a free tool, **Turtle** is unused in MVP 0.

### C5. Backend URL
`ApiClient` reads the base URL from: `?api=` (saved to localStorage) → `config.json` from the Pages build
→ `http://localhost:8080`. Parts 3 and 5 use the same client.

## Part 1: Registration and profile

**Principle:** collect as much teaching-useful data as possible, but ask for **as little as possible
up front**. More permissions and data are requested later, chapter by chapter, when the lesson about
that attack surface needs them (that request then becomes part of the lesson). One question per
screen, big buttons, everything skippable except email, password and terms. Fictional brands only.

Screens:
1. **Language** (en/fr/es, pre-filled from the locale) and country (pre-filled from timezone/locale).
2. **Email** (required) → server sends a 6-digit code → learner types it. Code: 10-minute expiry,
   5 tries, rate-limited sends, stored hashed. A working, verified email is mandatory (part 5
   depends on it). Derive and store `email_provider` (gmail / outlook / proton / icloud / other)
   from the domain. Verification email text: "We will never ask you for this code or your password
   in an email or a message."
3. **Name:** first name (personalisation, part 5), last name optional, display name for the
   leaderboard (default: first name + last initial).
4. **Password** (minimum 10 characters, no composition rules, show/hide toggle):
   - The **first attempt is the most valuable data point** (likely the learner's go-to password).
     Analyse **every attempt, including the first even if it is shorter than 10**: the client sends it
     over HTTPS; the server analyses it in memory and stores only the findings (C1 `password_findings`).
   - Analysis: Have I Been Pwned Pwned Passwords with k-anonymity (`/range/<first 5 SHA-1 hex>`,
     `Add-Padding: true`, only the prefix leaves the server); a zxcvbn-style JVM library (e.g. nbvcxz)
     for score and patterns; contains first name / email local part / a year; keyboard pattern;
     similarity to attempt 1 (e.g. attempt 1 + "123").
   - If too short: "At least 10 characters, please." If pwned: **say nothing about why**, only
     "Please choose a different password." Do not spoil the password chapter.
   - The accepted password is stored only as an **argon2id** hash. Plaintext is never logged,
     stored or returned.
   - Offline (no backend): no account, skip analysis, continue as a local guest.
5. **Onboarding questions** (docs/onboarding-and-field-mode.md): I'm here for (myself / an older
   relative / my NGO / my company), age range (optional), channels I use (email, SMS, messengers, calls,
   social), which email app (webmail, Outlook, phone mail app), what I do online (banking,
   shopping/parcels, job search, investing, donations, social; NGO: invoices, grants, shared accounts,
   beneficiary data), "Ever had a message you weren't sure about, or lost money?" (choices +
   optional text → gentle tone), self-rating 1–5, bigger text?, daily goal 2/5/10 min.
6. **Terms of use** (required checkbox "I accept the terms of use", with a collapsed "Read the
   terms" link, as in real apps). The practice-email clause is **in the terms, lightly buried but
   honest and easy to spot for anyone who expands them**: a short section "Practice messages: we
   sometimes send practice phishing emails to the address you registered. You won't know when.
   You can pause or stop them any time in Settings." Record whether the terms were opened, scroll depth and
   time spent (event `terms_accepted`). Accepting sets `practice_emails_allowed = true`; Settings
   always shows "Practice emails: on / pause / stop". A later chapter reveals "you accepted terms you
   didn't read" as a teaching moment.
7. **Permissions: none at signup.** Web: no notification, location or camera prompt. Android: only
   INTERNET. Later chapters request what they need, when they teach about it.
8. **Passive client signals** (no prompt; listed in "What we know about you"): platform, OS,
   browser, device class, screen size, touch vs mouse, locale, timezone, dark-mode preference,
   reduced motion, font scale, referrer/UTM, time per onboarding screen, whether the password was
   pasted or autofilled when detectable (password-manager signal).

**"What we know about you" screen:** profile, signals and password findings described in plain words
(e.g. "your first password was 8 characters long"; no pwned details yet, which are saved for the
password chapter), plus delete-my-account. It provides transparency (nLPD/GDPR) and is a later teaching hook.

API: `POST /auth/start {email, language}`, `POST /auth/verify {email, code}` → session token,
`POST /auth/password/attempt {password}` → `{accepted, message}`, `POST /auth/login`,
`GET/PUT /me/profile`, `POST /me/signals`, `GET /me/about`, `DELETE /me`, `POST /me/attempts`
(batched, idempotent for offline sync), `GET/PUT /me/mastery`. Session: random opaque token
(Authorization header; stored in localStorage on web, secure storage on Android).

## Part 2: Placement test (adaptive, ~3 minutes, 11 items)

Framed as "let's see where to start", never as an exam; no score out of 10. Runs fully client-side.

**Item modes**, all rendered by one shared `ItemRenderer` that part 4 reuses:
- **Trying to trick you, or fine?** One email in a generic mail-client view (no real-brand look).
- **Tap the warning sign:** tap the worrying parts (sender, reply-to, link, attachment, greeting,
  urgency words); each hotspot maps to a step. Links show their real target on hover / long-press,
  like a real mail client.
- **Where does this link really go?** Pick the real domain among 3 (lookalikes, subdomain tricks
  like `swissalpbank.example.login-check.example`).
- **What do you do next?** Pick an action (step 4, the step people miss most).
- After each answer: **"How sure are you?"** (guessing / fairly sure / certain).
- Every item has an **"I don't know yet"** button (counts as wrong, never shamed).
- Breadth (recorded separately, does not change email levels): one news headline (forward it?)
  and one image (real or AI-generated?).

**Adaptive logic** (`shared/engine/placement`, pure Kotlin, unit-tested), adapted from the `Skim`
placement in hopingo (github.com/nathmo/hopingo, `js/engine/placement.js`): binary search on an
ordered ladder, short cap, strict "known", tested-out levels re-checked later.

Difficulty ladder (item `difficulty`, built from the clue-removal operator in scenario-engine.md):
1. obvious: typos, random sender, "Dear customer", raw suspicious link
2. lookalike domain, polished text
3. perfect language, the learner's first name, a known service's display name, link text ≠ target
4. header and link tricks: reply-to differs, subdomain trick, attachment `invoice.pdf.html`
5. a real message that still uses urgency or is unsolicited, vs. a scam with no surface clue
6. hijacked real sender or ongoing thread (only step 4 helps)

- **Phase A: find the level (binary search, max 5 probes, ~90 s).** `lo=1, hi=7, idx=2`. A probe
  is a **pair** at level `idx` (one scam + one legit twin, random order, shown one at a time), so
  "everything is a scam" fails. The level is **known** only if both verdicts are right, neither was
  "guessing", and neither was slow (> 2× `expected_ms`, never shown to the learner).
  `known → lo = idx+1 else hi = idx; idx = (lo+hi)/2`; stop when `lo >= hi` or after 5 probes.
  Warm-up grace: the first probe ignores time; a miss there gets one retry at the same level.
- **Phase B: per-step probes at level `lo` (4 items, ~60 s):** tap the warning sign (who), where does the
  link go (who/ask), which feeling is pushed (emotion), what do you do next (verify).
- **Phase C: breadth (2 items, ~30 s).**
- Neutral feedback only ("Got it", "We'll look at this together"); no corrections during the test.
  Progress bar estimated, capped at 95%.

**Output** → `mastery`, `profiles.placement`, event `placement_completed`:
- `level` = boundary. Each step starts at the BKT prior implied by the level, then Phase B updates
  it. Levels below the boundary are marked `tested_out` but **due for early review** in practice,
  so a lucky guess is caught later (as in hopingo's `markKnown`).
- Hit rate, false-alarm rate (legit flagged as scam), calibration (confidence vs correctness),
  `overconfident` flag (self-rating ≥ 4, or "certain" on a wrong answer), breadth results, total time.
- Result screen: one strength and one thing to work on, e.g. "You spot urgency well. Let's work
  on checking through your own channel." Then "Start lesson 1".

## Part 4: Applied practice inside lesson 1 (faded four-step support)

Reuses the item format, `ItemRenderer`, mastery store and placement output.

The four steps (UI copy): 1) Who is really writing? 2) What feeling are they pushing? 3) What
exactly do they want me to do? 4) How can I check through a channel I control?

**Support level per step**, from that step's mastery p (with hysteresis, so it doesn't flicker):

| p | Level | What the learner sees for that step |
|---|---|---|
| < 0.35 | Worked | Cat explains the step on this email, the hotspot is highlighted; learner taps "Got it" |
| 0.35–0.6 | Prompted | Step question with 3 choices (`steps.<step>.choices`) |
| 0.6–0.8 | Hint on demand | No question; a small "Need a hint?" button reveals the prompt |
| ≥ 0.8 | Faded | Nothing; the learner gives only the verdict and next action |

Every item ends with the **verdict** (trying to trick me / fine / not sure) and the **next action**.

**Mastery** (pure functions, unit-tested), the same model as hopingo's `learner.js`: per-step
**Bayesian knowledge tracing**: slip 0.1, learn 0.12, guess by question type (verdict 0.5, multiple
choice 1/n, tap-hotspot 0.2). "Worked" answers give no evidence; a used hint or a slow answer counts as
wrong. A step is mastered at p ≥ 0.95 after ≥ 3 unaided answers. Item difficulty rises after 2 fast
right answers and drops after 2 mistakes. Spaced review: interval × 2.5 when ≥ 75% of review answers
are right. **Recurring mistakes:** a surface feature the learner gets wrong twice becomes a "trap"
(e.g. "trusts the display name"), and it is cleared after right answers on 3 different days.

**Item selection** for each `practice` section:
- interleave identities and asks (never the same seed twice in a row); ~40% legit or unsolicited;
- prefer items that train the weakest step; include tested-out levels due for review;
- **shortcut breaker:** count which surface clues appear in items the learner flagged; if one
  clue dominates (e.g. typos), next show a legit email with that clue and a scam without it;
- wrong items come back later in the session and in the next session.

**Feedback after each item:** right or wrong without blame; Spider's `attacker_reveal`; the
consequence of the chosen action; the four steps filled in for this email. If the learner gave
something away, Dog shows the matching "what to do now" card (`content/resources/resources.json`).

**End of the practice section:** one **held-out** email (new identity × ask combination,
`held_out: true`) with all prompts off: the "click" check. Then a summary (per-step progress bars,
XP, streak) and the event `lesson_completed`.

**Gamification:** XP, daily streak, progress bar (no hearts/lives), and a **leaderboard**:
- Demo: **one global leaderboard** (weekly and all-time XP, display names). `GET /leaderboard`.
- A "Compete with friends" button **asks to share contacts**: Contact Picker API where the browser
  supports it, otherwise manual entry of friends' emails; on Android, READ_CONTACTS requested only at
  that moment. Store in `shared_contacts` and emit `contacts_shared` (how easily people hand over
  their contacts is itself material for a later chapter). **Do not use contacts for ranking and never
  contact them in MVP 0.**
- `groups` / `group_members` exist in the schema for the real product (team / NGO / family leaderboards).
- Offline: the leaderboard is hidden; local XP and streak still work.

## Content to create (drafts, for human review)

AGENTS.md: core scenarios are written by humans. Build the schema, the bundled loader with
validation, and **draft** items marked `"status": "draft"` so the loop runs: ~14 placement items
(incl. legit twins and 2 breadth) and ~20 practice items (incl. ~8 legit or unsolicited, 1
held-out), en first, then fr and es. Fictional brands and `.example` domains only.

Seed pair to include (from nathann):
- **Legit but unsolicited** (`category: legit`, `unsolicited: true`): "This email is to let you
  know that someone is registering an account at SwissAlpBank with this address. Here is your
  verification code: 482 913. Do not share it with anyone." Real sender, no link. Right answer: it is
  genuine but you didn't ask for it, so **don't share the code, ignore it**, and if it keeps happening,
  open the bank's app yourself. Teaches: real emails can still be junk, and a code is never shared.
- **Phishing twin** (`category: scam`): the classic urgency login: "Your account has been hacked /
  CHF 2,400 was moved out of your account. Click here to change your password." Lookalike sender,
  link to a lookalike domain.

A validation test fails the build on: missing language files, deny-listed real brand names,
non-`.example` links, hotspots pointing at missing targets, items without `steps` for all four steps.

## Safety and privacy rules
- Verification emails come only from our own domain and never imitate a real service.
- Passwords: plaintext only in memory during the attempt analysis; argon2id hash at rest; findings only.
- Codes hashed, short-lived, rate-limited. No secrets in logs, images or the web build.
- Every stored field has a purpose string shown in "What we know about you"; delete-my-account works.
- Practice emails can be paused or stopped any time in Settings.

## Done when
- `docker compose up` (server + db) behind a tunnel, plus the Pages build (or
  `:composeApp:wasmJsBrowserDevelopmentRun` with `?api=http://localhost:8080`): a new user registers
  with a real email, receives and enters the code, sets a password (first attempt recorded), answers
  onboarding, accepts the terms, finishes placement in ~3 minutes, sees a per-step result, plays lesson 1
  practice where prompts visibly fade as they answer correctly, finishes with the held-out email, and
  appears on the global leaderboard.
- With the backend down, placement and practice still work and sync later.
- The database holds the user, profile, password findings (no plaintext), signals, mastery, attempts and
  the C1 events; part 5 can read users and write `field_*` events.
- Unit tests pass for: placement search + stopping rule, BKT update, support level + hysteresis,
  shortcut breaker, item selection, password analysis (HIBP mocked), item validation.
- The UI works in en, fr and es; adding a language needs files only; large-text mode works.
- The GitHub Actions Pages workflow is green on `main`.
