# LightMove

Multi-tenant SaaS for executive search and talent mapping.

A **Workspace** is the tenant. It holds **Members** (`ADMIN` / `CONSULTANT` / `RESEARCHER`) who run
**Projects** — search mandates for client companies. Auth is built; Projects are designed in
`claude-design/` and not yet modelled.

## Run it locally

You need Java 21, Node ≥ 20, and your own Postgres server running locally. No gcloud, no GCP role.

```bash
npm install
cp apps/api/src/main/resources/application-local.yml{.example,}   # fill in your Postgres credentials
npm run dev            # the SPA — Vite on :5173, proxies /api to :8080
```

The API is started separately, against whatever Postgres `application-local.yml` points at:

```bash
cd apps/api && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

or, from an IDE, a run config with active profile `local`. Flyway applies every migration to that
database on boot — it's **yours**, so nothing you do locally reaches anyone else.

The one thing it cannot conjure is the company universe the Strategy screens search over. That is a
separate one-time step — see **The company universe** below.

| | |
|---|---|
| Web | http://localhost:5173 |
| API | http://localhost:8080 |
| Actuator | http://localhost:9090 — separate port, loopback only |
| Database | whatever `application-local.yml`'s `spring.datasource` block names |

### The company universe

`app_lm_apollo_companies` holds 71,822 GCC companies. An external pipeline owns it, nothing in this
repo can regenerate it, and it is far too large for git — so migrations create the table empty and
every Strategy screen renders blank until you fill it, against your own local Postgres.

**Someone hands you the file.** No GCP of any kind — the restore is a local `pg_restore` against your
own database:

```bash
pg_restore -h localhost -p 5432 -U <user> -d <database> --no-owner ~/Downloads/apollo.dump
```

Use `--data-only` instead if the table already exists empty (Flyway's `V23` already ran). There is no
scripted restore step any more — run this by hand whenever you (re)create your local database.

**Or pull it from Cloud SQL**, if you have the access described below plus `brew install
cloud-sql-proxy libpq`, streaming straight into your own database over `\copy` rather than through an
intermediate file — see the column-order caveat in the `db-ops` skill before writing your own version
of this.

### Running against the shared Cloud SQL database

Only when you actually need the shared data — a new migration should be proven on your own local
database first, because Flyway runs at boot and applies it to everyone the moment the API starts.

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project hak-talent-mapping

cp apps/api/src/main/resources/application-local.yml{.example,}   # fill in the lm_app password

npm run dev:cloud
```

This one needs `roles/cloudsql.client` on the `hak-talent-mapping` project — ask an admin. The database
already exists; you don't create it and you don't run a proxy. Note that `application-local.yml` may pin
`provider: resend` with a live key — nothing overrides it automatically on either path any more, so a
signup against the shared database sends real email unless you change that file or set
`LIGHTMOVE_EMAIL_PROVIDER=log` yourself. See **Precedence** below.

**OAuth sign-in** needs `application-local.yml` either way: it is where the Google and LinkedIn client
credentials live, and it is still read on the `local` profile that both commands use. Without the file
you get password auth only, and `GET /api/v1/auth/providers` reports the buttons as unavailable.

**`app_lm_companies` is empty locally.** The company universe is copied from the `brightdata` warehouse
by `ops/cloudsql/sync-companies.sh`, which only targets Cloud SQL.

Sign up with a **work email** — gmail and friends are refused, because the email domain is what tells us
which firm someone works at. **No email provider is needed:** `LogEmailSender` is the default and prints
the verification and invitation links straight to the API console.

**How it connects.** `application.yml` declares one datasource: a Cloud SQL **Java connector** URL —
no host, no IP allowlist, no proxy process. Your Google identity authorises the *connection*
(`roles/cloudsql.client`); the database login itself is `lm_app` and its password. `cloud-sql-proxy`
appears in exactly one place in this repo — `psql.sh` — and running the app never needs it.

Plain local dev steps around all of that instead: `application-local.yml`'s datasource carries a host,
which never reaches the socket factory — Spring just never activates the `cloud` profile that supplies
it. `e2e/stack/up.sh` uses the same trick the other way, via an environment variable that outranks
every profile file.

**The `lm_app` password** is printed once, when the database is created, and cannot be recovered. Ask
whoever set the environment up, or reset it:

```bash
gcloud sql users set-password lm_app --instance=bright-gcc --prompt-for-password
```

**JWT keys are automatic.** On first boot the API generates an RSA keypair into `apps/api/.keys/`
(gitignored — never commit it). Only the `local`, `dev` and `test` profiles may do that: started with no
profile, as production is, the app refuses to boot rather than sign tokens with a throwaway key that
would change on every restart. Production points `JWT_PRIVATE_KEY_LOCATION` at real keys.

## Tests

```bash
npm test                      # both suites
cd apps/api && ./mvnw test    # 35 tests — needs Docker (Testcontainers runs a real Postgres 16)
cd apps/web && npx vitest     # 22 tests
```

Use **`./mvnw clean test`** if a result surprises you: an IDE Java language server writes its own output
into `target/classes`, and a stale broken class can survive an incremental build and surface as a runtime
`java.lang.Error: Unresolved compilation problem` inside a 500.

## Configuration

Secrets live in `apps/api/src/main/resources/application-local.yml`, which is gitignored. Everything below
can also be set as an environment variable.

| | |
|---|---|
| `LIGHTMOVE_EMAIL_PROVIDER` | `log` (default) or `resend` |
| `LIGHTMOVE_EMAIL_RESEND_API_KEY` | Resend key |
| `LIGHTMOVE_WEB_TRUSTED_PROXY_COUNT` | **0** by default: `X-Forwarded-For` is ignored and the socket peer is used. Set it to the number of reverse proxies actually in front of the app, or the rate limiter's per-IP budget becomes free to bypass |
| `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION` | Required in production. See above |
| `MANAGEMENT_PORT` | Actuator's port. `9090` locally; **`8080`** on Cloud Run, which routes only one port into a container |
| `FLYWAY_ENABLED` | `true` locally and in tests. **`false`** in the deployed service — migrations are a deploy step, not a boot step |
| `DB_POOL_MAX` | Hikari pool size, **per instance**. `bright-gcc` is a `db-f1-micro` (~25 connections) and the `brightdata` ETL shares it |

**Precedence.** An env var like `EMAIL_PROVIDER` only feeds a `${...}` placeholder in `application.yml`.
If `application-local.yml` sets the property *literally*, the placeholder is never consulted and the env
var silently does nothing. Use the full property path — `LIGHTMOVE_EMAIL_PROVIDER` — which outranks both
files.

This is not specific to email: **any** `${FOO:default}` in `application.yml` is dead on the `local`
profile if a profile file hardcodes that property, and nothing warns you. The rate-limit budgets are the
other one that bites — `application-local.yml` pins them to 100, so `AUTH_LOGIN_ATTEMPTS_PER_MINUTE` and
its siblings do nothing and the server looks unthrottled. Override with the property's own name
(`LIGHTMOVE_AUTH_RATE_LIMIT_LOGIN_ATTEMPTS_PER_MINUTE`) or the whole test run measures an unlimited API.

**Real email** needs Resend *and a verified domain*. Until a domain is verified, Resend delivers only to
the address your Resend account is registered under and 403s everything else — whatever `from` you set
(`onboarding@resend.dev` does not lift this). Verify at [resend.com/domains](https://resend.com/domains),
set `from-address` on that domain, then `provider: resend` plus the key. A failed send does not roll back
the signup, but the verification token is stored **hashed**, so a link that fails to send is
unrecoverable — use `/auth/verify/resend`.

**Google sign-in** is not configured: `GET /api/v1/auth/providers` returns `{"google": false}` and the SPA
hides the button, because a button leading to a 404 is worse than no button. To enable it, create an OAuth
client with redirect URI `http://localhost:8080/login/oauth2/code/google` and put the id and secret in
`application-local.yml`.

**Actuator** listens on its own port, bound to loopback; on the app port everything past `health` and
`info` is denied. It used to sit on :8080 behind `hasRole("ADMIN")` — which is the *tenant* role every
workspace creator is granted, so any customer could read our metrics. A workspace role must never double
as a system-admin role.

> Cloud Run routes exactly one port into a container, so there Actuator has to share :8080
> (`MANAGEMENT_PORT=8080`) or there is no health endpoint at all. The port-based fence then stands
> itself down and the matcher-based one in `SecurityConfig` is what keeps `/actuator/prometheus`
> off the internet. `SpaSecurityTest` asserts that, and the test profile boots the deployed
> configuration so the assertion means something.
>
> The loopback bind (`management.server.address`) can only be set under the `local` profile. Spring
> Boot refuses to start when it is configured while Actuator shares the app port — and it checks
> whether the *property exists*, not what it holds, so no value can opt out of that.

## Troubleshooting

**Every request 400s, including a body-less `GET`, and nothing appears in the API log.**
Cookies on `localhost` are shared across every port and every project you have ever run there. Once the
`Cookie` header exceeds **8 KB**, Tomcat rejects the request at the connector — before Spring, before
routing, before anything that logs. Clear the `localhost` cookies (DevTools → Application → Cookies).

Rejections like that appear *only* in the Tomcat access log, which is off by default:

```bash
cd apps/api
SERVER_TOMCAT_ACCESSLOG_ENABLED=true \
SERVER_TOMCAT_ACCESSLOG_DIRECTORY=/dev \
SERVER_TOMCAT_ACCESSLOG_PREFIX=stdout \
SERVER_TOMCAT_ACCESSLOG_SUFFIX= \
SERVER_TOMCAT_ACCESSLOG_ROTATE=false \
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**A 403 from `/onboarding/*` or the workspace screen.** The account is not verified. Workspace data
requires a verified address — the email domain is our only evidence that someone works at a firm, and an
unverified address is an unproven claim. Click the link in the API console.

**"Your session was ended for security reasons" after opening a second tab.** Refresh-token theft
detection firing. Fixed — but if you see it, sign in again; the old family is revoked by design.

## Reading the database (optional)

This is the **Cloud SQL** database. For your own local one, use `psql` directly — whatever client you
already have. Not needed to run the app either way — for querying the shared tables by hand:

```bash
./ops/cloudsql/psql.sh                                     # interactive shell
./ops/cloudsql/psql.sh -c "SELECT email, status FROM app_lm_user"
```

Needs `cloud-sql-proxy` and `psql` (`brew install cloud-sql-proxy libpq`; libpq is keg-only, so
`export PATH="/opt/homebrew/opt/libpq/bin:$PATH"`). The script runs a proxy with `--auto-iam-authn`,
connects as your Google identity, and tears it down on exit. **Read-only** — a human poking around
interactively should be able to look at anything and change nothing; writes go through the application,
where they are validated and audited.

> `gcloud sql connect` does **not** work for this. It prompts for a password, and an IAM principal hasn't
> got one — it authenticates with an OAuth token. That is the whole reason `psql.sh` exists.

This needs a *database* role, which is a separate thing from the `roles/cloudsql.client` you already have.
Migration `V2` grants `SELECT` to `$DB_IAM_USER`, but only on the run that first applies the schema, and
Flyway never re-runs a migration — so setting `DB_IAM_USER` on an existing database does nothing. To add
someone now, an admin registers them and grants by hand:

```bash
gcloud sql users create you@example.com --instance=bright-gcc --type=cloud_iam_user
# then, as an owner: GRANT USAGE ON SCHEMA public + GRANT SELECT ON ALL TABLES ... TO "you@example.com";
```

## Bootstrapping a new environment

Once per Cloud SQL instance — not something a developer joining the project runs.

```bash
DB_IAM_USER=you@example.com ./ops/cloudsql/create-database.sh   # database, app user, IAM principals
DB_IAM_USER=you@example.com npm run dev:api:cloud               # Flyway applies the schema; V2 grants you SELECT
```

`create-database.sh` is idempotent — on an existing database it says so and changes nothing.
`ops/cloudsql/harden.sql` locks down the company reference table, and `ops/cloudsql/sync-companies.sh`
fills it from the `brightdata` warehouse.

## Deploying

**One Cloud Run service, serving both halves.** The SPA is built into the jar and served by Spring from
`static/`; the API is the same process. Push to `main`, CI goes green, `deploy.yml` ships it.

That is not a packaging shortcut — it is the auth model. The refresh cookie is `SameSite=Strict` and
**host-only**, and the SPA calls a relative `/api/v1`, so the browser only ever returns that cookie to the
host that served the page. **The SPA and the API must be one origin.** One container makes them one by
construction. (It is the same reason the Vite dev server proxies `/api` instead of pointing at :8080 —
dev already behaves like production.)

This rules out the obvious splits, which is worth knowing before someone re-proposes them:

- **Firebase Hosting rewriting to Cloud Run** — Hosting strips *every* incoming cookie except `__session`,
  so it can CDN-cache dynamic responses. Two cookies have to reach Spring (`lm_refresh` and `XSRF-TOKEN`)
  and only one can be called `__session`. There is no rename that saves it.
- **A CDN proxying `/api` from another origin** — the refresh token **rotates on every use**, so any hop
  that drops a `Set-Cookie` on the way back trips theft detection and revokes the whole family.

### First time

```bash
./ops/gcp/bootstrap.sh              # service accounts, WIF, Artifact Registry, empty secrets — idempotent
./ops/cloudsql/create-migrate-role.sh
```

Then set the GitHub variables and the secret *values* that `bootstrap.sh` prints. The JWT keypair is
generated once, **on your machine, never in CI** — a pipeline that mints a signing key is a pipeline that
logs one. Losing it signs everyone out (access tokens live 15 minutes, refresh tokens are in the database,
so no data is lost); leaking it lets anyone mint a token for any user.

### What ships, and what does not

| | |
|---|---|
| `min-instances 0` | $0 idle. The price is a cold start — measured at **~5s**, not the 15s a Spring Boot app usually costs |
| `max-instances 2` | **Not** the default of 100. 2 × `DB_POOL_MAX=5` = 10 connections, under the `db-f1-micro`'s ~25 — which the `brightdata` ETL also draws on. Raise this and you can take down the neighbours |
| Image tag | The **git SHA**, never `latest`. You must be able to say which commit is serving, and roll back to a specific one |
| Flyway | **Not in the container.** It runs in the deploy pipeline as `lm_migrate`, so a bad migration fails the deploy and the old revision keeps serving. `lm_app` holds no DDL, which is what finally lets `harden.sql` be applied |
| Email | `EMAIL_PROVIDER=log` until a domain is verified with Resend. The verification link goes to **Cloud Logging**, and you complete a signup by reading it out |

### Verify a deploy by hand

The pipeline smoke-tests health, the SPA, and that Actuator and the API are still shut. The one thing it
cannot check for you:

**`TRUSTED_PROXY_COUNT` must be measured, not guessed.** Behind Cloud Run, Tomcat's `RemoteIpValve`
(`forward-headers-strategy: native`) may already resolve the real client, making **0** correct rather
than 1. Guess it too high and `X-Forwarded-For` becomes attacker-controlled: the rate limiter's per-IP
budget is free to bypass and the audit log records fiction. It defaults to `0`, which fails *closed*
(everyone shares one bucket) rather than open. Log the header against a request from a known IP, then
set the variable and assert it with a test.

## Layout

| | |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven) |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `Dockerfile` | Multi-stage: builds the SPA, copies it into the jar's `static/`, one image |
| `claude-design/` | HTML mockups — **the source of truth for all UI** |
| `ops/cloudsql/` | Database bootstrap, hardening, the `lm_migrate` role, and `psql.sh` |
| `ops/gcp/` | `bootstrap.sh` — everything on GCP the first deploy needs, idempotent |
| `.github/workflows/` | `ci.yml` gates; `deploy.yml` builds, migrates, deploys, smoke-tests |
| `docs/` | [Login & authentication](docs/login-and-authentication.md) — every signup/login/invite scenario, end to end |

[CLAUDE.md](CLAUDE.md) has the rules that shape the code, and the traps this codebase has already fallen
into.
