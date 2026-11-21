# Decisions log

Append-only. Format: `YYYY-MM-DD · who · decision · why`. Agents propose; humans decide.
Mark entries **PROPOSED** until the team agrees, then add a new line marking them **AGREED**.

- 2026-11-21 · nathann · **PROPOSED**: one project, Scholé as main challenge, IMC secondary, AXA optional. · Sponsor judges score fit to their brief; bolt-on features for many tracks win nothing.
- 2026-11-21 · nathann · **PROPOSED**: teaching engine with fading support; first topic is scam defence, with senior and NGO content packs. · Combines the long-horizon teaching idea with social impact; NGOs are customer and distribution channel.
- 2026-11-21 · nathann · **AGREED**: one git branch per person (first name, lowercase); nothing pushed to `main` except through reviewed PRs. · Avoid overwriting each other's work.
- 2026-11-21 · nathann · **PROPOSED**: scenario JSON format as in `content/scenarios/_example.json` is the contract between content, engine and UI. · Lets everyone work in parallel.
- 2026-11-21 · nathann · **PROPOSED**: scenario engine based on slots and variation operators, including legitimate twins, a held-out combination grid for the transfer test, and a "shortcut breaker". · Many varied scenarios from a few human-written seeds; see docs/scenario-engine.md.
- 2026-11-21 · nathann · **PROPOSED**: third category `misaligned_incentive` and a Switzerland pack (health insurance cold calls and broker commissions, LPP/3a "advice"). · Very common Swiss harm, legal or grey area; same four steps, checked against neutral official sources. Timely: basic insurance switch deadline is 30 November.
- 2026-11-21 · nathann · **AGREED**: multi-language (team covers fr, en, hi, te, ur, de, it, es, ca), with scams adapted per country; dark theme plus large text/high contrast. · Inclusion (IMC); team has native speakers for review.
- 2026-11-21 · nathann · **PROPOSED**: "what to do now" resource cards chosen by ask × country; every link human-verified before shipping. · Panicking users need one next step, not a list of links.
- 2026-11-21 · ferran (via nathann) · **PROPOSED**: AI & sensitive data pack (pasting patient/beneficiary data into chatbots, fake "AI helper" apps, shared chat links) with a new category `data_exposure`. · Real and growing risk for NGOs and carers; and the four steps still work with no attacker, which is the furthest transfer test.
