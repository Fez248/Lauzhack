# ThisIsTea (LauzHack 2026)

A teaching engine for multi-step procedures, applied to cyber security literacy. Learners practise
four steps on realistic emails, and the prompts for each step fade as they master it:

1. Who is really writing?
2. What feeling are they pushing?
3. What exactly do they want me to do?
4. How can I check through a channel I control?

- **What to build (MVP 0, single source of truth): [docs/mvp0-plan.md](docs/mvp0-plan.md)**
- Idea and design: [docs/idea.md](docs/idea.md)
- Team and agent rules (branches, communication): [AGENTS.md](AGENTS.md)
- Task board: [TASKS.md](TASKS.md) · Decisions: [docs/decisions.md](docs/decisions.md)

**Git rule:** everyone works on their own branch (first name, lowercase). Nothing goes to `main`
except through a reviewed pull request.

## What is in the repo

| Path | What |
|---|---|
| `shared/` | Kotlin Multiplatform library: models, engines, content loading, network, store, theme, all screens |
| `composeApp/` | Thin shells for web (wasm) and desktop (jvm) |
| `androidApp/` | Android shell, built only when an Android SDK is found |
| `server/` | Ktor server: accounts, sync, leaderboard, the opt-in practice email |
| `content/` | Items (`items/`), lesson 1 (`lessons/`), practice-email template (`field/`), resource links |
| `deploy/` | Dockerfile, Docker Compose, `.env.example` |
| `static/`, `guide-rules.md` | Design reference only (ported to Compose, not shipped) |

## Run it

You need JDK 21 (`JAVA_HOME` must point to it). Gradle comes with the wrapper.

```bash
# 1. Backend on http://localhost:8080 (in-memory database, emails printed to the console)
RATE_LIMITS=off ./gradlew :server:run

# 1b. Same, but the database survives a restart (keeps accounts and Vault drill progress)
RATE_LIMITS=off DATABASE_URL='jdbc:h2:file:./build/tea-dev;AUTO_SERVER=TRUE' ./gradlew :server:run

# 2a. Web app on http://localhost:8081
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
# 2b. or desktop app
./gradlew :composeApp:run
```

- The app finds the backend at `http://localhost:8080` by default. Another backend: open the web app
  with `?api=https://your-backend` (saved; `?api=reset` clears it), or set `TEA_API` on desktop.
- **Signing in without real email:** while no `SMTP_PASSWORD` is set, the server prints each
  verification code to its console and also accepts the debug code `426769`. The debug code switches
  off by itself once SMTP is configured (`DEV_VERIFY_CODE=off` disables it always).
- Without a backend the app still works as a guest and syncs later.

Tests and checks:

```bash
./gradlew :shared:jvmTest :server:test          # unit, content validation, end-to-end against a real server
./gradlew :shared:jvmTest -Pstrict=true         # missing translations become errors
./gradlew :androidApp:assembleDebug             # needs an Android SDK (local.properties sdk.dir or ANDROID_HOME)
```

Wasm tests (`:shared:wasmJsTest`) need a Chromium browser; set `CHROME_BIN` if Chrome is not installed.

## Deploy for the demo

1. Copy `deploy/.env.example` to `.env` at the repo root and fill it in (never commit `.env`).
2. `docker compose -f deploy/docker-compose.yml up --build` starts the server and Postgres on port 8080.
3. Expose it: `cloudflared tunnel --url http://localhost:8080`. Quick tunnel URLs change on restart:
   update the `API_URL` repository variable, or open the site with `?api=<tunnel url>`.
4. The web app is published to GitHub Pages by `.github/workflows/pages.yml` on every push to `main`
   (Settings → Pages → Source: GitHub Actions; repository variable `API_URL`).
5. Set `APP_PUBLIC_URL` (the Pages URL, with trailing slash) and `API_PUBLIC_URL` (the tunnel) in
   `.env` so the link in the practice email opens the app.

## Shared code and platform code

Shared in `shared/src/commonMain` (runs on web, desktop, Android and, for models and engines, the
server): data models and API types, the learning engines (placement, mastery tracking, prompt fading,
item selection, XP), content loading and validation, translations, the theme and components, every
screen, the network client and sync queue, the free-text grading interface.

Platform-specific (small `expect`/`actual` files): the app entry points, key-value storage
(localStorage, a file, SharedPreferences), the HTTP engine, device information, opening links and the
share sheet.

## Content

All items and the lesson are **drafts** (`"status": "draft"`) for human review. Fictional brands only,
every address on `.example`. Adding a language needs files only: see
[docs/adding-a-language.md](docs/adding-a-language.md).
