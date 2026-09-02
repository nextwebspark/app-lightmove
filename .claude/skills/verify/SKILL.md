---
name: verify
description: Build, run and drive LightMove locally to verify a change end-to-end (API + SPA).
---

# Verifying LightMove locally

## Launch

- `npm run dev` starts only the SPA, on :5173 (proxies `/api` to :8080). The API is started
  separately: `cd apps/api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local`, or an
  IntelliJ run config with active profile `local`. Both read
  `apps/api/src/main/resources/application-local.yml` (gitignored) for the datasource — it points at
  the developer's own local Postgres, so a `V*` migration in the tree applies only to them.
  `/actuator/health` is NOT exposed; probe any API route for a non-refused connection instead.
- **`npm run dev:cloud` is the shared Cloud SQL database.** Only use it when the shared data is the
  point, and then `LIGHTMOVE_EMAIL_PROVIDER=log` is on you — `application-local.yml` may pin a REAL
  Resend key, so a signup could send real email.
- Email prints to the API console whenever `lightmove.email.provider` resolves to `log` (the
  default); that is where the verification and invitation links come from.

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
