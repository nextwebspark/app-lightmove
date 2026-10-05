# Uncava Public API

Read-only JSON access to a workspace's positions, their companies and their executives, for an ATS, a BI
tool or a script.

- **Interactive docs (Swagger UI):** `https://beta.uncava.com/api/v1/public/docs`
- **OpenAPI 3.1 document:** `https://beta.uncava.com/api/v1/public/openapi.json`. The same file is committed at
  [`docs/public-api/openapi.json`](public-api/openapi.json) and can be fed to any OpenAPI client generator.

Everything below is also in the document's own description, which Swagger UI shows first.

## 1. Make a key

In Uncava, open **Settings → API keys → Create key**.

1. **Name it** after the tool that will use it, such as "Power BI dashboard". Whoever revokes it later can
   then tell what stops working.
2. **Choose what it can read** (its scopes; see below). Tick only what the tool needs.
3. **Choose when it expires:** 30, 90, 180 or 365 days.
4. **Copy the key** from the dialog. It is shown **once**: Uncava keeps only a fingerprint of it. If you lose
   it, revoke it and make another.

| Kind | Prefix | Who makes it | What it reads |
|---|---|---|---|
| Personal | `uncava_pat_` | Any staff member | Only the positions you can open in Uncava, checked on every request. It stops working if you leave the workspace or lose access. |
| Workspace | `uncava_svc_` | Admins only | Every position in the workspace. It keeps working when the person who made it leaves. |

A key is the prefix followed by 49 letters and digits. Keep it out of source control, as you would a
password. Make one key per tool, so you can revoke one without breaking the others.

## 2. Call the API

Send the key on every request:

```bash
export UNCAVA_API_KEY=uncava_pat_...   # the key you copied
BASE=https://beta.uncava.com/api/v1/public

curl -s $BASE/me -H "Authorization: Bearer $UNCAVA_API_KEY"
```

`/me` needs no scope. It describes the key (its workspace, scopes and expiry), so call it first to check
the key works.

```bash
# Positions you can read, newest first
curl -s "$BASE/projects?size=25" -H "Authorization: Bearer $UNCAVA_API_KEY"

# One position
curl -s "$BASE/projects/$PROJECT_ID" -H "Authorization: Bearer $UNCAVA_API_KEY"

# Its shortlisted companies, a page at a time
curl -s "$BASE/projects/$PROJECT_ID/companies?stage=shortlisted&page=0&size=100" \
  -H "Authorization: Bearer $UNCAVA_API_KEY"

# Its executives at one status, or at one company
curl -s "$BASE/projects/$PROJECT_ID/candidates?status=contacted" -H "Authorization: Bearer $UNCAVA_API_KEY"
curl -s "$BASE/projects/$PROJECT_ID/candidates?companyId=$COMPANY_ID" -H "Authorization: Bearer $UNCAVA_API_KEY"

# A whole stage in one call: every company with its executives nested
curl -s "$BASE/projects/$PROJECT_ID/universe?stage=inUniverse" -H "Authorization: Bearer $UNCAVA_API_KEY"
```

## Endpoints

| Route | Scope | Returns |
|---|---|---|
| `GET /me` | none | The calling key |
| `GET /projects?title=&page=&size=` | `projects:read` | Positions, paged; `title` filters by a case-insensitive substring |
| `GET /projects/{projectId}` | `projects:read` | One position |
| `GET /projects/{projectId}/companies?stage=&page=&size=` | `companies:read` | One stage's companies, paged |
| `GET /projects/{projectId}/candidates?status=&companyId=&page=&size=` | `candidates:read` | Executives, paged, first mapped first |
| `GET /projects/{projectId}/universe?stage=` | `companies:read` **and** `candidates:read` | The whole stage: `{ stage, companies: [{ company, executives }], unassigned }` |

- `stage` is `inUniverse` (the default), `shortlisted` or `declined`.
- `status` is `identified`, `contacted`, `engaged`, `interested`, `notInterested`, `offLimits` or `outOfScope`.
- An executive's `companyId` is the `id` of a company from the companies route, or null when their employer is not in
  the position's universe.
- In the universe read, those executives come back in `unassigned`, on `inUniverse` only.

## Scopes

| Scope | Reads |
|---|---|
| `projects:read` | Positions: title, client, stage, type, dates and counts |
| `companies:read` | Each position's companies |
| `candidates:read` | Each position's executives: profile, company and status |
| `candidates.contacts:read` | Fills an executive's `contacts` (emails and phones). Personal data |
| `candidates.compensation:read` | Fills an executive's `compensation` (the package). Personal data |

- **Personal data:** without its scope, `contacts` or `compensation` is `null`. It is never left out of the
  response, so a client can tell "not allowed" from "a field that does not exist".
- **AI guesses:** an executive's `seniority`, `nationality`, `gender` and `yearsExperience` are `null` while
  they are only a model's guess that nobody has confirmed.
- **Never sent:** notes, AI assessments, who added a row, and custom columns.

## Paging

List routes take `page` (from 0) and `size` (default 25, at most 100). They answer:

```json
{ "data": [ ... ], "page": 0, "size": 25, "totalCount": 142 }
```

Read until `(page + 1) * size >= totalCount`. The universe route is not paged. If a stage holds more than
5,000 companies or 10,000 executives, the universe route refuses it with `400 PUBLIC_API_UNIVERSE_TOO_LARGE`
rather than cutting it short. Use the paged routes for a stage that large.

## Rate limits

Each key may make 60 requests a minute, and each IP address 300. Past either limit, the answer is
`429 RATE_LIMITED` with a `Retry-After` header in seconds: wait that long, then retry. The limits are counted
on each server separately, so the real ceiling is somewhat higher, but build to the stated one.

## Errors

Every error is an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem document
(`application/problem+json`):

```json
{
  "type": "https://lightmove.app/errors/api-key-scope-missing",
  "title": "Forbidden",
  "status": 403,
  "detail": "This API key does not carry the scope this request needs",
  "instance": "/api/v1/public/projects/…/companies",
  "code": "API_KEY_SCOPE_MISSING",
  "requiredScope": "companies:read",
  "timestamp": "2026-10-05T12:00:00Z",
  "correlationId": "…"
}
```

Switch on `code`, never on `detail`. Quote `correlationId` when asking for support.

| Status | Code | When |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A parameter is out of range or not one of its values |
| 400 | `PUBLIC_API_UNIVERSE_TOO_LARGE` | A universe too large for one call; page instead |
| 401 | `API_KEY_INVALID` | The key is missing, malformed, unknown, revoked or expired, or its owner lost access |
| 403 | `API_KEY_SCOPE_MISSING` | The key lacks the scope the route needs (`requiredScope` names it) |
| 403 | `FORBIDDEN` | A personal key's owner is not on that position |
| 404 | `NOT_FOUND` | No such position in the key's workspace |
| 429 | `RATE_LIMITED` | Too many requests; wait for `Retry-After` |

## Generating a client

The OpenAPI document is complete enough to generate a client. For example:

```bash
npx @openapitools/openapi-generator-cli generate \
  -i https://beta.uncava.com/api/v1/public/openapi.json -g typescript-fetch -o uncava-client
```

## For Uncava maintainers

- **The contract is a snapshot.** `PublicApiContractTest` regenerates the spec and fails on any difference from
  `docs/public-api/openapi.json`. After a deliberate change, run
  `UPDATE_OPENAPI_SNAPSHOT=true ./mvnw test -Dtest=PublicApiContractTest` in `apps/api` and commit the file with
  the change, so the PR shows what integrations will see.
- **Kill switch:** the repository variable `PUBLIC_API_ENABLED=false` (`deploy.yml`) makes every public route
  answer 404, its docs included. Existing keys are kept and work again once it is turned back on.
- **Limits:** set by `lightmove.public-api.*`: the default and maximum key lifetime, the per-user key ceiling,
  and the two per-minute budgets.
- **Secret scanning:** register a GitHub secret-scanning custom pattern for the key format, so a committed key
  is flagged. Use the pattern `uncava_(pat|svc)_[0-9A-Za-z]{49}`, under *Settings → Code security → Secret
  scanning → Custom patterns*.
