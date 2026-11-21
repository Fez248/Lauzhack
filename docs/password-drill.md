# Password lesson and the Vault drill (lesson 2)

> Status: **built and agreed** (krishna, 2026-11-21). Owner: krishna. Server, screens and tests are in.
> Krishna has agreed the three `decisions.md` entries of the same day; the third one reverses part of
> nathann's earlier entry, so he confirms that one before the PR merges.
> **Satellite file.** MVP 0 ships lesson `email-01` only ([docs/mvp0-plan.md](mvp0-plan.md) §1.1, §1.4).
> This is the **next lesson after MVP 0**. If this file disagrees with the plan, **the plan wins**.
> It reuses what the plan already specifies: `PasswordAnalyzer` (§10.4), argon2id, the HIBP prefix
> check, contract C1 (§5) and the lesson contract C3 (§4.2). Nothing here changes a contract; the new
> tables and events are **additive**.
> **Stack: Kotlin** (Ktor server + Compose Multiplatform), matching [tech-stack.md](tech-stack.md).

## What this is

Lesson 2 of ThisIsTea teaches **passwords**: why length beats punctuation, why reuse is the thing that
actually hurts you, and how to hand the whole problem to a password manager. Then, instead of a practice
email, the learner does the **Vault drill**: they open accounts on **five fictional sites** — catLand,
dogLand, fishLand, birdLand, bunnyLand — inventing a username and a password for each. We grade each
password as they type it, check it against known breached passwords, refuse the bad ones with the real
reason, and keep the good ones. **In a later session we ask them to log back in to one of the five.**

The drill is the test the lesson can't fake. Anyone can agree that reuse is bad on a quiz screen. Five
sites in a row is where you find out whether they actually have a system — because by site three,
inventing another password from your head stops working, and the only way through is the manager they
just installed.

Three rules make it honest, and they are requirements, not options:

- **Nothing here unlocks anything.** The five sites are fictional, local to the app, and guard nothing.
  No real service is imitated, and no account we create exists anywhere.
- **We never hold a password.** Each accepted password becomes an argon2id hash plus a few facts
  (length, score, whether it was breached). The plaintext lives in memory for one request and is then
  gone, exactly as [mvp0-plan §10.4](mvp0-plan.md) requires for the account password.
- **We say so before the first keystroke.** The drill opens by telling the learner, in plain words, to
  invent new passwords and not type one they use anywhere real — and what we keep.

## Where this fits

```
lesson 2: passwords (Cat's rules → Dog's tools: install a password manager)
   → Vault drill: "Let's open five accounts. Spider will try to guess every one."
      → for each of catLand, dogLand, fishLand, birdLand, bunnyLand:
           learner invents a username + a password
             → live meter as they type (length, classes, pattern, "is this one of your others?")
             → submit → server: strength score + breach check + reuse check
                 → rejected → the real reason, nothing stored, try again
                 → accepted → argon2id hash + findings stored; "saved it in your manager?" nudge
      → debrief: five passwords, side by side, by what they have in common
         → Spider: "here's the one I'd have cracked first, and why"
   → (a later session) login drill: one of the five, picked at random
      → "log back in to dogLand"   ← they cannot know which, so all five have to be saved
      → as they answer: pasted, autofilled or typed? how long was the pause? (told up front)
         → right → the system holds; the account graduates
         → wrong 3× → "open your manager" → we show we can't help: we only kept a hash
            → that lands the lesson harder than the lesson did
```

**My part:** the drill screens, the live meter, the create/login endpoints, the checks, the storage, the
debrief and the login drill. The lesson copy (Cat's rules, Spider's lines, Dog's tool cards) is
human-written with the content owners; the password-manager links go in
[content/resources/resources.json](../content/resources/resources.json) like every other resource.

## Why five accounts, and why in a row

- **One account teaches nothing about reuse.** Reuse is a property of a *set* of passwords. You have to
  make several, in one sitting, before the problem is visible.
- **Five is where the human runs out.** Two or three can be invented and remembered. By the fourth,
  most people start a family: `catland1`, `dogland1`, `fishland1`. Catching that family in the act, with
  their own five passwords on screen, is the whole lesson in one picture.
- **The sites differ in what they hold** (a pet forum vs. a shop with a saved card), so the debrief can
  ask the second question: which of these five would actually hurt if it leaked? That is password
  tiering, taught by sorting their own work rather than by being told.
- **The gap before the login drill is the real test.** Knowing a rule is free; still having the password
  a day later is the habit. Same reasoning as the practice email in
  [awareness-sim-trigger.md](awareness-sim-trigger.md): inside the lesson the context gives the answer
  away.

## The lesson (before the drill)

Four rules, in Cat's voice, as a `content` section under contract C3. Deliberately four, and
deliberately **not** called "the four steps" — those belong to scam defence and must not be diluted.

1. **Long beats clever.** Four or five unrelated words beat `P@ssw0rd!` by a distance no punctuation
   closes. Show the arithmetic once; never show a "time to crack" number (they are guesses dressed as
   facts, and they go stale).
2. **Never the same one twice.** One breached site becomes five breached sites by itself — attackers
   replay leaked pairs everywhere. This is the rule the drill actually tests.
3. **Don't make it about you.** Your name, your year of birth, your pet, your team: all of it is on the
   internet already, and all of it is what guessers try first.
4. **You are not supposed to remember them.** That is the manager's job. You remember **one** strong
   passphrase and turn on two-factor login. Rule 4 is what makes rules 1–3 survivable.

Then a `tools` section (Dog) that walks them through **installing a manager now, in the lesson**, not
later. Candidates for the resource list, all with the honest trade-off said out loud:

| Resource | Why it is on the list | The catch, said plainly |
|---|---|---|
| Bitwarden | Open source, audited, free tier that is actually usable, syncs everywhere | Interface is plainer than the paid ones |
| Proton Pass | Swiss, free tier, same account as Proton Mail | Younger product than the others |
| KeePassXC | Fully local, no cloud, no company to trust | You handle your own backups and sync |
| 1Password | The smoothest for families; good recovery story | Paid only |
| The one already on your phone (Apple Passwords, Google Password Manager, Firefox) | Zero installation, already backed up, far better than reuse | Tied to one ecosystem; awkward if you switch |

Rules for this table, inherited from [AGENTS.md](../AGENTS.md) and §17:

- These are **resources**, not scenario content: real names are fine here (same as the HIBP and 2FA
  Directory entries already in `resources.json`), while scenarios keep fictional brands and `.example`
  domains.
- Every entry ships `verified: false` until a person opens the link and confirms it
  ([TASKS.md](../TASKS.md) #24).
- Five options with their catches, not one recommendation. We are teaching a habit, not selling a
  product, and a learner who feels sold to stops trusting the rest of the course.
- No manager is named as "the secure one". A manager with a public breach history is simply left off the
  list rather than attacked by name in learner-facing copy — this is a teaching app, not a review site.

## The drill

Five sites, fictional, served from the app. `.example` domains, per §17.

| `site_id` | Shown as | Domain | What it holds (for the tiering debrief) |
|---|---|---|---|
| `catland` | catLand | `catland.example` | A pet forum. Nothing but posts. |
| `dogland` | dogLand | `dogland.example` | A shop. A saved card. |
| `fishland` | fishLand | `fishland.example` | Aquarium supplies. Your home address. |
| `birdland` | birdLand | `birdland.example` | A birdwatching club. Your real name and photo. |
| `bunnyland` | bunnyLand | `bunnyland.example` | A newsletter. Your email, nothing else. |

Each site is one screen in the same generic sign-up shape: a username field, a password field with a
show/hide toggle, a live meter, and a "Create account" button. Styling comes from the ported theme
(§8); the sites differ by colour and animal, not by layout — they are obviously the same app wearing
five hats, which is the point.

**Before the first field**, a card the learner must dismiss once:

> "Invent five new passwords for these five pretend sites. Don't type a password you really use
> anywhere — not even once. We keep a scrambled fingerprint of each one so you can log back in later,
> plus a few facts like how long it was. We never keep the password itself, and these five accounts
> unlock nothing."

## The checks

Three checks per password. **None of them needs an AI call** — the whole module is rules, a library and
one cached HTTP request, which is what [AGENTS.md](../AGENTS.md) means by frugal.

1. **Strength**, client-side and instant: length, character classes, nbvcxz score (0–4),
   keyboard-row walks, the site's own name inside the password (`catland` in the catLand password),
   the learner's first name, email local part, and a four-digit year. Reuse the plan's
   `PasswordAnalyzer` rules so the account password and the drill are graded identically.
2. **Breached**, server-side: the HIBP Pwned Passwords range API exactly as
   [§10.4](mvp0-plan.md) specifies it — SHA-1, send the **first five hex characters only**, with
   `Add-Padding: true`, match the suffix locally, 2 s timeout, on failure `pwned = false` and log it.
   Answers are cached in memory for an hour, keyed by the full SHA-1 (which never leaves the process),
   so a demo room full of learners reaching for the same obvious password costs one request rather
   than thirty.
3. **Reuse**, server-side, against this learner's own four other sites **and their ThisIsTea account
   password**. Two fingerprints, both keyed with a pepper from `.env`:
   - `reuse_fp = HMAC-SHA256(PEPPER, password)` → exact reuse.
   - `family_fp = HMAC-SHA256(PEPPER, normalise(password))` where `normalise` lowercases, strips
     trailing digits and punctuation, and folds obvious leetspeak (`4→a`, `3→e`, `0→o`, `1→l`,
     `$→s`) → catches `CatLand1` / `dogland2!` / `f1shland3`, the family people actually build.

Argon2id cannot answer "is this the same as another one", because every hash has its own salt. The
keyed fingerprint is what makes reuse detection possible **without** keeping anything readable: it is
deterministic only inside this deployment, and useless to anyone who takes the table without the pepper.

The **ThisIsTea account password** needs neither: it already has an argon2id hash, so the drill simply
verifies the new password against it. No column is added to `password_findings`, which keeps contract
C1 untouched.

**Accept a password iff** `length >= 10`, `score >= 2`, `!pwned`, the site's own name is absent, and
there is no exact reuse. The site-name check is a refusal rather than a mere finding because it is
specific, teachable and trivially fixed. A **family** match is *not* a rejection — it is accepted with the lesson attached, because "you built a family" is a better
teaching moment delivered after the fact than a wall that makes people type `qwerty` variants until
something passes. **Rejected: answer with the reason and store nothing at all**, matching §1.4 ("storing
anything about rejected password attempts" is out of scope) and §10.4.

Messages say the real reason, in the learner's words, never a bare red bar:

| Reason | What the learner reads |
|---|---|
| too short | "At least 10 characters, please. Length is the part that actually helps." |
| weak score | "A guesser would get this quickly: it's close to a common one. Try four unrelated words." |
| pwned | "This one is in known breach lists (seen `<count>` times). Attackers try these first." |
| contains site / name / year | "It has `catland` in it — the first thing anyone guessing this site tries." |
| exact reuse | "That's the password you used for dogLand. One leak would open both. Try another." |

## Storage

Per accepted account: an argon2id hash, the two fingerprints, and the findings. **Never the plaintext.**

The brief for this drill said the passwords could be stored unhashed "because they will not be used to
access anything". They are hashed anyway, for three reasons, and this is the one place this doc
knowingly departs from the brief:

- **The login drill does not need plaintext.** Argon2id verifies a login perfectly; that is what it is
  for. Hashing costs nothing we need.
- **Learners will reuse a real password** no matter how clearly the card tells them not to — that is the
  very habit the lesson exists to break. A plaintext table of "pretend" passwords is therefore a table
  of some learners' real ones.
- **§17 is non-negotiable and this is a security course.** "Passwords: plaintext only in memory during
  analysis" already governs the account password; a plaintext drill table next to it would be the exact
  thing a judge enjoys finding, and it fails the plan's own honesty test (§17): a learner who discovered
  we kept their five passwords in the clear would feel tricked.

What plaintext would have bought — showing the learner their five passwords later — we deliberately give
up, and turn into the lesson: **we cannot show you, because we didn't keep them. Your password manager
can.** The login drill is then an honest test of whether they saved them, instead of a hint we could
quietly hand out.

### How a common password is still penalised without keeping it

The check does not need the password *later* — it needs it **once**, and it has it: the plaintext is in
memory for the whole creation request. Everything we will ever want to know is measured there, and the
**verdict** is what gets stored:

| Stored in `vault_findings` | Answers later, without the password |
|---|---|
| `score` (nbvcxz 0–4) | "was this weak?" — drives XP and the debrief forever |
| `pwned`, `pwned_count` | "was it in breach lists, how often?" — the count is why the debrief can say "seen 3.7 million times" |
| `length`, `classes`, `keyboard_pattern` | "what kind of password was it?" |
| `contains_name` / `_email` / `_year` / `_site` | "was it built out of guessable facts?" |
| `reuse_fp`, `family_fp` | "was it reused, or one of a family?" |

So a **common password is refused at creation**, while we can still see it, and a merely mediocre one is
recorded as mediocre for good. XP, mastery and the debrief all read the findings; none of them needs the
password. Storing the plaintext would only help if we wanted to **re-examine** the password later, and
there is exactly one honest case for that: a breach list published *after* the learner chose it. The
industry answer covers that too — re-run the check at the next login, when the plaintext is briefly in
memory again, and tell the learner then. Real services do precisely this, and they do not keep the
password either.

### Tables (additive to contract C1, §5)

```
vault_sites                -- static, from content; not a table if content-bundled
vault_accounts(id, user_id, site_id, username, password_hash, created_at,
    graduated_at?, UNIQUE(user_id, site_id))                                      (add)
vault_findings(account_id PK, length, classes, score, pwned, pwned_count,
    contains_name, contains_email, contains_year, contains_site, keyboard_pattern,
    reuse_fp, family_fp, analysed_at)                                             (add)
vault_logins(id, user_id, site_id, succeeded, attempt_no, input_method,
    time_to_first_input_ms, self_report?, at)                                     (add)
```

- `password_hash`: argon2id, same parameters as §10.4 (`m = 19456 KiB, t = 2, p = 1`, 16-byte salt).
- `reuse_fp` / `family_fp`: HMAC-SHA256 keyed by `PASSWORD_FP_PEPPER` from `.env` (never committed).
- `DELETE /me` must delete every row in all three, per §5.
- Every field appears in **"What we know about you"** (§9.2) with its purpose in plain words, e.g.
  "Your catLand password is 14 characters long and was not in any breach list. We kept a scrambled
  fingerprint so you can log in again. Purpose: your password lesson."

### Events (additive to §5.1)

| Type | Emitted by | Payload |
|---|---|---|
| `vault_drill_started` | client | `{lesson_id}` |
| `vault_account_created` | server | `{site_id, score, pwned, family_match}` |
| `vault_attempt_rejected` | server | `{site_id, reason}` — reason only, nothing about the password |
| `vault_drill_completed` | client | `{accounts, rejections, families}` |
| `vault_login_attempted` | server | `{site_id, succeeded, attempt_no, input_method}` |

### Endpoints

| Endpoint | Does |
|---|---|
| `POST /me/vault/accounts` | `{site_id, username, password}` → `{accepted, reason?, findings}`; on accept stores hash + findings |
| `GET /me/vault/accounts` | the learner's sites, usernames and dates — never a secret, never a fingerprint |
| `GET /me/vault/debrief` | the five findings side by side for the debrief screen |
| `GET /me/vault/challenge` | which site to ask about now, or `null` if none is due |
| `POST /me/vault/login` | `{site_id, username, password}` → `{ok, attempt_no, hint?}` |
| `DELETE /me/vault` | demo reset (admin key or the learner's own session) |

## The debrief

The five passwords are gone, so the debrief is built from the findings, which is enough and reads
better: five rows, one per site, with length, score, breached, and what each contained. Then three
questions the screen answers from their own data:

- **"Did you build a family?"** → the `family_fp` matches, drawn as links between the sites. This is the
  moment the lesson is actually taught.
- **"Which one would hurt most?"** → they sort the five sites by what each holds; we compare their
  sorting to the strength they actually chose. The card with a saved card number protected by their
  weakest password is the point.
- **"Where did these come from?"** → the counts for name, year, site name and keyboard walks.

Spider closes: *"this is the order I'd have tried them in"*, weakest first, with the reason. Spider
plays the guesser and takes nothing, per the framing in [decisions.md](decisions.md).

## The login drill (a later session)

- **Which:** **one of the five, picked uniformly at random** among the accounts that have not
  graduated. Random is the point: the learner cannot revise for a specific one, so the only strategy
  that works is having saved all five. A predictable order — creation order, or weakest first — teaches
  them to prepare one and forget the rest.
- **When:** `Review.kt` (§6.8) owns the timing only, **one site per session, never all five**. Real
  spacing is hours or days; the shipped default is `VAULT_CHALLENGE_DELAY_S=60`, short enough for the
  whole loop to fit a live demo, exactly as `FIELD_DELAY_*` does for the practice email.
- **Ask:** "Log back in to dogLand." Username is shown (it is not the secret); password is not.
- **Three attempts.** Each one records `vault_login_attempted`. No attempt reveals anything.
- **On success:** the account graduates (`graduated_at`), XP, and Cat's line: "that is the habit —
  you didn't remember it, you looked it up."
- **After three failures:** "Open your password manager and look it up. If it isn't in there, that is
  the lesson: we can't tell you either — we only kept a scrambled fingerprint." Offer a reset, which
  creates a new password for that site through the same checks, and schedule the challenge again.
- **Never** hand out a hint derived from the findings ("it was 14 characters, started with…"). That
  teaches guessing, which is the opposite lesson.

## Are they using a manager?

The drill's real question is not "do they know the rules" but "did the habit stick", and the habit is
**looking a password up instead of remembering it**. The login drill can see that cheaply, from how the
password arrives — under one condition: **we say we are looking, before we look.**

### What we observe, and what the learner is told

The login drill's card says, in plain words:

> "We notice whether you paste, autofill or type this, and how long you take — that is how we tell
> whether the manager habit is working. We never read your clipboard, and we never record what you type."

With that said up front, three signals are enough:

| Signal | How | What it suggests |
|---|---|---|
| `input_method` | from **the size of each change**: typing arrives one character at a time, a paste or an autofill in one jump of four or more; a single jump within 400 ms of focus is an autofill, later is a paste | paste or autofill → a manager, or at least *something* stored; typed → recall, or reading off another screen |
| `time_to_first_input_ms` | from the field gaining focus to the first character | a manager lookup costs a few seconds; pure recall is immediate, and so is giving up |
| `self_report` | one tap after the attempt: "looked it up" / "remembered it" / "had it written down" | the only one of the three that is not a guess |

One enum, one integer, one optional enum, on the `vault_logins` row. **Not** a keystroke stream.

### What we deliberately do not do

- **No keystroke timing or typing dynamics.** Per-key intervals are behavioural biometrics under the
  GDPR and the Swiss nFADP. Collecting them to tell *who* is typing is a different product from
  teaching, and one enum already answers the teaching question.
- **Never read the clipboard.** We observe *that* a paste happened, never what was in it, and never the
  clipboard at any other moment.
- **Nothing for failed attempts beyond the count.** Same rule as creation (§1.4): the attempt number and
  the outcome, never a fragment of what was typed.
- **No guessing which manager.** Sniffing extensions or autofill fingerprints to name the product is
  surveillance with no teaching value.

### Typing is not a wrong answer

The signal is soft in both directions: a paste can come from a sticky-note text file, and typing can
mean a manager open on the phone next to the laptop. So it **never** changes the verdict, the XP or the
mastery estimate. It changes the follow-up question, which is where the teaching actually is:

- **pasted or autofilled, correct** → "that is the habit — the manager did the remembering."
- **typed, correct** → "did you look that up, or remember it? If it is written down somewhere, let's get
  it into the manager."
- **typed, wrong three times** → the strongest moment in the lesson: they tried to remember, and five
  passwords is exactly where remembering stops working.

This is a heuristic over change sizes, not a clipboard hook, which is why it works the same on web,
desktop and Android and why there is nowhere for a keystroke stream to be kept even by accident:
`InputMethodWatcher` exposes one method name and one duration, and holds no text.

### The measure that needs no detection at all

Over a few sessions, **graduation rate is the answer**: nobody remembers five unrelated 14-character
passwords. A learner who logs back in to four or five of five is using a manager, whatever the input
method said. It is already in `vault_accounts.graduated_at`, costs nothing, and is the outcome the other
three signals are only proxies for. If the team wants no input-method detection at all, this is the
fallback and the design still works.

> **This section needs a [decisions.md](decisions.md) entry before it is built.**
> [§1.4](mvp0-plan.md) lists "paste detection" as out of scope, and the 2026-11-21 decision removed it
> deliberately, because the earlier design "read as a covert phishing and surveillance kit". What is
> proposed here differs in one way that matters: the learner is told we watch the input method, and told
> why, before they type — which is what the honesty test (§17) actually asks. It is still the reversal
> of a recorded decision, so nathann agrees it first.

## Rules (inherited, non-negotiable)

From [mvp0-plan.md §17](mvp0-plan.md) and [AGENTS.md](../AGENTS.md):

- **No plaintext, ever.** In memory for one request, then cleared (`CharArray`, not `String`, where the
  language allows). Never logged, never returned, never in an error message or a stack trace.
- **Nothing for rejected attempts.** No row, no counter keyed to the password, no paste detection.
- **Only the 5-character HIBP prefix leaves the server.** No other outbound request carries learner data.
- **Said up front.** The drill's opening card tells the learner what we keep and tells them not to use a
  real password. The honesty test applies: if finding out what we stored would feel like a trick,
  change the design.
- **Fictional sites only.** `.example` domains, invented animals, no real brand, service or person
  imitated anywhere in the drill. Password-manager names appear only as verified *resources*.
- **These accounts unlock nothing.** No session, no token, no access to anything in the app is granted
  by a vault login; it is a drill and nothing else.
- **No secrets in git.** `PASSWORD_FP_PEPPER` lives in `.env`, with a valueless entry in
  `deploy/.env.example`.
- **Input method, not keystrokes.** The login drill may record `paste | autofill | typed`, a
  time-to-first-input and a self-report, and only after the card has said so. Never per-key timings,
  never clipboard contents, never a guess at which manager they use. It never changes a verdict or XP.
- **Human-written copy.** Cat's rules, Spider's lines and the tool cards are written by a person; AI may
  draft variations for human review (§17).

## Open questions

- **Hashed, not plaintext** (see Storage): this doc overrides the brief. It needs nathann's confirmation
  because §10.4/§17 are marked non-negotiable. Nothing else in the design changes if the team decides
  otherwise — one column and one honest sentence on the opening card do.
- **Is this lesson 2, or after it?** MVP 0 is `email-01` only (§1.4). Proposed: build it as
  `content/lessons/password-01/` immediately after the MVP 0 demo stands up.
- **Does the drill count as placement input?** Password behaviour is a different skill from scam
  spotting; it probably wants its own mastery skill key rather than the four steps. Needs a `skill`
  name agreed with whoever owns `Bkt.kt`.
- **Five sites or three for the demo?** Five teaches reuse properly; three fits a two-minute stage slot.
  Proposed: five in the app, and the demo shows sites one, three and five.
- **Paste / autofill detection** (see "Are they using a manager?") reverses a recorded decision and
  needs its own `decisions.md` entry. Disclosed up front and stored as one enum, it is a different
  thing from the silent version that was cut — but nathann decides, and graduation rate works without it.
- **2FA follow-on:** the natural lesson 3 is turning on two-factor login, with the same drill shape
  (a fictional site, a real authenticator app). Out of scope here; worth a line in the pitch.

## What was built

All of the Must rows below are in, with 263 tests passing across `:server` and `:shared`:

- **Server** — `server/src/main/kotlin/tea/server/vault/`: `VaultService` (the three checks, argon2id,
  fingerprints, the random challenge, the three attempts), `VaultRoutes`, `VaultSites`,
  `VaultFingerprint`, `CachingPwnedClient`; tables and events additive to C1/§5.1; every field in
  "What we know about you" and wiped by `DELETE /me`.
- **Client** — `shared/src/commonMain/kotlin/tea/ui/screens/vault/`: the five sign-up screens, the
  live meter, the debrief, the login drill, and `InputMethodWatcher`; strings in en, fr and es.
- **Tests** — `server/.../VaultTest.kt` (17) and `shared/.../PasswordMeterTest.kt` (13), including a
  check that no stored value, response body or About line can contain a password.

| Priority | Piece | Owner |
|---|---|---|
| Must | Lesson 2 renderer reuse: `content` + `tools` sections for passwords (contract C3) | krishna |
| Must | Five fictional sign-up screens, one shape, five skins | krishna |
| Must | Live client-side meter (length, classes, patterns, site/name/year, own-set reuse) | krishna |
| Must | `POST /me/vault/accounts`: strength + HIBP + reuse, argon2id, findings, reasons | krishna |
| Must | Reuse and family fingerprints (HMAC + pepper) and the reuse check across the learner's set | krishna |
| Must | Debrief screen built from findings (family links, tiering sort, where they came from) | krishna |
| Must | Login drill: **random** non-graduated site, three attempts, graduation, honest failure | krishna |
| Should | Input-method signal (`paste`/`autofill`/`typed`) + time-to-first-input + self-report, disclosed on the card | krishna |
| Should | Graduation rate across the five accounts (the detection-free measure) | krishna |
| Must | "What we know about you" entries for every vault field; `DELETE /me` wipes all three tables | krishna |
| Must | HIBP prefix cache (1 h, in memory) | krishna |
| Should | Spider's "order I'd have tried them in" ranking from findings | krishna |
| Not mine | Cat's four rules, Spider's lines, Dog's tool cards (human-written copy) | content owners |
| Not mine | Verifying the password-manager links (`verified: true`) | resources owner |
| Not mine | The mastery skill key for password behaviour | engine owner |
