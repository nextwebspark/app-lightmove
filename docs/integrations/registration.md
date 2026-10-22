# Registering Uncava's provider apps (#649)

The shared apps behind **Settings → Integrations → Shared app**, one per provider, and the Recall.ai account.
Every value an admin console asks for is below; the reviews take weeks, so start them before anything else.
Environments: production `https://beta.uncava.com`, staging `<staging base url>`, local `http://localhost:5173`.

## Redirect URIs (every provider, every environment)

| Provider | Redirect URI | Also |
|---|---|---|
| Google | `<base>/api/v1/outreach/mailbox/callback` | — |
| Microsoft | `<base>/api/v1/outreach/mailbox/callback` | `<base>/settings/integrations` (admin consent returns here) |
| Zoom | `<base>/api/v1/outreach/zoom/callback` | — |

Settings → Integrations prints the same URIs for the running deployment (`ProviderAppSetup`), so a typo shows there.

## Microsoft (Entra ID)

1. **Entra admin center → App registrations → New registration.**
   - Name: `Uncava`.
   - Supported account types: **Accounts in any organizational directory (multitenant)**. Not personal accounts:
     the gateway signs in at `/organizations`.
   - Redirect URI: platform **Web**, the URIs above, one per environment.
2. **Certificates & secrets → New client secret.** Note its expiry (24 months at most) in the ops calendar; a
   lapsed secret stops every shared-app mailbox until it is replaced.
3. **API permissions → Add → Microsoft Graph → Delegated:** `offline_access`, `User.Read`, `Mail.ReadWrite`,
   `Mail.Send`, `Calendars.ReadWrite`. Do **not** grant admin consent here: it would only cover Uncava's own tenant.
4. **Branding & properties:** logo, home page `https://uncava.com`, terms and privacy URLs (see *Legal*).
5. **Publisher verification:** Branding & properties → *Add MPN ID to verify publisher*.
   - Needs a Microsoft Partner Network (Microsoft AI Cloud Partner Program) ID, free, and the publisher domain
     verified in the tenant (DNS TXT record on `uncava.com`).
   - Until verified, customer admins see "unverified" and many tenants block user consent outright.
6. Copy the **Application (client) ID** and the secret's **Value** into Secret Manager (below).

## Google (Cloud console)

1. **New project** `uncava-mail` under the Uncava organisation.
2. **APIs & Services → Library:** enable **Gmail API** and **Google Calendar API**.
3. **OAuth consent screen (Google Auth Platform):**
   - User type **External**; publishing status **Testing** until verification passes. In Testing, only the
     listed test users (up to 100) can connect, and their refresh tokens expire after 7 days.
   - App name `Uncava`, support email, logo, home page, privacy policy and terms URLs, authorised domain `uncava.com`.
   - **Data access (scopes):** `openid`, `email`, `https://www.googleapis.com/auth/gmail.send`,
     `https://www.googleapis.com/auth/gmail.readonly`, `https://www.googleapis.com/auth/calendar.events`,
     `https://www.googleapis.com/auth/calendar.freebusy`.
4. **Clients → Create client → Web application**, the redirect URIs above. Copy the client id and secret.
5. **Verification** (Publish app → Prepare for verification):
   - brand verification, then sensitive-scope verification (`gmail.send`, `calendar.events`) with a demo video
     of the consent screen and each scope in use;
   - `gmail.readonly` (a reply's words, in threads Uncava started; it replaced `gmail.metadata` in outreach
     phase 2 — add it to the app before deploying that release) is a **restricted** scope: production use for more than 100 users needs the annual CASA
     security assessment. Decide on it separately, once Google customers justify the cost — a customer's own
     Internal app (see `google-own-app.md`) needs none of this.

## Zoom (App Marketplace)

1. **Develop → Build App → General App**, user-managed.
2. **Basic information:** OAuth redirect URL and the allow list: the Zoom URI above, per environment.
3. **Scopes:** `meeting:write:meeting`, `meeting:update:meeting`, `meeting:delete:meeting`, `user:read:user`,
   `user:read:token`. Confirm the last against Zoom's on-behalf-of (OBF) token docs: it is what #651's bot will
   need to join an external meeting.
4. Copy the **Client ID** and **Client Secret** (Production; the Development pair works for testing).
5. **Submit for review:** privacy policy, terms, support URL, a demo video of connect → Book a call with Zoom →
   disconnect, and test credentials for the reviewer.

## Recall.ai

1. Open the account in the chosen region: US (`https://us-east-1.recall.ai`) or EU Frankfurt
   (`https://eu-central-1.recall.ai`). Each region is a separate account with its own key. Sign the DPA.
2. **API keys → create** one per environment.
3. **Webhooks → add endpoint** `<base>/api/v1/outreach/webhooks/recall`, subscribed to `calendar.update` and
   `calendar.sync_events`. Copy its signing secret (`whsec_…`).
4. Apply for the startup programme if eligible (it matters once #651 records calls).

## Secrets and switches

In the deploy project, each granted `roles/secretmanager.secretAccessor` to the runtime service account:

```bash
printf %s "$VALUE" | gcloud secrets create lightmove-google-mail-client-id        --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-google-mail-client-secret    --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-microsoft-mail-client-id     --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-microsoft-mail-client-secret --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-zoom-client-id               --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-zoom-client-secret           --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-recall-api-key               --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-recall-webhook-secret        --data-file=-
printf %s "$VALUE" | gcloud secrets create lightmove-recall-bot-webhook-secret    --data-file=-
tinkey create-keyset --key-template AES256_GCM --out-format json \
  | gcloud secrets create lightmove-credential-keyset --data-file=-
```

Then the repository variables `deploy.yml` reads, per environment:

| Variable | Value |
|---|---|
| `GOOGLE_MAIL_ENABLED`, `MICROSOFT_MAIL_ENABLED`, `ZOOM_ENABLED`, `RECALL_ENABLED` | `true` once that provider's secrets exist |
| `RECALL_BASE_URL` | the EU URL, for an EU account |
| `NOTETAKER_BACKUP_ENABLED` | `true` once `lightmove-recall-bot-webhook-secret` exists — the Notetaker's backup recorder, whose bots report to `/api/v1/notetaker/webhooks/recall` (subscribe that endpoint to `bot.*`, `transcript.done` and `transcript.failed` in Recall's dashboard). Each workspace still turns it on in Settings → Integrations |
| `CREDENTIAL_ENCRYPTION_ENABLED` | `true` once `lightmove-credential-keyset` exists (needed before any refresh token can be stored) |
| `OUTREACH_GATEWAY` | stays `nylas` until rollout (#650) |
| `*_OWN_APP_GUIDE_URL`, `*_SHARED_APP_GUIDE_URL` | where the guides in this folder are published |

## Uncava Notetaker (epic #949)

Our own bot needs no provider app — it joins as a guest — but its job does need setting up once per project:

1. `MEETINGBOT_ENABLED=true` deploys the Cloud Run Job `lightmove-meetingbot` from `apps/meetingbot/Dockerfile`
   and starts the API with `NOTETAKER_RUNNER=cloud-run-job`.
2. Grant the API's runtime service account `roles/run.developer` **on that job alone** (it needs
   `run.jobs.runWithOverrides`), never on the project:
   ```bash
   gcloud run jobs add-iam-policy-binding lightmove-meetingbot --region "$REGION" \
     --member "serviceAccount:$GCP_RUNTIME_SA" --role roles/run.developer
   ```
3. **No run token rides the execution (#956).** With `NOTETAKER_BOT_SERVICE_ACCOUNT` set (deploy.yml sets it to
   `MEETINGBOT_SA`), the API starts each execution with `NOTETAKER_TOKEN_AUDIENCE` in place of the token. The bot asks
   the metadata server for an ID token of its own service account for that audience (`WEB_BASE_URL` unless
   `NOTETAKER_BOT_TOKEN_AUDIENCE` says otherwise), and exchanges it once at `/internal/notetaker/runs/{id}/token`. The
   API checks Google's signature, the issuer, the audience, the lifetime and the account's email before it answers,
   and hands a run's token out only once. `MEETINGBOT_SA` still needs no role: every service account can mint its own
   ID token. Execution viewers can no longer read a token, though the join link is still in the override.
4. **A lifecycle rule on the documents bucket** as the backstop for the purge: delete objects under `notetaker/`
   older than 2 days. The API deletes a removed run's pieces on its next pass, and every piece once it is a day old
   (`lightmove.notetaker.media-max-age`); the rule catches anything a purge never reached.
   ```bash
   cat > /tmp/notetaker-lifecycle.json <<'JSON'
   {"rule":[{"action":{"type":"Delete"},"condition":{"age":2,"matchesPrefix":["notetaker/"]}}]}
   JSON
   gcloud storage buckets update "gs://$DOCUMENTS_BUCKET" --lifecycle-file=/tmp/notetaker-lifecycle.json
   ```
   A bucket that already has lifecycle rules: merge this one into them, since `--lifecycle-file` replaces the set.
5. **The daily canary (#956)**, once the job is: two standing test meetings, the deploy variables
   `NOTETAKER_CANARY_ENABLED=true`, `NOTETAKER_CANARY_MEET_URL`, `NOTETAKER_CANARY_TEAMS_URL`, `PUBLIC_BASE_URL` and
   `NOTETAKER_CANARY_SA` (a service account of its own, no role),
   a Cloud Scheduler trigger and an alert. The meetings, the trigger and the alert are `docs/notetaker.md`'s
   "Canary" runbook.

## Legal

The reviewers read the privacy policy, and the signup page already links `/privacy` — **which serves nothing yet**.
Publish it before submitting any review, and add:

- **Google, Microsoft and Zoom data access:** Uncava reads the sender of messages in threads it sent (never message
  content), sends email the consultant composed, reads calendar events only to keep those with people in the
  workspace (title, time, attendees, join link — never descriptions or bodies), reads free/busy, and creates calendar
  events and Zoom meetings the consultant books. Refresh tokens are stored encrypted and revoked on disconnect.
- **Google's Limited Use** statement: use of data from Google APIs adheres to the Google API Services User Data
  Policy, including the Limited Use requirements.
- **Uncava Notetaker records calls.** It joins as "Uncava Notetaker", posts in the call's chat that it is recording
  before it records anything, and leaves without recording if it cannot post that notice; it never turns on a
  microphone or camera. The policy should say so, and who may remove it (anyone on the position, from the drawer).
- **Recall.ai as a sub-processor** that receives calendar access (the OAuth app's keys and a refresh token) for
  workspaces syncing calendars through Recall.

## Done when (#649)

- [ ] Microsoft publisher verified
- [ ] Zoom app approved
- [ ] Google app in Testing with verification submitted
- [ ] Privacy policy published at `/privacy` with the above
- [ ] Guides in this folder published and their URLs set
- [ ] Recall account, key and webhook secret in the chosen region; DPA signed
