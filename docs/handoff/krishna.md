# Handoff: krishna

Branch: `Krishna` · Last updated: 2026-11-22 (third session)

## Done

- Merged `origin/main` (MVP 0 plan, Nicholas's lesson 1 prototype, nathann's honesty revision) into my
  branch and pushed. My `awareness-sim-*` docs are now the later-loop satellites nathann made of them.
- Designed **lesson 2: passwords + the Vault drill** — [docs/password-drill.md](../password-drill.md)
  (design) and [docs/password-drill-prompt.md](../password-drill-prompt.md) (build prompt), same
  template as the `awareness-sim-*` pair.
  - Lesson: four rules (long beats clever · never twice · not about you · you're not meant to remember
    them), then Dog walks the learner through installing a password manager.
  - Drill: five fictional sites (Cat Land, Dog's Den, Spider Web Design, Zoo Card Club, Sea Turtle
    Fan Club, all `.example`),
    one username + password each. Per password: client-side strength meter, HIBP prefix check,
    reuse + "family" check across the learner's own set. Rejections give the real reason and store
    nothing. Debrief is built from findings only. A later session asks for one login back.
  - Reuses what the plan already has: `PasswordAnalyzer` (§10.4), argon2id, `Review.kt` (§6.8),
    contract C3 lesson sections. New tables and events are additive to C1 / §5.1.
  - **Zero AI calls** in the module — rules, nbvcxz, one cached HTTP request.
  - Login drill picks **one of the five at random** (not in creation or weakness order), so the learner
    cannot prepare for a specific site; `Review.kt` decides only when it is due.
  - Added a "manager habit" signal on the login attempt: `input_method` (paste / autofill / typed),
    time-to-first-input and an optional self-report — disclosed on the card before they type, never
    affecting XP or the verdict. Graduation rate across the five is the detection-free fallback.
- Claimed TASKS #31 and #32; added #33 for the password-manager resource entries.
- **Built the drill** (TASKS #31/#32 now `review`). 263 tests pass across `:server` and `:shared`.
  - Server, `server/src/main/kotlin/tea/server/vault/`: `VaultService` (length → site name → HIBP →
    strength → reuse, then argon2id + findings), `VaultRoutes` (7 endpoints under `/me/vault`),
    `VaultSites`, `VaultFingerprint` (HMAC exact + family), `CachingPwnedClient` (1 h).
  - Tables `vault_accounts` / `vault_findings` / `vault_logins` and the `vault_*` events are additive
    to C1 and §5.1. `PasswordAnalyzer.estimate()` is now shared, so the drill and the account password
    are graded identically. Every vault field is in "What we know about you"; `DELETE /me` wipes them.
  - Client, `shared/src/commonMain/kotlin/tea/ui/screens/vault/`: five sign-up screens, the live
    meter, the debrief, the login drill, `InputMethodWatcher`. Strings in en, fr and es.
  - Config: `PASSWORD_FP_PEPPER` and `VAULT_CHALLENGE_DELAY_S` (default 60 s so the loop fits a demo),
    both in `deploy/.env.example` with no values.
  - Tests: `VaultTest.kt` (17) and `PasswordMeterTest.kt` (13). One asserts that no stored value, no
    response body and no About line can contain a password.
  - Two small changes outside my area, both forced and both intended: `MeTest`'s list of About fields
    gained my three rows (that assertion exists to catch exactly this), and `POST /me/events` now
    refuses the three server-only `vault_*` types the way it already refused `field_*`.

- Fixed three problems found by running the drill against a live server rather than only in tests:
  - **nbvcxz passed `zzzzzxzzzzzz`** (basic score 2 on length alone) while the client meter called it
    weak, so the server accepted what the UI had just warned about. Added `MIN_DISTINCT = 5`.
  - **An unreachable HIBP was silent.** `pwned = false` read as "clean" when the check had not run.
    Findings now carry `breach_checked`; the drill says so on the spot, and "What we know about you"
    says how many were not checked. §10.4's account-password path has the same ambiguity — nathann's
    table, so I left it and noted it in the design doc.
  - **The dev database vanished on restart**, taking the five accounts with it. The capability was
    there, just undiscoverable: the warning now names the file-backed URL, and README has a 1b line.
    Verified by restarting on the same file — accounts, session and argon2id logins all survived.
- Wired the drill into the lesson flow: `content/lessons/password-01/lesson.{en,fr,es}.json` (Cat's
  four rules across two cards, Dog's five managers, the status question), lesson 2's last button opens
  the drill, and Home gained a lesson 2 card plus a "one of your five is waiting" card. Added the four
  manager entries to `resources.json` (TASKS #33), all `verified: false` pending #24.

- Merged `origin/main` again (PR #4: the tunnel dropped, Docker Compose split into dev and prod).
  Two conflicts, both additive: `decisions.md` kept both sides, and `deploy/.env.example` kept my
  Vault block alongside nathann's `DEV_VERIFY_CODE=off`, with `PASSWORD_FP_PEPPER=changeme` to match
  his new convention that every secret carries a placeholder.
- **Ran the dev compose stack against real Postgres** once Docker was installed, and the drill holds
  up on it: the schema is created correctly (`vault_logins.at` quoted fine, the
  `(user_id, site_id)` unique constraint, `breach_checked boolean default true`), the `timestamptz`
  comparison behind the challenge is right in both directions (not due immediately, due after the
  gap), the random pick varies, argon2id logins work, and `select` over `vault_accounts` and
  `vault_findings` finds **zero rows** containing any plaintext password.
  - One gap it exposed: the dev compose had no `VAULT_CHALLENGE_DELAY_S`, so the login drill sat 60 s
    away and could not be demoed from that stack. Set to 10 s in `docker-compose.dev.yml`, next to
    `RATE_LIMITS: "off"`, for the same reason.
  - Not done: running the whole server suite against real Postgres. The compose db does not publish
    5432, and more importantly the tests would need a schema each (`{id}` names a database, which
    Postgres will not create on connect). Worth doing for CI; say if you want it.
- Earlier, before Docker was available, the vault schema had only ever been created on H2. Closed that
  gap too:
  `-Dtea.test.db.url=` now points the whole server suite at another database, with `{id}` in the URL
  for per-test isolation. All 57 server tests pass on H2's PostgreSQL dialect as well as its default
  one, so the new DDL survives it — `vault_logins.at` as a column name, `breach_checked BOOL DEFAULT
  true`, and the `(user_id, site_id)` unique index. Someone with Docker should still run the compose
  stack against real Postgres before the demo; the command is in the README.

- **Clicked the drill through in a browser** against the compose stack (web profile, Caddy at
  :8081 proxying /api to the server, real Postgres behind it). Registration, the disclosure card,
  Cat Land's sign-up, the live meter (red with the site-name reason, green with the positive line),
  account creation and the manager nudge, the family card on the second site, and the debrief all render and
  behave correctly. The debrief reads "We cannot show you the passwords, because we did not keep
  them" over two rows of findings and the family link, which is the lesson landing as intended.

- Merged `origin/main` again (PRs #5-#7: the boardroom challenge, the screenshot workflow, and the
  Home rewrite around a chapter catalog) and **adapted the drill to main's model instead of keeping
  my own**:
  - Home no longer gets hand-rolled cards from me. Main drives it from
    `content/chapters/chapters.<lang>.json`, and that catalog already had a `passwords` chapter
    sitting at `status: soon, target: null` — so I pointed it at `password-01`, set it available,
    and matched its title, blurb and minutes to the lesson. I took main's `HomeScreen` wholesale.
  - Its blurb promised "a second factor", which this lesson does not teach. Reworded rather than
    left over-promising; 2FA is the natural lesson 3.
  - nathann's lesson-end change (no opt-in card, the test fires automatically) needed nothing from
    me: lesson 2's test is the drill, reached by its last button, and my `goToSection` handover
    survived the merge untouched.
  - Added the lesson's closing card: five accounts now, save each one as you go, and **one of them
    comes back at random later**. Main's model lets a test arrive unannounced; saying the second
    half is coming is what keeps that honest here.
- Fixed two things that were **already broken on `origin/main`**, both confirmed by running them on a
  clean worktree of main before touching anything:
  - `PanesRenderTest` does not compile: main added `Screen.Challenge` without adding it to the
    exhaustive `when` that the file's own comment says guards it. My three vault screens needed
    adding too, so I did all four and put lesson 2 and the drill in the screenshot doc.
  - `EndToEndTest.definitionOfDone` fails: main changed `nextAfterSignIn` so registration ends on
    Home with placement as an optional card, and the comment says so, but the test still expected
    `PlacementIntro`. Updated the assertion to match the intent, not the other way round.

- **Renamed the five sites and gave each its own look** (krishna's call after review): Cat Land,
  Dog's Den, Spider Web Design, the Zoo Card Club and the Sea Turtle Fan Club. Four are pages of the
  cast, so each sign-up and each login wears that persona's colours and avatar via `PersonaAvatar`,
  which already degrades to a letter badge when the art is missing; the Zoo Card Club has no owner
  and carries its own purple, defined beside the drill rather than in nicholas's theme.
- **The last step changed shape.** There is no separate "one of your five is waiting" card and no
  retyping exercise: once the five accounts exist, **opening the password chapter is the test** and
  asks for a login to one of them at random. A local flag set on the fifth account lets `chapterTarget`
  decide without a round trip; resetting a site clears it. When nothing is due, that screen now offers
  the debrief instead of dead-ending, because it is a place learners now reach on purpose.
- Fixed a wart the rename exposed: the site-name check blocked short words anywhere, so `scattered`
  was refused for Cat Land. `VaultWords` is now shared by the meter and the server, and short words
  match whole words only.
- Cleared the drill rows from the dev database (`build/tea-demo`), which the rename would have
  orphaned anyway. Accounts and users were left alone.

- **Second pass over the prompt** ([docs/password-drill-prompt.md](../password-drill-prompt.md)),
  closing the five places where the built module was behind its own spec. 300 tests pass, strict
  mode included.
  - **The tiering question was a list, not a sort.** The copy already said "put the five in order,
    then compare", and the screen only showed the five with what each holds. It is now a real sort
    (up/down per card, then "compare with the passwords I chose"), and the comparison names the site
    whose place and password disagree most — the saved card number behind the weakest password, which
    is the point of the question. Strength comes from the server's own weakest-first `guess_order`,
    so Spider's ranking and the comparison cannot drift apart. Spider's order now gives the reason
    per site instead of five bare names.
  - **`vault_drill_started` and `vault_drill_completed` were declared and never emitted.** The client
    sends the first when the sign-ups open and the second on the debrief once all five exist. The
    rejection count in that payload is counted on the device, as a number: the server still keeps
    nothing whatever for a refused attempt.
  - **Nothing awarded XP.** Graduating a site is worth `Xp.VAULT_GRADUATED` (20), once per site, on
    the graduation itself. The input method still moves no XP and no verdict — only the follow-up
    line, which is the rule the decision entry rests on.
  - **The self-report never left the device.** It is tapped after the verdict, so it could not travel
    with the login request it was wired to; `POST /me/vault/self-report` now puts it on that
    attempt's row, and "What we know about you" says the answer back (§9.2 wants every stored field,
    and this one was finally being stored).
  - **"One site per session" was a free re-roll.** Every `GET /me/vault/challenge` dealt a new random
    site, so leaving the screen and coming back skipped the one the learner could not remember. A
    challenge with a failed attempt against it is now resumed; the pick stays random otherwise.
  - Prompt and design doc updated for all of it, including the new endpoint and the sort.

- **A one-minute demo of the password module**, as a second button under the chapter's Start on
  Home (`1-minute demo`). Same engine, less of it: the short lesson
  `content/lessons/password-01-demo` (two rules, the status question, the drill card, `minutes: 1`)
  and the drill cut to **two sites** — Cat Land and the Zoo Card Club, the pair that keeps the
  debrief's tiering contrast. The mode is one local flag set by the button and cleared by the
  chapter's own Start, so the real chapter is always five; a finished demo run opens straight at the
  login test, so it can be shown twice in a row. Also fixed on the way: entering the drill on a site
  that already has an account used to show a form that could only be refused, and now skips to the
  first site without one. 306 tests pass, strict mode included.

## In progress

- Nothing. The code is on my branch and all three `decisions.md` entries are AGREED, including the
  input-method signal, which replaces the "no paste detection" part of nathann's earlier entry.

## Blocked / needs

- Nothing blocking. Lesson 2 + the drill, hashed-not-plaintext storage and the disclosed
  input-method signal are all agreed and built. If anyone wants the signal gone later, delete the
  three fields from `VaultLoginRequest` and graduation rate still measures the same habit.
- Lesson 2's copy (Cat's four rules, Spider's lines, Dog's tool cards) is still placeholder-free only
  in structure: the words need a human writer, per §17.
- TASKS #33 is written but every link is `verified: false` until a person opens each one (#24).

## Next

1. Open the PR for the drill; point nathann at the input-method entry in the description, since it
   changes a rule he wrote.
2. Write `content/lessons/password-01/lesson.en.json` with the four rules and the tool cards, and add
   the manager entries to `resources.json` (#33) with `verified: false`.
3. Hook the drill into the lesson flow: lesson 2 should end on the drill the way lesson 1 ends on the
   opt-in card, and Home should surface a due challenge.
4. Review nathann's rewrite of my `awareness-sim-*` docs line by line and confirm or push back
   (he marked them "krishna to confirm").

## Questions for others

<!-- Start each line with "To <name>:" -->

- To nathann: the brief I was given said the five drill passwords could be stored **unhashed**, since
  they unlock nothing. I designed them **hashed** anyway (argon2id, same params as §10.4) plus two
  HMAC fingerprints for reuse detection, because (a) the login drill verifies fine against a hash, so
  plaintext buys nothing, (b) learners will type a real password however loudly we tell them not to, so
  a plaintext table of "pretend" passwords holds some real ones, and (c) §17 says plaintext lives in
  memory only, and a plaintext table in a security course fails your own honesty test. Confirm, or tell
  me to flip it — it is one column and one sentence on the opening card either way.
- To nathann: **heads-up, the input-method signal is agreed and in** (`decisions.md`, 2026-11-21), and
  it replaces the "no paste detection" part of your entry; the rest of that entry stands. The login
  drill notices whether the password was **pasted / autofilled / typed**, to see whether the manager
  habit stuck. §1.4 lists
  "paste detection" as out of scope and your 2026-11-21 entry removed it on purpose ("covert phishing
  and surveillance kit"). What is built is the disclosed version: the card tells the learner we watch
  the input method and why, *before* they type; we store one enum, a time-to-first-input and an
  optional self-report; no per-key timings (those are biometrics under the GDPR/nFADP), no clipboard
  reads, no guessing which manager, and it changes no XP and no verdict, only the follow-up line. I
  read that as passing your honesty test. Say if you disagree and I will take it out: deleting the
  three fields from `VaultLoginRequest` leaves the drill unaffected, because **graduation rate across
  the five accounts** measures the same habit with no detection at all.
- To nathann: should password behaviour get its own mastery `skill` key in `Bkt.kt`, separate from the
  four scam-defence steps? It is a different skill and I don't want to pollute the step mastery.
- To nathann: `AGENTS.md` and the AGREED entry in `decisions.md` say branches are the first name in
  **lowercase**, but mine is `Krishna` with a capital K and already pushed with that name. Leave it, or
  do you want it renamed before the PRs start?
- To nathann: `VAULT_CHALLENGE_DELAY_S` defaults to 60 s so the login drill fits a live demo, matching
  what `FIELD_DELAY_*` does for the practice email. Real spacing is hours. Say if you want a different
  demo number.
- To nathann or nicholas (whoever owns `TeaButton`): **a primary button renders near-black with grey
  text while a text field on the same screen has focus**, even though it is enabled and works when
  clicked. Seen on the email step and on every drill sign-up screen: the button is green when the
  field is empty and goes dark as soon as you type, which reads as "disabled" exactly when it has
  become usable. I nearly filed it as a broken drill before noticing Enter submitted fine. It is in
  the shared component, not my screens, so I have not touched it — but it is on the registration
  path, so every judge will meet it in the first ten seconds.
- To nathann: the terms summary ("What we store, in short") does not mention the drill's practice
  accounts, and §9.2 says the summary covers what we store. I added three stored things. Want me to
  add a line to it and to `terms_body`, or will you fold it into the next terms version?
- To nicholas: lesson 2 wants the same shape as your lesson 1 template (rule cards, then tool cards).
  Anything in `static/tea-theme.css` I should know about before I reuse your sign-up/form styling for
  the five fake sites?
- To whoever owns content: Cat's four password rules and Spider's "order I'd have tried them in" lines
  need a human writer. I've drafted the structure, not the voice.

## Answers

<!-- Answers to questions others asked you; quote their question briefly. -->

- nathann asked me to confirm his rewrite of `docs/awareness-sim-*.md` ("krishna to confirm"). Not yet
  reviewed line by line — on my Next list. First read: the honest opt-in and dropping the login form are
  right. Losing the credential form does cost the strongest training signal I had
  (`form_submitted`), so the reveal page should carry a "what gave it away?" question to replace it.
