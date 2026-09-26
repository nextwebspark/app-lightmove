# e2e — the end-to-end matrix

Drives a real API and a real browser against a disposable database. Not part of `npm test` or Maven:
it is plain shell plus a handful of ESM scripts. It runs nightly in GitHub Actions
([`.github/workflows/e2e.yml`](../.github/workflows/e2e.yml)), on demand from the Actions tab, and by
hand whenever you want to know whether the product still behaves — the auth surface it started as, and
now the mandate's own work: the brief, companies and executives, the spreadsheet in and out, and the
report, map and activity reads.

The Java suite proves units and slices. This proves the parts only a running system shows — that a
verification link clicked in a browser materialises a workspace, that CSRF is actually enforced, that
the rate limiter (disabled in the test profile) works at all.

It is **not** a merge gate. `deploy.yml` triggers on the `CI` workflow by name, so a red run here
blocks nothing. Deliberate: the run takes ~20 minutes and drives a browser, and a suite that can block a
merge on its first bad night gets switched off rather than fixed. Now that it is green, promoting it is
a matter of moving the job into `ci.yml`.

Findings from the last full run: [`results/auth-findings.md`](results/auth-findings.md).

```
run-all.sh                the whole matrix, all three boot variants, one exit code
stack/up.sh  down.sh      bring the stack up / tear it down
api/lib.sh                curl + assertion helpers, sourced by every script
api/fixtures.sh           builds the cast (cast.env) that 09-13 and spa/roles.mjs source
stack/seed-universe.sql   a synthetic 600-company Apollo universe, seeded by up.sh
api/01..15*.sh            the auth, RBAC, tenant and capture matrix, in dependency order
api/16-position-brief.sh  the brief: timeline, template draft, every step, publish, document
api/17-companies-...sh    companies and executives: triage doors, contacts ledger, custom columns
api/18-import-export.sh   the spreadsheet in (no model call) and the stage out, with its audit
api/19-reports-...sh      the report's chapters, researcher performance, map, activity, templates
api/20-workspace-...sh    several workspaces: found one in the app, switch, the tenant wall across it
spa/run.mjs               headless Chromium over the real SPA
spa/roles.mjs             the same, once per workspace role
spa/strategy.mjs          the Strategy screen over the company universe
spa/position.mjs          the brief's five steps, driven through the screen
spa/companies.mjs         the Companies grid and its two drawers
spa/import-export.mjs     the import dialog and the Export download
spa/reports.mjs           the Reports chapters, the team panel and the position drawer
spa/responsive.mjs        every screen at phone/tablet/desktop width, API stubbed
results/current/          per-run logs, cookie jars, cases.tsv  (gitignored)
spa/screenshots/          browser screenshots                    (gitignored)
```

## Running it

```bash
cd e2e
PROFILE=e2e ./run-all.sh       # everything; non-zero exit if any case failed
```

That is the whole thing: it brings the stack up, runs the scripts in dependency order, restarts the
API twice for the two scripts that need a different one (below), tears down, and prints the tally.

**Always `PROFILE=e2e`.** `stack/up.sh` defaults to `local` for historical reasons, and
`application-local.yml` is the one profile that does not raise `password-reset-requests-per-hour`.
Left at the production budget of 3/hour, the fourth reset request in the run is refused, so the link
never reaches the log, `token_for` hands back the previous one, and N20.2-3 and N30.1-4 fail against
a tree that is green on the profile CI uses. Six red cases, no bug.

To drive one script by hand:

```bash
cd e2e
PROFILE=e2e ./stack/up.sh      # postgres:16-alpine on :55432, API on :8080, Vite on :5173
bash api/01-happy-path.sh
node spa/run.mjs               # must be run from the e2e directory
KEEP_DB=1 ./stack/down.sh      # drop KEEP_DB to remove the database container too
```

Boot takes ~40 s: the Cloud SQL connector is bypassed but Flyway still applies the migrations against
an empty schema. `up.sh` waits for the API to answer and refuses to continue unless the email provider
is what the caller declared.

**The Apollo universe.** `api/14-strategy-company-search.sh` and `spa/strategy.mjs` read
`app_lm_apollo_companies`, which is ETL-owned and pulled with gcloud, so a disposable database has
none. `stack/up.sh` seeds [`stack/seed-universe.sql`](stack/seed-universe.sql) — 600 synthetic
companies shaped like the market (the eight Location countries, every headcount and revenue band,
V1 industry labels, keywords, names carrying `%` and `_`) — right after Flyway and before any script
runs, because the API caches the facet counts on first read. Every expected value those scripts
assert is read back from the table, so synthetic rows prove the same things real ones do. The seed is
a no-op on a table already loaded, and `SEED_UNIVERSE=0` skips it. Against the real universe:

```bash
npm run dev:db:apollo                                       # once, needs gcloud
npm run dev                                                 # api + web + postgres on :55433
cd e2e
PG_URL=postgresql://lm_app:lm@localhost:55433/lightmove bash api/14-strategy-company-search.sh
PG_URL=postgresql://lm_app:lm@localhost:55433/lightmove node spa/strategy.mjs
```

With fewer than 100 rows those two scripts skip themselves and exit 0. `PG_URL` defaults to the
e2e stack's **:55432**, not the dev database's :55433 — left at the default
against `npm run dev` these read the wrong database and find no universe.

**`PROFILE`** picks the Spring profile, defaulting to `local` — your own datasource password and OAuth
client. CI sets `PROFILE=e2e`, which is
[`application-e2e.yml`](../apps/api/src/main/resources/application-e2e.yml): committed, secret-free,
and the only way this runs anywhere but a laptop, since `application-local.yml` is gitignored. Booting
`local` on a runner silently inherits `application.yml`'s production defaults — a Secure/Strict refresh
cookie no browser keeps over plain http, and signup capped at five an hour.

## Five things that will bite you

**AI is off here, on purpose.** A runner holds no GCP credentials, and building the Vertex client
without them killed the API's boot on every nightly run from #25 to #49 — four weeks in which no case
ran at all. `application-e2e.yml` therefore sets `spring.ai.model.chat` and `.embedding.text` to
`none` and `lightmove.llm.enabled: false`, which registers a model that fails every call
(`DisabledLlmConfig`): each AI feature then takes the path it takes when Vertex is unreachable — the
import's header matcher, the brief's heuristic reader, an empty reading, `ASSISTANT_UNAVAILABLE`.
**No script here calls an AI route** (`/ai-enrich`, `/ai-assessment`, `/assistant`,
`/position/document/extract/*`, `/llm/*`), and no SPA journey presses an AI button or attaches a
document, which reads it. What a model *says* is the AI evaluation suite's to judge, not this one's.
The one AI-adjacent case left is 18's unknown import header, which proves the mapping falls back
rather than failing when the model cannot be reached.

**JWT signing keys.** `JwtConfig` lets the API generate its own keypair only on `local`, `dev` and
`test` — `e2e` is deliberately not one of them, because a profile that mints its own signing key is one
that could be started in production. So on a runner, where `apps/api/.keys/` does not exist, the API
refuses to boot at all. `up.sh` mints a disposable pair into `results/current/keys/` and points
`JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION` at it, once per run and reused across the three
legs — a fresh pair per leg would invalidate the previous leg's access tokens. This is why the first
nightly run reported 466 failures: the API never started, and every case answered `000`.

**Email.** `application-local.yml` pins `provider: resend` with a live API key, so an unguarded local
signup mails a real person. `up.sh` forces `LIGHTMOVE_EMAIL_PROVIDER=log` and aborts if the startup
banner does not confirm it. `EMAIL_PROVIDER` does **not** work — the local file hardcodes the value,
so only the property's own name outranks it.

**Environment overrides.** Same trap, wider. Any `${FOO:default}` placeholder in `application.yml` is
dead if a profile file sets that property literally. The rate-limit suite silently measured nothing
until the variables were switched from `AUTH_LOGIN_ATTEMPTS_PER_MINUTE` to
`LIGHTMOVE_AUTH_RATE_LIMIT_LOGIN_ATTEMPTS_PER_MINUTE`.

**Test addresses need real MX records.** The validator resolves the domain, so `@example.invalid`
style addresses behave differently from real ones. `lib.sh` mints
`lm-e2e-<tag>-<timestamp>@nextwebspark.com`, all lower-case — the API normalises addresses, so an
upper-case character would make every later SQL lookup and log grep miss.

## Variants some scripts need

`run-all.sh` sets all of these up itself; they are here for driving a script by hand.

`07-rate-limits.sh` expects tightened budgets, and the buckets are in-memory so a restart resets them:

```bash
EXTRA_ENV="LIGHTMOVE_AUTH_RATE_LIMIT_LOGIN_ATTEMPTS_PER_MINUTE=3 \
           LIGHTMOVE_AUTH_RATE_LIMIT_SIGNUP_ATTEMPTS_PER_HOUR=5 \
           LIGHTMOVE_AUTH_RATE_LIMIT_VERIFICATION_RESENDS_PER_HOUR=2 \
           LIGHTMOVE_AUTH_RATE_LIMIT_PASSWORD_RESET_REQUESTS_PER_HOUR=2" \
  KEEP_DB=1 ./stack/up.sh
```

`08-email-outage.sh` needs the opposite of every other script: a sender that **fails**. `up.sh` takes
`EXPECT_PROVIDER=blackhole` for it, and refuses to boot unless the base URL really is loopback — a
bogus API key alone still puts the request on the wire to Resend.

```bash
EXPECT_PROVIDER=blackhole \
  EXTRA_ENV="LIGHTMOVE_EMAIL_RESEND_API_KEY=re_e2e_bogus_key_not_a_real_credential \
             LIGHTMOVE_EMAIL_RESEND_BASE_URL=http://127.0.0.1:9" \
  KEEP_DB=1 ./stack/up.sh
```

Password reset is limited to 3/hour by default and `application-local.yml` does **not** raise it, so on
the `local` profile `04-tokens-verification.sh` needs
`LIGHTMOVE_AUTH_RATE_LIMIT_PASSWORD_RESET_REQUESTS_PER_HOUR=100`. `application-e2e.yml` raises it, so
`PROFILE=e2e` does not.

To check the domain blocklist, boot with `EMAIL_BLOCK_PUBLIC_DOMAINS=true` (that one is a live
placeholder — neither profile file overrides it, and neither may, or the variant stops working).

Two scripts have ordering rules `run-all.sh` encodes and a hand-run must respect:
`10-role-invariants.sh` is **not idempotent** and its last-admin cases only mean anything against a
workspace with exactly one admin, so re-run `api/fixtures.sh` immediately before it;
`12-tenant-isolation.sh` deletes a workspace and goes last.

**A local Chromium.** Playwright downloads the browser its version pins; a sandbox that ships its own
build can pass it as `CHROMIUM_PATH=/path/to/chromium` to every `spa/*.mjs`.

## Known failures

None. The suite is green.

The revival after the four dead weeks found only harness drift, no product bug: `PATCH /workspace`
gained a required `apolloAccountId` (09 R3.2, roles S3.3), 05 N25.2 probed a route that never existed
and passed on its 404, and `strategy.mjs` still assumed 25-row pages, fixed cell indexes, a per-row
"+" and a column picker it clicked past. `responsive.mjs`, left out of `run-all.sh`, had drifted with
nobody watching; it is in the matrix now.

The two the previous version of this file called open bugs are fixed: an unsupported `Content-Type`
returns 415 (N6.5), and a space-padded address is trimmed rather than rejected (N5.1, N3.4).

Worth knowing about the last two that were fixed to get here, because both were invisible failures
rather than wrong answers:

**The log format is load-bearing.** `link_for` finds an emailed link by reading the `To:` line and the
link as separate *lines*. `logback-spring.xml` hands JSON to every profile except `local`, `test` and
`e2e`, and JSON collapses the whole printed box into one escaped string — so the token lookup returns
nothing, verification 400s, and 335 cases fail with one cause. `up.sh` now refuses to continue if the
API is logging JSON.

**Global counts are not assertions.** `N43.8` counted every row in `app_lm_invitation`, which is only
meaningful against a virgin database. `run-all.sh` keeps the container between legs, so the invitations
the earlier legs legitimately created read as a failure. Scope a count to its own actor, the way every
other query here does.

## Writing a case

`check`, `check_status`, `check_code` and `check_contains` record pass/fail and keep going, so one
broken expectation does not hide the twenty behind it. `note` records an observation that is not a
judgement — use it where the interesting thing is *what* happened, not whether it matched.

```bash
post_json /auth/login "$(jq -nc --arg e "$EMAIL" --arg p "$PASSWORD" '{email:$e, password:$p}')"
check_code N9.1 "unknown address" 401 INVALID_CREDENTIALS
```

Always build JSON with `jq -nc --arg`. A double-quoted heredoc looks fine and breaks the moment an
address or a workspace name contains a shell metacharacter.

Useful helpers in `lib.sh`: `token_for <email> <verify|reset-password|accept-invite>` pulls a token
out of the API log for one recipient; `csrf_value <jar>` primes and returns the double-submit token
(use it whenever cookies are passed inline with `-b`, since that replaces the jar); `sql` / `sql_run`
reach the database for state no endpoint can set — a suspended status, an expired token, a null
password hash.
