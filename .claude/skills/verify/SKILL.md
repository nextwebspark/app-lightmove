---
name: verify
description: Build, run and drive LightMove locally to verify a change end-to-end (API + SPA).
---

# Verifying LightMove locally

## Launch

- API: `cd apps/api && LIGHTMOVE_EMAIL_PROVIDER=log ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`
  — the env override matters: `application-local.yml` points at the REAL Resend key, so without it a
  signup sends real email. With `log`, verification links print to the API console.
- Web: `npm run dev:web` (Vite on :5173, proxies `/api` to :8080).
- Boot takes ~30s (Cloud SQL connector + Flyway at boot — a new `V*` migration applies to the shared
  dev database immediately). `/actuator/health` is NOT exposed; probe any API route for a non-refused
  connection instead.

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
