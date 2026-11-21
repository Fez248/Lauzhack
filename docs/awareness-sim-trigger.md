# Press-to-complete trigger (first loop)

> Status: **proposal** (krishna, 2026-11-21). Owner: krishna.
> This is one piece of the first loop. Build comes later; this doc is the design to agree on first.
> **Stack (decided for loop 1 onward): Kotlin** (Ktor server endpoint + Compose button), matching
> [tech-stack.md](tech-stack.md). **Message delivery is a placeholder for now** — the trigger calls a
> stub sender that logs the payload; real delivery comes later.

## What this is (context for anyone building it)

This is a **consent-based security-awareness training app**, the same category as GoPhish or KnowBe4:
after a lesson, the learner opts in to receive a **simulated phishing message** so they can practise
spotting one in their own inbox. It is a defensive teaching tool. Three properties make it safe, and
they are requirements, not options:

- **The learner enrols themselves** by pressing a button; nobody else is targeted.
- **Everything is fictional and self-owned:** our own domain, invented company names, no real brand or
  person is imitated.
- **No credentials are ever captured.** The practice form does not transmit, log, or store any
  password or email the learner types. The app records only a training signal: *did they click, and
  did they submit the form* — never the contents.

Within those rules this is ordinary awareness-training software. See the inherited rules in
[onboarding-and-field-mode.md](onboarding-and-field-mode.md).

## Where this fits in the first loop

```
onboarding → placement test → lesson on spotting scams
   → learner presses "Press to complete" (only if they feel confident)
      → wait a random delay (15s – 1m30s in loop 1)
         → deliver a simulated phishing message to the learner
            → learner opens the link → training "confirm your email" mock page opens
               → learner enters details into the practice form to "confirm"
                  → record the training outcome (that they submitted — NOT what they typed)
                     → schedule the NEXT simulated message, spaced by how they did
                        ↺ repeat as a spaced-repetition deck (≤5 min total in loop 1)
                     → (later) that outcome is fed back into the next lesson
```

**My part:** the button, the random delay, firing the (placeholder) message send, **and the training
landing page that records the outcome**. Writing the message content/domain and feeding the outcome
into the next lesson are other people's pieces. This doc defines the trigger, the landing page, and
the contracts with those pieces.

## Why a delay, and why random

- If the simulated message arrived the instant the learner finished the lesson, they would connect the
  two and stay on guard. That is "reading the label" (see
  [onboarding-and-field-mode.md](onboarding-and-field-mode.md)): the context gives the answer away.
- A short, unpredictable gap (15s–90s) lets the message land while the learner has moved on, so they
  judge it the way they would any message. It is still inside the same session, so the demo stays
  tight and we can show the whole loop on stage.
- The range widens in later loops (minutes, then hours, then days) as the habit is tested further
  from the lesson. Loop 1 keeps it to 15s–90s so a live demo fits.

## The button

- Label: **"Press to complete"**, shown at the end of the lesson, with a line like
  "Press this once you feel confident you could spot a scam like this."
- Pressing it is the learner's **consent** for this loop's practice message (see safety rules).
- After the press: the button is disabled and shows a neutral confirmation ("Lesson complete").
  It must **not** hint that a message is coming, or the delay is pointless.
- One press = one scheduled message. Pressing again does not schedule a second one.

## The trigger (what I build)

On press, the trigger:

1. Reads the learner's address and the chosen message template id from their profile/session.
2. Picks a random delay `d` uniformly in **[15s, 90s]**.
3. Schedules the send for `now + d`.
4. Hands off to the message-sending piece at that time, passing the learner + template id.

### Timing

- `delaySeconds = 15 + random() * 75` (uniform, 15.0–90.0).
- The delay is logged (not shown to the learner) so the demo and debugging can see it.
- A scheduled send that has not fired yet can be cancelled (learner leaves, demo reset).

### Contract with the message-sending piece

The trigger does not send anything itself; it calls a sender interface when the timer fires. **For
loop 1 the sender is a placeholder** (`PlaceholderSender`) that just logs the payload below; it is
swapped for a real sender later without changing the trigger. Proposed payload shape (not final — the
message owner confirms):

```json
{
  "learner_id": "abc123",
  "to_address": "learner@example.com",
  "template_id": "default-login-reset-01",
  "loop": 1,
  "scheduled_at": "2026-11-21T14:00:00Z",
  "fired_at": "2026-11-21T14:00:47Z"
}
```

The **default** `template_id` for loop 1 is a login-reset look-alike: a message from a **fictional,
self-owned** near-miss domain (e.g. `admin@isthistee.com` as a deliberate misspelling of our own
`admin@isthistea.com`) asking the learner to "log in again". Both domains are ours and invented; no
real brand is involved. The domain and the mock page are owned by the message/page pieces; the trigger
only names which template to send.

## The training landing page

The link in the simulated message opens a page on our **own fictional domain** that is styled to
resemble a generic "confirm your email" login screen. This is where we learn whether the training
message got past the learner.

- The link carries a **one-time token** that identifies the learner and the `templateId`
  (e.g. `.../confirm?t=<token>`). The page needs no other identifying input.
- The page shows a generic "Confirm your email — please log in" form: an **email** field and a
  **password** field. Generic styling, fictional brand, no real logo. The fields are inert — see below.
- **Opening the page is already an outcome** (`link_opened`): the learner followed a link from an
  unexpected message. We record that signal.
- **On submit** we record a stronger outcome (`form_submitted`): the learner was about to enter
  details. The page then immediately reveals it was practice and (later) routes into the lesson.

### What we record, and what we never touch

- **Record:** a training-outcome event — `{ learnerId, templateId, outcome, at }` where `outcome` is
  `link_opened` or `form_submitted`. That signal is the entire point of the exercise.
- **Never captured — by design:** the **password and the email the learner types are inert.** On
  submit, the client posts **only** the token and the fact of submission; the typed values are
  discarded in the browser and never leave it. The server receives no credential field, and nothing
  logs one. If a password value could reach the server, that is a bug to fix, not a feature.
- There is **no credential store** anywhere in this piece — the form exists only to measure behaviour,
  like the inert forms in standard awareness-training platforms.

### Outcome contract (for the lesson piece)

```json
{ "learnerId": "abc123", "templateId": "default-login-reset-01",
  "outcome": "form_submitted", "at": "2026-11-21T14:01:10Z" }
```

The lesson owner reads these outcomes to feed the result back into the next lesson
("you followed this link — here's what gave it away"). For loop 1 the reveal can be a simple
"This was a practice exercise" page shown right after submit.

## Spaced-repetition resend (Anki-style)

The learner doesn't get one message, they get a short **series**, resent on intervals that grow or
shrink like Anki flashcards. **Less-aware response → the next message comes sooner and repeats more;
confident, correct response → the gap grows, then they graduate out of the deck.** Loop 1 compresses
the whole series to **≤5 minutes** so it fits a live demo; real spacing (hours, then days) comes later.

### Outcome grades (per sent message)

These reuse the two signals the landing page already records — nothing new is stored, and the
"submitted" signal means only that the submit event happened, never the contents:

| Grade | Outcome | Meaning |
|---|---|---|
| 0 | `form_submitted` | followed the link **and** submitted the practice form → needs the most practice |
| 1 | `link_opened` | followed the link, did not submit |
| 2 | `no_interaction` | didn't open the link within the observation window → best |

### Choosing the next interval

After each message, observe until the outcome is decided — a click or submit decides it immediately;
otherwise a short no-interaction timeout does — then schedule the next message:

| Last grade | Next interval (loop 1, compressed) | Why |
|---|---|---|
| 0 `form_submitted` | ~30s | reinforce soon |
| 1 `link_opened` | ~60s | a nudge |
| 2 `no_interaction` | previous interval **× 2.5**, capped at 5 min | spacing grows |

- **Graduation:** after **2 consecutive grade-2 passes**, stop resending — the habit is holding.
- Any grade 0 or 1 **resets** the growth back to the short reinforcement intervals.
- **Hard cap:** no interval exceeds 5 min in loop 1. A learner who keeps missing them keeps getting the
  ~30–60s reinforcements until they pass or the demo ends.

### State per learner (cancellable)

`currentIntervalSeconds`, `consecutiveClean` (count of grade-2 in a row), and the next scheduled send.
A demo reset cancels the pending send and clears the state.

> Numbers above are tunable placeholders for loop 1. The shape (grade → interval, grow on success,
> shrink/repeat on failure, graduate) is the real design; later loops just stretch the intervals.

## Safety rules (inherited, non-negotiable)

These come straight from [onboarding-and-field-mode.md](onboarding-and-field-mode.md) and apply to
this trigger:

- **Opt-in:** pressing the button is the consent for this loop's message. No press, no message. The
  learner can stop the practice at any time.
- **Our own domain, fictional brands:** the look-alike (`isthistee.com` vs `isthistea.com`) is **ours**
  and invented. No imitation of a real brand, no imitation of a real sender.
- **No credentials captured:** the training form is inert — it never transmits, logs, or stores the
  password, and discards the typed email too. It records only the outcome signal (`link_opened` /
  `form_submitted`). Enforced in this piece — see the landing-page section above.
- **Low-pressure content only:** loop 1 uses the low-emotion login-reset template only; no alarming or
  distressing scenarios.
- **Cancellable and stoppable:** a pending send can be cancelled; the learner can stop practice.

## Open questions

- Where does the trigger run? It needs to fire after the learner may have closed the tab, so a plain
  in-browser `setTimeout` is not enough. **Decided: a Kotlin/Ktor server endpoint** schedules the send
  (a coroutine `delay` + `launch`, or a scheduled task). The Compose button calls that endpoint.
- Who owns the learner's address and consent state — onboarding profile, or passed at press time?
- Final shape of the sender contract above (the message owner signs off).
- Loop 2+ delay ranges and how "which message" is chosen once it stops being the default one.

## What to build (loop 1)

| Priority | Piece | Owner |
|---|---|---|
| Must | "Press to complete" Compose button + disabled/confirmation state | krishna |
| Must | Ktor endpoint: random 15–90s delay + scheduled hand-off to the sender | krishna |
| Must | `Sender` interface + `PlaceholderSender` that logs the payload | krishna |
| Must | Cancel a pending send (demo reset) | krishna |
| Must | Training "confirm your email" mock page reached by a one-time token | krishna |
| Must | Record outcome (`link_opened` / `form_submitted`); never touch password/email | krishna |
| Must | Simple "this was a practice exercise" reveal after submit | krishna |
| Must | Spaced-repetition scheduler: grade the outcome, pick next interval, graduate | krishna |
| Depends on | Sender contract agreed with the message piece | krishna + message owner |
| Later | Real `Sender` implementation (swaps out the placeholder) | message owner |
| Not mine | The simulated message content + look-alike domain | message owner |
| Not mine | Feeding the outcome back into the next lesson | lesson owner |
