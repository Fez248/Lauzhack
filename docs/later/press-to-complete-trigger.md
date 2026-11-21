# Press-to-complete trigger (first loop)

> **Satellite file.** What to build for MVP 0 is defined only in [docs/mvp0-plan.md](../mvp0-plan.md) (part 5: §9.6, §10.6, §12). This file is a **later-loop design**: the resend series, look-alike domain and landing page are **out of scope for MVP 0** (decisions 3 and 4: click-only, plain Gmail sender, no sign-in page). If this file disagrees with the plan, **the plan wins**.

> Status: **proposal** (krishna, 2026-11-21). Owner: krishna.
> This is one piece of the first loop. Build comes later; this doc is the design to agree on first.
> **Stack (decided for loop 1 onward): Kotlin** (Ktor server endpoint + Compose button), matching
> [tech-stack.md](tech-stack.md). **The email send is a placeholder for now** — the trigger calls a
> stub sender that logs the payload; real delivery comes later.

## Where this fits in the first loop

```
onboarding → placement test → phishing lesson
   → learner presses "Press to complete" (only if they feel confident)
      → wait a random delay (15s – 1m30s in loop 1)
         → send a default fake phishing email to the learner
            → learner clicks the link → fake "confirm your email" login page opens
               → learner types email + password to "confirm"
                  → we record that they fell for it (NOT the password)
                     → schedule the NEXT phishing email, spaced by how badly they did
                        ↺ repeat as a spaced-repetition deck (≤5 min total in loop 1)
                     → (later) that outcome is fed back into the next lesson
```

**My part:** the button, the random delay, firing the (placeholder) email send, **and the fake login
landing page that records the outcome**. Writing the email content/domain and feeding the outcome
into the next lesson are other people's pieces. This doc defines the trigger, the landing page, and
the contracts with those pieces.

## Why a delay, and why random

- If the phishing email arrived the instant the learner finished the lesson, they would connect the
  two and stay on guard. That is "reading the label" (see
  [onboarding-and-field-mode.md](onboarding-and-field-mode.md)): the context gives the answer away.
- A short, unpredictable gap (15s–90s) makes the email land while the learner has moved on, so they
  judge it the way they would a real message. It is still inside the same session, so the demo stays
  tight and we can show the whole loop on stage.
- The range widens in later loops (minutes, then hours, then days) as the habit is tested further
  from the lesson. Loop 1 keeps it to 15s–90s so a live demo fits.

## The button

- Label: **"Press to complete"**, shown at the end of the phishing lesson, with a line like
  "Press this once you feel confident you could spot a scam like this."
- Pressing it is the learner's **consent** for this loop's practice email (see safety rules).
- After the press: the button is disabled and shows a neutral confirmation ("Lesson complete").
  It must **not** hint that an email is coming, or the delay is pointless.
- One press = one scheduled email. Pressing again does not schedule a second one.

## The trigger (what I build)

On press, the trigger:

1. Reads the learner's email and the chosen phishing-email id from their profile/session.
2. Picks a random delay `d` uniformly in **[15s, 90s]**.
3. Schedules the send for `now + d`.
4. Hands off to the email-sending piece at that time, passing the learner + email id.

### Timing

- `delaySeconds = 15 + random() * 75` (uniform, 15.0–90.0).
- The delay is logged (not shown to the learner) so the demo and debugging can see it.
- A scheduled send that has not fired yet can be cancelled (learner leaves, demo reset).

### Contract with the email-sending piece

The trigger does not send email itself; it calls a sender interface when the timer fires. **For loop 1
the sender is a placeholder** (`PlaceholderEmailSender`) that just logs the payload below; it is
swapped for a real sender later without changing the trigger. Proposed payload shape (not final — the
email owner confirms):

```json
{
  "learner_id": "abc123",
  "to_email": "learner@example.com",
  "phishing_email_id": "default-login-reset-01",
  "loop": 1,
  "scheduled_at": "2026-11-21T14:00:00Z",
  "fired_at": "2026-11-21T14:00:47Z"
}
```

The **default** `phishing_email_id` for loop 1 is the credential-reset lookalike: a message from a
near-miss domain (e.g. `admin@isthistee.com` instead of `admin@isthistea.com`) asking the learner
to "log in again". The lookalike domain and the fake login page are owned by the email/page pieces;
the trigger only names which template to send.

## The fake login landing page

The phishing email's link opens a page on our own fictional domain that **looks like** a login /
"confirm your email" screen. This is where we find out whether the learner fell for it.

- The link carries a **one-time token** that identifies the learner and the `phishingEmailId`
  (e.g. `.../confirm?t=<token>`). The page needs no other identifying input.
- The page shows a generic "Confirm your email — please log in" form: an **email** field and a
  **password** field. Generic styling, fictional brand, no real logo.
- **Opening the page is already an outcome** (`link_opened`): the learner clicked a link in a
  suspicious email. We record that.
- **On submit** we record a stronger outcome (`credentials_submitted`): the learner was ready to hand
  over a login. Then the page immediately reveals it was practice and (later) routes into the lesson.

### What we store, and what we never store

- **Store:** an outcome event — `{ learnerId, phishingEmailId, outcome, at }` where `outcome` is
  `link_opened` or `credentials_submitted`. That is the whole point: "this learner fell for it."
- **Never store:** the **password**. The password field's value is **never sent to the server** —
  on submit the client posts only the token and the outcome, and the password is discarded in the
  browser. Nothing logs it either.
- **Do not store the typed email either.** We already know who the learner is from the token, so the
  typed credentials are thrown away entirely. The form exists only to measure the behaviour.

### Outcome contract (for the lesson piece)

```json
{ "learnerId": "abc123", "phishingEmailId": "default-login-reset-01",
  "outcome": "credentials_submitted", "at": "2026-11-21T14:01:10Z" }
```

The lesson owner reads these outcomes to feed the result back into the next lesson
("you clicked this — here's what gave it away"). For loop 1 the reveal can be a simple
"This was a practice phishing test" page right after submit.

## Spaced-repetition resend (Anki-style)

The learner doesn't get one email, they get a short **series**, resent on intervals that grow or
shrink like Anki flashcards. **Worse behaviour → the next email comes sooner and repeats more; good
behaviour → the gap grows, then they graduate out of the deck.** Loop 1 compresses the whole series
to **≤5 minutes** so it fits a live demo; real spacing (hours, then days) comes later.

### Outcome grades (per sent email)

These reuse the two signals the landing page already records — nothing new is stored, and "typed in
the email" means only that the submit event happened, never the contents:

| Grade | Outcome | Meaning |
|---|---|---|
| 0 | `credentials_submitted` | clicked the link **and** typed in the email/password → worst |
| 1 | `link_opened` | clicked the link, did not submit |
| 2 | `no_interaction` | didn't open the link within the observation window → best |

### Choosing the next interval

After each email, observe until the outcome is decided — a click or submit decides it immediately;
otherwise a short no-interaction timeout does — then schedule the next email:

| Last grade | Next interval (loop 1, compressed) | Why |
|---|---|---|
| 0 `credentials_submitted` | ~30s | drill hard, soon |
| 1 `link_opened` | ~60s | a nudge |
| 2 `no_interaction` | previous interval **× 2.5**, capped at 5 min | spacing grows |

- **Graduation:** after **2 consecutive grade-2 passes**, stop resending — the habit is holding.
- Any grade 0 or 1 **resets** the growth back to the short drill intervals.
- **Hard cap:** no interval exceeds 5 min in loop 1. A learner who keeps failing keeps getting the
  ~30–60s drills until they pass or the demo ends.

### State per learner (cancellable)

`currentIntervalSeconds`, `consecutiveClean` (count of grade-2 in a row), and the next scheduled send.
A demo reset cancels the pending send and clears the state.

> Numbers above are tunable placeholders for loop 1. The shape (grade → interval, grow on success,
> shrink/repeat on failure, graduate) is the real design; later loops just stretch the intervals.

## Safety rules (inherited, non-negotiable)

These come straight from [onboarding-and-field-mode.md](onboarding-and-field-mode.md) and apply to
this trigger:

- **Opt-in:** pressing the button is the consent for this loop's email. No press, no email.
- **Our own domain, fictional brands:** the lookalike (`isthistee.com` vs `isthistea.com`) is **ours**
  and fictional. No spoofing a real brand, no spoofing a real sender.
- **No real secrets collected:** the fake login page **never stores or transmits the password**, and
  throws away the typed email too. It records only the outcome event (`link_opened` /
  `credentials_submitted`). This is enforced in this piece — see the landing-page section above.
- **No distressing content:** loop 1 uses the low-emotion credential-reset template only.
- **Cancellable and stoppable:** a pending send can be cancelled; the learner can stop field practice.

## Open questions

- Where does the trigger run? It needs to fire after the learner may have closed the tab, so a plain
  in-browser `setTimeout` is not enough. **Decided: a Kotlin/Ktor server endpoint** schedules the send
  (a coroutine `delay` + `launch`, or a scheduled task). The Compose button calls that endpoint.
- Who owns the learner's email address and consent state — onboarding profile, or passed at press time?
- Final shape of the sender contract above (the email owner signs off).
- Loop 2+ delay ranges and how "which email" is chosen once it stops being the default one.

## What to build (loop 1)

| Priority | Piece | Owner |
|---|---|---|
| Must | "Press to complete" Compose button + disabled/confirmation state | krishna |
| Must | Ktor endpoint: random 15–90s delay + scheduled hand-off to the sender | krishna |
| Must | `EmailSender` interface + `PlaceholderEmailSender` that logs the payload | krishna |
| Must | Cancel a pending send (demo reset) | krishna |
| Must | Fake "confirm your email" login page reached by a one-time token | krishna |
| Must | Record outcome (`link_opened` / `credentials_submitted`); never store password/email | krishna |
| Must | Simple "this was a practice test" reveal after submit | krishna |
| Must | Spaced-repetition scheduler: grade the outcome, pick next interval, graduate | krishna |
| Depends on | Sender contract agreed with the email piece | krishna + email owner |
| Later | Real `EmailSender` implementation (swaps out the placeholder) | email owner |
| Not mine | The phishing email content + lookalike domain | email owner |
| Not mine | Feeding the outcome back into the next lesson | lesson owner |
