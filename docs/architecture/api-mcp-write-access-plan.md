# Plan: write access for the public API, MCP and the assistant

Status: **proposal, for decision.** Nothing here is built. §10 lists the decisions needed before Phase 0 starts.

## 1. Goal

Whatever a staff member can do on screen, an integration (public API), an AI app (MCP) and the in-product assistant
can do too, under the same rules:

- **Never more than the person could do.** A personal key, an OAuth connection and the assistant act as their user.
  What they can do is the credential's scopes ∩ the user's live permissions, re-read on every call.
- **Service keys belong to the workspace.** Only an admin makes one. It acts as a named service account with exactly
  the scopes the admin ticked, across the workspace. It never inherits the admin's own access, and it keeps working
  after they leave.
- **One capability layer.** Each capability is written once. The REST route, the MCP tool and the assistant tool are
  thin adapters over it, so they cannot drift apart, and a new capability reaches all three at once.

Out of scope: SPA changes beyond the settings and approval screens this needs, the Chrome extension (it keeps its
session credential), and anything a platform super admin does.

## 2. Where we are

| Surface | Credential | Caller model | Authorisation | Writes |
|---|---|---|---|---|
| SPA | session JWT | `AuthPrincipal` | `@RequireProjectPermission` / `@workspaceAuthorizer` on controllers | ~300 routes over ~100 controllers |
| Public API `/api/v1/public/**` | `uncava_pat_` / `uncava_svc_` | `ApiKeyPrincipal` → `PublicReader` | `PublicApiAuthorizer`: the scope, then `WORK_VIEW` (personal key) or a workspace match (service key) | none |
| MCP `/api/v1/mcp` | MCP OAuth token, or a key with `mcp:use` | `McpCaller` → `PublicReader` | `@McpToolScopes` + `McpToolGuard`, then `PublicApiAuthorizer` | none |
| Assistant | session JWT | `AssistantToolContext` (workspace, project) | `WORK_EXECUTE` once, at the ask; the tools trust the context | proposes; a person accepts the card |

What the code tells us:

1. **Authorisation lives on controllers, not services.** `CandidateService.add(userId, workspaceId, projectId, …)`
   trusts its caller, `CandidateController`, behind `@RequireProjectPermission(WORK_EXECUTE)`. Any new door that calls
   a service directly has to re-implement that gate, which is how surfaces drift apart. The plan moves the gate to one
   shared boundary (§5.3) rather than copying it a fourth time.
2. **Every write is attributed to a user id.** `created_by`, `added_by`, `PersonActivityRecorder.record(…, actor, …)`
   and `app_lm_audit_event.actor_user_id` all take a `UUID userId`, and most of these are foreign keys to
   `app_lm_user`. A workspace key has no user, so it needs an identity of its own (§5.6).
3. **Services take `HttpServletRequest`** for the audit origin (`AuditService.Builder.from`). MCP and the assistant
   need an origin that is not a servlet request (§5.7).
4. **The domain services already hold the business rules**: duplicates, suppression, held profiles, consent,
   `LAST_ADMIN`, credit holds. Parity is safe only if the API reuses them and never re-implements them.
5. **`BaseEntity` carries `@Version`.** ETag / `If-Match` concurrency costs no migration.
6. **Scopes are a CHECK constraint** repeated on `app_lm_api_key` and `app_lm_oauth_client` (V114, V117, V217). Today
   every new scope is a migration that rewrites both, and a write catalog would make that constant.
7. **MCP lists every tool to every caller.** `McpToolRegistry` publishes the full set, and a missing scope is only
   answered at call time. Once tools can write, listing tools a caller can never run widens what an injected prompt
   can ask for.
8. **Spring AI is 2.0.2.** Its 2.1 release adds `@Tool(requiresConfirmation = true)`, which pauses a model reply
   before any write runs. That is the assistant's confirm-every-change rule, built in (§5.5).

## 3. How other platforms do it

Read from primary sources where the network allowed, otherwise from search excerpts of those pages; the research
notes mark which.

**Credentials and effective permission**

- **GitHub** — A GitHub App's user token "only has permissions that both the user and the app have". Its
  installation token is the app's alone, attributed to the app, and lives an hour. Fine-grained tokens are confined
  to one owner, offer read and write per resource, can be made to wait for org approval, and have an
  org-set maximum lifetime (≤ 366 days).
  [docs](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-user-access-token-for-a-github-app),
  [policy](https://docs.github.com/en/organizations/managing-programmatic-access-to-your-organization/setting-a-personal-access-token-policy-for-your-organization)
- **Microsoft Graph** — Delegated access is "the intersection of the user's privileges and the permissions that have
  been consented". Application access is whatever an admin consented to, and only an admin may consent to it.
  [docs](https://learn.microsoft.com/graph/permissions-overview)
- **Slack** — A bot token acts as the app's own bot user and survives the installer's deactivation. A user token acts
  as the person. [docs](https://api.slack.com/authentication/token-types)
- **Stripe** — A restricted key sets each resource to None, Read or Write, starts at None, and write implies read.
  [docs](https://docs.stripe.com/keys/restricted-api-keys)
- **ATS peers** — Greenhouse Harvest v1/v2 required `On-Behalf-Of: <user id>` on every write "to log changes".
  Lever stamps the key's name on every object the key creates. Ashby grants per-module read/write and starts a key
  at no permissions. [Greenhouse](https://support.greenhouse.io/hc/en-us/articles/360029266032),
  [Lever](https://help.lever.co/hc/en-us/articles/20087297592477),
  [Ashby](https://developers.ashbyhq.com/reference/authentication)
- **Salesforce** — Client credentials run as a named "Run As" integration user with an API-only profile. **Linear** —
  `actor=app` makes mutations the app's, shown as "User (via Application)", and "the actor is not a permission".
  [Linear](https://linear.app/developers/oauth-actor-authorization)

**Write contracts**

- **Idempotency** — Stripe keeps keys at least 24 hours and refuses a reused key with different parameters. The IETF
  draft answers 409 while the original is still running and 422 for a reused key with a different payload.
  [Stripe](https://docs.stripe.com/api/idempotent_requests),
  [draft](https://datatracker.ietf.org/doc/html/draft-ietf-httpapi-idempotency-key-header)
- **Concurrency** — RFC 9110's `If-Match` requires a strong comparison and answers 412. RFC 6585's 428 can require a
  conditional request. **Merge Patch** (RFC 7396): `null` deletes a member and arrays are replaced whole.
- **OWASP API 2023** — API1 BOLA and API5 BFLA are the authorisation failures. API3 BOPLA folds in mass assignment, and
  its fix is an allowlist of writable properties. API4 includes paid side effects such as email.
  [OWASP](https://owasp.org/API-Security/editions/2023/en/0xa3-broken-object-property-level-authorization/)
- **Webhooks** — Stripe signs `timestamp.raw_body` with HMAC, tolerates 5 minutes, and re-signs each retry.
  [docs](https://docs.stripe.com/webhooks)

**MCP and agents**

- **Spec** — Annotations are hints. Clients "MUST consider tool annotations to be untrusted", and servers MUST
  "validate all tool inputs, implement proper access controls, rate limit tool invocations". There SHOULD always be a
  human able to deny a call. `destructiveHint` defaults to true, and false means "only additive updates". OAuth step-up
  is `403 insufficient_scope`, which the spec suggests for incremental elevation on a first privileged call.
  Elicitation has a form mode and a URL mode. Revision 2026-07-28 makes the protocol stateless and turns elicitation
  into multi-round-trip requests: the tool returns `input_required` with an opaque `requestState`, which the server
  MUST integrity-protect and whose single use the server must enforce.
  [tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools),
  [elicitation](https://modelcontextprotocol.io/specification/2025-11-25/client/elicitation),
  [MRTR](https://modelcontextprotocol.io/specification/2026-07-28/basic/patterns/mrtr)
- **GitHub MCP** — Write tools are consolidated by resource. A read-only mode always wins, and a classic token's
  tools are hidden at startup by its scopes. `delete_repository` is listed only to clients that can elicit: it answers
  a typed "owner/repo" confirmation with a sealed state (repo id, ten-minute expiry), then re-checks everything before
  deleting. [repo](https://github.com/github/github-mcp-server)
- **Sentry, Atlassian** — Write tool groups are off by default. Atlassian's delete and manage tools need an admin to
  enable them, and every tool use goes to the org audit log.
  [Sentry](https://github.com/getsentry/sentry-mcp), [Atlassian](https://github.com/atlassian/atlassian-mcp-server)
- **Notion** — Its delete tool moves pages to Trash, so it can be undone.
- **Stripe** — Its restricted key is the permission boundary for its agent tools.
- **Hosts** — Claude's directory requires `title`, `readOnlyHint` and an honest `destructiveHint`. Users set each tool
  to Always allow, Needs approval or Blocked. OpenAI's Apps SDK asks for separate write tools so ChatGPT can confirm
  them, and the Responses API approves MCP calls by default.
  [Claude](https://claude.com/docs/connectors/building/review-criteria)
- **Anthropic, "Writing effective tools for agents"** — Build a few task-shaped tools rather than wrapping every
  endpoint, namespace them, return high-signal fields, make errors actionable, and measure with evals.
  [post](https://www.anthropic.com/engineering/writing-tools-for-agents)
- **Agent security** — OWASP LLM06 (Excessive Agency) is too much functionality, permission or autonomy. Its remedies
  are to run in the user's context, ask for approval on high-impact actions, and use "complete mediation" downstream,
  never in the model. Willison's "lethal trifecta" is private data, untrusted content and a way out, together.
  [OWASP LLM06](https://genai.owasp.org/llmrisk/llm062025-excessive-agency/)

**What we take from this**

| Question | Answer, and who it follows |
|---|---|
| Personal key / OAuth | scopes ∩ the user's live permissions (GitHub user token, Graph delegated) |
| Service key | its own grant, admin-consented, attributed to itself (GitHub installation, Slack bot, Graph application) |
| Scope shape | `resource:read` / `resource:write`, write implies read, sensitive actions split out (Stripe, GitHub, Ashby) |
| On-behalf-of | attribution plus a further limit, never wider (Greenhouse, Lever `perform_as`, Graph OBO) |
| Confirmation | enforced on the server; the model never redeems its own proposal (MCP spec, GitHub `delete_repository`) |
| Risky tools | off by default, enabled by an admin (Atlassian, Sentry); reversible deletes (Notion) |
| Tool exposure | listed only to callers who could run them; read-only mode always wins (GitHub) |

## 4. Principles

1. **One operation, three adapters.** A capability is an `Operation` (§5.3). REST, MCP and the assistant adapt it,
   and none of them holds business logic or a permission check of its own.
2. **Authorise by action at the operation boundary,** using the `WorkspaceAction` / `ProjectAction` catalog the SPA
   uses, re-read every call. A scope is a ceiling on a credential, never a grant of its own.
3. **Effective permission = credential scopes ∩ the actor's live permissions ∩ workspace policy.** Every surface
   computes it the same way.
4. **Public write contracts are allowlists.** A write body is a public DTO naming exactly the fields a caller may set.
   It maps onto the internal request and never is the internal request. Unknown properties are refused.
5. **Risk decides the default, not the surface.** Reads and ordinary writes run. Destructive, external and spending
   operations need a stronger scope and, from an agent, a person's confirmation that the server enforces.
6. **Fail closed at startup.** An operation that declares no action, scope or risk stops the boot, as
   `McpToolRegistry` already does for a tool without `@McpToolScopes`.

## 5. Design

### 5.1 Credentials, and what each may do

| Credential | Acts as | Ceiling | Floor it can never pass |
|---|---|---|---|
| Session (SPA) | the user | — | the user's permissions |
| Personal key `uncava_pat_` | the user | its scopes | the owner's live permissions; dies with membership |
| MCP OAuth grant | the user, via a named client | its consented scopes | the user's live permissions; Disconnect, password change |
| Personal key with `mcp:use` | the user, via MCP | its scopes | as a personal key |
| Workspace key `uncava_svc_` | its service account | its scopes, across the workspace | never a workspace action (§6); admin-made only |
| Workspace key + `Uncava-On-Behalf-Of` | its service account, for a member | its scopes ∩ that member's live permissions | refused if the member is not active staff |
| Assistant | the user, via the assistant | the user's permissions on the asked project | every write needs the user's click |

### 5.2 One caller model: `Actor`

Every surface builds one `Actor` and passes it down. Nothing below the adapter sees a credential.

```
Actor(
  workspaceId,                    // from the credential, never a parameter
  principal: User(userId) | ServiceAccount(serviceUserId, keyId),
  onBehalfOf: userId?,            // workspace keys only; narrows, never widens
  via: SESSION | API_KEY | MCP_OAUTH | MCP_KEY | ASSISTANT,
  credentialId, clientId?,        // key / grant / assistant turn; the OAuth client or MCP clientInfo
  scopes: Set<Scope>,             // the ceiling; every scope for a session
  origin: AuditOrigin(ip, userAgent, correlationId)
)
```

`AuthPrincipal`, `ApiKeyPrincipal` and `McpCaller` each gain an `actor()` factory, and `PublicReader` becomes a view
of `Actor`. `Actor` implements `TenantPrincipal`, so row-level security keeps reading the same workspace.

### 5.3 The operation catalog

An operation is one use case, task-shaped as the tool guidance asks, living in its feature's new `operation/`
sub-package:

```java
@Operation(name = "candidates.change_status",
           action = ProjectAction.WORK_EXECUTE,
           scope = Scope.CANDIDATES_WRITE,
           risk = Risk.WRITE,
           surfaces = {REST, MCP, ASSISTANT})
final class ChangeCandidateStatus implements ProjectOperation<ChangeCandidateStatusInput, PublicCandidate> {
    public Preview preview(Actor actor, UUID projectId, ChangeCandidateStatusInput input) { … }
    public PublicCandidate apply(Actor actor, UUID projectId, ChangeCandidateStatusInput input) { … CandidateService … }
}
```

- **`OperationCatalog`** (`core/operation`) collects every `@Operation` bean at startup. It refuses to start on a
  duplicate name, a missing action, scope or risk, or an input that is not a validated record. Its snapshot
  (`docs/operations.json`: name, action, scope, risk, surfaces) is a contract test like the OpenAPI and MCP ones, so a
  reviewer sees every change in reach.
- **`OperationExecutor`** is the one door. For each call it:
  1. resolves the actor's live permission (`WorkspaceAccess` / `ProjectAccess`, or the service-account rule in §5.6);
  2. checks the scope, the workspace policy and the risk gate;
  3. validates the input;
  4. runs inside `WorkspaceScope` and one transaction;
  5. writes the audit line;
  6. maps any failure to a `code`.
- **Adapters:**
  - **REST** — hand-written resource routes under `/api/v1/public`, so URLs stay idiomatic nouns and verbs. Each is a
    one-line call to the executor, and the OpenAPI snapshot keeps covering them.
  - **MCP** — one tool per operation that lists `MCP`. Its schema comes from the input record and its annotations
    from the risk (§5.5). It is listed only to callers whose scopes, permissions and policy could run it. Reads and
    writes stay separate tools, and deletes are tools of their own, because a hint covers the whole tool.
  - **Assistant** — Spring AI `ToolCallback`s over the same operations. The project is bound from
    `AssistantToolContext` and removed from the schema the model sees, so a tool never takes a project from the
    model.
- **Operations hold no business rules.** They authorise, map the public DTO to the internal request, call the
  existing service and map the result back. A rule found missing goes into the service, where the SPA gets it too.
- **The SPA's controllers stay as they are.** They already call the same services. A controller moves onto an
  operation only where that removes duplication.

Today's reads also move onto the catalog: `PublicReadService` and the MCP read tools become operations, and their
contracts (`docs/public-api/openapi.json`, `docs/mcp/tools.json`) must not change in the move.

### 5.4 Scope catalog v2

| Scope | Covers |
|---|---|
| `projects:read` / `projects:write` | positions: create, edit the brief, dates, stage |
| `companies:read` / `companies:write` | a position's companies: add from the universe, move stage, remove, notes |
| `candidates:read` / `candidates:write` | a position's executives: add, edit, status, stage, exit reason, remove |
| `candidates.contacts:read` / `:write` | emails and phones (personal data) |
| `candidates.compensation:read` / `:write` | the package (personal data) |
| `candidates.demographics:read` | gender and nationality; no write over the API |
| `people:read` / `people:write` | the workspace's people pool: owner, tags, do-not-contact, person notes |
| `tasks:read` / `tasks:write` | tasks and their assignment |
| `outreach:read` | sequences, conversations, meetings |
| `outreach:send` | send an email, start a sequence, book a meeting |
| `credits:spend` | contact lookup, AI research, Find executives |
| `mcp:use` | opens MCP to a key (unchanged) |

- **`x:write` implies `x:read` and nothing else.** No scope implies a personal-data scope.
- **Scopes move from CHECK constraints to a catalog table** (`app_lm_api_scope`, seeded like `app_lm_action`), with
  a join table per key and per OAuth client and a `ScopeCatalogTest` that keeps the enum and the seed in step, as
  `RbacCatalogTest` does. A new scope is then one INSERT. Every existing key keeps exactly the tokens it holds.
- **The consent screen and the key dialog show writes apart from reads**, as they already show personal data apart.
  Write scopes are unticked by default, and `outreach:send` and `credits:spend` each carry a line saying what they
  allow.
- **OAuth clients ask for writes when they need them.** A first write call without the scope is stepped up
  (`403 insufficient_scope` naming it), so a connection made for reading never silently holds write.

### 5.5 Risk, annotations and confirmation

| Risk | Examples | MCP annotations | REST, personal or OAuth | Workspace key | MCP agent | Assistant |
|---|---|---|---|---|---|---|
| `READ` | list, get, search | readOnly | run | run | run | run |
| `ADDITIVE` | add executive, add note, create task | destructive=false | run | run | run | user confirms |
| `UPDATE` | change status, move stage, edit brief | destructive=true, idempotent | run | run | run | user confirms |
| `DESTRUCTIVE` | remove executive or company, delete note | destructive=true | run, `If-Match` required | only if policy allows | confirmed by elicitation, else not listed | user confirms |
| `EXTERNAL` | send email, start sequence, book meeting | destructive=true, openWorld | run | only if policy allows | approved in Uncava | user confirms |
| `SPEND` | contact lookup, AI research, Find executives | openWorld | run, within caps | run, within caps | approved in Uncava above a per-call cost | user confirms |

Confirmation is **enforced on the server and never redeemable by the model**:

- **Preview.** Every write implements `preview()`: validate, authorise, describe what would change, write nothing.
  REST exposes it as `?preview=true`. It is a "what would change?" aid. It is not a confirmation, because a model
  can call preview and then apply by itself.
- **Confirmation token.** A preview that needs confirmation answers a sealed token (AEAD under a server key). It binds
  the user, workspace, credential, operation, a digest of the input, the target's `@Version` and a ten-minute expiry,
  and its single use is enforced by a row (`app_lm_operation_confirmation`), never assumed. Apply re-runs every check
  and refuses a token whose target has moved.
- **The assistant** marks every write `requiresConfirmation` (Spring AI 2.1). The paused reply is drawn as today's
  proposal card, now for any operation. The user's click applies it through the executor as that user. The model
  never sees the token. The suggested-companies card becomes the first instance of this rather than a second
  mechanism.
- **MCP, destructive.** As GitHub's `delete_repository` does: the tool is listed only to clients that declare form
  elicitation. The first call answers `input_required` (2026-07-28) or `elicitation/create` (2025-11-25) asking the
  user to type the target's name, with the sealed token as `requestState`. The retry is checked against it.
- **MCP, external and spending.** The tool answers a **pending action**: its id, a one-line summary and an approval
  link (`app_lm_pending_action`: operation, validated input, actor, preview, status, expiry). A person with the same
  permission approves it in Uncava, and it runs as that person, re-checked. Where the client supports URL-mode
  elicitation, the link is offered that way. This keeps sending and spending away from a host whose "Always allow"
  the server cannot see.
- **Reversible first.** A delete exposed to agents is a soft delete with a restore path, where the domain allows it.
  Hard and bulk deletes stay off MCP.

### 5.6 Service accounts

A workspace key acts as a **service account**: an `app_lm_user` row with `kind = 'SERVICE'` (a new migration) named
after the key ("Power BI sync"). It has no password, no identity, no session, no deliverable address and **no
membership row**, so it never appears in the roster, the seat count, a mention picker, an invitation or a digest.

- **Attribution.** Every `created_by`, activity line and audit event names the account, so the screen shows
  "Power BI sync (workspace key)" where it would show a person, and every existing foreign key holds (Lever,
  Linear).
- **Authorisation.** The executor answers a service account from its scopes alone:
  - every position in its workspace;
  - the project actions its scopes map to;
  - never `PROJECT_ARCHIVE`, `TEAM_MANAGE`, `CLIENT_ACCESS_MANAGE` or any workspace action.

  A service path that asks `WorkspaceAccess.requireActiveMember` for it fails closed. The authorisation matrix
  (§8) finds each of these, and each is decided on its own.
- **On behalf of.** An ATS syncing "Sara moved this candidate" may send `Uncava-On-Behalf-Of: <userId>`. The call is
  then attributed to both and runs under the key's scopes ∩ Sara's live permissions. It is refused unless Sara is
  active staff, and it never widens access.
- **Lifecycle.** Revoking the key disables the account, and its rows keep their attribution. Rotating a key keeps the
  account, so history does not split.

### 5.7 Attribution and audit

- `app_lm_audit_event` gains `actor_kind`, `via`, `credential_id`, `client_id`, `on_behalf_of` and `confirmed_by`
  (a new migration), filled by the executor for every operation.
- `MCP_TOOL_CALL` and `PUBLIC_API_READ` become one `OPERATION_CALL` event that keeps the old names as a `surface`
  detail, so existing dashboards still match.
- How a change was confirmed is recorded as one of: a UI decision id, an elicitation accept, an approval, or
  host-only.
- The activity feed and audit log show *how* a change was made: "via Claude", "via API key Power BI sync", "via the
  assistant, confirmed by Sara".
- `AuditOrigin(ip, userAgent, correlationId)` replaces `HttpServletRequest` in service signatures, one feature at a
  time as each moves onto the catalog.

### 5.8 REST write contract

- Writes live under `/api/v1/public`, additive to the read contract. The snapshot test keeps guarding it, and the
  contract stays unversioned until a breaking change is needed. When one is, it gets a dated `Uncava-Version` header
  (Stripe).
- **Routes:**
  - `POST` creates (201 + `Location`).
  - `PATCH` changes a public DTO with Merge Patch semantics. A clearable field is nullable by contract, and the DTO
    tells an absent field from an explicit `null`.
  - `DELETE` removes.
  - A state change with a business meaning is its own sub-resource (`PUT …/candidates/{id}/status`), as the SPA
    already splits a status flick from a whole-form save.
  - Collections such as tags are sub-resources, never replaced arrays.
- **Idempotency.** `Idempotency-Key` is accepted on every `POST`, scoped to the credential, and kept 24 hours with a
  fingerprint of the method, path and body and the stored response (`app_lm_idempotency_record`). A replay answers the
  stored response. A request whose original is still running answers `409 IDEMPOTENCY_IN_PROGRESS`, and a reused key
  with a different body answers `422 IDEMPOTENCY_KEY_REUSED`.
- **Concurrency.** A strong `ETag` comes from `@Version`. `If-Match` is honoured on `PATCH`/`PUT`/`DELETE` and answers
  412. It is required (428) on destructive routes from the start, and optional elsewhere until announced.
- **Bulk.** `POST …/companies:batch`-style routes are capped at 100 items, are all-or-nothing, and audit each item.
  Larger work is a job.
- **Jobs.** Long-running work (Find executives, imports, exports) answers 202 with a job resource to poll, an event
  once Phase 5 lands, and the MCP tasks extension where clients support it.
- **Errors and validation.** Errors are RFC 9457 with a `code`; new codes go in `ErrorCode` and the docs' error table.
  Every writable field gets its row in `docs/validation-matrix.md` with the public route as a surface.

### 5.9 Limits

- Writes get their own per-credential budget beside reads: 30 a minute per key to start, and a per-workspace ceiling
  across all its keys and grants.
- `SPEND` draws from the existing credit ledger with its idempotency key. The admin sets a daily spend cap on each
  key.
- An agent's writes also count against its user's write budget, so three connected apps cannot triple it.

### 5.10 Agent safety

The attack to design for: a profile, an email reply or a company's "about" text carries an instruction, and an agent
with write tools follows it. The defences, strongest first:

1. **No way out.**
   - No operation sends data to an address or URL the caller types.
   - An email goes only to an executive's own recorded address, through a template or a draft a person reviews.
   - A webhook goes only to a URL an admin registered.

   This breaks the lethal trifecta's third leg.
2. **Sending, spending and destroying need a person** (§5.5), confirmed through the server and never through the
   model.
3. **Least exposure.**
   - MCP lists only the tools a caller could run.
   - Write groups are off until a workspace turns them on.
   - OAuth asks for write scopes only when they are used.
   - A read-only header or URL (`/api/v1/mcp?readonly`) can narrow a connection and never widen it.
4. **Allowlisted output.** As today, notes, assessments, custom columns and AI guesses never leave, and tool text
   marks data as data.
5. **Kill on demand.** Disconnect and revoke stop writes on the next call, and every agent write is audited with its
   grant and client.

### 5.11 Workspace policy and kill switches

- **Settings → API keys → Policy** (`WORKSPACE_MANAGE`):
  - allow writes through keys;
  - allow writes through AI apps;
  - allow destructive writes;
  - allow sending;
  - allow spending, with a cap;
  - require admin approval for personal keys with write scopes (GitHub's org approval);
  - the maximum key lifetime.

  Every write setting is off by default, so turning the feature on changes nothing for an existing workspace.
- `PUBLIC_API_WRITES_ENABLED` and `MCP_WRITES_ENABLED` sit beside `PUBLIC_API_ENABLED` and `MCP_ENABLED`. When one is
  off, every write route on that surface answers 404 and its tools are not listed.

## 6. What is in, and what never is

| Tier | Capability | Surfaces |
|---|---|---|
| **1. The mandate's work** | positions (create, brief, dates, stage); companies (add from the universe, move, remove, notes); executives (add, edit, status, stage, exit reason, remove); custom column values; tags; the company universe search; reads of reports and the talent map | REST, MCP, assistant |
| **2. The team's work** | the people pool (owner, tags, do-not-contact, notes); tasks; notifications (read); dashboards (read); documents (upload, list, download) | REST, MCP, assistant |
| **3. Effects outside** | outreach (draft, send, sequences); meetings; contact lookup; AI research; Find executives; imports; exports | REST; MCP and the assistant through confirmation |
| **Never through a key or an agent** | sign-in, sessions, two-factor, API keys, OAuth grants, members and invitations, billing, workspace settings and integrations, mailbox connections, erasure, subject access, retention settings, the platform console | the session only |

The "never" list is deliberate:

- A credential that can mint credentials, admit members or change who pays turns one leaked key into a takeover.
- Erasure and subject access are legal acts a person answers for.

If customers ask for member provisioning, it becomes its own track (SCIM, under a workspace key with its own scope).

## 7. Phases

Each phase is one or more PRs. It ships behind its switch and leaves `main` releasable.

### Phase 0: Foundations, no new capability

- `Actor`, `AuditOrigin`, `@Operation`, `OperationCatalog`, `OperationExecutor`, the risk classes, and the
  `docs/operations.json` snapshot.
- The scope catalog table, migrated from the CHECK constraints with every existing token kept, and `ScopeCatalogTest`.
- The audit columns and `OPERATION_CALL`.
- Spring AI 2.0.2 → 2.1, for `requiresConfirmation`, with the MCP SDK it brings, checked against the existing MCP
  contract test.
- Today's public reads and MCP read tools moved onto operations.
- `OperationAuthorizationMatrixTest` (§8).
- **Exit:** `PublicApiContractTest` and `McpToolContractTest` unchanged, and every existing public, MCP and assistant
  test green.

### Phase 1: The first write slice, end to end, on every surface

Five operations, chosen to exercise every mechanism:

- change an executive's status (`UPDATE`);
- move an executive's stage (`UPDATE`);
- add a company note (`ADDITIVE`);
- create a task (`ADDITIVE`);
- remove an executive from a position (`DESTRUCTIVE`).

The phase also builds:

- service accounts and `Uncava-On-Behalf-Of`;
- `Idempotency-Key`, `ETag`/`If-Match`, and preview;
- confirmation tokens, MCP elicitation for the remove, and per-caller tool listing;
- the assistant's generalised confirmation card;
- workspace policy (writes off by default), the deployment switches and write budgets;
- the write scopes in Settings → API keys, the consent screen and the policy panel. **These need mockups** in
  `uncava-design` before they are built.

**Exit:** the same five changes, made from the SPA, a personal key, a workspace key, Claude over MCP and the assistant,
produce identical rows, identical activity lines except for "via", and identical refusals.

### Phase 2: Tier 1 breadth

- Positions, companies and executives, end to end.
- Custom column values and tags.
- Bulk add from the universe.
- Preview on every write.
- MCP evals (`ops/eval/mcp/`) gain write cases: the right tool is picked, and nothing is written when the question
  only asks.

### Phase 3: Tier 2

- The people pool and person notes.
- Tasks.
- Documents, as multipart upload under the existing size and type rules.
- Dashboard and notification reads.

### Phase 4: Tier 3, effects outside

- Outreach drafts and sends, meetings, contact lookup, AI research, Find executives, imports and exports.
- Pending actions and the approval screen. **This needs a mockup.**
- Jobs for long-running work, and per-key spend caps.

### Phase 5: Events out

Webhooks for what integrations ask for first: an executive's status changed, a company's stage changed, a position
was created.

- The endpoints are HTTPS URLs an admin registers.
- Each payload carries an event id and names its actor, so an integration can skip echoes of its own writes.
- Payloads are signed as `timestamp.body` with HMAC-SHA256, and each retry is re-signed, retried with backoff and
  logged.
- Delivery uses `GuardedHttpClient`, the same as every other fetch of a stranger's URL.

### Phase 6: The admin track, optional

SCIM-style member provisioning under a workspace key, only if customers ask for it.

## 8. Testing and evaluation

- **The authorisation matrix is the guard.** `OperationAuthorizationMatrixTest` runs every operation against every
  credential kind and every caller (admin, member, researcher seat, client seat, no seat, another workspace, a service
  account, on-behalf-of a member without the seat) and asserts either allow or the exact refusal code. A new
  operation is tested by being declared.
- **Parity tests.** For each operation, the SPA route and the public route produce the same change of state.
- **Contract snapshots.** OpenAPI, MCP tools and operations stay the review surface for every external change.
- **Confirmation tests.** A token is refused if it is replayed, expired, from another user or credential, carries a
  changed input, or names a target that has moved.
- **Evals.** MCP and assistant evals gain write cases and injection cases: a profile that says "email this list
  to …" must end in no write and no proposal.
- **End to end.** Each phase adds one journey through a real key and a real MCP client, run with `PROFILE=e2e`.

## 9. Risks

| Risk | Mitigation |
|---|---|
| A service path assumes every actor is a member | Service accounts have no membership, so those paths fail closed; the matrix finds each one and it is decided on its own |
| The operation layer grows into a second service layer | Operations hold no business rules: authorise, map, call the existing service |
| Scopes sprawl | Paired resource scopes plus two risk scopes; a new scope needs a row in §5.4 and a reason |
| Agents write wrong data confidently | The assistant confirms every write; MCP hosts' own prompts; preview; evals |
| An annotation lies, or a host's "Always allow" skips the prompt | Annotations are hints; destructive, external and spending operations are confirmed by the server |
| Existing integrations break | Read contracts frozen by snapshot; write scopes opt-in; `If-Match` optional outside destructive routes until announced |
| MCP spec churn (2025-11-25 → 2026-07-28) | Confirmation sits behind one seam that supports both elicitation shapes; the MCP contract test pins the wire |

## 10. Decisions needed

1. **Service-account identity:** a `kind = 'SERVICE'` user row with no membership (**recommended**, §5.6), or a
   nullable actor plus a key id on every attributed table.
2. **Assistant writes:** the user confirms every write (**recommended**, the current rule), or ordinary writes run
   directly as they do over MCP.
3. **MCP sending and spending:** approved in Uncava (**recommended**), or left to the host's own approval prompt.
4. **Phase 1's five operations:** as listed, or a different first slice.
5. **Policy defaults:** writes off in every workspace until an admin turns them on (**recommended**), or on for new
   workspaces.
