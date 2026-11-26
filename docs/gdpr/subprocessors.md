# Sub-processors and other recipients

_Draft for counsel (#930, #925, epic #917). Engineering facts read from the code on 2026-11-13; not legal advice._

The app-side list of every outside service that receives personal data, what it receives and from which class. The
website's public list (`uncava-website` `src/content/legal/subprocessors.md`, nextwebspark/uncava-website#29) is
written from this one; change both together. Transfer mechanisms and the legal role of each provider are ⚖️ and
listed in [governance-decisions.md](governance-decisions.md); [tia.md](tia.md) assesses the transfers.

**Role** says whose provider it is for the processing it does:
- **Sub-processor**: Uncava engages it to process workspace data for the customer (Art. 28(2)).
- **Uncava's processor**: it processes data Uncava controls (accounts, billing, its own vendor cache).
- **Customer's own provider**: the consultant connects their own account; Uncava acts through it ⚖️.

## How a change is announced

The contract promises 30 days' notice of a new sub-processor (decision F7, #1491). It is built, not a promise kept by
hand:

1. **Change the lists first.** Add the provider here and to the website's public list (`uncava-website`
   `src/content/legal/subprocessors.md`), and publish the website, so the notice can link to it.
2. **Publish the notice** from Platform → Sub-processors → Publish a change (`docs/platform-console.md`): provider,
   what it receives, purpose, region, the day it takes effect — at least 30 days from today, counted in UTC — and the
   https link to the published list. It asks for a fresh two-factor code.
3. **Every workspace admin is emailed** within a minute or so, by a scheduled pass rather than the publish itself
   (`lightmove.platform.subprocessor-notice-announce`), from Uncava's account sender, once each: the active holders of
   `WORKSPACE_MANAGE` in every workspace not deleted, an account that can no longer sign in left out. The email names
   the provider, what it receives, the purpose, the region, the date, the published list and an Object link. An email
   the provider refuses is retried, an hour later and then less often (a day at most), until the change takes effect
   (`lightmove.platform.subprocessor-notice-retry`); an admin who has left by then is no longer owed one. An admin
   who joins after that pass is not emailed, but sees the change in Settings.
4. **An admin may object** in Settings → Data & retention → Sub-processor changes, which the email's link opens, until
   the day it takes effect; the workspace's objection can be changed until then and is refused after
   (`SUBPROCESSOR_NOTICE_CLOSED`). One objection per workspace, the latest reason and who wrote it. Audited
   `SUBPROCESSOR_OBJECTION_RECORDED` in the workspace's audit log.
5. **Uncava reads the objections** on the notice's page in the console (workspace, admin, reason, date) and answers
   each customer before the date, as the contract's objection clause says ⚖️. Nothing is switched off automatically:
   what an objection leads to (a different provider for that customer, or ending the contract) is the business's
   decision with the customer.

Tables: V248 (`docs/architecture/database.md`). What is kept and for how long: [retention-schedule.md](retention-schedule.md).

## Infrastructure

| Provider | Service | What it receives | Where in code | Region | Role |
|---|---|---|---|---|---|
| Google Cloud | Cloud Run | every request and response | `.github/workflows/deploy.yml` (`REGION`) | `us-central1` | sub-processor and Uncava's processor |
| Google Cloud | Cloud SQL `bright-gcc` | the whole database | `application.yml` datasource | `us-central1`, single zone | sub-processor and Uncava's processor |
| Google Cloud | Cloud Storage `hak-talent-mapping-lightmove-documents` | CVs, documents, photos, email attachments, workspace export zips | `core/storage/service/GcsDocumentStore` | `us-central1` | sub-processor |
| Google Cloud | Vertex AI (Gemini chat, `text-embedding-004`) | candidate dossiers, notes, CV text and call transcripts with contacts taken out (`ContactRedaction`); position briefs; assistant chats | `core/llm/**`, `CandidateAiEnricher`, `CandidateNationalityClassifier`, `CandidateScorecardDrafter`, `MeetingSummariser`, `AssistantService` | **`global`** (`GOOGLE_CLOUD_LOCATION` unset; kept until the triggers in [residency-adr.md](residency-adr.md) §1) | sub-processor |
| Google Cloud | Secret Manager | keysets and provider secrets, no personal data | `deploy.yml` `--set-secrets` | project-level | — |
| Google Cloud | Cloud Logging | masked application lines (`PersonalDataLogMasker`); Cloud Run's request log keeps full URLs, `?q=` searches included | `logback-spring.xml` | `_Default` bucket, `global`, 30 days | sub-processor and Uncava's processor |
| Resend | account email | users' names and addresses (verification, reset, invitation, lockout, two-factor, billing, export and purge notices); notification emails name candidates (`NotificationEmails`) | `core/email/service/ResendEmailSender` | [Region] | sub-processor and Uncava's processor |
| Stripe | billing | workspace name and id; Checkout collects the payer's card, address and tax id | `billing/payment/service/StripePaymentGateway`, `StripeSeatSync` | [Region] | Uncava's processor ⚖️ (Stripe may act as controller for payments) |
| Mapbox | maps, geocoding | a company's city and country (server); the browser loads styles and tiles and opens a billing session, so the user's IP (client). Performance metrics are off (`MAPBOX_PRIVACY_OPTIONS` in `lib/mapboxPrivacy.ts`, #929) | `geocoding/service/MapboxGeocoder`; `TalentMapGlobe.tsx`, `HubGlobe.tsx` | US | sub-processor |

## Company and executive data

| Provider | What it receives | Where in code | Region | Role |
|---|---|---|---|---|
| Bright Data | a LinkedIn URL or slug as a lookup key; company and function searches | `BrightDataProfileEnricher`, `BrightDataPersonProfiles`, `BrightDataSearch`, `BrightDataCompanyEnricher`, `BusinessSearchProfileIndex` | [Region] | Uncava's processor for the shared vendor cache; sub-processor when a workspace asks ⚖️ |
| HarvestAPI | a LinkedIn profile URL | `HarvestApiProfileEnricher` | [Region] | as Bright Data |
| ContactOut | a LinkedIn URL (email and phone look-up); company, title and name searches | `ContactOutContactFinder`, `ContactOutPeopleClient`, `ContactOutLiveProfiles`, `StrategyPeopleService` | [Region] | as Bright Data |
| Apollo | nothing from the app. `app_lm_apollo_companies` is loaded by an ETL and holds companies, not people | — | US | not a recipient |

## Email, calendar and meetings

| Provider | What it receives | Where in code | Region | Role |
|---|---|---|---|---|
| Nylas | the mailbox grant, emails sent, replies in threads Uncava sent, calendar events; on the booking page, the visitor's IP and the details they enter (its widget's error reporter is off, `enableUserFeedback={false}`, #929) | `outreach/service/NylasMailboxGateway` | `NYLAS_BASE_URL`, default US (`api.us.nylas.com`); EU available | sub-processor |
| Recall.ai | calendar OAuth keys and a refresh token; each call's audio and video; Recall's transcription | `notetaker/service/RecallBotClient`, `outreach/service/RecallCalendarClient` | `RECALL_BASE_URL`, default `us-east-1`; EU available | sub-processor |
| Google (Gmail, Calendar) | sends the consultant's emails; reads the text of replies in threads Uncava sent (`gmail.readonly`); calendar events, free/busy | `outreach/service/GoogleMailboxGateway` | Google | customer's own provider ⚖️ |
| Microsoft (Outlook, Graph) | as Google (`Mail.ReadWrite`, `Calendars.ReadWrite`) | `outreach/service/MicrosoftMailboxGateway` | Microsoft | customer's own provider ⚖️ |
| Zoom | meetings the consultant books | `outreach/service/ZoomClient` | US | customer's own provider ⚖️ |

## Sign-in

| Provider | What it receives | Where in code | Role |
|---|---|---|---|
| Google, LinkedIn | the user's name, email and picture, returned to Uncava at sign-in | `core/security/service/OAuth2LoginSuccessHandler` | the user's own provider |

## Contracts and configuration

What each provider is bound by, and the setting that decides where it runs. **DPA** and **Transfer mechanism** are ⚖️
(governance decisions F1, F2) and the business's to fill; [residency-adr.md](residency-adr.md) holds the vendor DPA
checklist and what a second region would change. A base URL with no setting is fixed in the client's `*Settings` record.

| Provider | DPA signed | Transfer mechanism | Setting that decides region or endpoint |
|---|---|---|---|
| Google Cloud (Cloud Run, Cloud SQL, Storage, Logging) | [y/n] | [DPF / SCCs] | `deploy.yml` `REGION`; the datasource's instance name; `STORAGE_BUCKET`, created in `GCP_REGION` (`ops/gcp/bootstrap.sh`) |
| Google Cloud Vertex AI | [y/n] | [DPF / SCCs] | `GOOGLE_CLOUD_LOCATION` (unset, so `global`; [residency-adr.md](residency-adr.md) §1), `LLM_CHAT_MODEL`, `ASSISTANT_MODEL` |
| Resend | [y/n] | [DPF / SCCs] | `RESEND_API_KEY`; base URL in `ResendSettings` |
| Stripe | [y/n] | [DPF / SCCs] | `STRIPE_SECRET_KEY` |
| Mapbox | [y/n] | [DPF / SCCs] | `MAPBOX_PUBLIC_TOKEN`, `MAPBOX_GEOCODING_TOKEN`; base URL in `MapboxSettings` |
| Bright Data | [y/n] | [DPF / SCCs] | `BRIGHTDATA_API_KEY`; base URL in `BrightDataSettings` |
| HarvestAPI | [y/n] | [DPF / SCCs] | `HARVESTAPI_API_KEY`; base URL in `HarvestApiSettings` |
| ContactOut | [y/n] | [DPF / SCCs] | `CONTACTOUT_API_KEY`; base URL in `ContactOutSettings` |
| Nylas | [y/n] | [DPF / SCCs] | `NYLAS_BASE_URL` (US by default, `api.eu.nylas.com` for an EU application) |
| Recall.ai | [y/n] | [DPF / SCCs] | `RECALL_BASE_URL` (`us-east-1` by default; `eu-central-1` is a separate account) |

## Recipients the customer chooses

API keys (`/api/v1/public/**`) and AI apps connected over MCP (`/api/v1/mcp`, `MCP_ENABLED`) read what their scopes
and their owner's permissions allow. They are the customer's recipients, not Uncava's sub-processors.

## Not a recipient

- **Cloudflare**: DNS only, proxy off. No personal data passes through it.
- **Google Fonts**: the SPA's fonts are self-hosted (`@fontsource`, #929), so a page load reaches no font host.
- **Firebase Hosting** serves the website (uncava.com), not the app; it is on the website's own list.

## Holding the list to the code

The SPA's Content-Security-Policy (`core/security/config/SpaContentSecurityPolicy`, #929) names every origin a page may
reach: scripts from this origin only, `connect-src` to Mapbox and the configured Nylas host, images from any https host
(company logos, profile photos). A new script, call or frame to another host is refused by the browser until that policy
names it, which makes adding one a reviewed change to both the policy and this list. **Images are deliberately not held
to the list**: `img-src https:` (with the inline styles the editor needs) means a page can still load a picture, or a
CSS `url()`, from any https host, so an image recipient is caught by review, not by the browser.
