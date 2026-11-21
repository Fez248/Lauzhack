# Build prompt: password lesson and the Vault drill (lesson 2)

> A ready-to-paste prompt for a coding agent to build krishna's password piece.
> Design: [password-drill.md](password-drill.md). Keep the two in sync.
> **The plan wins:** [docs/mvp0-plan.md](mvp0-plan.md) §4.2 (C3), §5 (C1), §9.2, §10.4, §17.
> MVP 0 ships `email-01` only; build this **after** the MVP 0 demo stands up (§1.4).

---

You are building one piece of **ThisIsTea, a cyber security literacy course** (LauzHack hackathon).
Lesson 2 teaches passwords. Instead of a practice email, the learner then does the **Vault drill**: they
open accounts on **five fictional sites** (catLand, dogLand, fishLand, birdLand, bunnyLand), inventing a
username and password for each. You grade every password, check it against known breached passwords,
refuse bad ones with the real reason, and keep the good ones. **A later session asks the learner to log
back in to one of the five.** Three rules must hold in your code:

1. **Nothing here unlocks anything.** The five sites are fictional and local to the app. A vault login
   grants no session and no access. `.example` domains, invented animals, no real brand or service.
2. **You never hold a password.** Plaintext exists in memory for one request, then is cleared. You store
   an argon2id hash, two keyed fingerprints and a few facts. Never the password, in any table, log,
   response or error.
3. **The learner is told before the first keystroke.** The drill opens with a card saying what you keep
   and telling them to invent new passwords, not reuse a real one.

## Stack

- **Kotlin.** Server: **Ktor**. UI: **Compose Multiplatform**. Do not introduce another language.
- Reuse the plan's `PasswordAnalyzer` (§10.4) rather than writing a second strength checker.
- nbvcxz for scoring, Bouncy Castle for argon2id — both already in the plan's dependencies.
- **No AI calls anywhere in this module.** Rules, one library, one cached HTTP request.

## Build this

1. **Lesson 2 content screens.** Reuse the existing lesson renderer and contract C3 (§4.2):
   a `content` section with Cat's four rules, then a `tools` section (Dog) that walks the learner
   through installing a password manager now. Copy comes from
   `content/lessons/password-01/lesson.<lang>.json`; ship an `en` draft with `"status": "draft"`.
   Tool rows reference `resourceId`s in `content/resources/resources.json` — add entries for
   `bitwarden` (exists), `proton-pass`, `keepassxc`, `1password` and a built-in-manager row, each with
   `"verified": false`.

2. **Five sign-up screens.** One Compose screen, five skins driven by data:

   ```kotlin
   @Serializable
   data class VaultSite(val siteId: String, val shownAs: String, val domain: String,
                        val holds: String, val accent: String)
   ```

   `catland` / catLand / `catland.example`, then `dogland`, `fishland`, `birdland`, `bunnyland`.
   Each screen: username field, password field with show/hide, live meter, "Create account".
   Before site one, a card the learner dismisses once: *"Invent five new passwords for these five
   pretend sites. Don't type a password you really use anywhere — not even once. We keep a scrambled
   fingerprint so you can log back in later, plus a few facts like how long it was. We never keep the
   password itself, and these five accounts unlock nothing."*

3. **Live client-side meter** (no network): length, character classes, nbvcxz score, keyboard-row walk,
   and whether the password contains the site name, the learner's first name, their email local part or
   a `19\d\d|20\d\d` year. Also compare against the passwords typed earlier **in this drill session**,
   held in memory only, so reuse is caught before submit. Show the specific reason, never a bare
   coloured bar, and **never a "time to crack" figure**.

4. **`POST /me/vault/accounts`** (session auth) with `{site_id, username, password}`:
   - Analyse via `PasswordAnalyzer`: `length`, `classes`, `score` (0–4), `keyboard_pattern`,
     `contains_name`, `contains_email`, `contains_year`, `contains_site`.
   - **Breach check**, exactly as §10.4: SHA-1 the password, send only the **first 5 hex chars** to
     `https://api.pwnedpasswords.com/range/<prefix>` with `Add-Padding: true` and
     `User-Agent: ThisIsTea-server`, match the suffix locally. Timeout 2 s; on failure `pwned = false`
     and log "hibp unavailable". **Cache prefixes in memory for 1 hour.**
   - **Reuse check** against this learner's other vault accounts **and their account password**:
     `reuse_fp = HMAC-SHA256(PASSWORD_FP_PEPPER, password)` for exact matches, and
     `family_fp = HMAC-SHA256(PASSWORD_FP_PEPPER, normalise(password))` where `normalise` lowercases,
     strips trailing digits and punctuation, and folds leetspeak (`4→a 3→e 0→o 1→l $→s`).
   - **Accept iff** `length >= 10 && score >= 2 && !pwned && no exact reuse`. A `family_fp` match is
     **accepted**, with `family_match = true` returned for the debrief.
   - **Rejected:** return `{accepted: false, reason}` and **store nothing whatsoever** — no row, no
     counter, no log line containing any part of the password.
   - **Accepted:** store `vault_accounts` (with `password_hash = argon2id(password)`,
     `m = 19456 KiB, t = 2, p = 1`, 16-byte salt, PHC string) and `vault_findings`. Clear the
     `CharArray`. Emit `vault_account_created`.

   Reasons map to this copy:

   | `reason` | Learner reads |
   |---|---|
   | `too_short` | "At least 10 characters, please. Length is the part that actually helps." |
   | `weak` | "A guesser would get this quickly: it's close to a common one. Try four unrelated words." |
   | `pwned` | "This one is in known breach lists (seen `<count>` times). Attackers try these first." |
   | `contains_site` / `contains_name` / `contains_year` | "It has `catland` in it — the first thing anyone guessing this site tries." |
   | `reused` | "That's the password you used for dogLand. One leak would open both. Try another." |

5. **Debrief screen** from `GET /me/vault/debrief` — the five findings side by side (never a password,
   never a fingerprint). Three parts: **family links** between sites whose `family_fp` matches; a
   **tiering sort** where the learner ranks the five sites by what each holds, compared against the
   strength they actually chose; and **where these came from** (name / year / site name / keyboard
   counts). Spider ranks them weakest-first with the reason, and takes nothing.

6. **Login drill.** `GET /me/vault/challenge` returns a due site via `Review.kt` (§6.8), treating each
   vault account as a review item — first challenge at least a few hours later, **one site per session**.
   `POST /me/vault/login {site_id, username, password}` verifies against the argon2id hash:
   - Three attempts, each emitting `vault_login_attempted`. Username is shown; the password is not.
   - Success → set `graduated_at`, award XP, Cat: "that is the habit — you didn't remember it, you
     looked it up."
   - Three failures → "Open your password manager and look it up. If it isn't in there, that is the
     lesson: we can't tell you either — we only kept a scrambled fingerprint." Offer a reset that runs
     a new password through step 4 and reschedules the challenge.
   - **Never** give a hint derived from the findings (length, first character, anything).

7. **Transparency and deletion.** Add every vault field to `GET /me/about` ("What we know about you",
   §9.2) with its purpose in plain words. Extend `DELETE /me` to wipe `vault_accounts`,
   `vault_findings` and `vault_logins`. Add `DELETE /me/vault` for demo resets.

## Data shape

```kotlin
@Serializable
data class VaultCreateRequest(val siteId: String, val username: String, val password: String)

@Serializable
data class VaultCreateResponse(
    val accepted: Boolean,
    val reason: String? = null,          // too_short | weak | pwned | contains_site |
                                         // contains_name | contains_year | reused
    val pwnedCount: Int? = null,
    val findings: VaultFindings? = null, // accepted only
    val familyMatch: Boolean = false,
)

@Serializable
data class VaultFindings(
    val length: Int, val classes: Int, val score: Int,
    val pwned: Boolean, val pwnedCount: Int,
    val containsName: Boolean, val containsEmail: Boolean,
    val containsYear: Boolean, val containsSite: Boolean,
    val keyboardPattern: String? = null,
)

@Serializable
data class VaultLoginResponse(val ok: Boolean, val attemptNo: Int, val hint: String? = null)
```

### Tables (additive to contract C1, §5)

```
vault_accounts(id, user_id, site_id, username, password_hash, created_at,
    graduated_at?, UNIQUE(user_id, site_id))                                      (add)
vault_findings(account_id PK, length, classes, score, pwned, pwned_count,
    contains_name, contains_email, contains_year, contains_site, keyboard_pattern,
    reuse_fp, family_fp, analysed_at)                                             (add)
vault_logins(id, user_id, site_id, succeeded, attempt_no, at)                     (add)
```

### Events (additive to §5.1)

`vault_drill_started` `{lesson_id}` · `vault_account_created` `{site_id, score, pwned, family_match}` ·
`vault_attempt_rejected` `{site_id, reason}` · `vault_drill_completed`
`{accounts, rejections, families}` · `vault_login_attempted` `{site_id, succeeded, attempt_no}`

### Config

`PASSWORD_FP_PEPPER` in `.env` (32+ random bytes, base64), with a valueless entry in
`deploy/.env.example`. **Never committed.** Rotating it invalidates reuse detection for existing rows,
not logins — say so in a comment.

## Rules (non-negotiable)

- Plaintext in memory for one request only, then cleared. Never stored, logged, returned, or included
  in an error or stack trace. No plaintext column, in any table, for any reason.
- Rejected attempts store **nothing** — no row, no counter, no paste detection (§1.4).
- Only the 5-character HIBP prefix leaves the server. No other outbound request carries learner data.
- Fictional sites, `.example` domains, no real brand or service imitated. Real password-manager names
  appear only as `resources.json` entries, shipped `verified: false`.
- A vault login grants no session, no token and no access to anything.
- Every stored field is listed in "What we know about you" with its purpose; `DELETE /me` removes all of
  it; the honesty test (§17) applies to every screen.
- Lesson copy is human-written; AI only drafts variations for human review.

## Done when

- A learner can open five accounts; weak, breached and exactly-reused passwords are refused with the
  real reason, and nothing is stored for a refusal.
- `grep -ri` over the database schema, the logs of a full drill run, and every response body finds no
  plaintext password.
- A password already in a breach list (`password1`) is refused with its breach count; with the network
  off, the drill still works and the log says "hibp unavailable".
- `catland1` then `catland2` both get in, and the debrief draws the family link between them.
- The debrief shows five rows built only from findings — no password, no fingerprint.
- A later session challenges exactly one site; the right password graduates it, three wrong ones give
  the honest "we can't tell you either" message and never a derived hint.
- "What we know about you" lists every vault field; `DELETE /me` leaves no vault rows behind.
