# The platform console

Epic #1187. One staff-only area of the SPA, `/platform/*`, where Uncava's own super admins change what used to
need a release: a sourcing limit, the text and model of an AI prompt. A change is live on every instance within
seconds, is recorded with who made it and why, and can be put back.

It sits **above every tenant and inside none**. No screen, Try it, eval case or count reads a workspace's people,
notes or documents, and every table it adds is a platform table with no `workspace_id`.

| Screen | Route | What it changes | Detail |
|---|---|---|---|
| Templates | `/platform/templates` | The shared position-template library | `docs/architecture/features.md` (Platform → Templates) |
| Variables | `/platform/variables` | Allowlisted application values, over their yml defaults | [Variables](#variables) |
| Prompts | `/platform/prompts` | Each AI prompt's text, model and generation settings, as versions | [Prompts](#prompts) |
| Evals | `/platform/evals` | Nothing that serves: datasets, runs and the check before a version goes live | [Evals](#evals) |

The call path every prompt goes through is `docs/llm-guardrails.md`. The second factor is
`docs/security/platform-two-factor.md`. Tables are in `docs/architecture/database.md` (V57, V234 to V243).

## Who gets in

- **The role is `SUPER_ADMIN`** (V57), granted only by `ops/cloudsql/grant-platform-role.sh <email>`, never by the
  application, and never carried in the JWT: every check re-reads the database.
- **Eight platform actions** hang off it: `TEMPLATE_LIBRARY_MANAGE`, `CREDIT_GRANT`, `SUBSCRIPTION_MANAGE`,
  `WORKSPACE_LIFECYCLE_MANAGE`, `PROMPT_MANAGE`, `PROMPT_PROMOTE`, `EVAL_RUN`, `PLATFORM_SETTINGS_MANAGE`.
  `RbacCatalogTest` keeps the enum and the seeded rows in step.
- **Everyone else is told 404, on every route.** A workspace admin's `WORKSPACE_MANAGE` opens none of it. Two
  guards make that true:
  - `PlatformRouteGate` answers 404 to a caller holding no platform action from the security filter chain
    (`SecurityConfig`, the rule ahead of every other), before a handler is looked up or a body read. Decided any
    later, a wrong method was answered 405, a bad body 400 and a bare `OPTIONS` an `Allow` header, each by a route
    that must not admit to existing. A caller with no session is told 401, as on every route. It costs a second
    read of the caller's platform actions on each platform request, beside the route's own guard.
  - Each route's own `@RequirePlatformPermission` or `@RequireRecentTwoFactor` then decides the action and the
    factor. `PlatformTwoFactorIntegrationTest.everyPlatformRouteIsGuarded` fails a route with neither;
    `PlatformAuthorizationIntegrationTest.noPlatformRouteAdmitsToExisting` calls every route, with every method,
    as a workspace admin, a member and a client.
- **The SPA is not the boundary.** The avatar menu shows Platform only to a user whose `/me` lists a platform
  action, and the `/platform` chunk is lazy, so tenant users never download it; the server refuses regardless.
- **Two-factor.** With `lightmove.platform.require-two-factor` on, a super admin without a factor is answered
  `PLATFORM_TWO_FACTOR_REQUIRED` and shown Set up two-factor. High-impact acts (a variable change or reset, a
  prompt put live or rolled back, a credit grant, an invoiced subscription) also need a code entered within the
  last ten minutes (`PLATFORM_STEP_UP_REQUIRED`, the Confirm it's you dialog).

## Variables

`platformsettings`, V241, V242. Screen: Platform → Variables (#1315).

- **An allowlist in code.** `ApplicationVariable` names each key with its label, unit and bounds. A secret, a
  security switch or a rollout flag can never be on it. Today: five Find executives limits
  (`max-companies-per-run`, `index-people-per-company`, `title-checks-per-company`, `parallelism`,
  `run-deadline`). Every one is a whole number.
- **No row means the yml value.** `app_lm_platform_setting` holds only overrides; reset deletes the row. If the
  table cannot be read, the yml value serves.
- **Readers go through `ApplicationVariables`** (`core/config`), never the settings record, or a change does
  nothing. A long job takes its limits once at the start (`SourcingRunLimits`), so a value lowered mid-run cannot
  fail a run already going.
- **Every change and reset is recorded** in `app_lm_platform_setting_change` with the value before and after, the
  note and who; append-only.
- **Adding a variable** is one `ApplicationVariable` entry plus moving its readers onto `ApplicationVariables`.

## Prompts

`core/llm` serves, `promptregistry` manages; V235, V236, V243. Screens: the list and one prompt (#1318).

- **Versions are immutable.** A save is version N+1. The `prompts/*.st` file in a release is recorded at boot as
  a `BUNDLED` version (`PromptRegistrySync`); a save from the console is `EDITED`.
- **One label, `production`, names the version that serves.** Put live moves it; roll back moves it to an earlier
  version. Both need `PROMPT_PROMOTE` and a fresh code, and every instance reloads within seconds
  (`core/reload`).
- **A version carries its model and settings.** The model comes from `AiModelCatalog` (provider and model id, in
  code, GA models only); temperature, thinking budget and max output tokens stay inside that model's bounds. Unset
  means the calling feature's own default. A model change is a version like any other.
- **What stays in code:** the answer schema, the answer format and the user message. The user message decides
  which data reaches the model, so editing a prompt cannot change that.
- **Try it** runs one version on a fictional input through the feature's own call path. It is not counted as a
  call and serves nothing.
- **Fallbacks.** Registry unreadable: the bundled file serves. A version's model retired or unable to answer: the
  configured model answers, and the list says Model unavailable.
- **`lightmove.ai.prompt-registry.enabled`.** Off, the bundled file serves and the registry is never read, so a
  version put live serves nothing and no call is counted.

### Usage and the worse-than-before warning

#1323. Counts only: no prompt and no answer is stored anywhere.

- `app_lm_platform_prompt_call_daily`: per version, model and UTC day, the calls, tokens and finish reasons, plus
  guard refusals, schema re-asks and answers that would not bind.
- `app_lm_platform_prompt_outcome_daily`: per version and day, the answers a person kept as written and those
  they changed. Counted for the outreach opener, a candidate's AI-inferred background values, and the four
  position extraction prompts, each field a reading filled once, when the brief is published (#1452).
- `GET /api/v1/platform/prompts/{id}/usage?days=` reads both; `decisionsCounted` says whether the prompt has a kept
  share at all (`PlatformPrompt`, held to the callers of `PromptOutcomes` by `PlatformPromptTest`). A Try it and an
  eval run are not counted.
- Each prompt's `health` compares the live version's first days live with the one it replaced over the same
  number of days before (30 at most; `daysLive` above `days` says the screen names "its first 30 days", not the
  time since it went live): `MORE_FAILURES` when the failure rate is up five points with 50 calls a
  side, `FEWER_ACCEPTED` when the kept share is down ten points with 20 decisions a side (`PromptHealthRules`).
- On screen: a prompt's **Usage** tab is the table by version and model over the last 7, 30 or 90 days; a warning is
  a pill on the list (which counts it as needing attention) and a note on the prompt's page.

## Evals

`prompteval`; V238 to V240. Screens: datasets, runs, a run's comparison, Label pairs (#1321, #1322).

- **Synthetic cases only.** Fictional people, notes and transcripts, each reviewed by a person before it counts,
  split `DEV` / `HOLDOUT`. Real golden sets stay offline (`docs/eval/*`).
- **A run** answers the same cases with a candidate version and the live one, through the feature's own call
  path, on the API's background worker, one run at a time.
- **The check before put live** scores on holdout: deterministic checks first, a calibrated judge for free text.
  A version that regressed, or has no run, can still go live with a written reason, which is audited.
- **A pack gates only once reviewed**, and a rubric counts only while the judge agrees with a person's labels on
  80% of 20 pairs.

## Nothing outside the database

Every piece of state the console adds is a Postgres table: versions, the label and its moves, overrides and their
history, datasets, runs, results, the daily counts, the step-up timestamp. Nothing is written to a bucket, Secret
Manager or BigQuery, and no screen reads its answer back from Cloud Logging or from a Vertex call label. What
stays in code or yml is configuration: the model catalogue, the variables allowlist, the gate tolerances, the
answer schemas and the two rollout flags.

Every save, reset, put live, roll back, Try it, import and run is a security audit event. A prompt event records
the id, version and note, never the text.

## Rollout

Two switches, shipped off, turned on per deployment as repository variables (Settings → Secrets and variables →
Actions → Variables), which `deploy.yml` passes to Cloud Run.

| Step | What | Check |
|---|---|---|
| 1 | Every super admin sets up two-factor in Settings → Security. `MFA_ENABLED=true` and the credential keyset are already required for that. | `SELECT u.email FROM app_lm_user_platform_role p JOIN app_lm_user u ON u.id = p.user_id WHERE NOT EXISTS (SELECT 1 FROM app_lm_user_mfa_factor f WHERE f.user_id = u.id AND f.enabled_at IS NOT NULL)` answers no rows. |
| 2 | Set `PLATFORM_REQUIRE_TWO_FACTOR=true`; deploy. Never with `AUTH_RATE_LIMIT_ENABLED=false`: the limiter is the only brake on guessing a code. | A super admin opens `/platform`; Put live asks for a code. |
| 3 | Set `AI_PROMPT_REGISTRY_ENABLED=true`; deploy. | Use an AI feature, then `SELECT prompt_id, version, model_id, calls FROM app_lm_platform_prompt_call_daily WHERE day = current_date` shows it, and the prompt's Usage tab does. |
| 4 | Re-run `ops/cloudsql/harden.sql` as `postgres` (steps 2d and 2e), so the runtime role cannot rewrite a prompt version, a label move or a variable's history. | An `UPDATE` of `app_lm_platform_prompt_version` as `lm_app` is refused for lack of privilege, not only by the trigger. |
| 5 | Default both switches on in `application.yml` and `deploy.yml`. | A deploy with neither variable set still serves from the registry and asks for the factor. |

**Turning one off again** is the same variable set to `false` and a deploy. With the registry off the bundled
prompts serve at once, whatever is live in the console; with two-factor off the console opens on the role alone.

## Running it locally

```bash
AI_PROMPT_REGISTRY_ENABLED=true npm run dev
./ops/cloudsql/grant-platform-role.sh <your email> --local     # after signing up; reload the page
```

Add `PLATFORM_REQUIRE_TWO_FACTOR=true` to be asked for the factor. A local Try it or eval run makes a real,
billed model call when the machine has Vertex credentials. The e2e matrix runs with both switches on
(`application-e2e.yml`, `e2e/api/22-platform-console.sh`, `e2e/spa/platform.mjs`).

## Not built

- Per-workspace prompt or variable overrides, and any workspace-level model choice.
- Editing a response schema, the answer format or the user message from the screen.
- A second model provider (#1325).
- Editors for a boolean, a choice or a decimal variable; a case's own page; creating a dataset or editing a rubric
  on screen.
