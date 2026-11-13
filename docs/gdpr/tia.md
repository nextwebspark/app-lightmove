# Transfer impact assessment: transfers to the United States

_Draft for counsel (#930, #925, epic #917). Engineering facts read from the code on 2026-11-13; not legal advice. The
legal assessment of US law is counsel's; this file gives the facts it needs._

## 1. What is transferred, and how

Uncava runs in Google Cloud `us-central1`, so every personal record of an EU, EEA or UK person is transferred to the
US when a customer adds it. Onward transfers go to the providers below. The provider list itself is
[subprocessors.md](subprocessors.md).

| Recipient | Data | Location (from code) | Mechanism ⚖️ | Notes |
|---|---|---|---|---|
| Google Cloud: Cloud Run, Cloud SQL, Cloud Storage | everything | `us-central1` | [EU-US DPF / SCCs module 2 and 3 / UK IDTA or addendum] | Google-managed keys; no CMEK |
| Google Cloud: Vertex AI | dossiers, notes, CV text, transcripts (contacts redacted), briefs, assistant chats | **`global`**: Google may serve a request from any region | [as above] | #925 pins it; `application.yml` notes `us-central1` throttled the newer models |
| Google Cloud Logging | masked log lines; full request URLs | `global` bucket | [as above] | 30 days |
| Nylas | mailboxes, emails, replies, calendars | US by default (`api.us.nylas.com`); EU available | [mechanism] | `NYLAS_BASE_URL` selects |
| Recall.ai | call audio and video, transcription, calendar tokens | `us-east-1` by default; EU available | [mechanism] | `RECALL_BASE_URL` selects |
| Resend | users' emails; candidate names in notification emails | [Region] | [mechanism] | |
| Stripe | billing data | [Region] | [mechanism] | |
| Bright Data, HarvestAPI, ContactOut | LinkedIn URLs and slugs, search criteria | [Region] | [mechanism] | sources as well as recipients |
| Mapbox | company city and country; users' IP from tiles | US | [mechanism] | |
| Google Fonts | users' IP | global | [mechanism] | ends with #929 |

## 2. Laws and practices of the destination ⚖️

For counsel: whether each recipient is DPF-certified; whether it is an "electronic communications service provider"
under FISA 702 (Google, Microsoft, Nylas and Recall likely are); and whether EO 12333 access to data in transit is
relevant given TLS throughout. Engineering facts that bear on it:

- All traffic between the browser, Cloud Run, Cloud SQL and the providers is TLS.
- Uncava does not hold the encryption keys for data at rest (Google-managed), so Google could be compelled to produce
  plaintext. CMEK would not change that for a compelled provider holding the KMS too ⚖️.
- The data is professional (roles, employers, contact details), not communications content, except outreach emails,
  replies and call transcripts.

## 3. Supplementary measures already in place

| Measure | Effect |
|---|---|
| Contacts taken out of every model input (`ContactRedaction`); compensation and custom columns never sent (`CandidateDossier`) | less reaches Vertex |
| Provider refresh tokens and second-factor secrets sealed with Tink, keyset in Secret Manager | a database copy alone cannot open the mailboxes |
| Suppression entries stored as keyed hashes | the list reveals nobody |
| Masked logs; ids-only audit details | logs and the trail name no one |
| Vertex AI does not train on customer data (Google's terms) | no reuse by the provider |
| Recall audio deleted at Recall once its transcript is filed | short-lived at the recipient |

## 4. Options that would reduce the transfer

- Pin Vertex AI to a named region (#925 first box) — one setting.
- Nylas and Recall EU endpoints for EU customers.
- A regional stack (EU or GCC) per `data-protection-plan.md` §5 3.4 — an ADR for #925.

## 5. Conclusion

[Counsel: whether the mechanisms with the measures above give essentially equivalent protection, per recipient.]
