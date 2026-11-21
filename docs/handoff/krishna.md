# Handoff: krishna

Branch: `Krishna` · Last updated: 2026-11-21 (second session)

## Done

- Merged `origin/main` (MVP 0 plan, Nicholas's lesson 1 prototype, nathann's honesty revision) into my
  branch and pushed. My `awareness-sim-*` docs are now the later-loop satellites nathann made of them.
- Designed **lesson 2: passwords + the Vault drill** — [docs/password-drill.md](../password-drill.md)
  (design) and [docs/password-drill-prompt.md](../password-drill-prompt.md) (build prompt), same
  template as the `awareness-sim-*` pair.
  - Lesson: four rules (long beats clever · never twice · not about you · you're not meant to remember
    them), then Dog walks the learner through installing a password manager.
  - Drill: five fictional sites (catLand, dogLand, fishLand, birdLand, bunnyLand, all `.example`),
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

## In progress

- Nothing. The code is on my branch. I have agreed all three `decisions.md` entries; the third
  (the input-method signal) reverses part of nathann's earlier entry, so it is logged as agreed by me
  and awaiting his confirmation.

## Blocked / needs

- Nothing blocking. Lesson 2 + the drill and hashed-not-plaintext storage are agreed and done. The
  disclosed input-method signal is agreed by me and built; nathann confirms it before the PR merges,
  since it reverses part of his earlier entry. If he declines, delete `InputMethodWatcher`'s three
  fields from the login request and graduation rate still measures the same habit.
- Lesson 2's copy (Cat's four rules, Spider's lines, Dog's tool cards) is still placeholder-free only
  in structure: the words need a human writer, per §17.
- TASKS #33: the password-manager entries for `resources.json` are not written yet.

## Next

1. Open the PR for the drill; flag the input-method entry for nathann in the description.
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
- To nathann: **please confirm or decline the input-method signal.** The login drill notices whether the
  password was **pasted / autofilled / typed**, to see whether the manager habit stuck. §1.4 lists
  "paste detection" as out of scope and your 2026-11-21 entry removed it on purpose ("covert phishing
  and surveillance kit"). What is built is the disclosed version: the card tells the learner we watch
  the input method and why, *before* they type; we store one enum, a time-to-first-input and an
  optional self-report; no per-key timings (those are biometrics under the GDPR/nFADP), no clipboard
  reads, no guessing which manager, and it changes no XP and no verdict, only the follow-up line. I
  read that as passing your honesty test, and I have logged it **AGREED by krishna, nathann still to
  confirm** in `decisions.md`, because it still reverses something you recorded. If you would rather
  not have it: delete the three fields from `VaultLoginRequest` and the drill is unaffected, because
  **graduation rate across the five accounts** measures the same habit with no detection at all.
- To nathann: should password behaviour get its own mastery `skill` key in `Bkt.kt`, separate from the
  four scam-defence steps? It is a different skill and I don't want to pollute the step mastery.
- To nathann: `AGENTS.md` and the AGREED entry in `decisions.md` say branches are the first name in
  **lowercase**, but mine is `Krishna` with a capital K and already pushed with that name. Leave it, or
  do you want it renamed before the PRs start?
- To nathann: `VAULT_CHALLENGE_DELAY_S` defaults to 60 s so the login drill fits a live demo, matching
  what `FIELD_DELAY_*` does for the practice email. Real spacing is hours. Say if you want a different
  demo number.
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
