# Uncava – Data Protection: Thread Capture and Gap Analysis

**Source:** Gmail thread "data protection - Uncava" (Aleksandra Mizińska, Alac Partners, to Hakan and Alok), 29 Oct – 19 Nov 2026.
**Purpose:** Capture everything in the thread in one place, show what is still open, and prepare Alok's reply.

> **Scope note.** The thread is about **UAE law** (federal PDPL, DIFC, ADGM), not the EU GDPR. DIFC and ADGM are modelled on GDPR, so most work overlaps, but the key difference is that PDPL has **no "legitimate interest" basis**: every data type needs consent or a listed exception. Aleksandra recommends building to the highest common standard: PDPL consent approach + DIFC/ADGM rules on service providers, documentation, transfers and AI. If EU/Irish clients or candidates are in scope, a separate GDPR check is needed.

---

## 1. Status at a glance

| Area | Status in thread | Decision needed from Alok/Hakan |
|---|---|---|
| Overall approach (highest common standard) | Proposed 29 Oct | Confirm agreement |
| KSA law | Parked | Is KSA a priority alongside the UAE? |
| 1. Data sources (ContactOut) | Risk analysis sent 10 Nov | Choose model/mitigations |
| 2. Candidate perspective (consent, retention) | Proposal sent 10 Nov | Approve statuses, retention, system features |
| 3. Clients (documents) | Flagged 13 Nov, no action until areas 1, 2, 4 close | Alok: report which self-prepared documents already exist |
| 4. AI and call recordings | Inventory sent 13 Nov; changes agreed 19 Nov | Confirm and schedule implementation |

---

## 2. Regulatory background (29 Oct)

| Regime | Applies to | Key points |
|---|---|---|
| Federal PDPL (Decree-Law 45/2021) | Mainland UAE; also non-UAE companies processing data of people in the UAE | Consent-based, limited exceptions (e.g. data the person made public). No general legitimate-interest basis. |
| DIFC Data Protection Law (2020) | DIFC companies | Closely modelled on GDPR; detailed processor requirements. Separate **Regulation 10** on AI systems processing personal data, fully enforced since Jan 2026. |
| ADGM Data Protection Regulations (2021) | ADGM companies | Also GDPR-modelled. |

Other points raised:
- PDPL enforcement is still taking shape; many market practices are untested by the regulator.
- Tools sold to large companies must pass their compliance checks, even if an agency could accept the risk.
- Clients in DIFC/ADGM may be legally required to use compliant providers and pass requirements to Uncava by contract.
- KSA has its own law, to be addressed later.

**Proposed four areas:** (1) data sources, vendors, hosting, transfers outside the UAE; (2) candidate perspective; (3) working with clients; (4) AI and call recordings. Aleksandra asked whether to prepare recommendations for each.

---

## 3. Area 1 – Data sources: ContactOut (10 Nov)

**Roles.** Alok confirmed Uncava buys ContactOut access and passes the data to clients. That makes **Uncava a controller** for that data, responsible for passing it on lawfully (consent, or the candidate made it public).

| Data | Risk | Reason |
|---|---|---|
| Professional profiles (Find executives / plug-in) | Moderate | Candidates published it themselves, a fairly solid basis for recruitment sharing. |
| Private emails and phone numbers (Find email/phone) | **High** | Not published by the candidate, no consent, no clear legal basis. Risk is shared: client for using the contact, Uncava for supplying it. |

**Candidates must be told.** Uncava cannot contact each candidate and does not control client messages. Mitigations proposed:
1. Contract with ContactOut: Uncava may pass data on, and it was collected lawfully.
2. Contract with clients: recruitment use only, no onward sharing, state the data source at first contact.
3. Public Uncava privacy notice on the website (common under GDPR when individual notice is disproportionate effort; lawyers to confirm for PDPL).
4. In product: automatic, non-removable email footer with data source and privacy-notice link.

**Alternative model.** Each client holds its own ContactOut account/key and Uncava only connects. The client buys the data, Uncava becomes a processor. Much lower risk, but harder to sell and a business-model change (technical feasibility to be confirmed).

**Related:** Alok's earlier suggestion to keep a single profile provider.

**Question to Alok:** thoughts on the above.

---

## 4. Area 2 – Candidate perspective: consent and retention (10 Nov)

Principle: keep personal data only as long as needed for its purpose and be able to justify it. These are Uncava defaults; clients can change them (the client is responsible for its candidates' data).

| Status | Meaning | Default retention | Why |
|---|---|---|---|
| Grey | No consent: not contacted / no reply | 6 months from adding | Only basis is the public profile; a typical mapping or mandate finishes within this time. |
| Light green | Consent for current process only | Until mandate closes, then 12 months | UAE probation can last up to 6 months, so the shortlist may be needed again. |
| Dark green | Consent for current process and future roles | Consent valid 24 months | Market standard for talent pools; renewal keeps consent current. |
| Orange | Consent expired (mandate closed or validity ended) | 3 months | Last attempt to renew before deletion. |
| Red | Consent withdrawn: do not contact | – | Processing must stop without undue delay. Keep only name and profile link on a "do not contact" list to prevent re-adding. |

**Consent record, entered manually:** consent type (light/dark green); channel (LinkedIn, email, phone, WhatsApp, form); evidence (reply, screenshot, call note, email).
**Recorded automatically:** mandate; date and time (default today, editable); privacy-notice version the candidate received; who recorded it and when; expiry date (future-roles consent).

**System must-haves**
- Export of the consent register (audits)
- Consents included in the candidate access-request report
- Privacy-notice version management
- Default validity of future-roles consent
- Retention periods
- Own message templates (based on Uncava templates)

**System options to consider**
- Warning when an action needs consent and none is valid (upload CV, share profile with client, add to talent pool)
- Reminder plus template 30 days before future-roles consent expires
- Filter such as "talent pool with valid consent"

**Candidate rights to cover:** access, correction, deletion, withdrawal of consent.

---

## 5. Area 3 – Clients and documentation (13 Nov)

No action until areas 1, 2 and 4 are closed.

### 5a. Needs legal support (drafting or review)

| # | Document | Purpose |
|---|---|---|
| 1 | Data processing agreement (DPA) | Per client; annex to contract or accepted with terms. Client controls its candidates' data, Uncava processes on its behalf. |
| 2 | Terms of use | Published on website, accepted at account creation, referenced in contracts. Covers client use of Uncava data and the split of responsibility for data Uncava supplies (ContactOut). |
| 3 | Privacy notice for candidates | Public. Covers data Uncava supplies as controller. Clients use their own notices and may link to this one when data came from Uncava. |
| 4 | Privacy policy for users | Public. Client users' data (accounts, billing, activity). |
| 5 | DPIA for AI features | Internal; shown to regulators and large clients on request. Uncava drafts from the AI inventory, lawyers review. |
| 6 | Privacy notice template for clients | In-platform template for clients without their own notice (recruitment processing, sources, retention, rights). |

**Topics to discuss with lawyers:** passing ContactOut private contact details to clients (if the model stays), default retention periods, DIFC Regulation 10 for AI assessment, whether a **DPO** is needed.

### 5b. Documents Uncava can prepare itself (most also needed for ISO 27001)

Aleksandra asked Alok: *which of these are done, and do you need support?*

| # | Document | Visibility | Content |
|---|---|---|---|
| 1 | Record of processing activities (RoPA) | Internal, regulators on request | Data, sources, purposes, retention, recipients |
| 2 | List of subprocessors | Website or clients; attached to DPA | Each provider, data received, storage location |
| 3 | Data flow map | Internal; client compliance teams on request | Data movement between Uncava, clients, providers |
| 4 | Information security policy | Internal (ISO 27001); summary for clients | – |
| 5 | Access control policy | Internal (ISO 27001) | Roles, permissions, MFA, separation of client workspaces |
| 6 | Incident response and breach notification procedure | Internal; referenced in DPA | Who does what, who is notified |
| 7 | Retention and deletion policy | Internal; summarised in notice and DPA | Default periods; deletion incl. backups and subprocessors |
| 8 | Candidate request procedure | Internal | Access, correction, deletion, incl. access report |
| 9 | Backup and recovery procedure | Internal (ISO 27001) | – |
| 10 | Templates for clients | In platform | First-contact message with data source, consent confirmation, consent renewal request, response to access requests |

---

## 6. Area 4 – AI and call recordings (13 and 19 Nov)

Aleksandra sent `Uncava_AI_inventory.xlsx` (AI features in recruitment-process order; gaps to be reported). High-risk areas for discussion: **nationality, AI assessment, AI in meetings**.

### Agreed on 19 Nov

**Nationality and gender.** Delete AI inference of both. They may stay as optional fields (e.g. local companies, diversity reports) but must be filled by a human, without AI support.

**Changes agreed with Alok** (from the two tables in the 19 Nov email):

| Feature | Risk | Issue | Agreed change |
|---|---|---|---|
| Nationality | High | "South Asian" is an ethnic-origin category, i.e. sensitive data; inferring it from a name risks discrimination in selection. | AI does not infer nationality or origin. Optional "Local national" field with dropdown (UAE national / KSA national / Other GCC national / Not a local national / Not confirmed (default)), filled only from the candidate's own statement or CV. Optional "Nationality" field means citizenship, also only from the candidate's statement. Aggregated data in client reports. |
| Gender | Medium | Inferred without the candidate's statement and without a clear purpose. | Remove AI inference. If a client needs diversity reports: manually filled field, shown only in aggregated reports. |
| Assessment | High | Assessment of a person that affects decisions (PDPL Art. 18, DIFC Regulation 10). Risk of one-click approval and scores without interview evidence. Positive: a manual path exists and the AI never overwrites recruiter scores. | Each AI score confirmed before submitting; behavioural scores, personality and motivation only from notes or transcripts, otherwise "insufficient evidence"; sources shown per score; log of differences between AI draft and final version; rounded overall score; client report states the draft was AI-assisted. |
| Notetaker (Recall.ai) | High | Recording without consent may be a criminal offence in the UAE. Data goes to a subprocessor (Recall.ai). | Replace the "Ask [candidate] first" message with a mandatory confirmation of the candidate's consent, saved in the consent register **before the bot joins the call**; bot introduces itself at the start; default retention of recordings 90 days. |
| Live copilot | High | Real-time analysis of what the candidate says, which the candidate likely does not know about. Switches on automatically with the Notetaker, against privacy by default. | Copilot off by default, switched on separately. If on, the candidate must know the call is analysed by AI in real time (part of the consent confirmation). |

Note: the first row of the nationality image is cropped in the email, so a row above it (marked "...experience") is not readable.

---

## 7. Gap analysis

Status column reflects **only what the thread says**. Anything marked "Alok to confirm" is not stated in the thread and needs your input before replying.

### 7.1 Product / engineering gaps

| # | Requirement | Current state | Gap / action | Priority |
|---|---|---|---|---|
| P1 | Remove AI inference of nationality and gender | Currently inferred (per inventory) | Remove inference; add manual optional "Local national" / "Nationality" / gender fields; aggregated-only reporting | High |
| P2 | Consent register with statuses (grey, light green, dark green, orange, red) | Alok to confirm | Build statuses, fields (manual and automatic), expiry handling | High |
| P3 | Consent register export for audits | Alok to confirm | Build | High |
| P4 | Candidate access report includes consents | Alok to confirm | Build | High |
| P5 | Privacy-notice version management | Alok to confirm | Build; record version per candidate | High |
| P6 | Retention engine (6m / 12m / 24m / 3m defaults, client-editable) | Alok to confirm | Build scheduled deletion incl. backups and subprocessors | High |
| P7 | "Do not contact" minimal list (name + profile link) | Alok to confirm | Build; block re-adding | High |
| P8 | Notetaker: consent captured in register before bot joins; bot self-introduction; 90-day default retention | Currently "Ask [candidate] first" message | Replace with mandatory confirmation; enforce retention | High |
| P9 | Live copilot off by default, separate switch, candidate informed | Currently auto-on with Notetaker | Change default; add to consent confirmation | High |
| P10 | AI assessment controls (confirm each score, evidence-only behavioural scores, sources, change log, AI-assisted label) | Manual path exists; AI never overwrites recruiter scores | Add the five controls listed in section 6 | High |
| P11 | Automatic non-removable email footer (data source + privacy notice link) | Alok to confirm | Build | High (if ContactOut model stays) |
| P12 | Message templates (first contact with data source, consent confirmation, renewal, access-request response) | Alok to confirm | Create and make editable | Medium |
| P13 | Consent warning when action needs consent and none valid | Option | Decide | Medium |
| P14 | 30-day renewal reminder with template | Option | Decide | Medium |
| P15 | "Talent pool with valid consent" filter | Option | Decide | Low |
| P16 | Access control: roles, MFA, client workspace separation | Alok to confirm | Document and verify | Medium |

### 7.2 Data sources and vendors

| # | Requirement | Current state | Gap / action |
|---|---|---|---|
| V1 | Lawful basis for ContactOut private email/phone (high risk) | No clear basis | Decide: keep with mitigations, change model (client-owned ContactOut keys), or drop that feature |
| V2 | ContactOut contract (right to pass on, lawful collection) | Alok to confirm | Obtain or negotiate |
| V3 | Single profile provider | Alok suggested | Confirm decision |
| V4 | Hosting location and transfers outside UAE | Not covered yet | Map where data is stored; document transfers |
| V5 | Recall.ai as subprocessor | In use | Include in subprocessor list; DPA/transfer terms with Recall.ai |
| V6 | Other subprocessors (hosting, LLM providers, etc.) | Not in thread | Alok to list |

### 7.3 Legal documents (need lawyers)

| # | Document | State |
|---|---|---|
| L1 | DPA | Not started (waiting on areas 1, 2, 4) |
| L2 | Terms of use | Not started |
| L3 | Candidate privacy notice (public) | Not started |
| L4 | User privacy policy | Not started |
| L5 | DPIA for AI features | To be drafted from AI inventory, then lawyer review |
| L6 | Client privacy notice template | Not started |
| L7 | DPO question, DIFC Reg. 10 view, retention periods, ContactOut pass-through | Open for lawyers |

### 7.4 Internal documents (Alok / Uncava)

| # | Document | State |
|---|---|---|
| D1 | Record of processing activities | Alok to confirm |
| D2 | Subprocessor list | Alok to confirm |
| D3 | Data flow map | Alok to confirm |
| D4 | Information security policy | Alok to confirm |
| D5 | Access control policy | Alok to confirm |
| D6 | Incident response / breach notification | Alok to confirm |
| D7 | Retention and deletion policy | Alok to confirm (depends on P6 decisions) |
| D8 | Candidate request procedure | Alok to confirm |
| D9 | Backup and recovery procedure | Alok to confirm |
| D10 | Client templates | Alok to confirm |

---

## 8. Open decisions (reply checklist)

1. Do we adopt the "highest common standard" approach (PDPL consent + DIFC/ADGM requirements)?
2. Is KSA a priority now or later?
3. ContactOut: keep current model with mitigations, or move to client-owned keys? What about private email/phone lookups?
4. Approve consent statuses, default retention periods and consent fields as proposed?
5. Which system features are must-have for launch, and which optional ones do we take?
6. Confirm the AI changes (section 6) and agree a delivery date.
7. Which of the internal documents (7.4) already exist, and where do you need help?
8. Aleksandra to prepare recommendations for each area?
9. Send the full list of subprocessors and hosting locations.

---

## 9. Draft reply (edit the bracketed parts)

**Subject:** Re: data protection - Uncava

Hi Aleksandra, hi Hakan,

Thank you for the thorough work. My answers to the open points:

**Approach.** I agree with building to the highest common standard (PDPL consent approach plus DIFC/ADGM requirements). [KSA: later / priority now.]

**ContactOut.** [Decision: keep current model with the four mitigations / move to client-owned ContactOut keys / keep profile data and remove private email and phone lookup.] [Add what ContactOut's contract currently says, and whether it is a single provider.]

**Consent statuses and retention.** [Agree with the five statuses and defaults / changes.] I agree with the must-have features. From the options, I would take: [warning on missing consent / renewal reminder / valid-consent filter].

**AI features.** I agree with the changes we discussed: no AI inference of nationality or gender (human-filled optional fields only), score-by-score confirmation and evidence-only behavioural scores for assessments, consent confirmed in the register before the Notetaker joins with a 90-day default retention, and the live copilot off by default. I will plan delivery by [date].

**Documents we prepare ourselves.** Status: [list which of RoPA, subprocessor list, data flow map, security policy, access control policy, incident procedure, retention policy, candidate request procedure, backup procedure and client templates are done]. I would like support with [list]. I will also send the full list of subprocessors and hosting locations by [date].

**Next steps.** Yes, please prepare recommendations for each area. [Add a call date if useful.]

Best regards,
Alok
