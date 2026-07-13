# LightMove

Multi-tenant SaaS for executive search and talent mapping.

A **Workspace** is the tenant. It holds **Members** (`ADMIN` / `CONSULTANT` / `RESEARCHER`) who run
**Projects** — search mandates for client companies. Auth is built; Projects are designed in
`claude-design/` and not yet modelled.

## Prerequisites

| | | |
|---|---|---|
| **Java 21** | `brew install openjdk@21` | Maven comes with the wrapper (`./mvnw`) |
| **Node ≥ 20** | `brew install node` | |
| **Docker** | | Only for `./mvnw test` — Testcontainers starts a real Postgres 16 |
| **gcloud** | `brew install --cask google-cloud-sdk` | |
| **cloud-sql-proxy** | `brew install cloud-sql-proxy` | Only for `psql.sh` |
| **psql** | `brew install libpq` | Keg-only — see the PATH note below |

Homebrew does not link `psql` onto your PATH. Add it once:

```bash
echo 'export PATH="/opt/homebrew/opt/libpq/bin:$PATH"' >> ~/.zshrc
```

## Quick start

```bash
# 1. Database — creates it, the app user, and grants you IAM read access
DB_IAM_USER=you@example.com ./ops/cloudsql/create-database.sh

# 2. Local config — fill in the DB password the script printed
cp apps/api/src/main/resources/application-local.yml{.example,}

# 3. Cloud SQL authenticates as *you*, not with a key file
gcloud auth application-default login

# 4. Run
npm install
DB_IAM_USER=you@example.com npm run dev
```

| | |
|---|---|
| Web | http://localhost:5173 |
| API | http://localhost:8080 |
| Actuator | http://localhost:9090 — **separate port, loopback only** (see below) |

Sign up at http://localhost:5173/signup with a **work email** — gmail and friends are refused, because
the email domain is what tells us which firm someone works at.

**No email provider is needed.** `LogEmailSender` is the default and prints verification and invitation
links straight to the API console, so the entire flow — including invites — is testable on a fresh
clone with no accounts anywhere.

## Talking to the database

```bash
./ops/cloudsql/psql.sh                                     # interactive shell
./ops/cloudsql/psql.sh -c "SELECT email, status FROM app_lm_user"
```

It starts a `cloud-sql-proxy` with `--auto-iam-authn`, connects as your Google identity, and tears the
proxy down on exit. **Read-only** — migration `V2` grants your IAM principal `SELECT` and nothing else.
Poking around interactively should never be able to change anything; writes go through the application,
where they are validated and audited.

> `gcloud sql connect` does **not** work for this. It prompts for a password, and an IAM principal
> hasn't got one — it authenticates with an OAuth token. That is the whole reason `psql.sh` exists.

## Tests

```bash
npm test                      # both suites
cd apps/api && ./mvnw test    # 35 tests — needs Docker
cd apps/web && npx vitest     # 22 tests
```

Use **`./mvnw clean test`** if a result surprises you: an IDE Java language server writes its own output
into `target/classes`, and a stale broken class can survive an incremental build and surface as a
runtime `java.lang.Error: Unresolved compilation problem` inside a 500.

## Configuration

Secrets live in `apps/api/src/main/resources/application-local.yml`, which is gitignored. Everything
below can also be set as an environment variable.

| | |
|---|---|
| `DB_IAM_USER` | Your Google account. Flyway `V2` grants it read access |
| `LIGHTMOVE_EMAIL_PROVIDER` | `log` (default) or `resend` |
| `LIGHTMOVE_EMAIL_RESEND_API_KEY` | Resend key |
| `LIGHTMOVE_WEB_TRUSTED_PROXY_COUNT` | **0** by default: `X-Forwarded-For` is ignored entirely and the socket peer is used. Set to the number of reverse proxies in front of the app, or the rate limiter's per-IP budget becomes free to bypass |
| `MANAGEMENT_PORT` / `MANAGEMENT_ADDRESS` | Actuator's port (9090) and bind address (127.0.0.1) |

**Note on precedence:** an env var like `EMAIL_PROVIDER` only feeds a `${...}` placeholder in
`application.yml`. If `application-local.yml` sets the property *literally*, the placeholder is never
consulted and the env var silently does nothing. Use the full property path —
`LIGHTMOVE_EMAIL_PROVIDER` — which outranks both files.

### Real email

`LogEmailSender` is the default. To send actual mail you need **Resend** *and a verified domain*: until
one is verified, Resend delivers only to the address your Resend account is registered under and refuses
everything else with a 403 — whatever `from` address you use. `onboarding@resend.dev` is a sandbox
sender and does not lift that restriction.

1. Verify your domain at [resend.com/domains](https://resend.com/domains) and add its DNS records.
2. Set `from-address` to an address **on that domain**.
3. `provider: resend`, plus the API key.

A failed send does not roll back the signup — email is best-effort, the account is not. But note the
verification token is stored **hashed**, so a link that fails to send is unrecoverable: use
`/auth/verify/resend`.

### Google OAuth

Not configured. `GET /api/v1/auth/providers` returns `{"google": false}` and the SPA hides the button —
a button leading to a 404 is worse than no button. To enable it, create an OAuth client in the GCP
console with redirect URI `http://localhost:8080/login/oauth2/code/google` and put the id and secret in
`application-local.yml`.

### Actuator

Actuator listens on **its own port (9090), bound to loopback**, and `/actuator/**` beyond `health` and
`info` is denied on the app port. It used to sit on :8080 behind `hasRole("ADMIN")` — which is the
*tenant* role every workspace creator is granted, so any customer could read our metrics. A workspace
role must never double as a system-admin role.

## Troubleshooting

**Every request 400s, including a body-less `GET`, and nothing appears in the API log.**
Cookies on `localhost` are shared across every port and every project you have ever run there. Once the
`Cookie` header exceeds **8 KB**, Tomcat rejects the request at the connector — before Spring, before
routing, before anything that logs. Clear the `localhost` cookies (DevTools → Application → Cookies).

To see rejections like that at all, turn on the Tomcat access log — it is the only place they appear:

```bash
SERVER_TOMCAT_ACCESSLOG_ENABLED=true \
SERVER_TOMCAT_ACCESSLOG_DIRECTORY=/dev \
SERVER_TOMCAT_ACCESSLOG_PREFIX=stdout \
SERVER_TOMCAT_ACCESSLOG_SUFFIX= \
SERVER_TOMCAT_ACCESSLOG_ROTATE=false \
npm run dev
```

**A 403 from `/onboarding/*` or the workspace screen.**
The account is not verified. Onboarding *writes* and all workspace data require a verified address — the
email domain is our only evidence that someone works at a firm, and an unverified address is an unproven
claim. Click the link in the API console.

**"Your session was ended for security reasons" after opening a second tab.**
Fixed — but if you see it, this is refresh-token theft detection firing. Sign in again; the old family is
revoked by design.

## Layout

| | |
|---|---|
| `apps/api` | Spring Boot 4.1 (Java 21, Maven) |
| `apps/web` | React 19 SPA (Vite 8, TypeScript, Tailwind v4) |
| `claude-design/` | HTML mockups — **the source of truth for all UI** |
| `ops/cloudsql/` | Database bootstrap, hardening, and `psql.sh` |
| `docs/` | [Login & authentication](docs/login-and-authentication.md) — every signup/login/invite scenario, end to end |

[CLAUDE.md](CLAUDE.md) has the rules that shape the code, and the traps this codebase has already
fallen into.
