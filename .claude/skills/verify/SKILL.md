---
name: verify
description: Build, run and drive LightMove locally to verify a change end-to-end (API + SPA).
---

# Verifying LightMove locally

## Launch

- `npm run dev` — Docker Postgres on :55433, API on :8080, Vite on :5173 (proxies `/api` to :8080).
  The database is local and private, so a `V*` migration in the tree applies only to you. Boot takes
  ~20s. `/actuator/health` is NOT exposed; probe any API route for a non-refused connection instead.
- `npm run dev:db:reset` first when you want a virgin schema (re-runs V1 onwards).
- Halves separately: `npm run dev:db && npm run dev:api`, and `npm run dev:web`.
- **`npm run dev:cloud` is the shared Cloud SQL database.** Only use it when the shared data is the
  point, and then `LIGHTMOVE_EMAIL_PROVIDER=log` is on you — `application-local.yml` pins the REAL
  Resend key, so a signup sends real email. `ops/dev/api.sh` sets it for you; `dev:cloud` does not.
- Email always prints to the API console under `npm run dev`; that is where the verification and
  invitation links come from.

## Driving the API with curl

CSRF is double-submit: `GET /api/v1/auth/csrf` with a cookie jar, then echo the `XSRF-TOKEN` cookie
value as `X-XSRF-TOKEN` on every mutating request. Flow: signup → grep the API log for
`auth/verify?token=` → `POST /api/v1/auth/verify?token=…` → login (returns `accessToken`) →
`POST /api/v1/onboarding/workspace` → **re-login** (the first token lacks workspace claims) → then
clients/projects/etc. with `Authorization: Bearer`.

## Driving the SPA

Playwright is already in the repo's `node_modules` (import it by absolute path in a standalone
script, ESM won't resolve it from outside the repo). Login page submit button is labeled
**"Continue"**, email/password fields are label-associated. Screenshot at 1440×1000.

## Gotchas

- Login rate limits are raised in the local profile; signups per hour are still capped at 100.
- Use throwaway emails on a real-MX domain (e.g. `verify-<ts>@nextwebspark.com`) — the validator
  checks deliverability.
