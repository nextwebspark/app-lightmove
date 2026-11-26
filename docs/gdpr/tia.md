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
| Google Cloud: Vertex AI | dossiers, notes, CV text, transcripts (contacts redacted), briefs, assistant chats | **`global`**: Google may serve a request from any region | [as above] | kept at `global` for now: the Flash models are global-only ([residency-adr.md](residency-adr.md) §1) |
| Google Cloud Logging | masked log lines; full request URLs | `global` bucket | [as above] | 30 days |
| Nylas | mailboxes, emails, replies, calendars | US by default (`api.us.nylas.com`); EU available | [mechanism] | `NYLAS_BASE_URL` selects |
| Recall.ai | call audio and video, transcription, calendar tokens | `us-east-1` by default; EU available | [mechanism] | `RECALL_BASE_URL` selects |
| Resend | users' emails; candidate names in notification emails | [Region] | [mechanism] | |
| Stripe | billing data | [Region] | [mechanism] | |
| Bright Data, HarvestAPI, ContactOut | LinkedIn URLs and slugs, search criteria | [Region] | [mechanism] | sources as well as recipients |
| Mapbox | company city and country; users' IP from tiles | US | [mechanism] | |

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

- Pin Vertex AI to a named region: one setting, held back while the Flash models are global-only
  ([residency-adr.md](residency-adr.md) §1, with the triggers to revisit).
- Nylas and Recall EU endpoints for EU customers.
- A regional stack (EU or GCC) per `data-protection-plan.md` §5 3.4: proposed in [residency-adr.md](residency-adr.md) §2.

## 5. Conclusion

[Counsel: whether the mechanisms with the measures above give essentially equivalent protection, per recipient.]

## UAE addendum (2026-11-19)

_Draft for counsel; not legal advice. Nothing above this heading has changed._

§§1 to 5 above assess transfers of **EU, EEA and UK** data to the United States. They do not assess transfers of UAE
data. This addendum records the facts for that second assessment, so counsel can do it. The destination is the same
for most recipients; the legal test differs by regime and is counsel's [Counsel to confirm].

**No UAE or GCC region is in use.** Every deployed component is in the US or global. A Gulf region (`me-central2`
Dammam, `me-central1` Doha) is only a candidate in `data-protection-plan.md` §3.4, and a separate regional stack is only
**proposed** in [residency-adr.md](residency-adr.md) §2 ("when the first customer needs it"). Nothing has been decided or
built. Data of a person in the UAE therefore leaves the country when a customer adds it.

### 1. UAE data to the US and the rest of the world

| Recipient | Data | Location | Mechanism under PDPL / DIFC / ADGM | Notes |
|---|---|---|---|---|
| Google Cloud Run, Cloud SQL, Cloud Storage | everything | `us-central1` | [Counsel to confirm] | Google-managed keys; no CMEK; applies to every workspace, including UAE customers |
| Google Vertex AI | dossiers, notes, CV text, transcripts (contacts redacted), briefs, assistant chats | `global`: may be served from any region | [Counsel to confirm] | unchanged by this addendum; see [residency-adr.md](residency-adr.md) §1 |
| Google Cloud Logging | masked log lines; full request URLs | `global` bucket | [Counsel to confirm] | 30 days |
| Recall.ai | call audio and video, transcript, calendar tokens | `us-east-1` by default; an EU region is selectable through `RECALL_BASE_URL` | [Counsel to confirm] | the EU option moves the data to the EU, not to the UAE; whether that helps depends on the mechanism [Counsel to confirm]; a call recording is among the highest-risk items (the lawyer, message 7) |
| ContactOut | LinkedIn URLs, names and search criteria we send; contact data it returns | AWS `us-west-1` (vendor statement, unverified) | controller-to-controller SCCs under its DPA §3 for what it returns [Counsel to confirm module and acceptance]; processor terms for what we send [Counsel to confirm] | an independent controller of what it returns: [ropa.md](ropa.md) UAE addendum §B; written compliance approval to store its data requested 2026-11-19 |
| Nylas | mailboxes, emails, replies, calendars | US by default (`api.us.nylas.com`); EU available through `NYLAS_BASE_URL` | [Counsel to confirm] | |
| Resend | users' emails; candidate names in notification emails | [Region] | [Counsel to confirm] | |
| Stripe | billing data | [Region] | [Counsel to confirm] | |
| Bright Data, HarvestAPI | LinkedIn URLs and slugs, search criteria | [Region] | [Counsel to confirm] | sources as well as recipients |
| Mapbox | company city and country; users' IP from tiles | US | [Counsel to confirm] | |
| Firebase Hosting (website) | visitor logs | [Region] | [Counsel to confirm] | |

### 2. Transfer mechanisms under the three regimes

For counsel. Uncava has not established which of these is available or sufficient, and none is asserted here.

| Regime | Questions [Counsel to confirm] |
|---|---|
| Federal PDPL | what the transfer rules permit outside the UAE (adequacy, contract, consent); whether any list of adequate countries exists; whether consent to a transfer can be built into the consent register proposed on 2026-11-10 |
| DIFC DP Law 2020 | adequacy, appropriate safeguards (standard contractual clauses, binding rules), and exceptions; whether a DIFC customer may require us to pass terms on |
| ADGM DP Regulations 2021 | the equivalent adequacy and safeguards rules; whether the ADGM's own clauses are expected |

The lawyer's recommendation is a single highest common standard (message 1). If a UAE or DIFC/ADGM customer requires
data in-country or in a named region, the only path today is the regional stack in [residency-adr.md](residency-adr.md)
§2 plus a UAE or Gulf Vertex region, which has no stated availability for the models used here.

### 3. What the existing sections do and do not cover

| Existing section | UAE position |
|---|---|
| §2 (laws and practices of the destination) | US law only; not extended here. Whether the same US-law facts bear on a UAE test is [Counsel to confirm] |
| §3 (supplementary measures) | applies equally: contacts redacted from model input, sealed secrets, masked logs |
| §4 (options) | adds a Gulf-region stack; no code change, the work is ops |
| §5 (conclusion) | not given for the UAE; [Counsel: per recipient] |
