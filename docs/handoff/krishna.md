# Handoff: krishna

Branch: `Krishna` · Last updated: 2026-11-21

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
- Claimed TASKS #31 and #32; added #33 for the password-manager resource entries.

## In progress

- Nothing. Waiting on the answers below before building.

## Blocked / needs

- Needs a decision on hashed vs plaintext drill passwords (see the question to nathann). Everything
  else in the design is independent of it.

## Next

1. Confirm the password lesson is lesson 2, built after the MVP 0 demo stands up.
2. Build in this order: five sign-up screens → `POST /me/vault/accounts` with the three checks →
   debrief → login drill via `Review.kt`.
3. Review nathann's rewrite of my `awareness-sim-*` docs line by line and confirm or push back
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
- To nathann: should password behaviour get its own mastery `skill` key in `Bkt.kt`, separate from the
  four scam-defence steps? It is a different skill and I don't want to pollute the step mastery.
- To nathann: `AGENTS.md` and the AGREED entry in `decisions.md` say branches are the first name in
  **lowercase**, but mine is `Krishna` with a capital K and already pushed with that name. Leave it, or
  do you want it renamed before the PRs start?
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
