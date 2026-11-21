# Build prompt: press-to-complete trigger (loop 1)

> A ready-to-paste prompt for a coding agent to build krishna's piece of the first loop.
> Design: [press-to-complete-trigger.md](press-to-complete-trigger.md). Keep the two in sync.

---

You are building one piece of a scam-awareness training app for the Lauzhack hackathon. The app
teaches people to spot phishing, then (with their consent) sends them a simulated phishing email so
they practise on a real message. Your piece is the **press-to-complete trigger**: a button the
learner presses at the end of a lesson, which after a random delay fires a (placeholder) phishing
email send, **plus the fake "confirm your email" login page that the email links to** — which records
whether the learner fell for it, without ever storing their password.

## Stack

- **Kotlin.** Server: **Ktor**. UI: **Compose Multiplatform** (web/desktop target).
- This matches the team's `docs/tech-stack.md`. Do not introduce another language.
- The email send is a **placeholder** for now: no real email leaves the machine.

## Build this

1. **Compose button** labelled **"Press to complete"**, shown at the end of the phishing lesson, with
   a subtitle: "Press this once you feel confident you could spot a scam like this."
   - On press: call the server endpoint, then disable the button and show a neutral "Lesson complete"
     confirmation. **Do not** hint that an email is coming.
   - Pressing again does nothing (one press = one scheduled send).

2. **Ktor endpoint** `POST /complete` that:
   - Accepts `{ "learnerId": "...", "toEmail": "...", "phishingEmailId": "default-login-reset-01" }`
     (`phishingEmailId` defaults to `"default-login-reset-01"` if omitted).
   - Picks a random delay `d` uniformly in **[15.0s, 90.0s]**: `15.0 + Random.nextDouble() * 75.0`.
   - Schedules the send for `now + d` (a coroutine `launch { delay(...); sender.send(payload) }`,
     tracked so it can be cancelled). Logs the chosen delay at INFO (for the demo/debugging).
   - Returns `202 Accepted` immediately with the scheduling result (e.g. `{ "scheduledInSeconds": 47 }`).

3. **`EmailSender` interface** with one method `suspend fun send(payload: ScheduledSend)` and a
   **`PlaceholderEmailSender`** implementation that only logs the full payload. The real sender is
   added later by swapping the implementation — the trigger must not change.

4. **Cancel**: `POST /complete/cancel` with `{ "learnerId": "..." }` cancels that learner's pending
   send (for demo resets). Cancelling a non-existent one is a no-op 200.

5. **Fake login landing page.** When the placeholder "sends", it logs a link like
   `/confirm?t=<token>`, where the token encodes `learnerId` + `phishingEmailId` (a signed/opaque
   one-time token; a simple map in memory is fine for loop 1).
   - `GET /confirm?t=<token>`: serve a generic "Confirm your email — please log in" page with an
     **email** field and a **password** field. Generic styling, fictional brand, no real logo. Record
     outcome `link_opened` for that token on load.
   - `POST /confirm`: the page posts **only** `{ "token": "..." }` — **never the password, never the
     typed email**. Record outcome `credentials_submitted`, then return a simple reveal:
     "This was a practice phishing test. Here's what gave it away…". The password value is discarded
     in the browser and never sent.
   - Store outcomes as `OutcomeEvent` keyed by learner, readable by the lesson piece later.

6. **Spaced-repetition resend (Anki-style).** The learner gets a *series* of emails, not one. After
   each email's outcome is decided, schedule the next one; the interval depends on how badly they did.
   Loop 1 compresses the whole series to **≤5 minutes**.
   - **Grade the last email** from the recorded outcome:
     `credentials_submitted` → 0, `link_opened` → 1, `no_interaction` → 2.
     The outcome is decided when a click/submit arrives, or after a short no-interaction timeout.
   - **Next interval:** grade 0 → ~30s, grade 1 → ~60s, grade 2 → `previousInterval * 2.5` capped at
     300s. The first email keeps the existing random 15–90s after the button press.
   - **Graduate** (stop resending) after 2 consecutive grade-2 passes. Any grade 0 or 1 resets the
     growth to the short drill interval.
   - **Never exceed 300s** for any interval in loop 1.
   - Keep per-learner state (`currentIntervalSeconds`, `consecutiveClean`, next scheduled send); the
     cancel endpoint clears it and cancels the pending send.
   - Keep the intervals/ease as named constants at the top so they're easy to tune.

## Data shape

```kotlin
@Serializable
data class ScheduledSend(
    val learnerId: String,
    val toEmail: String,
    val phishingEmailId: String,
    val loop: Int = 1,
    val scheduledAt: String, // ISO-8601
    val firedAt: String? = null,
)
```

```kotlin
@Serializable
data class OutcomeEvent(
    val learnerId: String,
    val phishingEmailId: String,
    val outcome: String, // "link_opened" | "credentials_submitted"
    val at: String,      // ISO-8601
)
```

Use `kotlinx.serialization` and `kotlinx.datetime` (or `Instant`).

## Rules (non-negotiable — from docs/onboarding-and-field-mode.md)

- The button press is the learner's **consent**. No press, no send.
- The (future) email uses **our own fictional domain**, e.g. `isthistee.com` as a lookalike of
  `isthistea.com`. Never spoof a real brand or sender. The placeholder sends nothing, so just carry
  the id.
- The fake login page must **never store or transmit the password**, and must throw away the typed
  email too. It records only the outcome event. The `POST /confirm` body carries the token only — if a
  password field value can reach the server, that is a bug.
- Loop 1 uses only the low-emotion credential-reset template (`default-login-reset-01`).

## Done when

- Pressing the button schedules a log-only "send" 15–90s later, visible in the server log.
- The delay is random within range across presses.
- A pending send can be cancelled.
- Swapping `PlaceholderEmailSender` for a real one requires no change to the button or the scheduler.
- Opening `/confirm?t=<token>` records `link_opened`; submitting the form records
  `credentials_submitted` and shows the reveal.
- No request to the server ever carries the password or the typed email; only the token and the
  outcome are stored.
- After each email, the next one is scheduled on a shorter interval when they fell for it and a longer
  one when they didn't; a learner who ignores two in a row graduates and stops getting emails; no
  interval exceeds 5 min.
