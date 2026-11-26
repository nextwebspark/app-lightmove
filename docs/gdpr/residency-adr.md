# ADR: where personal data is processed (#925)

_Draft for counsel (#925, #930, epic #917). Engineering facts read from the code on 2026-11-15; not legal advice._

Status: **decided** for 1 (decision F4, 25 Nov 2026), **proposed** for 2. Inputs: [tia.md](tia.md) §4,
[data-protection-plan.md](data-protection-plan.md) §5 item 3.4, [subprocessors.md](subprocessors.md).

## 1. Vertex AI stays at `global`

### Context
`spring.ai.google.genai.location` and `…embedding.location` both read `GOOGLE_CLOUD_LOCATION`, default `global`
(`application.yml`), and nothing in `deploy.yml` or `ops/gcp/deploy.sh` sets it. At `global`, Google may serve a
request from any region it runs the model in. The prompts carry candidate dossiers, notes, CV text and call
transcripts with contacts taken out (`ContactRedaction`), position briefs and assistant chats.

The chat and assistant models (`gemini-3.5-flash`, `gemini-3.5-flash-lite`) are served from `global` only, and
`us-central1` throttled most calls on 13 Oct 2026 (the comment above `location` in `application.yml`). The embedding
model (`text-embedding-004`) is regional.

### Decision
Keep `global` for both until one of the triggers below fires. Pinning the chat model today means a different, older
regional model, a quality and throughput regression on every AI feature, for a residency gain that matters only once a
customer needs it.

Confirmed by the business as decision F4 of the decision list answered on 25 Nov 2026
([governance-decisions.md](governance-decisions.md)): the models used are not offered in a single region, so the AI
does not run in one fixed region for now. This supersedes the "pin Vertex to `us-central1`" box of #917 step 1 and
the first box of #925.

### Consequences
- The sub-processor list, the TIA and the privacy policy say "Vertex AI, `global`" rather than a region.
- **Triggers to revisit:** Google serves the Flash models from a named region; a customer contracts for EU, UK or GCC
  residency; counsel answers F1 (the transfer mechanism) in a way that requires a named location.
- **The change, when it comes:** set `GOOGLE_CLOUD_LOCATION` in `deploy.yml`'s `--set-env-vars` (and
  `ops/gcp/deploy.sh`), and switch `LLM_CHAT_MODEL` / `ASSISTANT_MODEL` to models served there. Embeddings can be
  pinned on their own first: they need a second variable, since both blocks read the same one today, and a re-embed is
  not needed because the model is unchanged.
- An EU or GCC stack (section 2) sets its own region regardless.

## 2. A separate stack per region, not a region per workspace

### Context
Everything runs in one Google Cloud project in `us-central1`:
- Cloud Run and Artifact Registry: `deploy.yml` hard-codes `REGION: us-central1`; `ops/gcp/*.sh` default `GCP_REGION`.
- Cloud SQL `bright-gcc`: the instance connection name is the datasource default in `application.yml`.
- The documents bucket: created with `--location="$REGION"` (`ops/gcp/bootstrap.sh`).
- The log bucket and the Workload Identity pool: `global` (the log bucket holds masked lines; the pool holds no
  personal data).
- Backups: multi-region unless `--backup-location` is pinned ([security-of-processing.md](security-of-processing.md)).

Two vendors are regional by configuration: Nylas (`NYLAS_BASE_URL`, `api.eu.nylas.com` for an EU application) and
Recall (`RECALL_BASE_URL`, `eu-central-1`; each region is its own account and key). The rest have no region in code
(governance decision F2).

### Options
| | A. Separate stack per region | B. One stack, a region per workspace |
|---|---|---|
| What it is | Another Cloud Run service, Cloud SQL instance, bucket, Vertex region and vendor accounts, on its own origin (for example `eu.uncava.com`) | One app routing each workspace's reads and writes to a database and bucket in its region |
| Identity | A user is per stack; someone in a US firm and an EU firm has two accounts | Users are global, so the user table and sign-in live somewhere, which is itself a transfer |
| Tenant isolation | Unchanged: RLS, `TenantTableGuard`, one database per stack | Every query must reach the right database first; a routing bug is a cross-region leak |
| One-origin rule (SPA, API, refresh cookie) | Holds per stack | Holds |
| Code change | None beyond configuration | Large: a data-source router, per-region buckets, regional vendor clients per workspace |
| Cost | A second stack to deploy, upgrade and monitor | One stack, more complex |

### Decision (proposed)
**A, a separate stack per region**, when the first customer needs it. It needs no code: the app already takes its
region, database, bucket and vendor endpoints from configuration. The work is ops.

### What a new stack needs
- A Google Cloud project or region: Cloud Run, Cloud SQL (with `--backup-location` in the same jurisdiction), the
  documents bucket, a log bucket in-region, Secret Manager keysets of its own (never copied from the US stack).
- `deploy.yml`: the region and instance as variables rather than literals, and a second deploy target.
- Vertex in-region (section 1's change), and a check of which models it serves.
- Nylas EU application (`NYLAS_BASE_URL`), Recall EU account (`RECALL_BASE_URL`), and Resend, Stripe, Bright Data,
  HarvestAPI and ContactOut confirmed per F2 or accepted as transfers.
- Its own origin: OAuth client redirect URIs, the MCP endpoint and its signing key, Stripe and vendor webhooks.
- **The Chrome extension** fixes its workspace origin at build time (`LM_WORKSPACE_ORIGIN`, in its host permissions),
  so a second stack needs a second extension build or a manifest naming both origins.
- The company universe (`app_lm_apollo_companies`) loaded by the ETL into each stack; it holds companies, not people.
- The website: a way to reach the right stack's sign-in.

## 3. Vendor DPAs

A business checklist, tracked here until the sub-processor list carries the answers (its "DPA signed" column):

| Vendor | DPA | Note |
|---|---|---|
| Google Cloud | [signed / accepted online] | Cloud Data Processing Addendum |
| Nylas | [ ] | |
| Recall.ai | [ ] | `docs/integrations/registration.md` asks for it when the account is opened |
| Resend | [ ] | |
| Stripe | [ ] | ⚖️ may act as controller for payments |
| Bright Data, HarvestAPI, ContactOut | [ ] | sources as well as recipients |
| Mapbox | [ ] | |
